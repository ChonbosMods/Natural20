package com.chonbosmods.progression.ambient;

import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.progression.GroupSource;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.math.vector.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmbientAnchorFinderTest {

    private static final AmbientAnchorFinder.SurfaceProbe FLAT_SURFACE = (x, z) -> 64;
    private static final AmbientAnchorFinder.HeadroomProbe CLEAR_SKY = (x, y, z) -> true;

    @Test
    void findsAnchorWhenAllRulesPass(@TempDir Path tmp) {
        AmbientAnchorFinder finder = emptyFinder(tmp);
        Optional<Vector3d> result = finder.find(
                FLAT_SURFACE, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isPresent(), "clean world should find an anchor on first try");
        Vector3d a = result.get();
        double dist = Math.hypot(a.getX(), a.getZ());
        assertTrue(dist >= 50 && dist <= 100,
                "anchor distance should fall in [50, 100]; got " + dist);
    }

    @Test
    void rejectsAllCandidatesInsidePoiVoidExclusion(@TempDir Path tmp) {
        CaveVoidRegistry voids = new CaveVoidRegistry(tmp.resolve("v.json"));
        // Seed voids densely enough that every 50-100 block radius candidate around origin
        // falls within 64 blocks of at least one void.
        for (int x = -200; x <= 200; x += 40) {
            for (int z = -200; z <= 200; z += 40) {
                CaveVoidRecord v = new CaveVoidRecord(
                        x, 50, z,                             // center
                        x - 8, 48, z - 8,                     // min
                        x + 8, 52, z + 8,                     // max
                        200, new ArrayList<int[]>(), 0L);     // volume, floorPositions, chunkKey
                voids.register(v);
            }
        }
        AmbientAnchorFinder finder = new AmbientAnchorFinder(
                voids, new SettlementRegistry(tmp),
                new Nat20MobGroupRegistry(tmp), AmbientSpawnConfig.load());
        Optional<Vector3d> result = finder.find(
                FLAT_SURFACE, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "all candidates inside POI void exclusion should abort");
    }

    @Test
    void rejectsAllCandidatesInsideSettlementExclusion(@TempDir Path tmp) {
        SettlementRegistry settlements = new SettlementRegistry(tmp);
        UUID world = new UUID(0, 0);
        for (int x = -200; x <= 200; x += 40) {
            for (int z = -200; z <= 200; z += 40) {
                settlements.register(new SettlementRecord(
                        "cell_" + x + "_" + z, world, x, 64, z, SettlementType.TOWN));
            }
        }
        AmbientAnchorFinder finder = new AmbientAnchorFinder(
                new CaveVoidRegistry(tmp.resolve("v.json")), settlements,
                new Nat20MobGroupRegistry(tmp), AmbientSpawnConfig.load());
        Optional<Vector3d> result = finder.find(
                FLAT_SURFACE, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "all candidates inside settlement exclusion should abort");
    }

    @Test
    void rejectsAllCandidatesInsideLiveGroupExclusion(@TempDir Path tmp) {
        Nat20MobGroupRegistry groups = new Nat20MobGroupRegistry(tmp);
        // Dense grid of existing groups anywhere a 50-100 candidate might land.
        for (int x = -200; x <= 200; x += 80) {
            for (int z = -200; z <= 200; z += 80) {
                MobGroupRecord r = new MobGroupRecord();
                r.setGroupKey("test:" + x + "_" + z);
                r.setSource(GroupSource.AMBIENT);
                r.setAnchor(x, 64, z);
                r.setSlots(List.of());
                groups.put(r);
            }
        }
        AmbientAnchorFinder finder = new AmbientAnchorFinder(
                new CaveVoidRegistry(tmp.resolve("v.json")),
                new SettlementRegistry(tmp),
                groups, AmbientSpawnConfig.load());
        Optional<Vector3d> result = finder.find(
                FLAT_SURFACE, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "all candidates inside live-group exclusion should abort");
    }

    @Test
    void rejectsWhenSurfaceIsInvalid(@TempDir Path tmp) {
        AmbientAnchorFinder finder = emptyFinder(tmp);
        AmbientAnchorFinder.SurfaceProbe waterEverywhere =
                (x, z) -> AmbientAnchorFinder.SurfaceProbe.INVALID;
        Optional<Vector3d> result = finder.find(
                waterEverywhere, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "surface probe returning INVALID everywhere must abort");
    }

    @Test
    void rejectsWhenHeadroomFails(@TempDir Path tmp) {
        AmbientAnchorFinder finder = emptyFinder(tmp);
        AmbientAnchorFinder.HeadroomProbe blocked = (x, y, z) -> false;
        Optional<Vector3d> result = finder.find(
                FLAT_SURFACE, blocked, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "no-headroom everywhere must abort");
    }

    @Test
    void rejectsCliffs(@TempDir Path tmp) {
        AmbientAnchorFinder finder = emptyFinder(tmp);
        // The finder calls SurfaceProbe 5 times per candidate: first is the center (rule 1),
        // then 4 cardinal neighbors (rule 3). Return 64 for the first call per candidate, 80
        // for the next four. Then reset. Every retry fails rule 3 (delta = 16 > 2).
        AmbientAnchorFinder.SurfaceProbe cliff = new AmbientAnchorFinder.SurfaceProbe() {
            private int callsThisCandidate = 0;
            @Override public int findSurfaceY(int x, int z) {
                int idx = callsThisCandidate++;
                if (callsThisCandidate >= 5) callsThisCandidate = 0;
                return idx == 0 ? 64 : 80;
            }
        };
        Optional<Vector3d> result = finder.find(
                cliff, CLEAR_SKY, new Vector3d(0, 64, 0), new Random(42));
        assertTrue(result.isEmpty(), "cliff (Y delta > 2) must fail rule 3 on every retry");
    }

    private static AmbientAnchorFinder emptyFinder(Path tmp) {
        return new AmbientAnchorFinder(
                new CaveVoidRegistry(tmp.resolve("v.json")),
                new SettlementRegistry(tmp),
                new Nat20MobGroupRegistry(tmp),
                AmbientSpawnConfig.load());
    }
}
