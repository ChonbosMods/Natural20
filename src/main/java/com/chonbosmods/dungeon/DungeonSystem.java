package com.chonbosmods.dungeon;

import com.chonbosmods.Natural20;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DungeonSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final DungeonPieceRegistry pieceRegistry = new DungeonPieceRegistry();
    private final ConnectorRegistry connectorRegistry = new ConnectorRegistry();
    private final Map<String, IPrefabBuffer> prefabCache = new HashMap<>();
    private Path dataDir;

    public void loadAll(Path dataDir) {
        this.dataDir = dataDir;
        pieceRegistry.loadAll(dataDir.resolve("dungeon_pieces"));
        connectorRegistry.loadAll(dataDir.resolve("dungeon_connectors"));
    }

    /**
     * Loads and caches a prefab buffer for the given prefab key.
     * Tries the asset pack system first, then falls back to resolving
     * from the plugin file path (needed for dev mode).
     *
     * @param prefabKey the prefab key (e.g. "Nat20/dungeon/room_3x1x3")
     * @return the loaded prefab buffer, or null if not found
     */
    public IPrefabBuffer getPrefabBuffer(String prefabKey) {
        return prefabCache.computeIfAbsent(prefabKey, key -> {
            // Try asset pack lookup first
            Path path = PrefabStore.get().findAssetPrefabPath(key);
            if (path == null) {
                // Fallback: resolve from plugin file path (dev mode)
                path = findPrefabFallback(key);
            }
            if (path == null) {
                LOGGER.atWarning().log("Prefab not found: %s", key);
                return null;
            }
            try {
                return PrefabBufferUtil.getCached(path);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load prefab: %s", key);
                return null;
            }
        });
    }

    private Path findPrefabFallback(String key) {
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return null;

        Path candidate = pluginFile;
        for (int i = 0; i < 4; i++) {
            Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                .resolve(key + ".prefab.json");
            if (Files.exists(assetsDir)) return assetsDir;
            // Also check Server/Prefabs directly (in case plugin root IS the assets dir)
            Path directDir = candidate.resolve("Server").resolve("Prefabs")
                .resolve(key + ".prefab.json");
            if (Files.exists(directDir)) return directDir;
            candidate = candidate.getParent();
            if (candidate == null) break;
        }

        return null;
    }

    public Path getDataDir() { return dataDir; }
    public DungeonPieceRegistry getPieceRegistry() { return pieceRegistry; }
    public ConnectorRegistry getConnectorRegistry() { return connectorRegistry; }
}
