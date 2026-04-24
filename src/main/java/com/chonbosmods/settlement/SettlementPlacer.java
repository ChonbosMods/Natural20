package com.chonbosmods.settlement;

import com.chonbosmods.prefab.PlacedMarkers;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SettlementPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Placer");

    /**
     * Place a settlement at {@code desiredAnchorWorld} by assembling pieces drawn
     * from the settlement's {@link PiecePlacement} pool. Returns a single merged
     * {@link PlacedMarkers} whose anchor is the shared center and whose spawn
     * lists aggregate every piece's markers.
     */
    public CompletableFuture<PlacedMarkers> place(
            World world, Vector3i desiredAnchorWorld, SettlementType type, Rotation yaw,
            ComponentAccessor<EntityStore> store, Random random) {
        return SettlementPieceAssembler.assemble(
            world, desiredAnchorWorld, type.getPlacement(), store, random);
    }

    /**
     * Same as {@link #place}, but with an explicit minimum-pasted-pieces floor.
     * Used by the tutorial spawn settlement (minPasted=1) so it never aborts
     * when the terrain is hostile to piece placement.
     */
    public CompletableFuture<PlacedMarkers> place(
            World world, Vector3i desiredAnchorWorld, SettlementType type, Rotation yaw,
            ComponentAccessor<EntityStore> store, Random random, int minPastedPieces) {
        return SettlementPieceAssembler.assemble(
            world, desiredAnchorWorld, type.getPlacement(), store, random, minPastedPieces);
    }
}
