package com.chonbosmods.settlement;

import com.chonbosmods.prefab.Nat20PrefabPath;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SettlementPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Placer");

    /** Cache of pre-loaded buffers for FULL-mode settlement types. */
    private final Map<SettlementType, IPrefabBuffer> fullBuffers = new EnumMap<>(SettlementType.class);

    /**
     * Pre-load buffers for every {@link FullPlacement} settlement type. PIECE-mode
     * pieces are discovered and loaded on demand inside {@link SettlementPieceAssembler}.
     */
    public void init() {
        for (SettlementType type : SettlementType.values()) {
            if (!(type.getPlacement() instanceof FullPlacement full)) continue;
            try {
                Path prefabPath = Nat20PrefabPath.resolve(full.prefabKey());
                if (prefabPath == null) {
                    LOGGER.atSevere().log("Prefab not found: %s", full.prefabKey());
                    continue;
                }
                fullBuffers.put(type, PrefabBufferUtil.getCached(prefabPath));
                LOGGER.atFine().log("Loaded full prefab for %s: %s", type, prefabPath);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load full prefab for %s", type);
            }
        }
    }

    /**
     * Place a settlement at {@code desiredAnchorWorld}. Dispatches on the settlement's
     * {@link SettlementPlacement}: FULL pastes a single prefab, PIECE invokes
     * {@link SettlementPieceAssembler}. Returns a single merged
     * {@link PlacedMarkers} either way (for PIECE the anchor is the shared
     * center and the spawn lists aggregate every piece's markers).
     */
    public CompletableFuture<PlacedMarkers> place(
            World world, Vector3i desiredAnchorWorld, SettlementType type, Rotation yaw,
            ComponentAccessor<EntityStore> store, Random random) {
        SettlementPlacement placement = type.getPlacement();
        return switch (placement) {
            case FullPlacement full -> placeFull(world, desiredAnchorWorld, type, yaw, store, random);
            case PiecePlacement piece -> SettlementPieceAssembler.assemble(
                world, desiredAnchorWorld, piece, store, random);
        };
    }

    private CompletableFuture<PlacedMarkers> placeFull(
            World world, Vector3i anchor, SettlementType type, Rotation yaw,
            ComponentAccessor<EntityStore> store, Random random) {
        IPrefabBuffer buffer = fullBuffers.get(type);
        if (buffer == null) {
            LOGGER.atWarning().log("No full prefab loaded for type: %s", type);
            return CompletableFuture.completedFuture(null);
        }
        return Nat20PrefabPaster.paste(buffer, world, anchor, yaw, random, store);
    }

    /**
     * @return true if the settlement type is ready to place. FULL types require a
     *         loaded buffer; PIECE types are always "ready" (pool is enumerated
     *         inside the assembler at place-time).
     */
    public boolean hasPrefab(SettlementType type) {
        return switch (type.getPlacement()) {
            case FullPlacement full -> fullBuffers.containsKey(type);
            case PiecePlacement piece -> true;
        };
    }
}
