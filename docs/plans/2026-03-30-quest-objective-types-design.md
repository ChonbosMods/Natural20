# Quest Objective Types: Full Implementation Design

**Date:** 2026-03-30
**Status:** Reference document. Implementation starts with FETCH_ITEM.

---

## Current State

KILL_MOBS is the only fully functional objective type. The other three are generated correctly (objective instances, variable bindings, dialogue summaries) but have no runtime tracking, completion detection, or item consumption on turn-in.

### Working KILL_MOBS Flow (Reference)

1. **Quest accepted** (GIVE_QUEST action): POI coordinates stored in quest bindings, POI marker placed on map
2. **Player approaches POI** (POIProximitySystem, 1s tick): When within 48 blocks, spawns mobs from `poi_spawn_descriptor`, sets `poi_mob_state: ACTIVE`, stores mob UUIDs
3. **Player kills mobs** (POIKillTrackingSystem, ECS DamageEventSystem): Matches victim UUID against `poi_mob_uuids`, increments objective progress
4. **All mobs killed**: Sets `phase_objectives_complete: true`, calls `refreshMarkers` which swaps POI marker for RETURN marker at settlement
5. **Player returns to NPC**: DialogueManager injects turn-in topic, player clicks "[Turn in]"
6. **TURN_IN_PHASE action**: Awards rewards, advances phase or completes quest, calls `refreshMarkers` which removes RETURN marker

### Shared Infrastructure

| Component | Location | Purpose |
|-----------|----------|---------|
| QuestTracker | `quest/QuestTracker.java` | Generic `reportProgress(type, targetId, amount)` and `reportCompletion(type, targetId)` |
| QuestMarkerProvider | `waypoint/QuestMarkerProvider.java` | Map markers: POI (center + ring) and RETURN (settlement) |
| POIProximitySystem | `quest/POIProximitySystem.java` | 1s tick, tracks player distance to POI, manages PENDING/ACTIVE/DETACHED state |
| POIPopulationListener | `quest/POIPopulationListener.java` | Spawns mobs at POI from `poi_spawn_descriptor` |
| POIKillTrackingSystem | `quest/POIKillTrackingSystem.java` | ECS DamageEventSystem, credits kills against quest objectives |
| DialogueActionRegistry | `action/DialogueActionRegistry.java` | GIVE_QUEST, TURN_IN_PHASE, COMPLETE_QUEST actions |
| DialogueManager | `dialogue/DialogueManager.java` | Injects turn-in topics when `phase_objectives_complete: true` |
| QuestRewardManager | `quest/QuestRewardManager.java` | Phase XP (30 + level*5, phase multiplier), loot rewards (stub) |
| QuestGenerator | `quest/QuestGenerator.java` | Generates quest instances with phases, objectives, and variable bindings |
| QuestPoolRegistry | `quest/QuestPoolRegistry.java` | Loads item/mob/narrative pools from JSON |

### Quest Variable Bindings (Runtime State)

| Key | Values | Used By |
|-----|--------|---------|
| `phase_objectives_complete` | "true" / absent | QuestTracker (set), DialogueManager (check), TURN_IN_PHASE (clear) |
| `poi_available` | "true" / "false" | QuestGenerator (set), POIProximitySystem (check), QuestMarkerProvider (check) |
| `poi_x`, `poi_y`, `poi_z` | coordinates | QuestGenerator (set), POIProximitySystem (read) |
| `poi_center_x`, `poi_center_z` | coordinates | QuestGenerator (set), QuestMarkerProvider (read) |
| `marker_offset_x`, `marker_offset_z` | -80 to +80 | QuestGenerator (set), QuestMarkerProvider (read) |
| `poi_mob_state` | PENDING/ACTIVE/DETACHED | POIProximitySystem (read/write) |
| `poi_mob_uuids` | comma-separated UUIDs | POIProximitySystem (write), POIKillTrackingSystem (read) |
| `poi_spawn_descriptor` | "role:count:x,y,z" | QuestGenerator (set), POIPopulationListener (read) |
| `quest_objective_summary` | string | QuestGenerator (set), QuestMarkerProvider (read label) |
| `subject_name` | string | TopicGenerator (set), QuestMarkerProvider (read label) |
| `gather_item_id` | item type ID | QuestGenerator (set), COLLECT tracking (future) |
| `quest_item` | item label | QuestGenerator (set), display |

### Pool Files

| Pool | File | Format | Used By |
|------|------|--------|---------|
| Hostile mobs | `quests/pools/hostile_mobs.json` | `{mobs: [{id, label, labelPlural}]}` | KILL_MOBS |
| Collect resources | `quests/pools/collect_resources.json` | `{items: [{id, label, labelPlural}]}` | COLLECT_RESOURCES |
| Evidence items | `quests/pools/evidence_items.json` | `{values: [{id, label, labelPlural}]}` | FETCH_ITEM |
| Keepsake items | `quests/pools/keepsake_items.json` | `{values: [{id, label, labelPlural}]}` | FETCH_ITEM |

---

## FETCH_ITEM

### Player Experience

1. NPC gives quest: "Find the stolen ledger at the Bone Cellar"
2. Map shows POI marker (same as kill quest: center icon + ring)
3. Player travels to POI, enters the area
4. When within 48 blocks: a chest spawns at the POI anchor point containing the quest item
5. Player opens chest, takes the item
6. Item enters inventory: POI marker removed, RETURN marker appears at settlement
7. Player returns to NPC, turns in quest
8. Quest item consumed from inventory, rewards given

### What Exists

- QuestGenerator creates FETCH_ITEM objectives with `requiredCount: 1`, `targetId` from evidence/keepsake pools
- POI coordinates are generated and stored in bindings (`poi_available`, `poi_x/y/z`, etc.)
- POIProximitySystem already handles PENDING→ACTIVE state transitions on approach
- QuestMarkerProvider already handles POI and RETURN markers

### What Needs To Be Built

**1. Chest spawning in POIProximitySystem**

When `poi_mob_state` transitions from PENDING to ACTIVE for a FETCH_ITEM objective:
- Instead of spawning mobs, spawn a chest at the POI anchor point
- Place the quest item inside the chest
- Store the chest's block position in bindings (e.g., `poi_chest_x/y/z`)
- The chest persists until the item is taken (no despawn needed)

Requires: ability to place a chest block and set its inventory contents. Reference `prefabworld-loot-chests.md` memory for ChunkStore.REGISTRY, setState, and block inventory patterns.

**2. Item pickup detection**

An ECS system (or extension of existing InventoryChangeEvent listener) that:
- On every inventory change, checks if the changed item matches any active FETCH_ITEM objective's `targetId`
- When match found: marks objective complete, sets `phase_objectives_complete`, calls `refreshMarkers`
- Must distinguish quest items from regular items of the same type (tag with quest ID or use unique item type)

Approach: Register an `EntityEventSystem<EntityStore, InventoryChangeEvent>` (same pattern as `Nat20EquipmentListener`). On each event, scan changed slots for quest item match.

**3. Item consumption on turn-in**

In TURN_IN_PHASE action, when the completed objective is FETCH_ITEM:
- Scan player inventory for the quest item
- Remove 1 of that item from inventory
- If item not found (player dropped it?): block turn-in or warn

**4. Quest item identification**

Options:
- **A. Unique item type per quest**: Register `nat20:quest_item_<questId>` items. Guarantees no collision. Requires dynamic item registration.
- **B. Tag via item metadata**: Use existing Hytale item metadata/NBT to tag items with quest ID. Check metadata on pickup.
- **C. Use the pool item ID directly**: `evidence_items.json` has IDs like "ledger", "letter", "manifest". If these map to real Hytale items, match by item type. Risk: player may already have that item type from other sources.

Recommendation: **Option C with a quest-specific flag.** Use the pool item ID as the Hytale item type, but store the quest ID in the item's custom data (if Hytale supports it). If custom data isn't available, accept the minor collision risk: quest items from evidence_items.json are narrative items (ledger, contract, signet ring) unlikely to appear naturally.

TBD: Verify whether Hytale item stacks support custom metadata tags.

---

## COLLECT_RESOURCES

### Player Experience

1. NPC gives quest: "Collect 8 Wood Logs"
2. No map marker (no POI). Quest appears in quest log/HUD only.
3. Player gathers resources through normal gameplay (chopping trees, mining, etc.)
4. When player's inventory contains >= 8 Wood Logs: RETURN marker appears at settlement
5. Player returns to NPC, turns in quest
6. 8 Wood Logs consumed from inventory, rewards given

### What Exists

- QuestGenerator creates COLLECT_RESOURCES objectives with `requiredCount: 4-10`, `targetId` from collect_resources pool
- `gather_item_id` stored in quest variable bindings
- collect_resources.json has IDs: RawMeat, Leather, Bone, Feather, WoodLog, Stone, IronOre, CottonFiber, Wheat, Clay, Flint, HerbBundle, CopperOre, Charcoal

### What Needs To Be Built

**1. Inventory scanning system**

A periodic check (not per-tick: too expensive. Every 5-10 seconds, or on InventoryChangeEvent) that:
- For each active quest with a COLLECT_RESOURCES objective in the current phase
- Counts the matching item type in player inventory
- Updates `objective.currentCount` to match inventory count
- When `currentCount >= requiredCount`: sets `phase_objectives_complete`, calls `refreshMarkers`

Key difference from FETCH_ITEM: COLLECT_RESOURCES tracks a COUNT of naturally-occurring items, not a single quest-specific item. The player may collect, use, and re-collect. The system should re-check periodically.

**2. Item consumption on turn-in**

In TURN_IN_PHASE action, when the completed objective is COLLECT_RESOURCES:
- Verify player still has >= requiredCount of the item
- Remove exactly requiredCount items from inventory
- If player no longer has enough (they used/dropped some): unset `phase_objectives_complete`, refresh markers, block turn-in

**3. Marker behavior**

- On quest accept: no marker (no POI)
- When resources collected: RETURN marker at settlement
- If player drops resources below threshold: RETURN marker removed (re-check on next scan)
- On turn-in: RETURN marker removed

**4. Item ID mapping**

The pool IDs (RawMeat, WoodLog, etc.) must map to Hytale item type IDs. TBD: verify the exact Hytale item type format (e.g., `Hytale:WoodLog` vs `hytale:wood_log`). May need a mapping table in the pool file or a naming convention.

---

## TALK_TO_NPC

### Player Experience

1. NPC A gives quest: "Speak with Elder Thalain about what happened"
2. Map shows yellow "!" marker at the settlement containing Elder Thalain
3. Player travels to that settlement, finds Elder Thalain
4. Player talks to Elder Thalain: a special quest dialogue topic is available
5. After the conversation: yellow "!" removed, green "?" RETURN marker appears at NPC A's settlement
6. Player returns to NPC A, turns in quest
7. Rewards given

### What Exists

- QuestGenerator creates TALK_TO_NPC objectives with `targetId: npcGeneratedName`, `locationId: targetSettlementCellKey`
- Target NPC selection: finds NPCs in nearby settlements, stores name and settlement info
- DialogueManager already injects turn-in topics for `phase_objectives_complete` quests

### What Needs To Be Built

**1. New marker type: TARGET_NPC**

In QuestMarkerProvider:
- Add `TARGET_NPC` to MarkerType enum
- New icon: yellow "!" (e.g., `QuestTarget.png`)
- In `refreshMarkers`: when quest has TALK_TO_NPC objective that is NOT complete AND `phase_objectives_complete` is not set:
  - Look up target NPC's settlement from objective's `locationId`
  - Create TARGET_NPC marker at that settlement's position
- When objective IS complete (but phase not turned in): create RETURN marker at source settlement
- This replaces the POI/RETURN binary: TALK_TO_NPC uses TARGET_NPC → RETURN flow

**2. Quest dialogue injection on target NPC**

In DialogueManager, when player talks to an NPC:
- Check if any active quest has a TALK_TO_NPC objective targeting this NPC (match by generated name)
- If found: inject a quest-related dialogue topic (e.g., "About the {quest_subject}...")
- The topic is a simple exchange: NPC shares information, player acknowledges
- On topic completion: mark objective complete, set `phase_objectives_complete`, refresh markers

This is similar to `injectTurnInTopics` but for the target NPC instead of the source NPC.

**3. No POI, no proximity system**

TALK_TO_NPC objectives should NOT trigger POIProximitySystem. The quest's `poi_available` should be "false" for these objectives. QuestGenerator already handles this: TALK_TO_NPC objectives don't set POI coordinates.

---

## Implementation Priority

1. **FETCH_ITEM** (closest to KILL_MOBS: same POI flow, adds chest spawn + item pickup)
2. **COLLECT_RESOURCES** (no POI, inventory scanning, item consumption)
3. **TALK_TO_NPC** (new marker type, dialogue injection on target NPC)

Each type shares the turn-in flow (TURN_IN_PHASE action, RETURN marker). The main differences are in tracking and completion detection.

---

## Shared Work Across All Three

**Item consumption in TURN_IN_PHASE**: Both FETCH_ITEM and COLLECT_RESOURCES need inventory scanning and item removal on turn-in. Build this once, parameterized by item ID and count.

**Marker refresh on objective completion**: All three types need to call `refreshMarkers` when their objective completes. The pattern is identical to KILL_MOBS: set `phase_objectives_complete: true`, save quest, call `refreshMarkers`.

**QuestMarkerProvider extensions**: TALK_TO_NPC needs a new TARGET_NPC marker type. FETCH_ITEM and COLLECT_RESOURCES use existing POI and RETURN types.
