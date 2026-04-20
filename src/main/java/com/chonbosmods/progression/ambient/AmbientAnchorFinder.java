package com.chonbosmods.progression.ambient;

import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.Optional;
import java.util.Random;

/**
 * Finds a valid ambient spawn anchor near a player. Stateless: all external state is injected.
 * Implements the six-rule validation from the design doc.
 */
public final class AmbientAnchorFinder {

    /**
     * Top-down surface finder. Returns world-Y of the standing surface (first solid block's
     * top face) at {@code (x, z)}, or {@link #INVALID} if the surface is unacceptable
     * (water, lava, leaves, partial block before solid). Rule 1 check.
     */
    @FunctionalInterface
    public interface SurfaceProbe {
        int INVALID = -1;
        int findSurfaceY(int x, int z);
    }

    /** Rule 2 check: at least 3 air blocks above {@code (x, surfaceY, z)}. */
    @FunctionalInterface
    public interface HeadroomProbe {
        boolean has3BlockHeadroom(int x, int surfaceY, int z);
    }

    private final CaveVoidRegistry voids;
    private final SettlementRegistry settlements;
    private final Nat20MobGroupRegistry groups;
    private final AmbientSpawnConfig cfg;

    public AmbientAnchorFinder(CaveVoidRegistry voids, SettlementRegistry settlements,
                               Nat20MobGroupRegistry groups, AmbientSpawnConfig cfg) {
        this.voids = voids;
        this.settlements = settlements;
        this.groups = groups;
        this.cfg = cfg;
    }

    /**
     * @return valid anchor on success; empty if all {@code cfg.anchorRetries()} candidates fail.
     *         Caller does NOT burn player cooldown on empty.
     */
    public Optional<Vector3d> find(SurfaceProbe surface, HeadroomProbe headroom,
                                   Vector3d playerPos, Random rng) {
        for (int attempt = 0; attempt < cfg.anchorRetries(); attempt++) {
            double bearing = rng.nextDouble() * 2.0 * Math.PI;
            int distBand = cfg.maxDistanceFromPlayer() - cfg.minDistanceFromPlayer();
            double dist = cfg.minDistanceFromPlayer() + rng.nextDouble() * distBand;
            int x = (int) Math.round(playerPos.getX() + Math.cos(bearing) * dist);
            int z = (int) Math.round(playerPos.getZ() + Math.sin(bearing) * dist);

            // Rule 1
            int y = surface.findSurfaceY(x, z);
            if (y == SurfaceProbe.INVALID) continue;

            // Rule 2
            if (!headroom.has3BlockHeadroom(x, y, z)) continue;

            // Rule 3
            int yN = surface.findSurfaceY(x, z + 3);
            int yS = surface.findSurfaceY(x, z - 3);
            int yE = surface.findSurfaceY(x + 3, z);
            int yW = surface.findSurfaceY(x - 3, z);
            if (!flatEnough(y, yN) || !flatEnough(y, yS)
                    || !flatEnough(y, yE) || !flatEnough(y, yW)) continue;

            // Rule 4
            if (voids.isNearAnyVoid(x, z, cfg.poiExclusionBlocks())) continue;

            // Rule 5
            if (settlements.isNearAnySettlement(x, z, cfg.settlementExclusionBlocks())) continue;

            // Rule 6
            if (isNearAnyGroupAnchor(x, z, cfg.groupAnchorExclusionBlocks())) continue;

            return Optional.of(new Vector3d(x, y, z));
        }
        return Optional.empty();
    }

    private boolean flatEnough(int anchorY, int neighborY) {
        if (neighborY == SurfaceProbe.INVALID) return false;
        return Math.abs(anchorY - neighborY) <= 2;
    }

    private boolean isNearAnyGroupAnchor(int x, int z, int radius) {
        long r2 = (long) radius * radius;
        for (MobGroupRecord r : groups.all()) {
            double dx = r.getAnchorX() - x;
            double dz = r.getAnchorZ() - z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }
}
