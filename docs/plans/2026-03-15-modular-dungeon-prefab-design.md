# Modular Dungeon Prefab System: Design

## Overview

A grid-based modular prefab system for generating dungeons from composable pieces. Pieces snap together on a 5-block grid with double walls at seams, connected by swappable connector prefabs, and assembled at runtime via a growth algorithm.

**Sprint 1 (this sprint):** single-floor dungeons, no theme, growth algorithm, connector prefabs, in-game authoring commands.
**Sprint 2 (deferred):** vertical stacking (staircases, multi-floor), ceiling/roofing system, theme system with block substitution, growth style parameter.

---

## Grid Specification

| Property | Value |
|----------|-------|
| Base unit | 5x5 blocks (horizontal footprint) |
| Floor height | 5 blocks (1 floor slab + 4 air) |
| Connector opening | 3 wide x 4 tall, centered on cell-face, on floor slab |
| Origin | bottom-northwest corner, y=0 is floor slab |
| Wall model | double walls: each piece owns all its own walls, adjacent pieces produce 2-block-thick seams |
| Dimension constraint | all horizontal prefab dimensions must be exact multiples of 5 |

**Interior space per unit:** a 5x5 footprint gives 3x3 interior air (wall, 3 air, wall). A 10x10 footprint (2x2 units) gives 8x8 interior air.

**Wall ownership:** every piece owns all four of its own walls completely. A 5-wide piece at origin x=0 spans x=0 through x=4 including both walls. Two adjacent pieces produce a double wall at the seam: piece A's east wall at x=4, piece B's west wall at x=5. The next piece east starts at x=5 (not x=4). Formula: `next_origin = prev_origin + piece_block_width`.

**Why double walls (not shared walls):**
- Each piece is fully self-contained: no overlap math, no stride adjustments
- Connector prefabs overwrite both wall columns at connection points, giving 2 blocks of depth for door frames, arches, and recessed doorways
- Sealed faces are the default state: both walls remain intact with no connector pasted
- Room merging: an "open wall" connector can remove both walls to combine adjacent rooms into larger spaces

> **DEFERRED: Vertical axis.**
> Height handling (ceiling/roofing, vertical stacking, stair connectors, multi-floor pieces) is deferred to sprint 2. Sprint 1 pieces are single-floor only. All references to y=5 ceiling and vertical dimensions in this doc are provisional and will be redesigned when vertical stacking is addressed.

---

## Coordinate System

Two data structures, serving different purposes:

### Grid-Cell Occupancy Map
A 3D boolean map where each cell is free or claimed. Used only for collision checking: "can I place a 2x1 piece at grid (3, 0, 2)?" Never used for coordinate math.

### Block-Origin Registry
When a piece is placed, its block-space origin is stored. When placing a connecting piece, the block origin is computed from the neighbor:

```
next_block_origin = neighbor_block_origin + neighbor_block_dimension_on_axis
```

Cross-axis origin is derived from the connecting cell's local offset within the new piece.

**Why not `gridX * 5`?** A 2-unit-wide prefab is 10 blocks, two adjacent 1-unit prefabs also span 10 blocks (5 + 5, no overlap). The shortcut `gridX * 5` happens to work for uniform piece sizes, but the block-origin registry formula works correctly for all piece sizes without special cases.

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

**Socket types (sprint 1):** `"open"` or `"sealed"`. Open means a connector can be placed here. Sealed means the wall is blocked (furniture, decoration, or intentionally closed).

**Tags:** categorize pieces for generation rules. Examples: `"hallway"`, `"room"`, `"entrance"`, `"boss_room"`, `"dead_end"`.

**Weight:** relative probability of selection. A piece with weight 10 is twice as likely as weight 5.

**Theme:** null for theme-neutral (sprint 1). Future sprint adds strings like `"crypt"`, `"mine"`. Theme-neutral pieces get block substitution to match the dungeon's selected theme.

### Vertical extension (deferred to sprint 2)

Vertical stacking, stair connectors, and multi-floor pieces are deferred. When implemented, add `localY` to socket entries and `"top"`/`"bottom"` face values. Ceiling/roofing system will be designed separately.

---

## Connector Prefab System

Connections between pieces are handled by **connector prefabs**: small block structures pasted at the seam between two adjacent pieces. The connector overwrites both walls at the connection point.

### Connector dimensions

- **5 wide:** full cell-face width along the wall
- **4 tall:** y=1 through y=4 (floor slab at y=0 is preserved)
- **2 deep:** spans both walls (one from each piece)

### Connector types

Examples: plain opening (all air in the passage zone), door frame, double door, stone arch, timber frame, iron gate. An "open wall" connector removes both walls entirely, enabling room merging: two adjacent 5x5 rooms become one continuous space.

**Sealed connections** use no connector: both walls remain intact as the default state. No paste needed.

### Connector metadata

Each connector has two files:
- `assets/Server/Prefabs/Nat20/dungeon/connectors/<name>.prefab.json`: block data
- `data/nat20/dungeon_connectors/<name>.json`: metadata

```json
{
  "prefabKey": "Nat20/dungeon/connectors/stone_arch",
  "type": "connector",
  "tags": ["arch"],
  "weight": 5.0,
  "theme": null
}
```

Dimensions are always 5x4x2, so they are not stored in metadata. `tags` enable theme-driven connector selection in sprint 2 (e.g., crypt theme picks connectors tagged `"stone"`, mine theme picks `"timber"`). `weight` controls selection probability.

### Connector selection

**Sprint 1:** weighted random from all available connectors.
**Sprint 2:** theme-driven: the dungeon's theme filters the connector pool by tag.

### Connector rotation

Connectors are authored in a canonical orientation (north-south: 5 wide in X, 2 deep in Z). For east-west connections, the connector is rotated 90 degrees. `PrefabUtil.paste()` already supports rotation (`None`, `CW90`, `CW180`, `CW270`).

### Connector paste position

The connector occupies the 2-block-deep zone at the seam between two pieces. For a connection where piece A is west and piece B is east:
- Piece A's east wall is at `A_origin_x + A_width - 1`
- Piece B's west wall is at `B_origin_x`
- Connector origin X = `A_origin_x + A_width - 1` (starts at A's wall, extends 2 deep through B's wall)
- Connector origin Y = piece origin Y + 1 (skip floor slab)
- Connector origin Z = cell-face origin Z (aligned to the cell)

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
5. Compute block origin: `neighbor_block_origin + neighbor_block_dimension` on the connection axis.
6. `PrefabUtil.paste()` the prefab.
7. Record the connection (anchor piece + face, new piece + face) for the connector pass.
8. Mark occupied grid cells in occupancy map, store block origin in registry.
9. Add new piece's open cell-faces to open sockets (excluding the connected one). Remove anchor from list.
10. If guarantee tags still unplaced, evaluate the new piece's sockets: reserve the one whose target cell has the most free neighbors, but only if it's better than the current reservation.

**Stall detection:** if all unreserved sockets are iterated without a successful placement, exit the loop. A smaller dungeon is better than an infinite loop.

### Step 3: Guarantee Placements
For each reserved socket, force-pick a piece matching the required tag (e.g., "boss_room"). If no valid piece fits the reserved socket, allow budget to flex by +1-2 pieces and try adjacent unreserved sockets before giving up.

### Step 4: Dead-End Capping
Iterate remaining open sockets. Try to place dead-end tagged pieces (1x1, one open face, rest sealed): treasure rooms, shrines, collapsed tunnels. These don't count against budget. Makes dead ends feel intentional rather than abrupt.

### Step 5: Connector Pass
Iterate every recorded connection. For each:
1. Determine the connection axis and direction
2. Compute connector paste position (seam between the two pieces' walls)
3. Pick a connector prefab (weighted random from available pool)
4. Rotate the connector to align with the connection axis
5. `PrefabUtil.paste()` the connector, overwriting both wall columns

### Step 6: Seal
Any sockets still open after capping already have solid double walls. Nothing to do.

---

## Sealed Face Detection (Marker Block)

Authors mark cell-faces as "no connection allowed" by placing a **marker block** in the 3x4 socket zone of that face. The marker block is an existing unused block type (e.g., poison root block, thorium ore in mud) that the author places anywhere within the socket zone.

### Save pipeline socket detection

For each perimeter cell-face, the save command scans the 3x4 zone (3 wide centered, y=1 through y=4):

1. If any marker block found in the zone: mark `"sealed"`. Replace all marker blocks in the zone with the dominant wall block type (majority vote of non-marker blocks in that wall column).
2. If no marker block found: mark `"open"`.

This keeps authoring fully in-game: no hand-editing metadata to block connections. Place furniture or decorations behind the wall, drop a marker block in the socket zone, and the save command handles the rest.

**Open question:** verify what `getBlock()` returns for air positions (null vs explicit air block type). Affects how the save command distinguishes wall material from empty space.

---

## In-Game Authoring Commands

### `/gridprefab save <name> <gridW> <gridH> <gridD> [rotatable]`

Author stands at bottom-northwest corner (the origin). The command:

1. Computes block region: `gridW*5` x `gridH*5` x `gridD*5` from player position
2. Gets a `LocalCachedChunkAccessor` from the world covering the bounding box
3. Iterates every position in the region, reading `getBlock()`, `getRotationIndex()`, `getFiller()` into a `PrefabBuffer`
4. During the same iteration, runs perimeter socket scan: for each block on a perimeter cell-face, checks if it falls within the 3x4 socket zone. Detects marker blocks for sealed faces, marks remaining faces as open.
5. Replaces any marker blocks in sealed socket zones with the dominant surrounding wall block type
6. Validates dimensions are exact multiples of 5 (should be guaranteed by grid params, but sanity check)
7. Warns if perimeter walls are not fully solid (excluding the floor slab row at y=0)
8. Writes prefab via `PrefabBufferUtil.writeToFileAsync()` to `assets/Server/Prefabs/Nat20/dungeon/<name>.prefab.json`
9. Writes metadata JSON to `data/nat20/dungeon_pieces/<name>.json` with auto-detected sockets, grid dimensions, `rotatable: true` by default, empty tags array, weight 1.0, theme null

### `/gridprefab saveconnector <name>`

Author builds a 5x4x2 connector structure in-game, stands at the origin, runs the command. Validation:
- Must be exactly 5x4x2 blocks
- Must have at least one air block (walkable passage)
- Warns if no walkable path through the connector

Writes prefab to `assets/Server/Prefabs/Nat20/dungeon/connectors/<name>.prefab.json` and metadata to `data/nat20/dungeon_connectors/<name>.json`.

### `/gridprefab preview <name>`
Paste a registered piece at the player's position for visual testing.

### `/gridprefab list`
List all registered dungeon pieces and connectors with their dimensions and socket summary.

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
  connectors/
    open.prefab.json
    door_frame.prefab.json
    stone_arch.prefab.json
    ...

data/nat20/dungeon_pieces/
  corridor_straight.json
  corridor_L.json
  room_basic.json
  room_boss.json
  entrance.json
  dead_end_shrine.json
  ...

data/nat20/dungeon_connectors/
  open.json
  door_frame.json
  stone_arch.json
  ...
```

---

## Starter Piece Catalog (Minimum Viable)

To generate interesting single-floor dungeons, the minimum catalog:

### Room pieces

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

### Connector pieces

| Connector | Tags | Notes |
|-----------|------|-------|
| open | opening | plain air passage, no frame |
| door_frame | door | wooden door frame |
| stone_arch | arch | curved stone archway |

More connectors added over time for variety. Theme-specific connectors (iron gate, timber supports) added in sprint 2.
