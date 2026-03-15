# Modular Dungeon Prefab: Authoring Guide

A practical reference for building dungeon pieces in-game. Read the [design doc](2026-03-15-modular-dungeon-prefab-design.md) first for the system overview.

---

## The Grid Rules

Every piece must follow these rules exactly. The `/gridprefab save` command validates them, but knowing them upfront saves rework.

1. **All dimensions are multiples of 5.** Width, depth, and height in blocks. A 1x1x1 piece is 5x5x5. A 2x1x2 piece is 10x5x10. Never 7, never 12.
2. **y=0 is the floor slab.** The bottom layer of every piece is a solid floor, one block thick.
3. **y=1 through y=4 is air space.** Four blocks of headroom per story.
4. **y=5 is the ceiling** (and the floor of the piece above, when vertical stacking arrives).
5. **All perimeter walls are solid.** Every outer face of the piece must be fully walled. The generator carves openings at connection time: you never build openings yourself.
6. **Interior is yours.** Everything inside the walls is free: furniture, pillars, stairs, decorations, lighting, whatever you want.

---

## Step-by-Step Build Workflow

### 1. Pick your piece size

Decide the grid dimensions before you start building. Common sizes:

| Grid Size | Block Size | Interior Air | Use Case |
|-----------|-----------|-------------|----------|
| 1x1x1 | 5x5x5 | 3x4x3 | Corridors, closets, guard posts |
| 2x1x1 | 10x5x5 | 8x4x3 | Long hallways, narrow rooms |
| 2x1x2 | 10x5x10 | 8x4x8 | Standard rooms, shops, arenas |
| 3x1x3 | 15x5x15 | 13x4x13 | Boss rooms, grand halls |

Interior air dimensions account for 1-block walls on each side and the floor slab.

### 2. Mark your origin

Stand at the spot that will be the **bottom-northwest corner** of your piece. This is block (0,0,0): the northwest corner of the floor slab. Drop a distinctive marker block here so you can find it later.

Cardinal directions for reference:
- **North:** -Z direction
- **South:** +Z direction
- **West:** -X direction
- **East:** +X direction

The piece extends **east** (+X) for width, **south** (+Z) for depth, and **up** (+Y) for height from your origin.

### 3. Lay the floor

Place a solid floor slab across the entire footprint at y=0. Every block in the floor layer must be solid: this is what the player walks on and what separates vertically stacked pieces.

For a 1x1x1 piece: 5x5 floor = 25 blocks.
For a 2x1x2 piece: 10x10 floor = 100 blocks.

### 4. Build the walls

Build solid walls on all four perimeter faces, from y=0 (floor) through y=4 (top of air space). Walls are 1 block thick.

**Do not leave any openings.** The generator handles all doorways. Build every wall face completely solid.

For a 1x1x1 piece, the perimeter is:
- North wall: 5 blocks wide (x=0 to x=4) at z=0, y=0 to y=4
- South wall: 5 blocks wide at z=4, y=0 to y=4
- West wall: 5 blocks deep (z=0 to z=4) at x=0, y=0 to y=4
- East wall: 5 blocks deep at x=4, y=0 to y=4
- Corners are shared between adjacent walls

### 5. Build the ceiling

Place a solid ceiling across the entire footprint at y=5 (one block above the air space). This mirrors the floor slab and serves as the floor of the piece above when stacking.

**Exception for sprint 1:** since we're single-floor only, the ceiling still must be built (it seals the piece from terrain above), but vertical connections won't use it yet.

### 6. Decorate the interior

Fill the interior with whatever makes the room interesting:
- Furniture, tables, chests, anvils
- Pillars, arches, rubble
- Light sources (torches, lanterns, glowing blocks)
- NPC spawn markers (if applicable)
- Floor patterns, carpet, rugs

Stay within the interior bounds. Don't place decorations in the wall layer or floor/ceiling slabs.

### 7. Export

Stand on your origin marker and run:

```
/gridprefab save <name> <gridW> <gridH> <gridD>
```

Example for a 2x1x2 standard room:
```
/gridprefab save room_tavern 2 1 2
```

The command will:
- Capture all blocks in the region
- Validate dimensions and wall integrity
- Write the `.prefab.json` and metadata `.json`
- Report any warnings (missing walls, unexpected air in perimeter)

### 8. Tag and weight

Open the generated metadata file at `data/nat20/dungeon_pieces/<name>.json` and fill in:

```json
{
  "tags": ["room"],
  "weight": 5.0
}
```

Common tag values: `"hallway"`, `"room"`, `"entrance"`, `"boss_room"`, `"dead_end"`.

Weight is relative: higher = more likely to be picked. Guidelines:
- **1.0:** rare pieces (boss rooms, unique setups)
- **3.0-5.0:** standard pieces (normal rooms, corridors)
- **8.0-10.0:** common connective tissue (basic hallways)

### 9. Test

Preview the piece in the world:
```
/gridprefab preview <name>
```

Check that it looks right, walls are solid, and the interior feels good at player scale.

---

## Common Mistakes

### Walls not fully solid
Every perimeter block from y=0 through y=4 must be filled. A single missing block in a wall face will cause the `/gridprefab save` command to warn. If a wall has a gap, terrain will be visible through the dungeon wall at that spot.

### Origin in the wrong corner
The origin is the **bottom-northwest** corner, not the center, not where you're standing when you finish. Always mark it before building. If you export from the wrong position, every socket will be misaligned.

### Decorations in the wall layer
Placing a torch or banner on the outer surface of a wall (the side facing out of the piece) means it occupies the wall-sharing overlap zone. When another piece connects, its wall overwrites that block. Keep all decorations on the interior face of walls.

### Building openings manually
Don't cut doorways into your walls. The generator carves 3x4 openings automatically at connection time. If you pre-cut openings, the socket auto-detection during export will flag those faces as "open," but since the generator also carves, you'd get double-processing on those faces (harmless but unnecessary).

### Piece too unique to rotate
If your piece has a fireplace on the north wall, a window niche on the east wall, and is otherwise asymmetric, set `"rotatable": false` in the metadata after export. Otherwise the registry generates rotated variants that look wrong (fireplace on the east wall, etc.). Symmetric corridors and plain rooms should stay `"rotatable": true`.

### Forgetting the ceiling
The ceiling at y=5 is easy to forget since you can't see it from inside the room. Without it, terrain above bleeds into the piece when placed underground. Always cap the top.

---

## Socket Auto-Detection

When you run `/gridprefab save`, the command scans each perimeter cell-face for existing openings. This is how it determines initial socket state:

For each cell-face on the perimeter, it checks the 3x4 socket zone:
- **Horizontally:** 3 blocks wide, centered (1 block from each corner of the cell-face)
- **Vertically:** 4 blocks tall, from y=1 through y=4 (above the floor slab, up to the ceiling)

If all 12 blocks in the zone are air: the cell-face is marked `"open"`.
If any block in the zone is solid: the cell-face is marked `"sealed"`.

Since you should build all walls solid, every cell-face should auto-detect as `"sealed"`. If you see unexpected `"open"` detections in the output, you have a hole in your wall.

---

## Multi-Unit Pieces

For pieces larger than 1x1x1, the interior spans across multiple grid cells. There are no internal walls between cells within the same piece: the interior is one continuous space.

Example: a 2x1x2 room (10x5x10 blocks)
- Floor: 10x10 at y=0
- Perimeter walls: along x=0, x=9, z=0, z=9 from y=0 to y=4
- Interior air: x=1 through x=8, z=1 through z=8, y=1 through y=4
- Ceiling: 10x10 at y=5
- No walls at x=5 or z=5 (those would be internal cell boundaries)

The piece occupies 4 grid cells (0,0), (1,0), (0,1), (1,1). Each perimeter cell-face gets its own socket entry. The 4 internal faces are omitted from the metadata.

---

## Piece Catalog Checklist

Minimum pieces needed for interesting dungeon generation:

- [ ] **entrance** (1x1x1): one open face (south). The dungeon entry.
- [ ] **corridor_straight** (1x1x1): two opposite open faces. Rotates to N-S and E-W.
- [ ] **corridor_L** (1x1x1): two adjacent open faces. Rotates to 4 variants.
- [ ] **corridor_T** (1x1x1): three open faces. Rotates to 4 variants.
- [ ] **corridor_cross** (1x1x1): four open faces. Symmetric, 1 variant.
- [ ] **room_small** (1x1x1): one open face. Rotates to 4 variants. Basic side room.
- [ ] **room_medium** (2x1x2): 1-2 open cell-faces. Standard gameplay room.
- [ ] **room_boss** (2x1x2): 1 open cell-face. Guaranteed placement.
- [ ] **dead_end_shrine** (1x1x1): one open face. Socket cap with altar/loot.
- [ ] **dead_end_collapsed** (1x1x1): one open face. Socket cap with rubble.

With rotation, ~10 authored pieces produce ~25-30 effective variants. Enough for varied single-floor dungeons. Add more pieces for variety over time.

---

## Quick Reference

```
/gridprefab save <name> <w> <h> <d>   -- export piece from player position
/gridprefab preview <name>             -- paste piece at player position
/gridprefab list                       -- list all registered pieces
/gridprefab validate <name>            -- re-check grid rules
```

| Dimension | Blocks | Interior |
|-----------|--------|----------|
| 1 unit | 5 | 3 |
| 2 units | 10 | 8 |
| 3 units | 15 | 13 |
| 4 units | 20 | 18 |

**Formula:** interior = (units * 5) - 2

**Wall overlap formula:** `next_origin = prev_origin + block_width - 1`
