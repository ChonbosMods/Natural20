# POI + Quest Group Spawn Integration: Design

**Date:** 2026-04-16
**Status:** Design validated, ready for implementation plan
**Owner:** Zachariah (Chonbo's Mods)
**Supersedes:** `memory/poi-quest-group-spawn-integration.md` (older two-direction-at-authoring assumption)

## 1. Purpose

`Nat20MobGroupSpawner` (3-7 CHAMPION + 1 BOSS, rolled `DifficultyTier`, shared champion affixes, boss nameplate) is live and reachable via `/nat20 spawngroup` / `/nat20 spawntier`. POI quest objectives still run on the legacy per-mob `spawnMobs` / `poi_mob_uuids` / per-mob `PENDING -> ACTIVE -> DETACHED` path from before groups existed. The legacy path assumes `N calls = N individual mobs`, cannot survive chunk unload, and has no concept of a boss.

This design integrates the two. After the integration:

- POI quests spawn groups, not lone mobs.
- The KILL_MOBS objective is rewritten in place when the group first spawns: either `"Kill N {mob_type}"` (count) or `"Kill {boss_name}"` (boss-only), rolled 50/50 at spawn time.
- Groups survive chunk unload and reload without losing identity, affixes, nameplate, or quest progress.
- Double-spawn failure modes that burned the NPC system before are defended explicitly.

## 2. Scope

**In scope:**
- Persistent mob-group registry keyed by `groupKey`, serialized to `mob_groups.json`.
- `Nat20MobGroupMemberComponent` attached to every group mob: stable `(groupKey, slotIndex)` identity that does not rely on UUID.
- Chunk-load reconciliation listener mirroring `SettlementWorldGenListener`'s 3-tier pattern.
- 50/50 direction roll at first spawn, objective rewrite, variable bindings back-fill.
- Quest-lifecycle cleanup: complete, abandon, server restart, stale sweep.
- Retirement of legacy per-mob POI code paths.

**Out of scope:**
- World-scoped (multi-player shared) groups. Groups remain per-player-per-quest, matching the existing POI semantics.
- Dungeon-boss groups. `DUNGEON_BOSS` is still only reachable via `/nat20 spawntier`.
- Multi-group POIs. One POI still spawns one group.
- Mid-quest re-rolls. Direction and `championCount` are frozen at first spawn; survive chunk cycles and server restarts unchanged.

## 3. Foundational decisions

### 3.1 Per-player-per-quest scoping

**Decision:** `groupKey = "poi:{playerUuid}:{questId}:{poiSlotIdx}"`.

**Why:** Matches existing POI semantics (mobs live in the player's quest bindings today, not a shared world structure). Two players with the same POI quest each get their own group; they do not compete for kill credit or see each other's bosses. Keeps cleanup trivial (when the quest ends, the record ends).

**Trade-off:** Visual stacking if two players converge on the same POI. Acceptable for MVP; world-shared phasing is a separate future design.

### 3.2 Stable identity is the component, not the UUID

**Decision:** Every spawned mob carries `Nat20MobGroupMemberComponent(groupKey, slotIndex)`. Reconciliation, kill dispatch, and double-spawn defense all key off this component. UUIDs are treated as ephemeral cache.

**Why:** UUIDs rewrite on chunk reload when the engine revives a natively-persisted entity. Custom components get silently dropped when they don't. Neither is reliable; the combination of "component present AND matches expected key" is.

**Pattern source:** Mirrors `Nat20NpcData.getGeneratedName()` on settlement NPCs, where the name (not UUID) is the stable identifier that reconciliation matches against.

### 3.3 Direction is frozen at first spawn

**Decision:** The 50/50 roll between `KILL_COUNT` and `KILL_BOSS` happens once, inside the first-spawn coordinator. It is written to `MobGroupRecord.direction` and never mutated.

**Why:** User rule: "the quest objective is the ONLY thing that changes (which includes the waypoint name and what mob or mobs are flagged as quest objective mobs)." Freezing at first spawn guarantees exactly one mutation point and eliminates the "direction changed mid-quest" failure class. Server restart replays the same direction because the record is persisted.

**Roll source:** `ThreadLocalRandom.current().nextBoolean()`.

### 3.4 Objective semantics per direction

**KILL_COUNT (50%):**
- `requiredCount = championCount + 1` (boss is one of the kills)
- `targetLabel = mob_type_plural`
- Waypoint label = `"{mob_type_plural} camp"` (or equivalent, pending waypoint refresh investigation in Task 0)
- Every mob in the group carries the member component; every kill increments progress.

**KILL_BOSS (50%):**
- `requiredCount = 1`
- `targetLabel = boss_name`
- Waypoint label = `boss_name`
- Every mob in the group carries the member component, but only boss kills credit. Champions die for XP/loot only.

### 3.5 Variable bindings populated once, at spawn

**Decision:** After the objective rewrite, the coordinator writes these entries to `quest.getVariableBindings()`:
- `mob_type`, `mob_type_plural`
- `champion_count` (stringified int)
- `boss_name`
- `group_difficulty` (e.g. `"rare"`, for optional flavor in tracker / turn-in dialogue)

**Why:** Bearer offer dialogue already resolved at quest-accept time and does not re-resolve: these bindings only affect post-spawn surfaces (objective text, tracker, turn-in, mid-quest bark lines if any). `DialogueResolver.substituteAndClean` already handles `{token}` substitution without further plumbing.

**Highlight:** `boss_name` should be added to `HIGHLIGHTED_QUEST_VARS` in `DialogueResolver` so it renders like other entity references.

## 4. Data model

### 4.1 `Nat20MobGroupRecord` (persisted to `mob_groups.json`)

```
groupKey: String                     // "poi:{playerUuid}:{questId}:{poiSlotIdx}"
ownerPlayerUuid: String
questId: String
poiSlotIdx: int
mobRole: String                      // NPC role id for respawn lookups
anchor: Vector3d
spawnGenerationId: long              // bumped on each respawn cycle; used for duplicate rejection
groupDifficulty: DifficultyTier
bossDifficulty: DifficultyTier
direction: Direction                 // KILL_COUNT | KILL_BOSS, frozen
championCount: int                   // 3..7, rolled once
bossName: String                     // pre-resolved nameplate
sharedChampionAffixes: List<RolledAffix>
bossAffixes: List<RolledAffix>
slots: List<SlotRecord>
createdAtMillis: long
```

### 4.2 `SlotRecord`

```
slotIndex: int                       // 0..championCount-1 for champions; championCount for boss
isBoss: boolean
isDead: boolean                      // terminal; never respawn this slot once true
currentUuid: @Nullable String        // ephemeral; refreshed on every (re)spawn
```

### 4.3 `Nat20MobGroupMemberComponent` (new `Component<EntityStore>`)

```
groupKey: String
slotIndex: int
```

Registered in `Natural20.setup()` before `withConfig()` via `getEntityStoreRegistry().registerComponent(...)`. `BuilderCodec` patterned after `Nat20MobAffixes`.

### 4.4 `Nat20MobGroupRegistry`

`ConcurrentHashMap<String, MobGroupRecord>` keyed by `groupKey`. Debounced async save to `mob_groups.json` on mutation (same pattern as `SettlementRegistry`). Lookup index by `ownerPlayerUuid` for stale-record sweep.

## 5. Reconciliation flow

Listener on `ChunkPreLoadProcessEvent`, registered via `registerGlobal`, deferring work to `world.execute(...)` so native-persistence has finished before reconciliation reads the entity store. Same shape as `SettlementWorldGenListener`.

For each `MobGroupRecord` whose `anchor` falls inside the just-loaded chunk bounds (with a 1-chunk buffer):

For each `!isDead` slot, in order:

1. **Member-scan first.** Scan entities in the anchor's chunk plus 3x3 neighborhood for any bearing `Nat20MobGroupMemberComponent` matching `(groupKey, slotIndex)`. If found, set `slot.currentUuid = entity.getUuid()`, skip spawn, continue. *This is the primary double-spawn defense: members are identified by the component, not by the possibly-rewritten UUID.*

2. **UUID-check as confirmation only.** If member-scan found nothing and `slot.currentUuid != null`, try `world.getEntityRef(currentUuid)`. Accept only if the resolved entity *also* has the matching member component. Otherwise treat as stale.

3. **Per-group reconcile lock.** `AtomicBoolean inFlight` guarded by `compareAndSet` prevents two overlapping chunk-load events from racing to spawn the same slot.

4. **Debounce cooldown.** 500 ms per-`groupKey` cooldown, matching `SettlementWorldGenListener`.

5. **Spawn.** Only when `!isDead` AND member-scan empty AND UUID-check empty AND lock acquired AND cooldown expired. Spawn re-applies: scale tier, difficulty tier + tint, shared champion affixes (from record, not re-rolled), boss nameplate (same `bossName` string), boss affixes (from record). Attach `Nat20MobGroupMemberComponent`. Write new UUID to `slot.currentUuid`.

6. **Post-spawn sweep.** Immediately after step 5, re-run member-scan. If more than one entity bears the same `(groupKey, slotIndex)`, despawn everything except the one whose UUID matches `slot.currentUuid`.

Steps 1 and 6 together guarantee at most one live entity per slot. Steps 3 and 4 absorb races.

## 6. Kill tracking (direction-aware credit)

`POIKillTrackingSystem` rewired:

1. On kill, read `Nat20MobGroupMemberComponent` from the victim. Absent -> not a Nat20 group mob; fall through to legacy native-Hytale path if any exists.
2. Look up `MobGroupRecord` by `groupKey`. Locate slot by `slotIndex`. If `slot.isDead` already, return early (double-credit guard).
3. Mark `slot.isDead = true`, clear `slot.currentUuid`, flush registry.
4. Dispatch by `record.direction`:
   - `KILL_COUNT`: call `obj.incrementProgress(1)` regardless of `slot.isBoss`.
   - `KILL_BOSS`: call `obj.incrementProgress(1)` only if `slot.isBoss`.
5. Save quest state via existing `QuestStateManager` path.

Environmental deaths (lava, fall, suffocation) route the same way. Slot stays dead; no `spawnReplacement(1)` logic, no exceptions.

## 7. Lifecycle

| Event | Registry action | World mobs |
|---|---|---|
| First-spawn coordinator runs | Create record | Spawn group with member components |
| Chunk loads, record applies | No change (unless mutations from reconcile) | 3-tier reconcile per slot |
| Mob killed | Mark slot dead, clear UUID, flush | (nothing: entity already dead) |
| Quest completes | Delete record | Alive mobs decay into ordinary world mobs (no respawn driver, no kill credit) |
| Quest abandoned | Delete record | Immediate despawn for any `slot.currentUuid` that still resolves |
| Server restart | Registry loads from disk | Nothing yet; reconciliation on chunk loads |
| Plugin init stale sweep | Delete records whose owner + quest pair no longer matches an active `Nat20PlayerData` quest | Orphan mobs decay as above |

## 8. Retired code paths

After integration, delete (do not flag-gate):

- `POIPopulationListener.SpawnDescriptor` record + its `parse` / `writeSpawnDescriptor` methods.
- `POIPopulationListener.spawnMobs(World, String, int, int, int, int)` per-mob helper.
- `POIProximitySystem.PENDING / ACTIVE / DETACHED` state machine and its transitions.
- `POIProximitySystem.transitionFromDetached` rebound / per-mob leash logic.
- `POIKillTrackingSystem.spawnReplacement(...)` environmental-kill replacement path.
- Quest bindings keys: `poi_mob_uuids`, `poi_detached_uuids`, `poi_mob_state`, `poi_spawn_descriptor`. Active quests carrying these at migration time: accept the loss of legacy state; direction rolls fresh on next approach.

## 9. Risks and open unknowns

**Task 0 investigations (must complete before coding rewrites):**

1. **Waypoint label refresh.** Does the waypoint UI re-resolve its label from the objective / bindings every render, or cache at objective-create time? If cached, we need an explicit "objective mutated" signal to force redraw after direction roll.
2. **Quest-tracker UI refresh.** Same question for the in-UI objective text. Section 4 step 7 of the first-spawn flow depends on this being re-resolve-on-render or on having a refresh hook we can call.

**Known risks (pre-acknowledged):**

- `Nat20MobGroupMemberComponent` is a new codec-persisted component. Must register in `setup()` before `withConfig()`, per the project's repeated codec-order pitfall.
- Anchor-chunk scan radius: default to 3x3 chunks around anchor. Group mobs spread +/-3 blocks from anchor at spawn; wandering extends that. A 3x3 radius (96 block diameter) handles normal behavior. Revisit if smoke-test shows long-range wanderers escape the scan.
- Per-player scope means two players at the same POI get two stacked groups. Cosmetic-only issue for MVP.

## 10. Smoke-test plan

1. `/nat20 spawngroup trork` at an arbitrary point: registry has one record, every spawned mob carries `Nat20MobGroupMemberComponent`.
2. Accept a POI quest, walk into range: group spawns once, direction logged (`KILL_COUNT` or `KILL_BOSS`), objective text + waypoint reflect the rolled direction.
3. Kill 2 champions, `/suicide`, walk far enough for the POI chunk to unload, return. Progress reads 2/N; only `championCount - 2` champions + boss respawn; boss nameplate + affixes identical to session 1; shared champion affixes identical.
4. `KILL_BOSS` run: kill a champion, progress stays 0/1; kill boss, quest completes; leftover champions linger as world mobs, no longer respawning on chunk reload.
5. Abandon quest mid-fight: registry record gone, remaining group mobs despawned within a tick.
6. Server restart with surviving group: reconciliation respawns survivors on chunk load with identical state.
7. Chunk edge thrash (walk back-and-forth across the anchor's chunk boundary): post-spawn sweep logs zero duplicate incidents; no visible extra mobs.
