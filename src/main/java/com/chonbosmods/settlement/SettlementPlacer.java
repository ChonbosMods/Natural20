package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SettlementPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Placer");

    private final Map<SettlementType, IPrefabBuffer> prefabs = new EnumMap<>(SettlementType.class);

    /**
     * Load all prefabs. Tries the asset pack system first, then falls back to
     * resolving from the plugin's file path (needed for dev mode where assets/
     * is separate from the classpath).
     */
    public void init() {
        for (SettlementType type : SettlementType.values()) {
            try {
                Path prefabPath = findPrefabPath(type);
                if (prefabPath == null) {
                    LOGGER.atSevere().log( "[Nat20] Prefab not found: " + type.getPrefabKey());
                    continue;
                }
                IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
                prefabs.put(type, buffer);
                LOGGER.atFine().log( "[Nat20] Loaded prefab: " + type.getPrefabKey() + " from " + prefabPath);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Nat20] Failed to load prefab: " + type.getPrefabKey());
            }
        }
    }

    private Path findPrefabPath(SettlementType type) {
        String key = type.getPrefabKey();

        // Try asset pack lookup first (works when IncludesAssetPack=true and assets are in pack root)
        Path assetPath = PrefabStore.get().findAssetPrefabPath(key);
        if (assetPath != null) {
            return assetPath;
        }

        // Fall back: resolve from plugin file path. In dev mode, plugin root is src/main/resources/
        // but assets live in the sibling assets/ directory.
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile != null) {
            // pluginFile is e.g. .../src/main/resources/ or .../src/main/
            // Walk up to find assets/Server/Prefabs/
            Path candidate = pluginFile;
            for (int i = 0; i < 4; i++) {
                Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                    .resolve(key + ".prefab.json");
                if (Files.exists(assetsDir)) {
                    LOGGER.atFine().log( "[Nat20] Found prefab via fallback path: " + assetsDir);
                    return assetsDir;
                }
                // Also check Server/Prefabs directly (in case plugin root IS the assets dir)
                Path directDir = candidate.resolve("Server").resolve("Prefabs")
                    .resolve(key + ".prefab.json");
                if (Files.exists(directDir)) {
                    LOGGER.atFine().log( "[Nat20] Found prefab via direct path: " + directDir);
                    return directDir;
                }
                candidate = candidate.getParent();
                if (candidate == null) break;
            }
        }

        return null;
    }

    /**
     * Place a settlement structure via {@link Nat20PrefabPaster}, returning the
     * marker positions that were scanned out of the prefab. The returned future
     * completes asynchronously once chunks are loaded and the paste finishes.
     * Completes with {@code null} on failure.
     */
    public CompletableFuture<PlacedMarkers> place(
            World world, Vector3i desiredAnchorWorld, SettlementType type, Rotation yaw,
            ComponentAccessor<EntityStore> store, Random random) {
        IPrefabBuffer buffer = prefabs.get(type);
        if (buffer == null) {
            LOGGER.atWarning().log("No prefab loaded for type: %s", type);
            return CompletableFuture.completedFuture(null);
        }
        return Nat20PrefabPaster.paste(buffer, world, desiredAnchorWorld, yaw, random, store);
    }

    public boolean hasPrefab(SettlementType type) {
        return prefabs.containsKey(type);
    }
}
