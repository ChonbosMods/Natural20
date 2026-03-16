package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
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
     * Loads block data from a .blocks.json file in the given subdirectory.
     *
     * @param name the piece or connector name
     * @param type the subdirectory name ("dungeon_pieces" or "dungeon_connectors")
     * @return the parsed BlockData, or null if the file does not exist or cannot be read
     */
    public BlockData loadBlockData(String name, String type) {
        if (dataDir == null) return null;
        Path file = dataDir.resolve(type).resolve(name + ".blocks.json");
        if (!Files.exists(file)) return null;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            int w = obj.get("width").getAsInt();
            int h = obj.get("height").getAsInt();
            int d = obj.get("depth").getAsInt();
            List<BlockData.BlockEntry> entries = new ArrayList<>();
            for (var el : obj.getAsJsonArray("blocks")) {
                JsonObject b = el.getAsJsonObject();
                entries.add(new BlockData.BlockEntry(
                    b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt(),
                    b.get("id").getAsString()));
            }
            return new BlockData(w, h, d, entries);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load block data: %s", file);
            return null;
        }
    }

    public Path getDataDir() { return dataDir; }
    public DungeonPieceRegistry getPieceRegistry() { return pieceRegistry; }
    public ConnectorRegistry getConnectorRegistry() { return connectorRegistry; }
}
