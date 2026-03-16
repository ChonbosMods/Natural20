package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DungeonSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final DungeonPieceRegistry pieceRegistry = new DungeonPieceRegistry();
    private final ConnectorRegistry connectorRegistry = new ConnectorRegistry();
    private Path dataDir;

    public void loadAll(Path dataDir) {
        this.dataDir = dataDir;
        pieceRegistry.loadAll(dataDir.resolve("dungeon_pieces"));
        connectorRegistry.loadAll(dataDir.resolve("dungeon_connectors"));
    }

    /**
     * Loads a .blocks.json file from the given subdirectory under dataDir.
     *
     * @param name the piece/connector name (without extension)
     * @param type the subdirectory name (e.g. "dungeon_pieces" or "dungeon_connectors")
     * @return the parsed BlockData, or null if not found or parse error
     */
    public BlockData loadBlockData(String name, String type) {
        if (dataDir == null) {
            LOGGER.atWarning().log("Data directory not initialized");
            return null;
        }

        Path file = dataDir.resolve(type).resolve(name + ".blocks.json");
        if (!Files.exists(file)) {
            return null;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int width = obj.get("width").getAsInt();
            int height = obj.get("height").getAsInt();
            int depth = obj.get("depth").getAsInt();

            List<BlockData.BlockEntry> blocks = new ArrayList<>();
            JsonArray arr = obj.getAsJsonArray("blocks");
            for (JsonElement el : arr) {
                JsonObject b = el.getAsJsonObject();
                int x = b.get("x").getAsInt();
                int y = b.get("y").getAsInt();
                int z = b.get("z").getAsInt();
                String id = b.get("id").getAsString();
                int rot = b.has("rot") ? b.get("rot").getAsInt() : 0;
                blocks.add(new BlockData.BlockEntry(x, y, z, id, rot));
            }

            return new BlockData(width, height, depth, blocks);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load block data: %s", file);
            return null;
        }
    }

    public Path getDataDir() { return dataDir; }
    public DungeonPieceRegistry getPieceRegistry() { return pieceRegistry; }
    public ConnectorRegistry getConnectorRegistry() { return connectorRegistry; }
}
