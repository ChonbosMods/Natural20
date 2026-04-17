# POI + Quest Group Spawn Integration: Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the legacy per-mob POI populator with group-spawn integration. Groups spawn via `Nat20MobGroupSpawner`, objective rewrites 50/50 between `KILL_COUNT` and `KILL_BOSS` at first spawn, and the group survives chunk unload + server restart via a persistent registry + 3-tier reconciliation (mirrors `SettlementRegistry` / `SettlementWorldGenListener`).

**Design doc:** `docs/plans/2026-04-16-poi-quest-group-spawn-integration-design.md`

**Tech stack:** Java 25, Hytale SDK (`BuilderCodec`, `Component<EntityStore>`, `ChunkPreLoadProcessEvent`, `NPCPlugin.spawnEntity`, `EntityStatMap`, `Nameplate`), Gson, JUnit-style runtime smoke via `/nat20` commands.

**Core references:**
- Design: `docs/plans/2026-04-16-poi-quest-group-spawn-integration-design.md`
- Settlement persistence pattern: `src/main/java/com/chonbosmods/settlement/SettlementRegistry.java` + `SettlementWorldGenListener.java`
- Current POI legacy code: `src/main/java/com/chonbosmods/quest/POIPopulationListener.java`, `POIProximitySystem.java`, `POIKillTrackingSystem.java`
- Spawner entrypoint: `src/main/java/com/chonbosmods/progression/Nat20MobGroupSpawner.java`
- Hytale codec patterns: @superpowers:using-superpowers -> use `Skill` tool to invoke `hytale-codec-config` when touching component codecs.
- NPC spawning: @superpowers:using-superpowers -> invoke `hytale-npc-spawning` when extending `Nat20MobGroupSpawner`.

---

## Task 0: UI refresh investigations (MUST land before Task 6+)

**Purpose:** The first-spawn coordinator rewrites the KILL_MOBS objective's `requiredCount`, `targetLabel`, and associated waypoint. Two UI surfaces may or may not re-resolve automatically when these fields mutate after quest-accept. Confirm behavior before committing to the rewrite-in-place design.

### 0.1: Waypoint label refresh

**Files:**
- Read-only investigation across: `src/main/java/com/chonbosmods/quest/**`, `src/main/resources/**waypoint**`, `src/main/resources/**map**`.

**Steps:**
1. Grep for `waypoint`, `WaypointManager`, `QuestMarker`, `setWaypointLabel`, `mapMarker`. Identify where the POI waypoint's display text comes from.
2. Determine: does the waypoint pull its label from `objective.getTargetLabel()` / quest bindings on every render, or does it cache at waypoint-create time?
3. If cached: identify the refresh hook (event, method call, dirty flag) the coordinator will need to invoke after rewriting the objective.

**Deliverable:** 2-3 sentence note committed at top of Task 6 as an inline comment, stating "waypoint label auto-refreshes" OR "waypoint label requires explicit refresh via `X.Y(...)`".

### 0.2: Quest-tracker UI refresh

**Files:**
- Read-only investigation across `src/main/java/com/chonbosmods/quest/**` and any UI templates under `src/main/resources/Common/UI/` touching quest state.

**Steps:**
1. Locate the quest-tracker rendering path (in-world HUD panel or open-quest page). Likely candidates: `QuestTrackerUI`, `QuestPage`, `ActiveQuestPanel`.
2. Determine whether the tracker re-reads `objective.getRequiredCount()` / `getCurrentCount()` / `getTargetLabel()` each render, or caches at objective-create time.
3. Confirm behavior when quest bindings are mutated post-creation: are `{mob_type}` / `{boss_name}` substitutions re-resolved on every render via `DialogueResolver.substituteAndClean`, or baked at objective-create time?

**Deliverable:** Same as 0.1: inline note at top of Task 7 stating "tracker auto-refreshes" OR the explicit refresh hook.

**Stop condition:** If either investigation finds the UI caches aggressively and offers no refresh hook, stop and flag for design revision before continuing. This would change Section 4 of the design doc (possibly requiring a new event type).

---

## Task 1: Register `Nat20MobGroupMemberComponent`

**Files:**
- Create: `src/main/java/com/chonbosmods/loot/mob/Nat20MobGroupMemberComponent.java`
- Edit: `src/main/java/com/chonbosmods/Natural20.java` (register in `setup()` before `withConfig()`)

**Step 1:** Write the component. `BuilderCodec` mirroring `Nat20MobAffixes`:

```java
package com.chonbosmods.loot.mob;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.codec.BuilderCodec;
// ...

public class Nat20MobGroupMemberComponent implements Component<EntityStore> {
    private String groupKey;
    private int slotIndex;

    public Nat20MobGroupMemberComponent() {}
    public Nat20MobGroupMemberComponent(String groupKey, int slotIndex) {
        this.groupKey = groupKey;
        this.slotIndex = slotIndex;
    }

    public String getGroupKey() { return groupKey; }
    public int getSlotIndex() { return slotIndex; }

    @Override public Nat20MobGroupMemberComponent clone() {
        return new Nat20MobGroupMemberComponent(groupKey, slotIndex);
    }

    public static BuilderCodec<Nat20MobGroupMemberComponent> buildCodec() {
        // String + int codec, keys "GroupKey" + "SlotIndex" (PascalCase per project rule)
    }
    public static ComponentType<EntityStore, Nat20MobGroupMemberComponent> getComponentType() { ... }
}
```

**Step 2:** Register the component type in `Natural20.setup()` before any `withConfig()` call, matching how `Nat20MobAffixes` is registered.

**Step 3:** `./gradlew compileJava` to confirm codec and registration compile.

**Verification:** No runtime verification here. Component is inert until attached in Task 5.

---

## Task 2: Add `MobGroupRecord` + `SlotRecord` + `Direction` enum

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/poi/MobGroupRecord.java`
- Create: `src/main/java/com/chonbosmods/quest/poi/SlotRecord.java`
- Create: `src/main/java/com/chonbosmods/quest/poi/PoiGroupDirection.java`

**Step 1:** Enum:

```java
package com.chonbosmods.quest.poi;
public enum PoiGroupDirection { KILL_COUNT, KILL_BOSS }
```

**Step 2:** `SlotRecord` as a mutable class (Gson-friendly) with fields per design §4.2.

**Step 3:** `MobGroupRecord` as a mutable class with fields per design §4.1. Include a static `keyFor(playerUuid, questId, poiSlotIdx)` helper that produces the canonical `groupKey`.

Both classes are plain POJOs serialized by Gson; no special codec. `List<RolledAffix>` is already Gson-serializable.

**Verification:** Compile only.

---

## Task 3: Create `Nat20MobGroupRegistry`

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/poi/Nat20MobGroupRegistry.java`
- Edit: `src/main/java/com/chonbosmods/Natural20.java` (instantiate in plugin init, expose getter)

**Step 1:** Copy `SettlementRegistry` as a structural template. Replace:
- `settlements.json` -> `mob_groups.json`.
- `SettlementRecord` -> `MobGroupRecord`.
- `cellKey` keying -> `groupKey` keying.
- Add a secondary lookup helper: `List<MobGroupRecord> forOwner(UUID playerUuid)`.

**Step 2:** Debounced async save via `AtomicBoolean savePending` + executor, same as settlements. Save-on-mutate: `put`, `remove`, `markSlotDead`, `updateCurrentUuid`.

**Step 3:** Wire registry instance into `Natural20`: instantiate with `pluginDataDir`, expose via `getMobGroupRegistry()`.

**Verification:** Compile. Runtime smoke deferred to Task 9 (`/nat20 spawngroup` after full wiring).

---

## Task 4: First-spawn coordinator

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/poi/POIGroupSpawnCoordinator.java`
- Edit: `src/main/java/com/chonbosmods/progression/Nat20MobGroupSpawner.java` (extract / expose a pre-rolled-record spawn entrypoint; see Step 2)

**Step 1:** Coordinator public API:

```java
public SpawnOutcome firstSpawn(
    World world, Vector3d anchor, String mobRole,
    UUID playerUuid, String questId, int poiSlotIdx,
    QuestInstance quest, ObjectiveInstance objective
);
```

**Step 2:** Sequence inside `firstSpawn`:
1. Check registry: if a record already exists for `groupKey`, bail (first-spawn should only run once per groupKey).
2. Roll `championCount` (3..7), `groupDifficulty` (via `Nat20MobScaleSystem.rollDifficultyWeighted`), boss-legendary upgrade (25% on EPIC).
3. Roll `direction` via `ThreadLocalRandom.current().nextBoolean()`.
4. Pre-roll shared champion affixes (`Nat20MobAffixManager.rollAffixes(CHAMPION, championDiff)`).
5. Pre-roll boss affixes (`Nat20MobAffixManager.rollAffixes(BOSS, bossDiff)`).
6. Pre-resolve boss nameplate (`Nat20MobNameGenerator.generate(bossDifficulty)` seeded; seed = `Objects.hash(groupKey, bossDifficulty.name())` so reconciliation reproduces the same name).
7. Build + persist `MobGroupRecord` with all rolled state and `SlotRecord` skeletons (all `isDead=false, currentUuid=null`).
8. Call extended `Nat20MobGroupSpawner.spawnFromRecord(world, record)` (Step 3 below). Receive `SpawnOutcome` with each slot's fresh UUID.
9. Update `record.slots[i].currentUuid` from the outcome. Flush registry.
10. Rewrite the objective per design §3.4. Populate quest bindings per design §3.5.
11. Return `SpawnOutcome`.

**Step 3:** Extend `Nat20MobGroupSpawner` with a new entrypoint `spawnFromRecord(World world, MobGroupRecord record)`:
- Resolve `roleIndex` from `record.mobRole`.
- For each slot not already satisfied by a live member (first-spawn: all slots), spawn the entity, attach `Nat20MobGroupMemberComponent(record.groupKey, slot.slotIndex)` immediately after spawn.
- For champion slots: call `scaleSystem.setTier(ref, store, CHAMPION)` + `applyDifficulty(...)` + `affixMgr.applyAffixes(ref, store, CHAMPION, championDiff, record.sharedChampionAffixes)`.
- For boss slot: same sequence with `BOSS` + `record.bossDifficulty` + `record.bossAffixes`. Set `Nameplate` using `record.bossName` (not re-rolled).
- Collect `Map<Integer, UUID> spawnedByIndex` and return.

Keep the existing `spawnGroup(...)` method in place for `/nat20 spawngroup` command usage; both paths call shared internals. Either refactor `spawnGroup` to use `spawnFromRecord` + an in-memory-only record, or keep them parallel for now (cleaner for Task 9 command smoke-test).

**Verification:** Compile. Deferred runtime test to Task 9.

---

## Task 5: Reconciliation listener

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/poi/MobGroupChunkListener.java`
- Edit: `src/main/java/com/chonbosmods/Natural20.java` (register via `registerGlobal(ChunkPreLoadProcessEvent.class, ...)`)

**Step 1:** Copy `SettlementWorldGenListener` as a structural template. Adapt:
- Iterate `Nat20MobGroupRegistry.all()` filtering by anchor-in-chunk (with 1-chunk buffer).
- Per record, per `!isDead` slot, run the 6-step reconciliation from design §5:
  1. Member-scan (iterate entities in anchor's 3x3 chunk neighborhood, match by `(groupKey, slotIndex)` on `Nat20MobGroupMemberComponent`).
  2. UUID-check with component-presence confirmation.
  3. `AtomicBoolean inFlight` per groupKey (guarded by `compareAndSet`).
  4. 500 ms debounce cooldown per groupKey (copy the settlements pattern).
  5. Spawn via `Nat20MobGroupSpawner.spawnFromRecord(...)` single-slot variant; apply stored state (no re-rolls).
  6. Post-spawn duplicate sweep.

**Step 2:** Add single-slot variant to `Nat20MobGroupSpawner`:

```java
public Ref<EntityStore> respawnSlot(World world, MobGroupRecord record, SlotRecord slot);
```

Spawns exactly one slot, attaches the member component, re-applies tier + difficulty + affixes (+ nameplate if boss) from record state. Does NOT roll anything new.

**Step 3:** Register listener in `Natural20.setup()` alongside `SettlementWorldGenListener`. Defer via `world.execute(...)` so native persistence finishes first.

**Verification:** Compile. Deferred runtime test to Task 9.

---

## Task 6: Rewire `POIProximitySystem` to call first-spawn coordinator

**Files:**
- Edit: `src/main/java/com/chonbosmods/quest/POIProximitySystem.java`

**Step 1:** Inline the Task 0.1 finding here: `// WAYPOINT_REFRESH: auto-refreshes` OR `// WAYPOINT_REFRESH: requires X.Y(...)`.

**Step 2:** Gut the state machine. New proximity check:
1. Read player's active POI quests (existing logic).
2. For each POI quest's objective: compute `groupKey`. Look up in `Nat20MobGroupRegistry`.
3. If record exists AND group has any `!isDead` slot: done (reconciliation handles respawn on chunk load; nothing to do here).
4. If record exists AND all slots dead: done (post-complete decay).
5. If no record AND player within spawn-trigger radius (use existing `SPAWN_RADIUS` constant): call `POIGroupSpawnCoordinator.firstSpawn(...)`.
6. Trigger waypoint refresh if the Task 0.1 investigation found an explicit hook is needed.

**Step 3:** Delete:
- `PENDING` / `ACTIVE` / `DETACHED` state machine.
- `transitionToActive`, `transitionFromDetached`, `spawnMobs` helpers.
- `LEASH_RADIUS_SQ` and associated leash math.
- Any reads / writes of `poi_mob_uuids`, `poi_detached_uuids`, `poi_mob_state`, `poi_spawn_descriptor`.

**Verification:** Compile. `./gradlew compileJava`.

---

## Task 7: Rewire `POIKillTrackingSystem` to dispatch by direction

**Files:**
- Edit: `src/main/java/com/chonbosmods/quest/POIKillTrackingSystem.java`

**Step 1:** Inline the Task 0.2 finding: `// TRACKER_REFRESH: auto-refreshes` OR explicit hook.

**Step 2:** New `processKill`:
1. Read `Nat20MobGroupMemberComponent` from the victim. Absent -> return (legacy path if any native-Hytale kill credit still exists, otherwise no-op).
2. Look up `MobGroupRecord` by `groupKey`. Absent -> stale component, return.
3. Locate slot. If `isDead` already -> return (double-credit guard).
4. Mark `isDead=true`, clear `currentUuid`, flush registry.
5. Dispatch:
   - `KILL_COUNT`: `obj.incrementProgress(1)`.
   - `KILL_BOSS`: `if (slot.isBoss) obj.incrementProgress(1);`.
6. Save quest state via `questSystem.getStateManager().saveActiveQuests(...)`.
7. On `obj.isComplete()`: existing quest-complete plumbing fires. Registry cleanup happens in Task 8.

**Step 3:** Delete:
- `spawnReplacement(...)` environmental-kill helper.
- Any direct reads of `poi_mob_uuids` from quest bindings.

**Verification:** Compile.

---

## Task 8: Quest lifecycle hooks (complete, abandon, stale sweep)

**Files:**
- Edit: `src/main/java/com/chonbosmods/quest/QuestStateManager.java` (or wherever complete / abandon currently fire)
- Edit: `src/main/java/com/chonbosmods/Natural20.java` (plugin init stale sweep)

**Step 1: Quest complete.** After existing complete handling (banner, XP, marker), call `Nat20MobGroupRegistry.remove(groupKey)` for every POI slot in the completed quest. No world entity cleanup needed: mobs decay to ordinary world hostiles.

**Step 2: Quest abandoned.** Same as complete, plus: for each slot with `currentUuid` that still resolves to a living entity, despawn it.

**Step 3: Plugin init stale sweep.** On plugin `setup()` after registry load:
1. Iterate `Nat20MobGroupRegistry.all()`.
2. For each record, check if `record.ownerPlayerUuid` has a `Nat20PlayerData` with an active quest matching `record.questId`. If absent, remove the record. No world cleanup (mobs not yet loaded).

**Verification:** Compile.

---

## Task 9: Smoke-test pass

**Runtime checklist** (per design §10 smoke-test plan). Run the devserver (from `main`, not a worktree, per the project's build rule). Execute in order, watching `server.log` for the new structured log lines:

1. `/nat20 spawngroup trork`: verify registry file `mob_groups.json` appears with one record, each spawned mob has `Nat20MobGroupMemberComponent`.
2. Accept a POI quest; walk into range. Confirm log line `GroupSpawn direction=KILL_COUNT|KILL_BOSS champions=N`. Confirm objective text + waypoint reflect direction.
3. Kill 2 champions, `/suicide`, run to unload the POI chunk, return. Confirm progress 2/N; `championCount - 2` champions + boss respawn; boss nameplate identical; shared champion affixes identical (log them on respawn).
4. `KILL_BOSS` run: champion kills do NOT increment progress; boss kill completes the quest; leftover champions remain alive.
5. Abandon quest mid-fight: record gone from `mob_groups.json`; any resolved `currentUuid` entities despawned within a tick.
6. `Ctrl+C` the devserver with survivors, restart it, load the POI chunk: verify survivors respawn with identical state.
7. Walk back-and-forth across anchor's chunk boundary 10x: no duplicate entities, no duplicate kill credit.

**On any failure:** revert the failing change, diagnose, amend plan, retry. Do not ship half-working states.

---

## Task 10: Commit

**Split into two commits** (preserve bisect-friendliness):

1. `feat(poi): persistent mob-group registry + reconciliation` — Tasks 1-5 (new code, no behavior change to live POI path yet).
2. `feat(poi): group-spawn integration with 50/50 direction roll` — Tasks 6-8 (wires the new pieces in, deletes legacy paths).

Task 9's smoke-test log output (if captured) can land as a separate doc commit.

Do not push. `main` rule: commit locally, user pushes.
