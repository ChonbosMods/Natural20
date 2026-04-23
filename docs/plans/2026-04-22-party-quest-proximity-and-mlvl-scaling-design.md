# Party Quest Proximity + Monster-Level Scaling Design

**Status:** Locked. Ready for implementation planning.
**Date:** 2026-04-22
**Amends:** `docs/plans/2026-04-21-party-multiplayer-quest-design.md` (§4, §5, §6, §10)
**Scope:** Wire up party-proximity gating on phase-objective completion (new "Quest Missed" eviction) and party-size mlvl scaling on mob-group spawns.
**Non-goals:** Monster count/group-size scaling, dynamic mid-encounter rescaling, loot-distribution changes beyond existing rally-style rules, icon art for the Quest Missed banner.

---

## 1. Summary

Two features wired up, one proximity radius governs both:

1. **Quest-Missed eviction on phase-objective completion.** When a phase objective fully completes, every accepter more than 80 blocks from the triggering player (or offline) is evicted from that quest: banner, quest removed from their active list, no reward eligibility, no completion record.
2. **Strength-only mlvl scaling at mob-group spawn.** `mlvl_effective = mlvl_base + (nearby_party_members - 1)`, linear, clamped to `+6`. "Nearby" = online party members of the spawn trigger, within 80 blocks of them, inclusive of the trigger. Applied at spawn time, frozen for the life of those mobs.

Single constant `NAT20_PARTY_PROXIMITY = 80.0` drives both checks for MVP. Can be decoupled later as a tuning knob if playtest demands.

## 2. What this amends in 2026-04-21

Three rule changes, one rule preserved.

**§4 Progress.** Still true that progress ticks on any accepter's qualifying action. New: at the moment a phase objective fully completes, out-of-range / offline accepters are evicted (see §4 of this doc). Phase advancement still propagates to every non-evicted accepter, unchanged.

**§5 Reward model.** Turn-in bundle is now distributed to **surviving** accepters only, i.e. `accepters \ droppedAccepters`. The full `accepters` list is no longer the reward recipient set.

**§10 Completion banner.** The "absent-at-turn-in" banner variant planned for §10 is replaced by an earlier-firing **Quest Missed** banner at phase-objective completion. Evicted players see Quest Missed, not a late-firing completion banner with rewards. Surviving accepters still see the existing Quest Completed banner at turn-in regardless of their distance from the turn-in NPC.

**§2 Bedrock rule (preserved).** `accepters` is still frozen and immutable. Eviction is modeled as derived state via a new `droppedAccepters` set. Historical quest records always reflect the original accept-time roster.

## 3. Data model

### QuestInstance additions

```java
// On QuestInstance
private final Set<UUID> droppedAccepters = new HashSet<>();

public boolean isEligible(UUID player) {
    return accepters.contains(player) && !droppedAccepters.contains(player);
}

public Set<UUID> eligibleAccepters() {
    Set<UUID> out = new HashSet<>(accepters);
    out.removeAll(droppedAccepters);
    return out;
}
```

Migration is zero-cost: existing quests deserialize with an empty set. `droppedAccepters` persists alongside `accepters` in `Nat20PartyQuestStore`'s aggregate JSON.

### Nat20PartyQuestStore index semantics

The per-player index `Map<PlayerUuid, Set<QuestInstanceId>>` is maintained against **eligibility**, not raw accepters. When a player is added to `droppedAccepters`, the store removes that quest from their index entry in the same write. `QuestStateManager.getActiveQuests(player)` continues to read through the index: a dropped player sees the quest disappear from their active log instantly, identical to if they had never accepted.

### Nat20PlayerData additions

```java
// On Nat20PlayerData
private final List<PendingQuestMissedBanner> pendingQuestMissedBanners = new ArrayList<>();

public record PendingQuestMissedBanner(UUID questId, String topicHeader, long queuedAtEpochMs) {}
```

`topicHeader` is denormalized into the record so the banner can fire correctly even if the quest has since left the store (turned in by surviving accepters, or `Nat20PartyQuestStore` was reconciled).

### Turn-in and completion records

`CompletedQuestRecord` is written **only** for `eligibleAccepters()` at turn-in. Dropped accepters get no history: no completedQuests entry, no failed-quest entry, nothing. This matches the "flag it as if they weren't part of the party" rule: from their perspective, the quest vanished and never returned.

## 4. Quest Missed flow

### Single insertion point

All four objective types funnel their completion through `ObjectiveInstance.markComplete()` -> `QuestInstance.markPhaseComplete()` -> `Nat20PartyQuestStore`. We wrap the transition with a proximity sweep before phase advancement fires.

Pseudocode for the wrapper:

```
onPhaseObjectiveComplete(quest, triggeringPlayer):
    anchor = triggeringPlayer.position
    for uuid in quest.eligibleAccepters():
        if uuid == triggeringPlayer.uuid:
            continue
        memberPos = resolvePositionOrNull(uuid)           # null if offline
        if memberPos == null
                OR distance(memberPos, anchor) > NAT20_PARTY_PROXIMITY:
            quest.droppedAccepters.add(uuid)
            store.removeFromIndex(uuid, quest.id)
            if online(uuid):
                QuestMissedBanner.show(ref(uuid), quest)
            else:
                playerData(uuid).pendingQuestMissedBanners.add(
                    new PendingQuestMissedBanner(quest.id, quest.getTopicHeader(), now())
                )
    store.save()
    advancePhaseForEligibleAccepters(quest)                # unchanged path
```

One pass, one persist, one phase-advance. The phase advancement path is untouched from today: it just iterates `eligibleAccepters()` instead of `accepters`.

### Triggering player is always eligible

Distance-to-self is 0, so the killer/gatherer/talker/fetcher never self-evicts. Required invariant: the triggering player must be an accepter (already enforced by progress-ticking rules in §4 of 2026-04-21).

### Solo parties

Party of 1 means `eligibleAccepters()` has one member, who is the triggering player. The loop is a no-op. Zero runtime cost.

### Per-objective-type anchors

All four types use the **triggering player's current position**:

| Objective | Triggering player |
|---|---|
| `KILL_MOB` / `KILL_BOSS` | Player who landed the fatal blow (from the existing damage-contributor tracker's owner selection) |
| `COLLECT_RESOURCES` | Player who broke the final qualifying block |
| `FETCH_ITEM` | Player who handed over the final required item to the receiver |
| `TALK_TO_NPC` | Player who closed the qualifying dialogue state |

Each tracking system already identifies this player when it calls `creditOwner` today. We pass that player through to the proximity sweep.

### All-accepters-in-range case

The typical case: party is fighting together, all within 80 blocks of the killer. The loop distance-checks each, all pass, `droppedAccepters` stays empty, and the flow behaves identically to today.

## 5. QuestMissedBanner

New file `src/main/java/com/chonbosmods/quest/QuestMissedBanner.java`, mirroring `QuestCompletionBanner.java`:

```java
public final class QuestMissedBanner {
    public static void show(Ref<EntityStore> player, QuestInstance quest) {
        EventTitleUtil.showEventTitleToPlayer(
            player,
            Message.raw("Quest Missed"),
            Message.raw(quest.getTopicHeader()),
            /*isMajor=*/ true,
            /*icon=*/ null,
            /*duration=*/ 5.0,
            /*fadeIn=*/ 0.5,
            /*fadeOut=*/ 0.5
        );
    }
}
```

### Deferred banners on login

Offline accepters evicted during a phase completion get their banner queued on `Nat20PlayerData.pendingQuestMissedBanners`. On `PlayerConnectEvent` (or `PlayerReadyEvent`, whichever fires after the player entity is fully initialized per `store-thread-affinity` rules), drain the list: fire a banner per entry, then clear. Fires go through `world.execute` to stay on the world thread, per the `custom-page-open-world-thread` gotcha.

### Ghost case is silent (self-cleaning)

On turn-in, after distributing the Completion bundle to surviving accepters, the store iterates `quest.droppedAccepters`: for each uuid, remove any `pendingQuestMissedBanners` entry matching that `questId`. Then the quest record is deleted from the store. If a ghost player logs in a month later, their pending-banner list is already empty and they see nothing. The quest simply never existed for them.

## 6. Monster strength scaling

### Nat20PartyMlvlScaler helper

```java
public final class Nat20PartyMlvlScaler {
    private static final int MLVL_PARTY_CAP = 6;

    public static int apply(int baseMlvl, UUID triggeringPlayerUuid) {
        var party = Nat20PartyRegistry.get().getParty(triggeringPlayerUuid);
        int nearby = NearbyPartyCount.count(
            party.getMembers(),
            uuid -> Nat20PartyRegistry.get().isOnline(uuid),
            uuid -> resolvePositionOrNull(uuid),
            triggeringPlayerPosition,
            NAT20_PARTY_PROXIMITY
        );
        int bump = Math.min(Math.max(0, nearby - 1), MLVL_PARTY_CAP);
        return baseMlvl + bump;
    }
}
```

Every spawn site that knows a triggering player routes the base mlvl through this helper before passing it into `Nat20MobGroupSpawner.spawnGroup(...)`.

### Call-site wiring

1. **`POIGroupSpawnCoordinator.firstSpawn`** : triggering player = whoever entered POI spawn radius. Present in the POI-proximity event. Replace the plain base-mlvl lookup at the difficulty-roll step with `Nat20PartyMlvlScaler.apply(baseMlvl, triggeringPlayer)`.
2. **Ambient surface group spawns** (0.5% chunk-load roll per `ambient-surface-group-spawn-design`): triggering player = the player whose chunk load fired the roll. Present in the chunk-load event.
3. **`/nat20 spawngroup` debug command:** triggering player = command sender.
4. **Quest-authored group spawns tied to phase triggers:** triggering player = the phase-advancing accepter.

Any future spawn path added to the codebase must pass a triggering player through the helper. If a spawn genuinely has no identifiable triggering player (none exists today), the fallback is `baseMlvl` with no bump.

### Spawn-time freezing preserved

Consistent with §6 of 2026-04-21: once a group is placed in the world, its mlvl is fixed for the lifetime of those mobs. No dynamic rescaling as party members arrive or leave during the encounter. HP and damage curves already pegged to mlvl do the rest.

### No group-size scaling in MVP

Champion count, boss count, and base mob count are untouched by this design. Strength-only. Group-size scaling is deferred pending playtest of the mlvl-only lever.

## 7. Constants

| Name | Value | Used by |
|---|---|---|
| `NAT20_PARTY_PROXIMITY` | 80.0 blocks | Quest Missed sweep, mlvl scaler |
| `MLVL_PARTY_CAP` | +6 | mlvl scaler clamp |

Both live on a new `Nat20PartyTuning` class (or similar constants-only holder) so they can be playtested without hunting for magic numbers. The proximity constant is shared today but can be split into `QUEST_CREDIT_PROXIMITY` and `MLVL_SCALE_PROXIMITY` later if encounters cross-scale unexpectedly.

## 8. Edge cases

| Case | Handling |
|---|---|
| Triggering player is themselves offline somehow | Cannot happen: they just performed an action to trigger completion |
| All accepters are out of range (including triggering player somehow) | Cannot happen: triggering player is always in range of themselves |
| Quest has multiple phases and a player is evicted on phase 1 | They miss all subsequent phases. Turn-in distributes bundle only to phase-1 survivors ∩ phase-2 survivors ∩ ... ∩ phase-N survivors |
| Party member disconnects exactly as phase completes | `resolvePositionOrNull` returns null -> treated as offline -> evicted with deferred banner |
| Party member re-joins after being kicked/leaving | Per 2026-04-21 §3 they stay on `accepters`. Proximity rule applies to them the same as anyone else. If they wander back in-range before the next phase completes, they are still eligible (they were never dropped) |
| Solo party size 1 | Loop is a no-op. No wire-level change observable |
| Ghost accepter (never logs back in) | No banner, no history, quest record removed on turn-in with all ghost state cleaned up |
| Spawn trigger player leaves party between spawn and mob death | Irrelevant: mlvl frozen at spawn. Quest credit at death uses the death-triggering player's current party |

## 9. Open items

1. **Quest Missed banner icon.** MVP: none. Followup: greyed/X'd completion icon if art gets made.
2. **Party-size mlvl cap of +6.** Confirm in playtest. May tighten to +4 or loosen to +8 depending on large-party feel.
3. **Decoupled proximity radii.** `NAT20_PARTY_PROXIMITY` is shared between quest-credit and mlvl scaling. Keep coupled for MVP; split if cross-encounter mlvl scaling becomes a playtest issue.
4. **Banner flood on login.** If a ghost returns after missing 20 phase completions simultaneously, they get 20 banners on connect. Low priority. Coalesce to a single "You missed N quests while away" summary only if it becomes a real UX complaint.
5. **Non-player-triggered spawns.** Audit that every current spawn path has an identifiable triggering player. Fallback is base mlvl.

## 10. Decisions locked

- Proximity radius: **80 blocks** (governs both quest-credit and mlvl-scaling for MVP)
- Quest Missed trigger: **phase-objective completion only** (not per-tick, not per-phase-transition, not per-turn-in)
- Anchor for proximity sweep: **the triggering player's position**
- Offline at phase completion: **same as out-of-range** (evicted, deferred banner on login)
- Dropped player history: **none** (no completedQuests entry, no failed entry)
- Ghost case: **silent** (self-cleaning banner queue on turn-in)
- Turn-in bundle recipients: **surviving accepters only** (`accepters \ droppedAccepters`)
- Turn-in completion banner: **fires for every surviving accepter regardless of distance** (unchanged from 2026-04-21 §10)
- `accepters` bedrock immutability: **preserved** (eviction = derived set, not mutation)
- mlvl formula: **linear `mlvl += (nearby_party_members - 1)` clamped to +6**
- mlvl scope: **strength only** (group size unchanged)
- mlvl freeze: **at spawn time, fixed for mob lifetime** (no dynamic rescaling)
- mlvl call sites wired: **POIGroupSpawnCoordinator, ambient surface spawns, /nat20 spawngroup, quest-authored group spawns**

## 11. Known shortcomings

- **Hard 80-block cliff.** A party member at 81 blocks is evicted; at 79 they stay. No graceful falloff. Acceptable for MVP given playtest tuning via the single constant. A radius this generous should make the cliff feel fair in practice.
- **Ranged-kill weirdness.** A sniper killing a final-phase mob from 80 blocks out anchors the sphere at themselves, potentially evicting teammates still standing next to the dead mob. Mitigated by the 80-block radius being well beyond most ranged-combat distances; tightening the radius would make this worse. Acceptable.
- **No "rejoin the quest" path.** Once evicted, the player cannot re-claim the quest by returning to the party's location later in the arc. Matches the "treated as never accepted" framing and is deliberate. If it becomes a complaint, a post-MVP rescue mechanic could re-add them (un-drop) at turn-in if they are on the original accepters and within range when turn-in fires: explicitly out of scope here.
