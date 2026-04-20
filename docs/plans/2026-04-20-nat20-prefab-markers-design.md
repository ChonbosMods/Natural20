# Nat20 Prefab Markers : Design

**Status:** locked, ready for implementation plan
**Date:** 2026-04-20

**POST-R&D UPDATE:** Marker asset IDs use underscores (flat keys `Nat20_Anchor`, `Nat20_Direction`, ...), **not** slashes (`Nat20/Anchor`). Block registration happens via Item JSONs with embedded `BlockType` sub-assets, authored under `src/main/resources/Server/Item/Items/nat20/Nat20_*.json`. The `.blockType.json` file format referenced in the original design does not exist in Hytale. See `docs/plans/2026-04-20-nat20-prefab-markers-plan.md` Schema notes section for full details. The design's architecture, semantics, and wire-up are unchanged : only the asset-registration mechanism and ID format were updated.

## Problem

Natural20 places prefabs for settlements and hostile POIs via `PrefabUtil.paste`. The placement pipeline today has three gaps:

1. **No per-cell author control over carving.** Painted `Empty` always carves; there is no way for the author to say "leave this cell untouched." The 148K `Empty` blocks in `tree1/tree2` work because we always force-overwrite, but that also means the prefab cannot embed itself into terrain.
2. **No in-prefab marker for NPC / mob-group / chest positions.** Settlement NPC offsets are hardcoded into `SettlementType.npcSpawns` (`NpcSpawnDef` with baked XZ); POI mobs scatter randomly around a code-chosen `Vector3d anchor`; fetch-quest chests drop at `poi_x/y/z` regardless of prefab geometry. Author cannot visually specify where things go.
3. **Anchor + direction collapsed into one coordinate.** The JSON-level `anchorX/Y/Z` pairs with a "which face is anchor on" heuristic to infer entrance direction. Works for Dungeon2Test but cannot represent anchor-plus-direction cleanly, and cannot survive non-axis-anchor setups.

Hytale's `Editor_Anchor / Editor_Block / Editor_Empty` blocks are the obvious fit but `/prefab save` strips them (`PrefabSaver.java:182, 228, 262`), so they can't be used as a persistent marker channel.

## Goals

- Author paints six semantic markers in PrefabMaker. Each survives `/prefab save` losslessly.
- Author controls which cells carve terrain and which let terrain bleed through.
- Settlements, hostile POIs, and fetch-quest chests consume marker positions instead of hardcoded offsets / random scatter / shared anchor.
- Existing subsystems (NPC role selection, mob-group rolling, loot tables) are untouched : only their input positions change.

## Non-goals

- Per-marker metadata (role name on each NPC marker, loot table on each chest, etc.). Metadata stays prefab-scoped via existing config (`SettlementType`, quest bindings).
- Backward compatibility with current prefabs. All five authored prefabs (`blackwoodHouse`, `cart_basic`, `outpost_basic`, `town_basic`, `testHouse`, `Dungeon2Test`, `tree1`, `tree2`) are deleted. Re-authored prefabs use the new markers from day one.

## Decisions

### D1 : Six custom plugin blocks carry all markers

Registered in the Natural20 asset pack under `assets/Server/BlockTypes/Nat20/`:

| Block key | Author semantic | Paste-time effect |
|---|---|---|
| `Nat20/Anchor` | entrance / paste origin | scanner records position; stripped from world |
| `Nat20/Direction` | direction pair partner for `Anchor` | scanner records position; direction vector = `direction.pos - anchor.pos`; stripped from world |
| `Nat20/Npc_Spawn` | NPC spawn candidate | scanner records position; stripped from world |
| `Nat20/Mob_Group_Spawn` | hostile mob-group anchor | scanner records position; stripped from world |
| `Nat20/Chest_Spawn` | fetch-quest chest candidate | scanner records position; stripped from world |
| `Nat20/Force_Empty` | force-carve this cell to air | rewritten to `Empty` before paste |

Custom plugin blocks work because `PrefabSaver.java` only filters the three `Editor_*` keys. Anything else round-trips through `/prefab save`.

### D2 : Reuse Hytale's `Editor_Empty` texture

Copy the vanilla `Editor_Empty` client texture into `assets/Client/Textures/Nat20/editor_marker.png`. All six marker blocks reference it. Rationale: visible during authoring (not invisible), familiar to anyone who has used vanilla editor blocks, zero art budget.

Zero collision server-side so the markers don't interfere with player movement during authoring. Solid material for paint compatibility with PrefabMaker tools.

### D3 : `Anchor` + `Direction` as a pair, not a single axis-aware anchor

- Exactly one `Nat20/Anchor` block per prefab.
- Exactly one `Nat20/Direction` block per prefab.
- Direction vector computed as `direction.localPos - anchor.localPos`. Must be axis-aligned and magnitude 1 (i.e. one of the six cardinal unit vectors). Magnitude-0 or diagonal offsets trigger a warning and snap to the dominant axis.
- The JSON top-level `anchorX/Y/Z` field is ignored post-flip. Our scanner's anchor is authoritative.

### D4 : `Empty` semantic flip

- **Before:** painted `Empty` (id 0) force-carves the world cell to air at paste.
- **After:** painted `Empty` is a passthrough : the filter drops the cell from vanilla paste's iteration; the world block remains unchanged.
- To carve: paint `Nat20/Force_Empty` instead. The filter rewrites its blockId to 0 (`Empty`) before the block callback fires, and vanilla paste force-overwrites the cell.

This flip lets authors use the default eraser tool (which paints `Empty`) to mean "don't touch," which matches natural authoring expectations.

### D5 : Vanilla authoring / spawner blocks are always stripped

`Editor_Anchor`, `Editor_Block`, `Editor_Empty`, `Prefab_Spawner_Block`, `Spawner_Rat`, `Block_Spawner_Block`, `Block_Spawner_Block_Large`, `Geyzer_Spawner1` are filtered out of every Nat20 paste. This is defensive : most are already stripped by `/prefab save`, but `.lpf` imports or future copy-paste workflows could reintroduce them.

### D6 : Loose entities discarded; block-state holders preserved

We call vanilla `PrefabUtil.paste` with `loadEntities=false`. Block-state `holder` values (lit lamps, chest inventory, sign text) ride along with each block's callback and are preserved by vanilla paste at `PrefabUtil.java:211-213`. Loose entity save-state (NPCs / drops saved inside the prefab) is dropped : all NPCs are spawned from `Npc_Spawn` markers via code, so we don't want prefab-embedded ones.

## Architecture

Four new types in `com.chonbosmods.prefab`:

### `Nat20PrefabConstants`

Holds resolved block IDs. Populated in `Natural20.setup()` after the asset pack loads:

```java
public static int anchorId, directionId, npcSpawnId, mobGroupSpawnId,
                   chestSpawnId, forceEmptyId;
public static IntSet stripIds;           // all 6 markers + 8 vanilla strip keys

public static void resolve() {
    var map = BlockType.getAssetMap();
    anchorId = map.getIndex("Nat20/Anchor");
    // ... etc, fail-fast on MIN_VALUE
    stripIds = new IntOpenHashSet();
    stripIds.add(anchorId);
    // ... etc
    for (String key : List.of("Editor_Anchor", "Editor_Block", "Editor_Empty",
                              "Prefab_Spawner_Block", "Spawner_Rat",
                              "Block_Spawner_Block", "Block_Spawner_Block_Large",
                              "Geyzer_Spawner1")) {
        int id = map.getIndex(key);
        if (id != Integer.MIN_VALUE) stripIds.add(id);
    }
}
```

### `MarkerScan` (record)

```java
public record MarkerScan(
    Vector3i anchorLocal,                 // exactly one
    Vector3i directionLocal,              // exactly one
    Vector3i directionVector,             // cardinal unit, snapped
    List<Vector3i> npcSpawnsLocal,
    List<Vector3i> mobGroupSpawnsLocal,
    List<Vector3i> chestSpawnsLocal
) {}
```

Produced by `Nat20PrefabMarkerScanner.scan(IPrefabBuffer)` : single `buffer.forEach` pass, buckets positions by block ID, validates counts, snaps direction vector to dominant axis.

### `Nat20FilteredBuffer`

Thin `IPrefabBuffer` wrapper around an inner buffer. All bounds, anchor, and column-count methods forward unchanged. Overrides `forEach` to intercept the block callback:

```java
inner.forEach(iter, (x, y, z, blockId, holder, support, rot, filler, c, fluidId, fluidLevel) -> {
    // Skip markers (scanner records them; they never hit the world)
    if (Nat20PrefabConstants.stripIds.contains(blockId)) return;
    // Skip plain Empty (passthrough)
    if (blockId == 0 && fluidId == 0) return;
    // Remap Force_Empty → Empty
    int outId = (blockId == Nat20PrefabConstants.forceEmptyId) ? 0 : blockId;
    blockCb.accept(x, y, z, outId, holder, support, rot, filler, c, fluidId, fluidLevel);
}, entityCb, childCb, call);
```

Vanilla `PrefabUtil.paste` does the rest : rotation math, chunk caching, fluid / support / holder writes.

### `Nat20PrefabPaster`

Entry point: `PlacedMarkers paste(IPrefabBuffer buffer, World world, Vector3i desiredAnchorWorld, Rotation yaw, ComponentAccessor<EntityStore> store)`.

Steps:

1. `MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer)`.
2. `Vector3i translation = desiredAnchorWorld - rotate(scan.anchorLocal, yaw)`.
3. Preload chunks for `[minX+t, maxX+t] × [minZ+t, maxZ+t]` as non-ticking (same pattern as existing `UndergroundStructurePlacer` / `Nat20PrefabBufferUtil` helper).
4. Defer 5 ticks after chunks load.
5. `PrefabUtil.paste(new Nat20FilteredBuffer(buffer), world, pasteAnchorWorld, yaw, true, rng, 0, store)`.
6. Rotate + translate every marker position in `scan` into world coords; return as `PlacedMarkers`.

### `PlacedMarkers` (record)

```java
public record PlacedMarkers(
    Vector3i anchorWorld,
    Vector3i directionVectorWorld,        // cardinal, post-rotation
    List<Vector3d> npcSpawnsWorld,
    List<Vector3d> mobGroupSpawnsWorld,
    List<Vector3d> chestSpawnsWorld
) {}
```

`Vector3d` for NPC/mob/chest because those feed into entity spawn systems that use doubles.

## Wire-up to existing systems

### Settlements

- `settlement/NpcSpawnDef.java` removed. Replaced by new `npc/NpcSpawnRole(String role, int count, double leashRadius, int disMin, int disMax)`.
- `SettlementType` entries stop baking positions:
    ```java
    TREE1("Nat20/tree1", 32, List.of(
        new NpcSpawnRole("Villager", 3, 6, 40, 80),
        new NpcSpawnRole("Guard",    1, 3, 30, 60)
    ), poiTypes, mobTypes)
    ```
- `SettlementPlacer.place(world, pos, type, yaw, store, random)` returns `PlacedMarkers` instead of `void`.
- `SettlementWorldGenListener.onChunkLoad`:
    1. Call `placer.place(...)`, receive `PlacedMarkers`.
    2. `Collections.shuffle(markers.npcSpawnsWorld(), seedRng)` for deterministic distribution per cell.
    3. Iterate `SettlementType.npcSpawns` in order; assign each role's `count` NPCs to shuffled markers sequentially.
    4. If marker count < required, log warning and spawn as many as possible.
    5. If marker count > required, surplus markers go unused.
- `NpcManager.spawnSettlementNpcs(store, world, SettlementType, origin, cellKey, placedAt)` refactored to per-spawn: `spawnSettlementNpc(store, world, NpcSpawnRole, worldPos, cellKey, placedAt)`.

### Hostile POI (`UndergroundStructurePlacer.placeAtVoid`)

Two behavior changes inside `placeAtVoid`:

1. **Rotation derivation.** Current: maps `wallDir` → hardcoded `Rotation` assuming prefab entrance is on +Z. Replaced by:
    ```java
    Vector3i wantedWorldDir = wallDirToAwayVector(bestWallDir);     // -X → (1,0,0), etc.
    Rotation yaw = computeYawToAlign(scan.directionVector, wantedWorldDir);
    ```
    `computeYawToAlign` is a four-entry lookup that returns the `Rotation` taking the prefab-local direction onto the world direction.

2. **Paste + marker return.** Swap `PrefabUtil.paste` for `Nat20PrefabPaster.paste`, propagate `PlacedMarkers` back to the caller.

Callers that need mob-group anchors iterate `placed.mobGroupSpawnsWorld()` and call `POIGroupSpawnCoordinator.firstSpawn(marker, ...)` once per position. `POIGroupSpawnCoordinator` itself is unchanged : it already takes a `Vector3d anchor` and delegates to `Nat20MobGroupSpawner.spreadAround(anchor, 3.0)`.

### Fetch-quest chests

Chest placement is lazy (on player proximity, `POIProximitySystem.maybePlaceQuestChest`), not at paste time. So the marker positions must be persisted from paste into the quest bindings so proximity can read them later.

1. At paste time (`UndergroundStructurePlacer.placeAtVoid` completion, or its caller in `DialogueActionRegistry.finalizePlacement`): write `poi_chest_positions` into the quest bindings as a semicolon-delimited coord list: `"x1,y1,z1;x2,y2,z2;..."`.
2. `POIProximitySystem.maybePlaceQuestChest` reads `poi_chest_positions` first. If present, pick position[0] (or seeded random). If absent, fall back to existing `poi_x/y/z` behavior.
3. `QuestChestPlacer.placeQuestChest(world, x, y, z, itemTypeId, itemLabel)` unchanged : only the input coords move.

For settlements that ship chests (not currently a use case, but possible future): `placed.chestSpawnsWorld()` is available to any caller that wants chest coords. No wire-up yet.

## Migration

No migration. The following prefabs are deleted in the same commit that ships the block registration:

```
assets/Server/Prefabs/Nat20/blackwoodHouse.prefab.json + .lpf
assets/Server/Prefabs/Nat20/cart_basic.prefab.json + .lpf
assets/Server/Prefabs/Nat20/outpost_basic.prefab.json + .lpf
assets/Server/Prefabs/Nat20/testHouse.prefab.json + .lpf
assets/Server/Prefabs/Nat20/town_basic.prefab.json + .lpf
assets/Server/Prefabs/Nat20/dungeon/Dungeon2Test.prefab.json + .lpf
assets/Server/Prefabs/Nat20/dungeon/Dungeon1Test.prefab.json
assets/Server/Prefabs/Nat20/dungeon/testDungeon.prefab.json + .lpf
assets/Server/Prefabs/Nat20/dungeon/b.prefab.json + .lpf
assets/Server/Prefabs/Nat20/dungeon/b1.prefab.json
assets/Server/Prefabs/Nat20/dungeon/b2.prefab.json
```

`tree1` / `tree2` under `UserData/Mods/nat20.nat20/Server/Prefabs/` are user files, not repo-managed. User deletes them before re-authoring.

New prefabs must be authored from scratch with the marker blocks. At minimum each prefab needs:
- One `Nat20/Anchor`
- One `Nat20/Direction` adjacent (1 block offset, cardinal)
- Zero or more spawns per role (settlements need `Npc_Spawn`; POIs need `Mob_Group_Spawn`; fetch POIs need `Chest_Spawn`)
- `Nat20/Force_Empty` wherever carving is intended

A new `/nat20 place <prefabKey>` admin command (in `commands/`) pastes a prefab at the player's feet using the new paster, for authoring iteration.

## Risks and open items

1. **Block-type JSON schema.** Hytale's `.blockType.json` format is not yet verified against SDK source. Will confirm against vanilla `BlockType` entries in `Assets.zip` before writing the six JSONs. Likely covered by `hytale-schema-generation` skill and `hytale-assets` skill.
2. **`Editor_Empty` texture path.** Need to locate the texture file inside Hytale's install directory and confirm we can copy + redistribute under our asset pack without licensing concerns. Worst case: draw our own 16×16 placeholder with a crosshatch pattern.
3. **`IPrefabBuffer` method enumeration.** `compare` and `getChildPrefabs` also need forwarding in the filtered buffer. `PrefabBufferUtil.getCached` returns an `IPrefabBuffer` implementation with a fixed interface ; will enumerate when writing the wrapper.
4. **Chest placement timing.** If `finalizePlacement` runs before `UndergroundStructurePlacer.placeAtVoid` completes asynchronously (chunk preload + 5-tick defer), `poi_chest_positions` may not be written when the player first approaches. Needs either: synchronous completion of the paste before quest finalization, or a fallback path in `POIProximitySystem` that waits for positions to appear.
5. **Rotation of marker positions.** Vanilla `PrefabUtil.paste` rotates block positions internally. Our scanner records local coords *before* rotation, then the paster applies `PrefabRotation.rotate` to convert to world coords. Need smoke test that rotated positions line up with rotated blocks.
6. **Fluid interaction with passthrough.** Vanilla paste writes fluids even for `blockId == 0` cells (line 164). Our filter currently drops cells where both `blockId == 0 && fluidId == 0`. If author paints a water block in a cell that we want to keep as passthrough, behavior is unclear. Current rule: any non-zero `fluidId` carries through. Worth a test.

## Summary

Six custom plugin blocks (`Nat20/Anchor`, `Nat20/Direction`, `Nat20/Npc_Spawn`, `Nat20/Mob_Group_Spawn`, `Nat20/Chest_Spawn`, `Nat20/Force_Empty`) registered in the Natural20 asset pack, reusing Hytale's editor-marker texture. `Empty` flips to passthrough; `Nat20/Force_Empty` replaces it for carving. A thin filter buffer around vanilla `PrefabUtil.paste` handles the semantic flip and strips all marker / vanilla-authoring blocks. A scanner extracts marker positions (locally), the paster rotates + translates them into world coords and returns `PlacedMarkers`. Existing settlement / POI / chest systems consume marker positions instead of hardcoded offsets / shared anchors. No migration : all current prefabs deleted, re-authored with markers.
