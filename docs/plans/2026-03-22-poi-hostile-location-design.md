# Point of Interest System: Hostile Location

**Date:** 2026-03-22
**Status:** Design complete, pending implementation

---

## Goal

Connect the cave structure placement system to the quest system so settlements can generate quests that send players to dungeon POIs. The first POI type is "hostile location": a dungeon prefab placed in a cave void, populated with mobs when the player accepts the quest and approaches.

## Architecture

### Flow

```
Settlement spawns → NPCs spawn → quest generated
                                       |
                          QuestGenerator rolls phase needing POI
                                       |
                          Query CaveVoidRegistry for nearby unclaimed void
                                       |
                          Claim void → place Dungeon2Test prefab
                                       |
                          Store POI coords + population spec in quest bindings
                                       |
                          Quest assigned to NPC for dialogue
                                       |
                 Player accepts quest → population request registered
                                       |
                 Player approaches POI → chunks load → mobs spawn
                                       |
                 Player kills 2 specific spawned mobs → objective complete
```

### Key Decisions

- **Prefab placed at quest generation time** (not settlement spawn): only places dungeons when a quest actually needs one, avoids wasted work
- **Mobs spawn on quest accept, chunk-aware deferred**: triggered on accept but only materializes when POI chunks load (matches vanilla Hytale pattern)
- **POI data lives in quest bindings**: no new registry, no new persistence file. Quest owns all POI state via existing variable binding system
- **DirectionUtil text hints only**: waypoints/compass markers deferred to later investigation
- **Specific entity tracking**: spawned mobs tagged by UUID, 2 kills of those specific entities required for completion

## Data Model

### Quest Bindings (new keys)

| Key | Value | Example |
|-----|-------|---------|
| `poi_x` | Entrance X coordinate | `"950"` |
| `poi_y` | Entrance Y coordinate | `"33"` |
| `poi_z` | Entrance Z coordinate | `"-133"` |
| `poi_type` | POI type identifier | `"hostile_location"` |
| `poi_populated` | Whether mobs/loot spawned | `"false"` |
| `poi_population_spec` | What to spawn | `"KILL_MOBS:Skeleton_Warrior:4"` |
| `poi_mob_uuids` | Spawned entity UUIDs (after population) | `"uuid1,uuid2,uuid3,uuid4"` |
| `location_hint` | DirectionUtil text | `"south-west, about 150 blocks"` |

### ObjectiveInstance

No structural changes. Existing fields used:
- `targetId`: `"poi:950,33,-133"` (POI coordinate string)
- `locationHint`: DirectionUtil text for dialogue
- `locationId`: same as targetId

### CaveVoidRecord

Existing `claimed` and `claimedBySettlement` fields reused. `claimedBySettlement` stores the settlement cell key of the quest-originating settlement.

## POI-Linked Objective Types

Any objective type can be POI-linked. The POI provides the location, the objective type determines what happens there:

| Objective Type | POI Population | Completion |
|---|---|---|
| EXPLORE_LOCATION | Nothing extra | Player within ~20 blocks of POI |
| KILL_MOBS | Hostile NPCs spawned inside | 2 kills of specific spawned entity UUIDs by quest holder |
| KILL_NPC | Named boss NPC spawned inside | That specific entity killed |
| FETCH_ITEM | Loot chest with quest item | Player picks up item from chest |

GATHER_ITEMS is NOT POI-linked: that objective type is for collecting world resources to deliver to an NPC.

For the first iteration, only KILL_MOBS is implemented.

## Quest Generation Changes

### QuestGenerator.generate()

When a phase variant's objective config specifies a POI type:

1. Query `CaveVoidRegistry.findNearbyVoids(settlementX, settlementZ, 100, 300)`
2. If void found: claim it, call `UndergroundStructurePlacer.placeAtVoid()`
3. Wait for placement to complete (returns entrance position)
4. Store POI bindings in quest: `poi_x/y/z`, `poi_type`, `poi_population_spec`
5. Compute `location_hint` via `DirectionUtil.computeHint(npcPos, poiPos)`
6. Build ObjectiveInstance with `targetId = "poi:X,Y,Z"`

If no void found within range: fall back to existing settlement-targeting EXPLORE_LOCATION or skip this objective type.

## Quest Accept → Deferred Population

### On accept

1. `QuestSystem` processes accept, checks if quest has `poi_population_spec` with `poi_populated = "false"`
2. Registers a **pending population** keyed by POI coordinates: mob role, count, quest instance ID
3. Registers a chunk-load listener for the POI's chunk region

### On chunk load near POI

1. Listener detects POI chunks loaded (player approaching)
2. Spawns mobs at interior positions within the prefab using `NPCPlugin.spawnEntity()`
3. Records spawned entity UUIDs in quest bindings (`poi_mob_uuids`)
4. Sets `poi_populated = "true"`
5. Deregisters the chunk listener (one-shot)

### Kill Tracking

1. Existing NPC death event listener in `QuestTracker`
2. On mob death: check if killer is a player with an active quest
3. Check if dead entity's UUID is in that quest's `poi_mob_uuids`
4. If match: increment kill count on the objective
5. When kill count reaches 2: log success, mark objective complete

## Mob Spawning Details

- Use `NPCPlugin.spawnEntity()` (not `entityChunk.storeEntityHolder`) so mobs get full behavior trees
- Spawn at interior positions within the prefab footprint, offset from anchor
- Mobs get a hostile role (e.g. Skeleton_Warrior) so they attack on sight
- Random yaw for variety
- Spawn 4 mobs, require 2 kills (player doesn't have to clear the whole dungeon)

## Test Scenario

1. Boot dev server with default worldgen
2. Walk until settlement spawns with quest-bearing NPC
3. Talk to NPC, receive quest with hostile location objective
4. Direction hint in dialogue: "There's trouble in a cave to the south-west, about 150 blocks from here"
5. Travel to cave system, find dungeon entrance embedded in tunnel wall
6. Approach triggers mob spawning inside
7. Enter dungeon, kill 2 of the 4 Skeleton Warriors
8. Quest objective marked complete in log

## Files to Change

1. **QuestGenerator.java**: Add POI path when rolling objectives, void query + claim + placement
2. **QuestSystem.java**: On quest accept, register pending population if poi_populated = false
3. **QuestTracker.java**: Add UUID-based kill tracking for POI mobs
4. **New: POIPopulationListener.java**: Chunk-load listener that spawns mobs/loot at POI when chunks load
5. **CaveVoidsCommand.java**: Optional: add a `testquest` subcommand for manual testing

## Out of Scope

- Map waypoints / compass markers
- Multiple POI types (only hostile_location)
- Multiple prefabs (only Dungeon2Test)
- Boss NPC spawning (KILL_NPC objective)
- Chest loot population (FETCH_ITEM objective)
- POI discovery without a quest
- Quest journal UI integration
