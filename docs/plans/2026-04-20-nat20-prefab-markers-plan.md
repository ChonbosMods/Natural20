# Nat20 Prefab Markers Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace hardcoded offsets and code-chosen anchors in the settlement/POI/fetch-chest pipelines with six author-painted marker blocks (`Nat20/Anchor`, `Nat20/Direction`, `Nat20/Npc_Spawn`, `Nat20/Mob_Group_Spawn`, `Nat20/Chest_Spawn`, `Nat20/Force_Empty`), and flip the semantics of plain `Empty` from force-carve to passthrough.

**Architecture:** Custom BlockType assets register six marker blocks inside the Natural20 asset pack. A new `prefab/` package adds a scanner (single-pass buffer walk that records marker positions), a filter buffer wrapper (intercepts vanilla `PrefabUtil.paste`'s block callback to drop markers + plain Empty, and rewrites `Force_Empty` → `Empty`), and a paster (composes scanner + filter + vanilla paste, returns `PlacedMarkers` in world coords). Settlements, cave POIs, and fetch-quest chests consume marker positions instead of hardcoded offsets / random scatter / shared anchors.

**Tech Stack:** Java 25, Hytale SDK (2026.02.18-f3b8fff95), JUnit 5, ScaffoldIt Gradle plugin 0.2.14. No new external dependencies.

**Design doc:** `docs/plans/2026-04-20-nat20-prefab-markers-design.md`.

**Memory to respect:**
- Devserver cannot run from git worktrees : work on a branch directly, not a worktree.
- `System.getLogger()` only writes to log files. Use `HytaleLogger.get("Name")` for console-visible logs.
- `.ui` element IDs cannot contain underscores : doesn't apply here (no UI) but kept in mind.

---

## Phase 0 : Prep

### Task 0.1: Create the feature branch

**Files:** none yet.

**Step 1:** Confirm current branch is `main` and working tree is clean apart from the design+plan docs we just wrote.

```bash
git status --short
```
Expected: `AFFIX_AUDIT.md`, `CLAUDE.md`, `dev/...` and the two new `docs/plans/2026-04-20-nat20-prefab-markers-*.md` files untracked. No modified tracked files.

**Step 2:** Stage and commit the design + plan docs first.

```bash
git add docs/plans/2026-04-20-nat20-prefab-markers-design.md \
        docs/plans/2026-04-20-nat20-prefab-markers-plan.md
git commit -m "docs(plan): Nat20 prefab markers design + implementation plan"
```

**Step 3:** Create the feature branch.

```bash
git checkout -b feat/prefab-markers
```

**Step 4:** Sanity-check compile baseline.

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`, warnings only (no new errors).

---

## Phase 1 : Custom BlockType assets

### Task 1.1: R&D — discover the Hytale `.blockType.json` schema

**Files:** read-only investigation.

**Step 1:** Search the unpacked server for a schema-generating codec or examples.

```bash
grep -rl "BlockType" /home/keroppi/Development/Hytale/Hytale-Server-Unpacked/com/hypixel/hytale/server/core/asset/type/blocktype/config/ | head
```

Also extract a vanilla example from `Assets.zip`:

```bash
unzip -p ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip \
    "Server/BlockTypes/Empty.blockType.json" 2>/dev/null || \
unzip -l ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip | grep -i "blockType.json" | head -5
```

**Step 2:** If the archive has `Server/BlockTypes/*.blockType.json`, extract the three we care about as templates:

```bash
mkdir -p /tmp/blocktype-samples
cd /tmp/blocktype-samples
unzip -j ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip \
    "Server/BlockTypes/Editor_Empty.blockType.json" \
    "Server/BlockTypes/Editor_Anchor.blockType.json" \
    "Server/BlockTypes/Empty.blockType.json" 2>&1
```

**Step 3:** Open each and note the required top-level fields (key, rendering reference, collision, material, etc.) and any annotations.

```bash
cat Editor_Empty.blockType.json 2>/dev/null | head -50
```

If vanilla block JSONs live at different paths, search broadly:

```bash
unzip -l ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip | grep -iE "editor.*(block|empty|anchor).*json"
```

**Step 4:** Record findings at the top of this plan in a `## Schema notes` section (append) so future tasks have the reference.

**Acceptance:** we know the exact structure of a single Nat20 BlockType JSON and can template the six.

**No commit** : this task produces notes in the plan doc only. Use the `hytale-schema-generation` skill or `hytale-assets` skill if deeper investigation is needed.

---

### Task 1.2: Copy the editor-marker texture into our asset pack

**Files:**
- Create: `assets/Client/Textures/Nat20/editor_marker.png`

**Step 1:** Locate the vanilla `Editor_Empty` texture.

```bash
unzip -l ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip | grep -iE "editor.*empty.*png"
```

**Step 2:** Extract it.

```bash
mkdir -p /home/keroppi/Development/Hytale/Natural20/assets/Client/Textures/Nat20
unzip -j ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip \
    "<path-from-step-1>" -d /home/keroppi/Development/Hytale/Natural20/assets/Client/Textures/Nat20/
mv /home/keroppi/Development/Hytale/Natural20/assets/Client/Textures/Nat20/Editor_Empty.png \
   /home/keroppi/Development/Hytale/Natural20/assets/Client/Textures/Nat20/editor_marker.png
```

**Step 3:** Verify file is a valid PNG.

```bash
file /home/keroppi/Development/Hytale/Natural20/assets/Client/Textures/Nat20/editor_marker.png
```
Expected: `PNG image data, 16 x 16, ...` (or whatever the native size is).

**Step 4:** Commit.

```bash
git add assets/Client/Textures/Nat20/editor_marker.png
git commit -m "chore(assets): copy Editor_Empty texture for Nat20 markers"
```

---

### Task 1.3: Write the six BlockType JSON files

**Files:**
- Create: `assets/Server/BlockTypes/Nat20/Anchor.blockType.json`
- Create: `assets/Server/BlockTypes/Nat20/Direction.blockType.json`
- Create: `assets/Server/BlockTypes/Nat20/Npc_Spawn.blockType.json`
- Create: `assets/Server/BlockTypes/Nat20/Mob_Group_Spawn.blockType.json`
- Create: `assets/Server/BlockTypes/Nat20/Chest_Spawn.blockType.json`
- Create: `assets/Server/BlockTypes/Nat20/Force_Empty.blockType.json`

**Step 1:** Using the schema discovered in Task 1.1, author `Anchor.blockType.json`:

```json
{
  "Key": "Nat20/Anchor",
  "Material": "Solid",
  "Collision": "None",
  "Rendering": {
    "Texture": "Nat20/editor_marker"
  }
}
```
(The exact field names come from Task 1.1's findings. The snippet above is a placeholder shape.)

**Step 2:** Repeat for the other five keys with identical fields except the `Key`.

**Step 3:** Verify the devserver can load the pack.

```bash
./gradlew compileJava
./gradlew devServer 2>&1 | head -100 | grep -iE "Nat20/(Anchor|Direction|Npc_Spawn|Mob_Group_Spawn|Chest_Spawn|Force_Empty)|ERROR"
```
(Kill with Ctrl+C after the pack is loaded; we're only watching startup logs.)

Expected: one log line per block registration or no error. Any `Failed to load block` is a blocker : revisit schema.

**Step 4:** Commit.

```bash
git add assets/Server/BlockTypes/Nat20/
git commit -m "feat(prefab): register six Nat20 marker block types"
```

---

### Task 1.4: Create `Nat20PrefabConstants` to resolve block IDs at startup

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/Nat20PrefabConstants.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (call `Nat20PrefabConstants.resolve()` at end of `setup()`)

**Step 1:** Write the test.

Create `src/test/java/com/chonbosmods/prefab/Nat20PrefabConstantsTest.java`:

```java
package com.chonbosmods.prefab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PrefabConstantsTest {
    @Test
    void stripKeysIncludesAllMarkersAndVanilla() {
        // Can't test resolve() without the asset map loaded, but we can
        // assert the static strip key list is exactly what the design mandates.
        assertEquals(
            java.util.Set.of(
                "Nat20/Anchor", "Nat20/Direction", "Nat20/Npc_Spawn",
                "Nat20/Mob_Group_Spawn", "Nat20/Chest_Spawn", "Nat20/Force_Empty",
                "Editor_Anchor", "Editor_Block", "Editor_Empty",
                "Prefab_Spawner_Block", "Spawner_Rat",
                "Block_Spawner_Block", "Block_Spawner_Block_Large",
                "Geyzer_Spawner1"
            ),
            Nat20PrefabConstants.STRIP_KEYS
        );
    }
}
```

**Step 2:** Run test, verify it fails.

```bash
./gradlew test --tests Nat20PrefabConstantsTest
```
Expected: FAIL (class doesn't exist).

**Step 3:** Implement.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Set;

public final class Nat20PrefabConstants {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PrefabMarkers");

    public static final Set<String> STRIP_KEYS = Set.of(
        "Nat20/Anchor", "Nat20/Direction", "Nat20/Npc_Spawn",
        "Nat20/Mob_Group_Spawn", "Nat20/Chest_Spawn", "Nat20/Force_Empty",
        "Editor_Anchor", "Editor_Block", "Editor_Empty",
        "Prefab_Spawner_Block", "Spawner_Rat",
        "Block_Spawner_Block", "Block_Spawner_Block_Large",
        "Geyzer_Spawner1"
    );

    public static int anchorId = Integer.MIN_VALUE;
    public static int directionId = Integer.MIN_VALUE;
    public static int npcSpawnId = Integer.MIN_VALUE;
    public static int mobGroupSpawnId = Integer.MIN_VALUE;
    public static int chestSpawnId = Integer.MIN_VALUE;
    public static int forceEmptyId = Integer.MIN_VALUE;

    /** All block IDs that must be dropped from paste (markers + vanilla authoring/spawner blocks). */
    public static IntSet stripIds = new IntOpenHashSet();

    private Nat20PrefabConstants() {}

    /** Call from Natural20.setup() after the asset pack has loaded. */
    public static void resolve() {
        var map = BlockType.getAssetMap();
        anchorId         = requireId(map, "Nat20/Anchor");
        directionId      = requireId(map, "Nat20/Direction");
        npcSpawnId       = requireId(map, "Nat20/Npc_Spawn");
        mobGroupSpawnId  = requireId(map, "Nat20/Mob_Group_Spawn");
        chestSpawnId     = requireId(map, "Nat20/Chest_Spawn");
        forceEmptyId     = requireId(map, "Nat20/Force_Empty");

        stripIds = new IntOpenHashSet();
        for (String key : STRIP_KEYS) {
            int id = map.getIndex(key);
            if (id == Integer.MIN_VALUE) {
                // Vanilla strip keys are best-effort: log and continue.
                if (key.startsWith("Nat20/")) {
                    throw new IllegalStateException("Nat20 marker block not registered: " + key);
                }
                LOGGER.atWarning().log("Strip key '%s' not present in block asset map (skipping)", key);
                continue;
            }
            stripIds.add(id);
        }
        LOGGER.atInfo().log("Resolved Nat20 marker IDs: anchor=%d direction=%d npc=%d mob=%d chest=%d forceEmpty=%d (%d strip IDs total)",
            anchorId, directionId, npcSpawnId, mobGroupSpawnId, chestSpawnId, forceEmptyId, stripIds.size());
    }

    private static int requireId(com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType> map, String key) {
        int id = map.getIndex(key);
        if (id == Integer.MIN_VALUE) {
            throw new IllegalStateException("Required Nat20 marker block not registered: " + key);
        }
        return id;
    }
}
```

**Step 4:** Run test.

```bash
./gradlew test --tests Nat20PrefabConstantsTest
```
Expected: PASS.

**Step 5:** Wire into `Natural20.setup()`. Find the last line of `setup()` in `src/main/java/com/chonbosmods/Natural20.java` and add before it:

```java
com.chonbosmods.prefab.Nat20PrefabConstants.resolve();
```

**Step 6:** Compile-check.

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

**Step 7:** Devserver smoke.

```bash
./gradlew devServer 2>&1 | grep -iE "Resolved Nat20 marker IDs|marker block not registered" | head -5
```

Expected: a single `Resolved Nat20 marker IDs: anchor=... direction=...` info line. No `IllegalStateException`.

**Step 8:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/Nat20PrefabConstants.java \
        src/test/java/com/chonbosmods/prefab/Nat20PrefabConstantsTest.java \
        src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(prefab): resolve Nat20 marker block IDs at startup"
```

---

## Phase 2 : Scanner

### Task 2.1: Define the `MarkerScan` record

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/MarkerScan.java`

**Step 1:** Write test first.

Create `src/test/java/com/chonbosmods/prefab/MarkerScanTest.java`:

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarkerScanTest {
    @Test
    void recordHoldsAllSixFields() {
        MarkerScan scan = new MarkerScan(
            new Vector3i(0, 0, 0),
            new Vector3i(1, 0, 0),
            new Vector3i(1, 0, 0),
            List.of(new Vector3i(5, 0, 5)),
            List.of(new Vector3i(10, 0, 10)),
            List.of()
        );
        assertEquals(new Vector3i(0, 0, 0), scan.anchorLocal());
        assertEquals(1, scan.npcSpawnsLocal().size());
        assertEquals(0, scan.chestSpawnsLocal().size());
    }
}
```

**Step 2:** Run, verify failure.

```bash
./gradlew test --tests MarkerScanTest
```

**Step 3:** Implement.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import java.util.List;

/**
 * Result of scanning a prefab buffer for Nat20 marker blocks. All positions are
 * in prefab-local coordinates (pre-rotation, pre-translation).
 */
public record MarkerScan(
    Vector3i anchorLocal,
    Vector3i directionLocal,
    Vector3i directionVector,
    List<Vector3i> npcSpawnsLocal,
    List<Vector3i> mobGroupSpawnsLocal,
    List<Vector3i> chestSpawnsLocal
) {}
```

**Step 4:** Test passes.

**Step 5:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/MarkerScan.java \
        src/test/java/com/chonbosmods/prefab/MarkerScanTest.java
git commit -m "feat(prefab): add MarkerScan record"
```

---

### Task 2.2: Test-drive direction-vector snap helper

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/DirectionVector.java`
- Create: `src/test/java/com/chonbosmods/prefab/DirectionVectorTest.java`

**Step 1:** Write test.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionVectorTest {
    @Test
    void cardinalUnitVectorsPassThrough() {
        assertEquals(new Vector3i(1, 0, 0), DirectionVector.snapToCardinal(1, 0, 0));
        assertEquals(new Vector3i(-1, 0, 0), DirectionVector.snapToCardinal(-1, 0, 0));
        assertEquals(new Vector3i(0, 0, 1), DirectionVector.snapToCardinal(0, 0, 1));
        assertEquals(new Vector3i(0, 0, -1), DirectionVector.snapToCardinal(0, 0, -1));
    }

    @Test
    void longerOffsetsSnapToDominantAxis() {
        // (3, 0, 1) → dominant X → (1, 0, 0)
        assertEquals(new Vector3i(1, 0, 0), DirectionVector.snapToCardinal(3, 0, 1));
        // (-1, 0, -2) → dominant -Z → (0, 0, -1)
        assertEquals(new Vector3i(0, 0, -1), DirectionVector.snapToCardinal(-1, 0, -2));
    }

    @Test
    void zeroVectorThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> DirectionVector.snapToCardinal(0, 0, 0));
    }

    @Test
    void yAxisIgnoredForCardinalSnap() {
        // Direction is a horizontal facing: vertical component should not dominate.
        // (0, 5, 1) → dominant Z → (0, 0, 1). Y is ignored.
        assertEquals(new Vector3i(0, 0, 1), DirectionVector.snapToCardinal(0, 5, 1));
    }
}
```

**Step 2:** Run, verify failure.

```bash
./gradlew test --tests DirectionVectorTest
```

**Step 3:** Implement.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;

public final class DirectionVector {
    private DirectionVector() {}

    /**
     * Snap an arbitrary XZ offset to the nearest cardinal unit vector.
     * The Y component is ignored (direction is a horizontal facing).
     * @throws IllegalArgumentException if both X and Z are zero.
     */
    public static Vector3i snapToCardinal(int dx, int dy, int dz) {
        int absX = Math.abs(dx);
        int absZ = Math.abs(dz);
        if (absX == 0 && absZ == 0) {
            throw new IllegalArgumentException(
                "Direction offset is zero on both horizontal axes; direction block must not coincide with anchor");
        }
        if (absX >= absZ) {
            return new Vector3i(Integer.signum(dx), 0, 0);
        }
        return new Vector3i(0, 0, Integer.signum(dz));
    }
}
```

**Step 4:** Run tests, verify pass.

**Step 5:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/DirectionVector.java \
        src/test/java/com/chonbosmods/prefab/DirectionVectorTest.java
git commit -m "feat(prefab): DirectionVector.snapToCardinal with Y-ignored dominant-axis snap"
```

---

### Task 2.3: Test-drive `Nat20PrefabMarkerScanner` with a fake buffer

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/Nat20PrefabMarkerScanner.java`
- Create: `src/test/java/com/chonbosmods/prefab/Nat20PrefabMarkerScannerTest.java`
- Create: `src/test/java/com/chonbosmods/prefab/FakePrefabBuffer.java` (test helper)

**Step 1:** Build a minimal `FakePrefabBuffer` that implements `IPrefabBuffer`'s `forEach(ColumnIter, BlockCallback, EntityCallback, ChildPrefabCallback, PrefabBufferCall)` by replaying a list of pre-seeded cells. Skeleton (exact method signatures must match `IPrefabBuffer`; look up in `com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer`):

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
// ... other imports ...

class FakePrefabBuffer implements IPrefabBuffer {
    record Cell(int x, int y, int z, int blockId) {}
    private final List<Cell> cells;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    FakePrefabBuffer(List<Cell> cells) {
        this.cells = List.copyOf(cells);
        // Compute bounds from cells
        ...
    }

    @Override public int getMinX() { return minX; }
    @Override public int getMaxX() { return maxX; }
    // ... etc ...
    @Override public int getAnchorX() { return 0; }
    @Override public int getAnchorY() { return 0; }
    @Override public int getAnchorZ() { return 0; }
    @Override public int getColumnCount() { return cells.size(); }
    @Override public ChildPrefab[] getChildPrefabs() { return new ChildPrefab[0]; }

    @Override
    public void forEach(ColumnIter iter, BlockCallback blockCb, ...) {
        for (Cell c : cells) {
            blockCb.accept(c.x, c.y, c.z, c.blockId,
                null, 0, 0, 0, call, 0, (byte) 0);
        }
    }
    // ... other required methods (compare, etc.) ...
}
```

**Important:** before writing the fake, enumerate the exact `IPrefabBuffer` interface. Read the SDK source:

```bash
find /home/keroppi/Development/Hytale/Hytale-Server-Unpacked -name "IPrefabBuffer*.java" -exec cat {} \;
```

Implement every abstract method; unused ones can throw `UnsupportedOperationException`.

**Step 2:** Write scanner test.

```java
package com.chonbosmods.prefab;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PrefabMarkerScannerTest {
    @BeforeAll
    static void setupConstants() {
        // Test-only: inject known IDs without going through asset map.
        Nat20PrefabConstants.anchorId = 100;
        Nat20PrefabConstants.directionId = 101;
        Nat20PrefabConstants.npcSpawnId = 102;
        Nat20PrefabConstants.mobGroupSpawnId = 103;
        Nat20PrefabConstants.chestSpawnId = 104;
        Nat20PrefabConstants.forceEmptyId = 105;
    }

    @Test
    void scanFindsAnchorAndDirection() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
            new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId),
            new FakePrefabBuffer.Cell(5, 1, 5, 99)  // some structural block
        ));
        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);
        assertEquals(new Vector3i(0, 0, 0), scan.anchorLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionVector());
    }

    @Test
    void scanBucketsMultipleSpawns() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
            new FakePrefabBuffer.Cell(0, 0, 1, Nat20PrefabConstants.directionId),
            new FakePrefabBuffer.Cell(2, 0, 2, Nat20PrefabConstants.npcSpawnId),
            new FakePrefabBuffer.Cell(3, 0, 2, Nat20PrefabConstants.npcSpawnId),
            new FakePrefabBuffer.Cell(4, 0, 4, Nat20PrefabConstants.npcSpawnId),
            new FakePrefabBuffer.Cell(5, 0, 5, Nat20PrefabConstants.mobGroupSpawnId)
        ));
        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);
        assertEquals(3, scan.npcSpawnsLocal().size());
        assertEquals(1, scan.mobGroupSpawnsLocal().size());
        assertEquals(0, scan.chestSpawnsLocal().size());
    }

    @Test
    void scanThrowsOnMissingAnchor() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId)
        ));
        assertThrows(IllegalArgumentException.class,
                () -> Nat20PrefabMarkerScanner.scan(buffer));
    }

    @Test
    void scanThrowsOnMultipleAnchors() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
            new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId),
            new FakePrefabBuffer.Cell(5, 0, 0, Nat20PrefabConstants.anchorId)
        ));
        assertThrows(IllegalArgumentException.class,
                () -> Nat20PrefabMarkerScanner.scan(buffer));
    }
}
```

**Step 3:** Run, verify failure.

**Step 4:** Implement.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.util.ArrayList;
import java.util.List;

public final class Nat20PrefabMarkerScanner {
    private Nat20PrefabMarkerScanner() {}

    public static MarkerScan scan(IPrefabBuffer buffer) {
        List<Vector3i> anchors = new ArrayList<>();
        List<Vector3i> directions = new ArrayList<>();
        List<Vector3i> npcs = new ArrayList<>();
        List<Vector3i> mobGroups = new ArrayList<>();
        List<Vector3i> chests = new ArrayList<>();

        buffer.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, support, rot, filler, call, fluidId, fluidLevel) -> {
                if (blockId == Nat20PrefabConstants.anchorId)         anchors.add(new Vector3i(x, y, z));
                else if (blockId == Nat20PrefabConstants.directionId) directions.add(new Vector3i(x, y, z));
                else if (blockId == Nat20PrefabConstants.npcSpawnId)  npcs.add(new Vector3i(x, y, z));
                else if (blockId == Nat20PrefabConstants.mobGroupSpawnId) mobGroups.add(new Vector3i(x, y, z));
                else if (blockId == Nat20PrefabConstants.chestSpawnId)    chests.add(new Vector3i(x, y, z));
            },
            (x, z, entities, t) -> {},
            (x, y, z, path, fit, seed, cond, weights, rot, t) -> {},
            null
        );

        if (anchors.size() != 1) {
            throw new IllegalArgumentException("Prefab must have exactly one Nat20/Anchor block; found " + anchors.size());
        }
        if (directions.size() != 1) {
            throw new IllegalArgumentException("Prefab must have exactly one Nat20/Direction block; found " + directions.size());
        }

        Vector3i anchor = anchors.get(0);
        Vector3i direction = directions.get(0);
        Vector3i dirVec = DirectionVector.snapToCardinal(
            direction.getX() - anchor.getX(),
            direction.getY() - anchor.getY(),
            direction.getZ() - anchor.getZ());

        return new MarkerScan(anchor, direction, dirVec, npcs, mobGroups, chests);
    }
}
```

**Step 5:** Run tests.

```bash
./gradlew test --tests Nat20PrefabMarkerScannerTest
```

Note: the `null` last argument to `buffer.forEach` (for `PrefabBufferCall`) may need to be a fresh `new PrefabBufferCall(new Random(), PrefabRotation.NONE)` or similar. Adjust when compiling.

**Step 6:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/Nat20PrefabMarkerScanner.java \
        src/test/java/com/chonbosmods/prefab/Nat20PrefabMarkerScannerTest.java \
        src/test/java/com/chonbosmods/prefab/FakePrefabBuffer.java
git commit -m "feat(prefab): scanner extracts anchor, direction, and spawn markers"
```

---

## Phase 3 : Filter buffer

### Task 3.1: Implement `Nat20FilteredBuffer` skeleton (forwards all interface methods)

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/Nat20FilteredBuffer.java`

**Step 1:** Read the full `IPrefabBuffer` interface.

```bash
find /home/keroppi/Development/Hytale/Hytale-Server-Unpacked -name "IPrefabBuffer*.java" -exec cat {} \;
```

Enumerate every abstract method.

**Step 2:** Write the skeleton that forwards every non-`forEach` method to the inner buffer.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
// ... other imports ...

public class Nat20FilteredBuffer implements IPrefabBuffer {
    private final IPrefabBuffer inner;

    public Nat20FilteredBuffer(IPrefabBuffer inner) {
        this.inner = inner;
    }

    @Override public int getMinX() { return inner.getMinX(); }
    @Override public int getMaxX() { return inner.getMaxX(); }
    // ... every other delegate ...

    @Override
    public void forEach(...) {
        // Placeholder: forward unchanged for now. Real filter comes in next task.
        inner.forEach(...);
    }
    // ... etc ...
}
```

**Step 3:** Compile-check.

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL. (No tests yet; real logic comes next.)

**Step 4:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/Nat20FilteredBuffer.java
git commit -m "feat(prefab): Nat20FilteredBuffer skeleton forwarding to inner buffer"
```

---

### Task 3.2: Test-drive the filter logic inside `Nat20FilteredBuffer.forEach`

**Files:**
- Modify: `src/main/java/com/chonbosmods/prefab/Nat20FilteredBuffer.java`
- Create: `src/test/java/com/chonbosmods/prefab/Nat20FilteredBufferTest.java`

**Step 1:** Write test.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Nat20FilteredBufferTest {
    @BeforeAll
    static void setupConstants() {
        Nat20PrefabConstants.anchorId = 100;
        Nat20PrefabConstants.directionId = 101;
        Nat20PrefabConstants.npcSpawnId = 102;
        Nat20PrefabConstants.mobGroupSpawnId = 103;
        Nat20PrefabConstants.chestSpawnId = 104;
        Nat20PrefabConstants.forceEmptyId = 105;
        Nat20PrefabConstants.stripIds = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        Nat20PrefabConstants.stripIds.addAll(List.of(100, 101, 102, 103, 104, 105));
    }

    @Test
    void markerBlocksDropped() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
            new FakePrefabBuffer.Cell(1, 0, 0, 42)  // structural block
        ));
        Nat20FilteredBuffer filtered = new Nat20FilteredBuffer(inner);
        List<Integer> seenBlockIds = new ArrayList<>();
        filtered.forEach(IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, support, rot, filler, call, fluidId, fluidLevel) ->
                seenBlockIds.add(blockId),
            (x, z, entities, t) -> {},
            (x, y, z, path, fit, seed, cond, weights, rot, t) -> {},
            null);
        assertEquals(List.of(42), seenBlockIds, "anchor dropped, structural block passed through");
    }

    @Test
    void plainEmptyDropped() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, 0),   // Empty
            new FakePrefabBuffer.Cell(1, 0, 0, 42)   // structural
        ));
        Nat20FilteredBuffer filtered = new Nat20FilteredBuffer(inner);
        List<Integer> seen = new ArrayList<>();
        filtered.forEach(IPrefabBuffer.iterateAllColumns(),
            (x, y, z, id, h, s, r, f, c, fl, flv) -> seen.add(id),
            (a, b, c, d) -> {}, (a, b, c, d, e, f, g, h, i, j) -> {}, null);
        assertEquals(List.of(42), seen);
    }

    @Test
    void forceEmptyRewrittenToZero() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.forceEmptyId)
        ));
        Nat20FilteredBuffer filtered = new Nat20FilteredBuffer(inner);
        List<Integer> seen = new ArrayList<>();
        filtered.forEach(IPrefabBuffer.iterateAllColumns(),
            (x, y, z, id, h, s, r, f, c, fl, flv) -> seen.add(id),
            (a, b, c, d) -> {}, (a, b, c, d, e, f, g, h, i, j) -> {}, null);
        assertEquals(List.of(0), seen, "Force_Empty remapped to Empty id");
    }

    @Test
    void vanillaAuthoringBlocksDropped() {
        // Editor_Anchor and Prefab_Spawner_Block have IDs in stripIds (set by BeforeAll subset).
        // Add a representative strip ID to the set for this test.
        Nat20PrefabConstants.stripIds.add(999);
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
            new FakePrefabBuffer.Cell(0, 0, 0, 999),
            new FakePrefabBuffer.Cell(1, 0, 0, 42)
        ));
        Nat20FilteredBuffer filtered = new Nat20FilteredBuffer(inner);
        List<Integer> seen = new ArrayList<>();
        filtered.forEach(IPrefabBuffer.iterateAllColumns(),
            (x, y, z, id, h, s, r, f, c, fl, flv) -> seen.add(id),
            (a, b, c, d) -> {}, (a, b, c, d, e, f, g, h, i, j) -> {}, null);
        assertEquals(List.of(42), seen);
    }
}
```

**Step 2:** Run, verify failure (filter currently passes everything through).

**Step 3:** Implement the filter inside `Nat20FilteredBuffer.forEach`.

```java
@Override
public void forEach(ColumnIter iter, BlockCallback blockCb, EntityCallback entityCb,
                    ChildPrefabCallback childCb, PrefabBufferCall call) {
    inner.forEach(iter,
        (x, y, z, blockId, holder, support, rot, filler, c, fluidId, fluidLevel) -> {
            // Drop markers and vanilla authoring/spawner blocks
            if (Nat20PrefabConstants.stripIds.contains(blockId)) return;
            // Plain Empty = passthrough, but preserve fluids
            if (blockId == 0 && fluidId == 0) return;
            // Force_Empty → Empty (carve)
            int outId = (blockId == Nat20PrefabConstants.forceEmptyId) ? 0 : blockId;
            blockCb.accept(x, y, z, outId, holder, support, rot, filler, c, fluidId, fluidLevel);
        },
        entityCb, childCb, call);
}
```

**Step 4:** Run tests, verify pass.

**Step 5:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/Nat20FilteredBuffer.java \
        src/test/java/com/chonbosmods/prefab/Nat20FilteredBufferTest.java
git commit -m "feat(prefab): filter buffer drops markers, flips Empty, remaps Force_Empty"
```

---

## Phase 4 : Paster

### Task 4.1: Define `PlacedMarkers` record

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/PlacedMarkers.java`

**Step 1:** Write small test.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlacedMarkersTest {
    @Test
    void recordHoldsAllFields() {
        PlacedMarkers p = new PlacedMarkers(
            new Vector3i(10, 64, 10),
            new Vector3i(1, 0, 0),
            List.of(new Vector3d(5.5, 64, 5.5)),
            List.of(new Vector3d(15.5, 64, 15.5)),
            List.of()
        );
        assertEquals(1, p.npcSpawnsWorld().size());
        assertEquals(new Vector3i(1, 0, 0), p.directionVectorWorld());
    }
}
```

**Step 2:** Implement:

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import java.util.List;

public record PlacedMarkers(
    Vector3i anchorWorld,
    Vector3i directionVectorWorld,
    List<Vector3d> npcSpawnsWorld,
    List<Vector3d> mobGroupSpawnsWorld,
    List<Vector3d> chestSpawnsWorld
) {}
```

**Step 3:** Test pass, commit.

```bash
git add src/main/java/com/chonbosmods/prefab/PlacedMarkers.java \
        src/test/java/com/chonbosmods/prefab/PlacedMarkersTest.java
git commit -m "feat(prefab): add PlacedMarkers record"
```

---

### Task 4.2: Test-drive `computeYawToAlign(prefabDir, worldDir)`

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/YawAlignment.java`
- Create: `src/test/java/com/chonbosmods/prefab/YawAlignmentTest.java`

**Step 1:** Write test.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YawAlignmentTest {
    private static final Vector3i POS_X = new Vector3i(1, 0, 0);
    private static final Vector3i NEG_X = new Vector3i(-1, 0, 0);
    private static final Vector3i POS_Z = new Vector3i(0, 0, 1);
    private static final Vector3i NEG_Z = new Vector3i(0, 0, -1);

    @Test
    void identityRotation() {
        assertEquals(Rotation.None, YawAlignment.computeYawToAlign(POS_Z, POS_Z));
    }

    @Test
    void ninetyRotatesPosZToPosX() {
        // Rotation.Ninety should take prefab +Z facing onto world +X facing.
        // (Rotation sign convention depends on Hytale; verify against existing
        // UndergroundStructurePlacer wallDir→rotation table.)
        assertEquals(Rotation.Ninety, YawAlignment.computeYawToAlign(POS_Z, POS_X));
    }

    @Test
    void oneEightyFlips() {
        assertEquals(Rotation.OneEighty, YawAlignment.computeYawToAlign(POS_Z, NEG_Z));
    }

    @Test
    void twoSeventyRotatesPosZToNegX() {
        assertEquals(Rotation.TwoSeventy, YawAlignment.computeYawToAlign(POS_Z, NEG_X));
    }

    @Test
    void negativePrefabFacing() {
        // Prefab direction = -X, world direction = +Z → rotation that takes -X to +Z.
        // Composition: (Rotation of POS_Z to POS_Z = None) but prefab starts at -X.
        // -X → +Z is a 90° rotation (same as +Z → +X).
        assertEquals(Rotation.Ninety, YawAlignment.computeYawToAlign(NEG_X, POS_Z));
    }
}
```

**Step 2:** Run, verify failure.

**Step 3:** Implement with a lookup table.

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;

public final class YawAlignment {
    private YawAlignment() {}

    /**
     * Rotation that takes {@code prefabDir} onto {@code worldDir}.
     * Both vectors must be cardinal horizontal unit vectors.
     */
    public static Rotation computeYawToAlign(Vector3i prefabDir, Vector3i worldDir) {
        int pa = axisIndex(prefabDir);   // 0=+X, 1=+Z, 2=-X, 3=-Z
        int wa = axisIndex(worldDir);
        int steps = Math.floorMod(wa - pa, 4);
        return switch (steps) {
            case 0 -> Rotation.None;
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> throw new AssertionError();
        };
    }

    private static int axisIndex(Vector3i v) {
        if (v.getX() ==  1 && v.getZ() ==  0) return 0; // +X
        if (v.getX() ==  0 && v.getZ() ==  1) return 1; // +Z
        if (v.getX() == -1 && v.getZ() ==  0) return 2; // -X
        if (v.getX() ==  0 && v.getZ() == -1) return 3; // -Z
        throw new IllegalArgumentException("Not a cardinal horizontal unit vector: " + v);
    }
}
```

**Note:** the direction convention (whether `Rotation.Ninety` rotates +Z to +X or +Z to -X) must match Hytale's `PrefabRotation.rotate`. Verify against the existing working case in `UndergroundStructurePlacer`:

- Current: `wallDir=-X` → `Rotation.TwoSeventy` (comment says "entrance +Z becomes +X")
- In our model: prefabDir = +Z, worldDir = +X (facing away from -X wall = toward +X) → expect `Rotation.TwoSeventy`.

If the test above fails because `Ninety`/`TwoSeventy` are swapped, swap the mapping. Authoritative answer: plant a test prefab with a +Z direction block, paste with each of the four rotations, screenshot, confirm which rotation flips +Z → +X.

**Step 4:** Run tests, iterate on rotation convention until matching the existing placer table.

**Step 5:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/YawAlignment.java \
        src/test/java/com/chonbosmods/prefab/YawAlignmentTest.java
git commit -m "feat(prefab): YawAlignment.computeYawToAlign lookup-table helper"
```

---

### Task 4.3: Implement `Nat20PrefabPaster`

**Files:**
- Create: `src/main/java/com/chonbosmods/prefab/Nat20PrefabPaster.java`

**Step 1:** This step is integration-level and hard to unit-test cleanly (needs World, chunk preloading, etc.). Manual smoke test only.

Implementation:

```java
package com.chonbosmods.prefab;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Nat20PrefabPaster {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Paster");
    private static final int DEFER_TICKS = 5;

    private Nat20PrefabPaster() {}

    /**
     * Scan + filter + paste. Returns a future completing with the PlacedMarkers result
     * once the paste finishes (chunks loaded + N tick defer + paste).
     *
     * @param desiredAnchorWorld world coord where the prefab's anchor block should end up.
     * @param yaw rotation to apply to the prefab.
     */
    public static CompletableFuture<PlacedMarkers> paste(
            IPrefabBuffer buffer,
            World world,
            Vector3i desiredAnchorWorld,
            Rotation yaw,
            Random random,
            ComponentAccessor<EntityStore> store) {

        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);
        PrefabRotation rot = PrefabRotation.fromRotation(yaw);

        // Rotate + translate anchor local → world.
        Vector3i rotatedAnchor = rotateInt(rot, scan.anchorLocal());
        Vector3i translation = new Vector3i(
            desiredAnchorWorld.getX() - rotatedAnchor.getX(),
            desiredAnchorWorld.getY() - rotatedAnchor.getY(),
            desiredAnchorWorld.getZ() - rotatedAnchor.getZ());

        // Chunk preload (non-ticking)
        int minCX = ChunkUtil.chunkCoordinate(buffer.getMinX() + translation.getX());
        int maxCX = ChunkUtil.chunkCoordinate(buffer.getMaxX() + translation.getX());
        int minCZ = ChunkUtil.chunkCoordinate(buffer.getMinZ() + translation.getZ());
        int maxCZ = ChunkUtil.chunkCoordinate(buffer.getMaxZ() + translation.getZ());

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                futures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }

        CompletableFuture<PlacedMarkers> result = new CompletableFuture<>();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(30, TimeUnit.SECONDS)
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    LOGGER.atSevere().withCause(error).log("Chunk load failed; abort paste");
                    result.complete(null);
                    return;
                }
                deferTicks(world, DEFER_TICKS, () -> {
                    try {
                        IPrefabBuffer filtered = new Nat20FilteredBuffer(buffer);
                        Vector3i pasteAnchorWorld = new Vector3i(
                            scan.anchorLocal().getX() + translation.getX(),
                            scan.anchorLocal().getY() + translation.getY(),
                            scan.anchorLocal().getZ() + translation.getZ());
                        // PrefabUtil.paste's position parameter is the anchor location;
                        // we're passing the world coord where buffer's anchor-local should land.
                        // BUT: vanilla paste uses JSON-level anchor, not ours. Reset to
                        // world coord - rotated-buffer-json-anchor. For Nat20 prefabs
                        // JSON anchor is (0,0,0) by construction, so this simplifies to
                        // desiredAnchorWorld - rotated(scan.anchorLocal).
                        PrefabUtil.paste(filtered, world, translation, yaw, true,
                            random, 0, store);

                        result.complete(rotateMarkers(scan, rot, translation));
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Paste failed");
                        result.complete(null);
                    }
                });
            });
        return result;
    }

    private static PlacedMarkers rotateMarkers(MarkerScan scan, PrefabRotation rot, Vector3i t) {
        Vector3i anchorW = new Vector3i(
            rotateInt(rot, scan.anchorLocal()).getX() + t.getX(),
            scan.anchorLocal().getY() + t.getY(),
            rotateInt(rot, scan.anchorLocal()).getZ() + t.getZ());
        Vector3i dirW = rotateInt(rot, scan.directionVector());  // direction vector rotates without translation
        return new PlacedMarkers(
            anchorW,
            dirW,
            translateAll(scan.npcSpawnsLocal(), rot, t),
            translateAll(scan.mobGroupSpawnsLocal(), rot, t),
            translateAll(scan.chestSpawnsLocal(), rot, t));
    }

    private static List<Vector3d> translateAll(List<Vector3i> locals, PrefabRotation rot, Vector3i t) {
        List<Vector3d> out = new ArrayList<>(locals.size());
        for (Vector3i v : locals) {
            Vector3i r = rotateInt(rot, v);
            out.add(new Vector3d(r.getX() + t.getX() + 0.5, r.getY() + t.getY() + 0.5,
                                 r.getZ() + t.getZ() + 0.5));
        }
        return out;
    }

    private static Vector3i rotateInt(PrefabRotation rot, Vector3i v) {
        Vector3d tmp = new Vector3d(v.getX(), v.getY(), v.getZ());
        rot.rotate(tmp);
        return new Vector3i((int) Math.round(tmp.getX()), (int) Math.round(tmp.getY()),
                            (int) Math.round(tmp.getZ()));
    }

    private static void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) { world.execute(action); return; }
        world.execute(() -> deferTicks(world, ticks - 1, action));
    }
}
```

**Note on translation:** `PrefabUtil.paste(buffer, world, position, ...)` treats `position` as where buffer-local (0,0,0) maps to in the world (because vanilla uses the JSON-level anchor = (0,0,0) for Nat20 prefabs). Double-check this with the existing `UndergroundStructurePlacer.placeAtVoid` behavior (it uses `pastePos = (structX, structY, structZ)` and the JSON anchor places itself there). If vanilla's paste applies the JSON anchor, we should pass the desired translation as-is.

Mental model: `worldBlockCoord = rotate(localBlockCoord) + translation`. For the anchor block at `scan.anchorLocal`: `desiredAnchorWorld = rotate(scan.anchorLocal) + translation`, so `translation = desiredAnchorWorld - rotate(scan.anchorLocal)`.

**Step 2:** Compile.

```bash
./gradlew compileJava
```

**Step 3:** Commit.

```bash
git add src/main/java/com/chonbosmods/prefab/Nat20PrefabPaster.java
git commit -m "feat(prefab): Nat20PrefabPaster scans, filters, pastes, returns PlacedMarkers"
```

**Note:** no unit test for this task : it requires a live world. Manual smoke test is covered in Task 8.3.

---

## Phase 5 : Settlement wire-up

### Task 5.1: Add `NpcSpawnRole` record (replaces `NpcSpawnDef`)

**Files:**
- Create: `src/main/java/com/chonbosmods/npc/NpcSpawnRole.java`

**Step 1:** Implement:

```java
package com.chonbosmods.npc;

public record NpcSpawnRole(String role, int count, double leashRadius, int dispositionMin, int dispositionMax) {
    /** Convenience: default disposition range [40, 80]. */
    public NpcSpawnRole(String role, int count, double leashRadius) {
        this(role, count, leashRadius, 40, 80);
    }
}
```

**Step 2:** Compile-check.

**Step 3:** Commit.

```bash
git add src/main/java/com/chonbosmods/npc/NpcSpawnRole.java
git commit -m "feat(npc): NpcSpawnRole replaces position-bearing NpcSpawnDef"
```

---

### Task 5.2: Refactor `SettlementType` to use `NpcSpawnRole`

**Files:**
- Modify: `src/main/java/com/chonbosmods/settlement/SettlementType.java`

**Step 1:** Rewrite entries. Since we're deleting all old prefabs (Task 8.2), the only enum constant we keep is a new `TREE1` for our test prefab. Temporarily keep TOWN/OUTPOST/CART so existing code compiles, but drop their `NpcSpawnDef` list (replace with empty `NpcSpawnRole` lists):

```java
package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnRole;
import java.util.List;

public enum SettlementType {
    // Placeholder — will be re-authored as TREE1 test prefab lands
    TOWN("Nat20/tree1", 32, List.of(
        new NpcSpawnRole("Villager", 3, 6, 40, 80),
        new NpcSpawnRole("Guard",    1, 3, 30, 60)
    ),
        List.of("mine", "farm", "tavern", "blacksmith", "well", "market"),
        List.of("goblins", "wolves", "skeletons")
    ),
    OUTPOST("Nat20/tree1", 16, List.of(
        new NpcSpawnRole("Villager", 1, 4, 35, 70),
        new NpcSpawnRole("Guard",    1, 2, 30, 55)
    ),
        List.of("farm", "well", "watchtower"),
        List.of("goblins", "wolves")
    ),
    CART("Nat20/tree1", 8, List.of(
        new NpcSpawnRole("Traveler", 1, 3, 25, 65)
    ),
        List.of(),
        List.of("goblins")
    );

    private final String prefabKey;
    private final int footprint;
    private final List<NpcSpawnRole> npcSpawns;
    private final List<String> poiTypes;
    private final List<String> mobTypes;

    SettlementType(String prefabKey, int footprint, List<NpcSpawnRole> npcSpawns,
                   List<String> poiTypes, List<String> mobTypes) {
        this.prefabKey = prefabKey;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
        this.poiTypes = poiTypes;
        this.mobTypes = mobTypes;
    }

    public String getPrefabKey() { return prefabKey; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnRole> getNpcSpawns() { return npcSpawns; }
    public List<String> getPoiTypes() { return poiTypes; }
    public List<String> getMobTypes() { return mobTypes; }

    public String getDisplayLabel() {
        return switch (this) {
            case OUTPOST, CART -> "outpost";
            case TOWN -> "village";
        };
    }
}
```

**Step 2:** Compile. There will be errors in `NpcManager` (still references `NpcSpawnDef` with its old position fields). Fix next task.

**Step 3:** No commit yet : compile is broken.

---

### Task 5.3: Refactor `NpcManager.spawnSettlementNpcs` to per-spawn API

**Files:**
- Modify: `src/main/java/com/chonbosmods/npc/NpcManager.java`

**Step 1:** Find the existing `spawnSettlementNpcs(store, world, SettlementType, origin, cellKey, placedAt)`. Replace with:

```java
/** Spawn a single NPC at a given world position. */
public NpcRecord spawnSettlementNpc(
        Store<EntityStore> store, World world,
        NpcSpawnRole role, Vector3d worldPos,
        String cellKey, long placedAt) {
    // ... lift the body of the old method's single-NPC branch ...
}
```

Look up the existing body of `spawnSettlementNpcs` to see how it resolves role index, randomizes rotation, applies disposition/leash, and returns an `NpcRecord`. Copy that into the per-spawn method. Remove the outer loop (now done by the caller).

**Step 2:** Delete the old `spawnSettlementNpcs(... SettlementType ...)` signature (or leave it as a thin wrapper for legacy callers if any exist).

**Step 3:** Compile.

```bash
./gradlew compileJava
```
Expected errors now only in `SettlementPlacer` and `SettlementWorldGenListener`.

**Step 4:** No commit yet.

---

### Task 5.4: Refactor `SettlementPlacer.place` to return `PlacedMarkers`

**Files:**
- Modify: `src/main/java/com/chonbosmods/settlement/SettlementPlacer.java`

**Step 1:** Replace the body:

```java
public CompletableFuture<PlacedMarkers> place(World world, Vector3i desiredAnchorWorld,
                                              SettlementType type, Rotation yaw,
                                              ComponentAccessor<EntityStore> store,
                                              Random random) {
    IPrefabBuffer buffer = prefabs.get(type);
    if (buffer == null) {
        LOGGER.atWarning().log("No prefab for " + type);
        return CompletableFuture.completedFuture(null);
    }
    return Nat20PrefabPaster.paste(buffer, world, desiredAnchorWorld, yaw, random, store);
}
```

`hasPrefab` and `init` stay unchanged.

**Step 2:** Compile. Errors now only in `SettlementWorldGenListener`.

**Step 3:** No commit yet.

---

### Task 5.5: Update `SettlementWorldGenListener.onChunkLoad` to consume `PlacedMarkers`

**Files:**
- Modify: `src/main/java/com/chonbosmods/settlement/SettlementWorldGenListener.java`

**Step 1:** Inside the `world.execute(() -> { ... })` block, replace the direct `placer.place(...)` + `spawnSettlementNpcs(...)` pair with:

```java
placer.place(world, new Vector3i(settlementX, groundY, settlementZ),
             SettlementType.TOWN, Rotation.None, store, new Random(seed))
    .whenComplete((placed, error) -> world.execute(() -> {
        if (placed == null) {
            LOGGER.atWarning().log("Settlement placement returned null for cell " + cellKey);
            return;
        }
        List<Vector3d> markers = new ArrayList<>(placed.npcSpawnsWorld());
        Collections.shuffle(markers, new Random(seed));

        int markerIdx = 0;
        List<NpcRecord> spawned = new ArrayList<>();
        for (NpcSpawnRole role : SettlementType.TOWN.getNpcSpawns()) {
            for (int i = 0; i < role.count() && markerIdx < markers.size(); i++, markerIdx++) {
                NpcRecord rec = Natural20.getInstance().getNpcManager()
                    .spawnSettlementNpc(store, world, role, markers.get(markerIdx),
                                        cellKey, record.getPlacedAt());
                if (rec != null) spawned.add(rec);
            }
        }
        if (markerIdx < SettlementType.TOWN.getNpcSpawns().stream().mapToInt(NpcSpawnRole::count).sum()) {
            LOGGER.atWarning().log("Settlement at cell %s has %d NPC markers but config requires %d",
                cellKey, markers.size(), ...);
        }

        record.setPosY(groundY);
        record.getNpcs().addAll(spawned);
        registry.saveAsync();

        Natural20.getInstance().onSettlementCreated(record, world);
        LOGGER.atFine().log("Settlement placed at %d, %d, %d with %d NPCs",
            settlementX, groundY, settlementZ, spawned.size());
    }));
```

**Step 2:** Compile.

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

**Step 3:** Commit the whole settlement refactor as one commit.

```bash
git add src/main/java/com/chonbosmods/npc/NpcManager.java \
        src/main/java/com/chonbosmods/settlement/SettlementType.java \
        src/main/java/com/chonbosmods/settlement/SettlementPlacer.java \
        src/main/java/com/chonbosmods/settlement/SettlementWorldGenListener.java
git commit -m "refactor(settlement): place via Nat20PrefabPaster, spawn NPCs at marker positions"
```

---

### Task 5.6: Delete `NpcSpawnDef.java`

**Files:**
- Delete: `src/main/java/com/chonbosmods/npc/NpcSpawnDef.java`

```bash
git rm src/main/java/com/chonbosmods/npc/NpcSpawnDef.java
./gradlew compileJava
git commit -m "chore(npc): remove obsolete NpcSpawnDef"
```

Expected: BUILD SUCCESSFUL (no remaining references).

---

## Phase 6 : Cave POI wire-up

### Task 6.1: Route `UndergroundStructurePlacer.placeAtVoid` through `Nat20PrefabPaster`

**Files:**
- Modify: `src/main/java/com/chonbosmods/cave/UndergroundStructurePlacer.java`

**Step 1:** Change the return type of `placeAtVoid` from `CompletableFuture<Vector3i>` to `CompletableFuture<PlacedMarkers>`. Callers will get both the entrance position (via `placed.anchorWorld()`) and the mob-group spawn positions.

**Step 2:** Replace the rotation derivation (lines ~164-175) with direction-vector based:

```java
MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);
Vector3i wantedWorldDir = switch (bestWallDir) {
    case "-X" -> new Vector3i( 1, 0,  0);
    case "+X" -> new Vector3i(-1, 0,  0);
    case "-Z" -> new Vector3i( 0, 0,  1);
    case "+Z" -> new Vector3i( 0, 0, -1);
    default   -> new Vector3i( 0, 0,  1);
};
Rotation rotation = YawAlignment.computeYawToAlign(scan.directionVector(), wantedWorldDir);
```

**Step 3:** Replace the `PrefabUtil.paste(...)` call with `Nat20PrefabPaster.paste(...)`:

```java
return Nat20PrefabPaster.paste(buffer, world,
    new Vector3i(floorX, floorY, floorZ), rotation, new Random(), store);
```

Remove the chunk-preload / defer-tick block (now handled by the paster).

**Step 4:** Compile. Fix callers that expected `Vector3i` from `placeAtVoid` (they now get `PlacedMarkers`).

**Step 5:** Commit.

```bash
git add src/main/java/com/chonbosmods/cave/UndergroundStructurePlacer.java
# and any caller fixes
git commit -m "refactor(cave): UndergroundStructurePlacer returns PlacedMarkers, rotation from anchor+direction"
```

---

### Task 6.2: Wire `Mob_Group_Spawn` markers to `POIGroupSpawnCoordinator`

**Files:** callers of `POIGroupSpawnCoordinator.firstSpawn`. Locate with:

```bash
grep -rn "firstSpawn\b" src/main/java
```

**Step 1:** For each caller that has access to a `PlacedMarkers`, iterate `placed.mobGroupSpawnsWorld()` instead of passing a code-chosen anchor:

```java
for (Vector3d mgsPos : placed.mobGroupSpawnsWorld()) {
    coordinator.firstSpawn(world, mgsPos, mobRole, playerUuid, questId, slotIdx++, quest, obj, playerData);
}
```

If a caller has only one mob group to spawn, use `placed.mobGroupSpawnsWorld().get(0)` with a size check.

**Step 2:** Compile and commit.

```bash
git add src/main/java/com/chonbosmods/...
git commit -m "refactor(poi): spawn mob groups at Mob_Group_Spawn marker positions"
```

---

## Phase 7 : Fetch-chest wire-up

### Task 7.1: Persist `poi_chest_positions` at paste time

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` (the `finalizePlacement` path)
- Or: wherever the POI prefab's `PlacedMarkers` become available to the quest

**Step 1:** Locate the point where the POI prefab finishes placing and quest bindings `poi_x/y/z` are written (`grep -rn "poi_x\|poi_y\|poi_z" src/main/java`).

**Step 2:** After `placed = Nat20PrefabPaster.paste(...)` completes, serialize `placed.chestSpawnsWorld()` into a single binding:

```java
StringBuilder sb = new StringBuilder();
for (Vector3d v : placed.chestSpawnsWorld()) {
    if (sb.length() > 0) sb.append(';');
    sb.append(String.format("%d,%d,%d", (int)v.getX(), (int)v.getY(), (int)v.getZ()));
}
quest.getVariableBindings().put("poi_chest_positions", sb.toString());
```

**Step 3:** Compile, commit.

```bash
git add src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(chest): persist Chest_Spawn marker positions into quest bindings"
```

---

### Task 7.2: `POIProximitySystem.maybePlaceQuestChest` reads from `poi_chest_positions`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/POIProximitySystem.java`

**Step 1:** Inside `maybePlaceQuestChest`, before the existing `poi_x/y/z` read:

```java
String chestPositions = b.get("poi_chest_positions");
int ax, ay, az;
if (chestPositions != null && !chestPositions.isEmpty()) {
    // Pick first position (deterministic; seeded random if multiple is worth a knob later)
    String[] parts = chestPositions.split(";")[0].split(",");
    ax = Integer.parseInt(parts[0]);
    ay = Integer.parseInt(parts[1]);
    az = Integer.parseInt(parts[2]);
} else {
    // Fallback: legacy behavior (anchor position)
    ax = (int) anchor.getX();
    ay = (int) anchor.getY();
    az = (int) anchor.getZ();
}
boolean placed = QuestChestPlacer.placeQuestChest(world, ax, ay, az, fetchItemType, ...);
```

**Step 2:** Compile, commit.

```bash
git add src/main/java/com/chonbosmods/quest/POIProximitySystem.java
git commit -m "feat(chest): read Chest_Spawn positions from bindings, fallback to anchor"
```

---

## Phase 8 : Admin command, cleanup, smoke

### Task 8.1: `/nat20 place <prefabKey>` admin command

**Files:**
- Create: `src/main/java/com/chonbosmods/commands/PlaceMarkerPrefabCommand.java`
- Modify: `src/main/java/com/chonbosmods/commands/Nat20Command.java` (register subcommand)

**Step 1:** Model after `src/main/java/com/chonbosmods/commands/PlacePrefabsCommand.java`. Body:

```java
public class PlaceMarkerPrefabCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> prefabKeyArg =
        withRequiredArg("prefabKey", "Prefab key e.g. Nat20/tree1", ArgTypes.STRING);

    public PlaceMarkerPrefabCommand() {
        super("place", "Place a Nat20 marker prefab at your feet via the new paster");
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        String key = prefabKeyArg.get(ctx);
        Path path = PrefabStore.get().findAssetPrefabPath(key);
        if (path == null) {
            ctx.sendMessage(Message.raw("Prefab not found: " + key));
            return;
        }
        IPrefabBuffer buffer;
        try { buffer = PrefabBufferUtil.getCached(path); }
        catch (Exception e) { ctx.sendMessage(Message.raw("Failed to load buffer: " + e.getMessage())); return; }

        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3i pos = new Vector3i((int) tf.getPosition().getX(),
                                    (int) tf.getPosition().getY(),
                                    (int) tf.getPosition().getZ());

        Nat20PrefabPaster.paste(buffer, world, pos, Rotation.None, new Random(), store)
            .whenComplete((placed, error) -> {
                if (placed == null) {
                    ctx.sendMessage(Message.raw("Paste failed"));
                    return;
                }
                ctx.sendMessage(Message.raw(String.format(
                    "Placed %s. Anchor=%s Direction=%s NPC=%d MobGroup=%d Chest=%d",
                    key, placed.anchorWorld(), placed.directionVectorWorld(),
                    placed.npcSpawnsWorld().size(), placed.mobGroupSpawnsWorld().size(),
                    placed.chestSpawnsWorld().size())));
            });
    }
}
```

**Step 2:** Register in `Nat20Command.java` (pattern will match existing subcommand adds).

**Step 3:** Compile, commit.

```bash
git add src/main/java/com/chonbosmods/commands/PlaceMarkerPrefabCommand.java \
        src/main/java/com/chonbosmods/commands/Nat20Command.java
git commit -m "feat(cmd): /nat20 place <key> admin command to test marker pastes"
```

---

### Task 8.2: Delete the old prefabs

**Files:**
- Delete: `assets/Server/Prefabs/Nat20/*.prefab.json` + `.lpf` (everything except `Nat20/tree1` once re-authored)
- Delete: `assets/Server/Prefabs/Nat20/dungeon/*` wholesale

**Step 1:** Delete:

```bash
cd /home/keroppi/Development/Hytale/Natural20
git rm assets/Server/Prefabs/Nat20/blackwoodHouse.prefab.json*
git rm assets/Server/Prefabs/Nat20/cart_basic.prefab.json*
git rm assets/Server/Prefabs/Nat20/outpost_basic.prefab.json*
git rm assets/Server/Prefabs/Nat20/testHouse.prefab.json*
git rm assets/Server/Prefabs/Nat20/town_basic.prefab.json*
git rm -r assets/Server/Prefabs/Nat20/dungeon/
```

**Step 2:** Compile (expect no breakage; code only references via `SettlementType.getPrefabKey()` and `UndergroundStructurePlacer.TEST_PREFAB_KEY`).

```bash
./gradlew compileJava
```

**Step 3:** Commit.

```bash
git commit -m "chore(prefabs): delete legacy prefabs ahead of marker re-authoring"
```

---

### Task 8.3: End-to-end smoke test

**Manual procedure, no code changes:**

1. Re-author `tree1` inside PrefabMaker:
   - Place **one** `Nat20/Anchor` block.
   - Place **one** `Nat20/Direction` block exactly one cell away from Anchor on +X (or any cardinal).
   - Place 2-3 `Nat20/Npc_Spawn` blocks at reasonable NPC spawn spots.
   - Place 1 `Nat20/Mob_Group_Spawn` block (optional).
   - Place 1 `Nat20/Chest_Spawn` block (optional).
   - Paint `Nat20/Force_Empty` where you want terrain carved.
   - `/prefab save tree1`.
2. Verify the saved `tree1.prefab.json` contains all six Nat20 keys:
   ```bash
   grep -o '"name": "Nat20/[^"]*"' ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/nat20.nat20/Server/Prefabs/tree1.prefab.json | sort -u
   ```
   Expected: six unique `Nat20/...` keys.
3. Copy `tree1.prefab.json` into `assets/Server/Prefabs/Nat20/`.
4. `./gradlew devServer` from main branch (merge `feat/prefab-markers` first if needed : devserver can't run from branch checkouts either, or do a throwaway merge into main for testing per the memory rule).
5. In-game:
   - `/nat20 place Nat20/tree1` : verify the prefab appears with carved terrain and markers are NOT visible as blocks.
   - Check chat output: marker counts match what you painted.
   - Travel 512 blocks to trigger natural settlement spawn : verify NPCs spawn at `Npc_Spawn` positions (not random offsets).
   - For cave POI: `/nat20 spawngroup` or accept a quest that triggers a POI placement : verify rotation aligns entrance with cave opening using the Direction block.
   - For fetch quest: accept a fetch quest tied to a POI with a `Chest_Spawn` marker. Walk to the POI. Verify chest appears at the marker position, not at the anchor.

**No commit.** If anything fails, open a bug task and iterate.

---

## Summary / task roll-up

| Phase | Tasks | Focus |
|---|---|---|
| 0 | 0.1 | Branch + baseline |
| 1 | 1.1 – 1.4 | Asset registration |
| 2 | 2.1 – 2.3 | Scanner (pure, TDD) |
| 3 | 3.1 – 3.2 | Filter buffer (TDD) |
| 4 | 4.1 – 4.3 | Paster (partial TDD; integration manual) |
| 5 | 5.1 – 5.6 | Settlement wire-up |
| 6 | 6.1 – 6.2 | Cave POI wire-up |
| 7 | 7.1 – 7.2 | Fetch-chest wire-up |
| 8 | 8.1 – 8.3 | Admin command, cleanup, smoke |

Roughly 22 tasks. Expected completion: ~4-6 hours of focused work plus manual smoke.

Commit after each passing test / compile step. Don't batch.
