package com.chonbosmods.prefab;

import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20FilteredBufferTest {

    private static final int STRUCTURAL_ID = 42;
    private static final int EXTRA_STRIP_ID = 999;

    /** Captures every block the downstream callback actually sees. */
    private record SeenBlock(int x, int y, int z, int blockId,
                             int supportValue, int rotation, int filler,
                             int fluidId, int fluidLevel) {}

    @BeforeAll
    static void seedMarkerIds() {
        Nat20PrefabConstants.anchorId = 100;
        Nat20PrefabConstants.directionId = 101;
        Nat20PrefabConstants.npcSpawnId = 102;
        Nat20PrefabConstants.mobGroupSpawnId = 103;
        Nat20PrefabConstants.chestSpawnId = 104;
        Nat20PrefabConstants.forceEmptyId = 105;

        // Populate stripIds with the six marker IDs plus an extra one used by
        // the extraStripIdAlsoDropped test to prove strip logic is driven off
        // the whole IntSet, not hard-coded to the six markers.
        IntOpenHashSet strip = new IntOpenHashSet();
        strip.add(Nat20PrefabConstants.anchorId);
        strip.add(Nat20PrefabConstants.directionId);
        strip.add(Nat20PrefabConstants.npcSpawnId);
        strip.add(Nat20PrefabConstants.mobGroupSpawnId);
        strip.add(Nat20PrefabConstants.chestSpawnId);
        strip.add(Nat20PrefabConstants.forceEmptyId);
        strip.add(EXTRA_STRIP_ID);
        Nat20PrefabConstants.setStripIdsForTests(IntSets.unmodifiable(strip));
    }

    @AfterAll
    static void restoreSentinelState() {
        Nat20PrefabConstants.resetForTests();
    }

    private static List<SeenBlock> runThroughFilter(IPrefabBuffer inner) {
        Nat20FilteredBuffer filtered = new Nat20FilteredBuffer(inner);
        List<SeenBlock> seen = new ArrayList<>();
        filtered.forEach(
                IPrefabBuffer.iterateAllColumns(),
                (x, y, z, blockId, holder, supportValue, rotation, filler,
                 call, fluidId, fluidLevel) ->
                        seen.add(new SeenBlock(x, y, z, blockId,
                                supportValue, rotation, filler,
                                fluidId, fluidLevel)),
                (x, z, entities, t) -> { /* no-op */ },
                (x, y, z, path, fitHeightmap, inheritSeed,
                 inheritHeightCondition, weights, rot, t) -> { /* no-op */ },
                new PrefabBufferCall(new Random(), PrefabRotation.ROTATION_0)
        );
        return seen;
    }

    @Test
    void anchorAndDirectionAndSpawnMarkersRewrittenToAir() {
        // Anchor, direction, NPC-spawn, and mob-group-spawn markers must be
        // rewritten to blockId 0 (not silently dropped) so PrefabUtil.paste
        // carves the world cell to air. The scanner has already extracted
        // their positions upstream; leaving the marker block in the world
        // would make NPCs/mobs spawn inside a visible beacon block.
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
                new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId),
                new FakePrefabBuffer.Cell(2, 0, 0, Nat20PrefabConstants.npcSpawnId),
                new FakePrefabBuffer.Cell(3, 0, 0, Nat20PrefabConstants.mobGroupSpawnId),
                new FakePrefabBuffer.Cell(4, 0, 0, STRUCTURAL_ID)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(5, seen.size());
        assertEquals(0, seen.get(0).blockId(), "anchor rewritten to air");
        assertEquals(0, seen.get(1).blockId(), "direction rewritten to air");
        assertEquals(0, seen.get(2).blockId(), "npc spawn rewritten to air");
        assertEquals(0, seen.get(3).blockId(), "mob group spawn rewritten to air");
        assertEquals(STRUCTURAL_ID, seen.get(4).blockId(), "structural block passes through");
    }

    @Test
    void chestMarkerSilentlyDropped() {
        // Chest markers are NOT rewritten to air: the downstream chest-spawn
        // system places an actual chest block at that position, which would
        // be overwritten if we also cleared the cell. Leave the world cell
        // alone (marker block is silently stripped).
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.chestSpawnId),
                new FakePrefabBuffer.Cell(1, 0, 0, STRUCTURAL_ID)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(1, seen.size());
        assertEquals(STRUCTURAL_ID, seen.get(0).blockId());
    }

    @Test
    void plainEmptyDropped() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                // blockId 0 + fluidId 0 = plain Empty passthrough, should be dropped
                new FakePrefabBuffer.Cell(0, 0, 0, 0),
                new FakePrefabBuffer.Cell(1, 0, 0, STRUCTURAL_ID)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(1, seen.size());
        assertEquals(STRUCTURAL_ID, seen.get(0).blockId());
    }

    @Test
    void forceEmptyRewrittenToZero() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.forceEmptyId)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(1, seen.size());
        assertEquals(0, seen.get(0).blockId(),
                "Force_Empty should be rewritten to Empty (id 0) so PrefabUtil.paste "
                        + "force-overwrites the world cell to air");
    }

    @Test
    void structuralBlockPassesThrough() {
        // support=5, rotation=3, filler=7, fluidId=11, fluidLevel=2
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, STRUCTURAL_ID,
                        5, 3, 7, 11, 2)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(1, seen.size());
        SeenBlock b = seen.get(0);
        assertEquals(0, b.x());
        assertEquals(0, b.y());
        assertEquals(0, b.z());
        assertEquals(STRUCTURAL_ID, b.blockId());
        assertEquals(5, b.supportValue());
        assertEquals(3, b.rotation());
        assertEquals(7, b.filler());
        assertEquals(11, b.fluidId());
        assertEquals(2, b.fluidLevel());
    }

    @Test
    void extraStripIdAlsoDropped() {
        FakePrefabBuffer inner = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, EXTRA_STRIP_ID),
                new FakePrefabBuffer.Cell(1, 0, 0, STRUCTURAL_ID)
        ));

        List<SeenBlock> seen = runThroughFilter(inner);

        assertEquals(1, seen.size());
        assertEquals(STRUCTURAL_ID, seen.get(0).blockId(),
                "stripIds membership must drive the drop, not a hard-coded list "
                        + "of the six Nat20 marker IDs");
    }
}
