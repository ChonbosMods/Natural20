# Modular Dungeon Prefab System: Design

## Overview

A grid-based modular prefab system for generating dungeons from composable pieces. Pieces snap together on a 5-block grid, share walls at connection seams, and are assembled at runtime via a growth algorithm.

**Sprint 1 (this sprint):** single-floor dungeons, no theme, growth algorithm, in-game authoring command.
**Sprint 2 (deferred):** vertical stacking (staircases, multi-floor), theme system with block substitution, growth style parameter.

---

## Grid Specification

| Property | Value |
|----------|-------|
| Base unit | 5x5x5 blocks |
| Floor height | 5 blocks (1 floor slab + 4 air) |
| Socket opening | 3 wide x 4 tall, centered on cell-face, sitting on floor slab |
| Origin | bottom-northwest corner, y=0 is floor slab |
| Wall overlap | 1 block shared at every seam |
| Dimension constraint | all prefab dimensions must be exact multiples of 5 |

**Interior space per unit:** a 5x5 footprint gives 3x3 interior air (wall, 3 air, wall). A 10x10 footprint (2x2 units) gives 8x8 interior air.

**Wall ownership:** every piece owns its own walls. Two adjacent pieces' walls overlap at the same block position. A 5-wide piece at origin x=0 spans x=0 through x=4. The next piece east starts at x=4 (not x=5). Formula: `next_origin = prev_origin + piece_block_width - 1`.

> **UNRESOLVED: Vertical unit height.**
> The table above says the base unit is 5x5x5, but counting actual blocks: floor slab at y=0, air at y=1-4, ceiling at y=5 = 6 blocks tall, not 5. A single-story piece is 5 wide x 6 tall x 5 deep. This means:
> - Block dimensions would be `gridWidth * 5` x `gridHeight * 6` x `gridDepth * 5`
> - Horizontal stride remains 4 (5 - 1 wall overlap)
> - Vertical stride would be 5 (6 - 1 ceiling/floor overlap)
> - The "all dimensions must be multiples of 5" rule doesn't apply uniformly to height
>
> The horizontal spec (5-block units, 1-block wall overlap, stride of 4) is confirmed correct. The vertical axis needs further analysis before locking in. All references to height in this doc should be treated as provisional until resolved.

---

## Coordinate System

Two data structures, serving different purposes:

### Grid-Cell Occupancy Map
A 3D boolean map where each cell is free or claimed. Used only for collision checking: "can I place a 2x1 piece at grid (3, 0, 2)?" Never used for coordinate math.

### Block-Origin Registry
When a piece is placed, its block-space origin is stored. When placing a connecting piece, the block origin is computed from the neighbor:

```
next_block_origin = neighbor_block_origin + neighbor_block_dimension_on_axis - 1
```

Cross-axis origin is derived from the connecting cell's local offset within the new piece.

**Why not `gridX * 4`?** A 2-unit-wide prefab is 10 blocks, but two adjacent 1-unit prefabs span only 9 blocks (wall sharing). The `gridX * 4` shortcut fails for multi-unit pieces. Block origins as source of truth are always correct regardless of piece size.

---

## Prefab Metadata Schema

Each piece has two files:
- `assets/Server/Prefabs/Nat20/dungeon/<name>.prefab.json`: block data
- `data/nat20/dungeon_pieces/<name>.json`: metadata

### Metadata format

```json
{
  "prefabKey": "Nat20/dungeon/tavern_2x2",
  "gridWidth": 2,
  "gridHeight": 1,
  "gridDepth": 2,
  "rotatable": true,
  "sockets": [
    { "localX": 0, "localZ": 0, "face": "north", "type": "open" },
    { "localX": 1, "localZ": 0, "face": "north", "type": "open" },
    { "localX": 0, "localZ": 0, "face": "west",  "type": "sealed" },
    { "localX": 1, "localZ": 0, "face": "east",  "type": "sealed" },
    { "localX": 0, "localZ": 1, "face": "south", "type": "open" },
    { "localX": 1, "localZ": 1, "face": "south", "type": "sealed" },
    { "localX": 0, "localZ": 1, "face": "west",  "type": "sealed" },
    { "localX": 1, "localZ": 1, "face": "east",  "type": "sealed" }
  ],
  "tags": ["room"],
  "weight": 5.0,
  "theme": null
}
```

**Sockets are per-cell-face, not per-piece-face.** A 2x2 piece has 8 perimeter cell-faces (4 internal faces omitted). Each gets its own socket type independently. This enables a 1x1 corridor to connect to one cell-face of a 2x2 room without ambiguity.

**Socket types (sprint 1):** `"open"` or `"sealed"`. Future sprint adds `"stair_up"`, `"stair_down"`, and theme-specific types.

**Tags:** categorize pieces for generation rules. Examples: `"hallway"`, `"room"`, `"entrance"`, `"boss_room"`, `"dead_end"`.

**Weight:** relative probability of selection. A piece with weight 10 is twice as likely as weight 5.

**Theme:** null for theme-neutral (sprint 1). Future sprint adds strings like `"crypt"`, `"mine"`. Theme-neutral pieces get block substitution to match the dungeon's selected theme.

### Vertical extension (sprint 2)

No schema restructure needed. Add `localY` to socket entries and `"top"`/`"bottom"` face values:

```json
{ "localX": 0, "localY": 1, "localZ": 0, "face": "top", "type": "stair_up" }
```

---

## Rotation System

**Generator-side rotation.** Author one prefab, the registry generates up to 4 rotational variants at load time.

### Transform rules

| Rotation | Face remap | Local coordinate transform |
|----------|-----------|---------------------------|
| 0 (none) | identity | identity |
| 90 CW | N→E, E→S, S→W, W→N | `newLocalX = localZ`, `newLocalZ = (gridWidth-1) - localX` |
| 180 | N→S, S→N, E→W, W→E | `newLocalX = (gridWidth-1) - localX`, `newLocalZ = (gridDepth-1) - localZ` |
| 270 CW | N→W, W→S, S→E, E→N | `newLocalX = (gridDepth-1) - localZ`, `newLocalZ = localX` |

Grid dimensions swap on odd rotations: a 2x1 piece at 90 degrees becomes 1x2. Each variant stores its own `gridWidth` and `gridDepth`.

`PrefabUtil.paste()` already accepts rotation (`None`, `CW90`, `CW180`, `CW270`), so block data rotation is free.

### Deduplication

At load time, sort each variant's socket array into canonical order and hash it. Identical socket configurations (e.g., 4-way crossroads) collapse to one variant. L-corridors correctly produce 4 distinct variants.

### Opt-out

`"rotatable": false` for asymmetric decorative pieces (fireplace against a specific wall, narratively-directional rooms). Default is `true`.

---

## Generation Algorithm

### Input
- Piece budget: min-max range (e.g., 8-15)
- Theme: null (sprint 1)
- World anchor position
- Optional bounding box

### Step 1: Initialize
Place the entrance piece at grid (0,0,0). Record block origin. Add all "open" cell-faces to the **open sockets list**. If guarantee rules require specific tags (e.g., "boss_room"), reserve one open socket: pick the socket whose target cell has the most free neighbors (gives the guarantee placement the most room to breathe).

### Step 2: Growth Loop
While unreserved open sockets remain and piece count < budget:

1. Pick a random unreserved open socket (the "anchor"). Default: random selection. Future sprint adds `growthStyle` parameter: `"random"` (branchy), `"depth"` (LIFO, snaking corridors), `"breadth"` (FIFO, compact layouts).
2. Compute target grid cell (anchor cell + facing direction). If outside optional bounding box, skip.
3. Find valid placements:
   - For each candidate variant in the registry, iterate all of its cell-faces that have `"open"` on the **opposite** face of the anchor
   - For each matching cell-face, compute the implied piece origin: `target_cell - matching_cell_local_offset`
   - Check that **every** cell the candidate would occupy from that origin is free in the occupancy grid
   - Collect all valid `(variant, origin)` pairs
4. Weighted random pick from valid pairs. If no valid pairs, skip this socket.
5. Compute block origin from anchor piece's block origin using `neighbor_origin + neighbor_dimension - 1` on the connection axis.
6. `PrefabUtil.paste()` the prefab.
7. **Carve the connection:** set the 3x4 block region on the shared wall face to air. Coordinates are deterministic: shared wall position from connection math, opening offset 1 block from corner horizontally, y+1 through y+4 vertically. One helper function, called once per connection.
8. Mark occupied grid cells in occupancy map, store block origin in registry.
9. Add new piece's open cell-faces to open sockets (excluding the connected one). Remove anchor from list.
10. If guarantee tags still unplaced, evaluate the new piece's sockets: reserve the one whose target cell has the most free neighbors, but only if it's better than the current reservation.

**Stall detection:** if all unreserved sockets are iterated without a successful placement, exit the loop. A smaller dungeon is better than an infinite loop.

### Step 3: Guarantee Placements
For each reserved socket, force-pick a piece matching the required tag (e.g., "boss_room"). If no valid piece fits the reserved socket, allow budget to flex by +1-2 pieces and try adjacent unreserved sockets before giving up.

### Step 4: Dead-End Capping
Iterate remaining open sockets. Try to place dead-end tagged pieces (1x1, one open face, rest sealed): treasure rooms, shrines, collapsed tunnels. These don't count against budget. Makes dead ends feel intentional rather than abrupt.

### Step 5: Seal
Any sockets still open after capping are already solid wall (pieces authored with sealed walls). Nothing to do.

---

## Wall Carving

Prefabs are authored with all walls solid. Connections are carved programmatically after pasting both pieces.

**Why carve-after-paste (not pre-cut openings):**
- Paste order doesn't matter
- No dependency on air-overwrites-solid behavior in `PrefabUtil.paste()`
- Unconnected faces are sealed by default with no cleanup pass
- One helper function with deterministic coordinates

**Carve spec:** 3 blocks wide (offset 1 from cell corner), 4 blocks tall (y+1 through y+4, floor slab at y+0, ceiling at y+5). Set all 12 blocks in this region to air on the shared wall face.

**Open question:** verify what `getBlock()` returns for air positions (null vs explicit air block type). Affects socket auto-detection in the authoring command.

---

## In-Game Authoring Command

### `/gridprefab save <name> <gridW> <gridH> <gridD> [rotatable]`

Author stands at bottom-northwest corner (the origin). The command:

1. Computes block region: `gridW*5` x `gridH*5` x `gridD*5` from player position
2. Gets a `LocalCachedChunkAccessor` from the world covering the bounding box
3. Iterates every position in the region, reading `getBlock()`, `getRotationIndex()`, `getFiller()` into a `PrefabBuffer`
4. During the same iteration, runs perimeter socket scan: for each block on a perimeter cell-face, checks if it falls within the 3x4 socket zone. If all 12 blocks in a cell-face's socket zone are air, marks that cell-face as `"open"`. Otherwise `"sealed"`.
5. Validates dimensions are exact multiples of 5 (should be guaranteed by grid params, but sanity check)
6. Warns if perimeter walls are not fully solid (excluding detected socket openings)
7. Writes prefab via `PrefabBufferUtil.writeToFileAsync()` to `assets/Server/Prefabs/Nat20/dungeon/<name>.prefab.json`
8. Writes metadata JSON to `data/nat20/dungeon_pieces/<name>.json` with auto-detected sockets, grid dimensions, `rotatable: true` by default, empty tags array, weight 1.0, theme null

### `/gridprefab preview <name>`
Paste a registered piece at the player's position for visual testing.

### `/gridprefab list`
List all registered dungeon pieces with their grid dimensions and socket summary.

### `/gridprefab validate <name>`
Re-run grid rule validation on an existing prefab: check dimensions, wall integrity, socket consistency.

---

## File Structure

```
assets/Server/Prefabs/Nat20/dungeon/
  corridor_straight.prefab.json
  corridor_L.prefab.json
  room_basic.prefab.json
  room_boss.prefab.json
  entrance.prefab.json
  dead_end_shrine.prefab.json
  ...

data/nat20/dungeon_pieces/
  corridor_straight.json
  corridor_L.json
  room_basic.json
  room_boss.json
  entrance.json
  dead_end_shrine.json
  ...
```

---

## Starter Piece Catalog (Minimum Viable)

To generate interesting single-floor dungeons, the minimum catalog:

| Piece | Grid Size | Open Faces | Tags | Notes |
|-------|-----------|------------|------|-------|
| entrance | 1x1x1 | south only | entrance | dungeon entry point |
| corridor_straight | 1x1x1 | north, south | hallway | rotates to E-W variant |
| corridor_L | 1x1x1 | north, east | hallway | rotates to 4 variants |
| corridor_T | 1x1x1 | north, east, south | hallway | rotates to 4 variants |
| corridor_cross | 1x1x1 | all 4 | hallway | 1 variant (symmetric) |
| room_small | 1x1x1 | north | room | rotates to 4 variants |
| room_medium | 2x1x2 | varies | room | standard gameplay room |
| room_boss | 2x1x2 | 1 open face | boss_room | guaranteed placement |
| dead_end_shrine | 1x1x1 | north | dead_end | caps unused sockets |
| dead_end_collapsed | 1x1x1 | north | dead_end | variety capping |

With rotation, this gives ~25-30 effective variants from 10 authored pieces.
