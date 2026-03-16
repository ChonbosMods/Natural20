package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;

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

    public IPrefabBuffer getPrefabBuffer(String prefabKey) {
        return prefabCache.computeIfAbsent(prefabKey, key -> {
            Path path = PrefabStore.get().findAssetPrefabPath(key);
            if (path == null) {
                LOGGER.atWarning().log("Prefab not found in asset store: %s", key);
                return null;
            }
            try {
                return PrefabBufferUtil.getCached(path);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load prefab buffer: %s", key);
                return null;
            }
        });
    }

    public Path getDataDir() { return dataDir; }
    public DungeonPieceRegistry getPieceRegistry() { return pieceRegistry; }
    public ConnectorRegistry getConnectorRegistry() { return connectorRegistry; }
}
