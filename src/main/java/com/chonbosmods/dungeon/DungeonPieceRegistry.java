package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class DungeonPieceRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<String, DungeonPieceDef> defsById = new LinkedHashMap<>();
    private final List<DungeonPieceVariant> variants = new ArrayList<>();

    public void loadAll(Path dir) {
        if (!Files.isDirectory(dir)) {
            LOGGER.atWarning().log("Dungeon pieces directory does not exist: %s", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".blocks.json"))
                 .sorted().forEach(this::loadFile);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to list dungeon piece files in %s", dir);
        }
        LOGGER.atInfo().log("Loaded %d piece definitions, %d variants", defsById.size(), variants.size());
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String name = file.getFileName().toString().replace(".json", "");
            DungeonPieceDef def = parseDef(name, obj);
            registerDef(def);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse dungeon piece file: %s", file);
        }
    }

    private DungeonPieceDef parseDef(String name, JsonObject obj) {
        String prefabKey = obj.get("prefabKey").getAsString();
        int gridWidth = obj.get("gridWidth").getAsInt();
        int gridHeight = obj.get("gridHeight").getAsInt();
        int gridDepth = obj.get("gridDepth").getAsInt();
        boolean rotatable = obj.has("rotatable") && obj.get("rotatable").getAsBoolean();
        double weight = obj.has("weight") ? obj.get("weight").getAsDouble() : 1.0;

        List<SocketEntry> sockets = new ArrayList<>();
        if (obj.has("sockets")) {
            JsonArray arr = obj.getAsJsonArray("sockets");
            for (JsonElement el : arr) {
                JsonObject s = el.getAsJsonObject();
                sockets.add(new SocketEntry(
                    s.get("localX").getAsInt(),
                    s.get("localZ").getAsInt(),
                    Face.valueOf(s.get("face").getAsString().toUpperCase()),
                    s.get("type").getAsString()
                ));
            }
        }

        List<String> tags = new ArrayList<>();
        if (obj.has("tags")) {
            for (JsonElement el : obj.getAsJsonArray("tags")) {
                tags.add(el.getAsString());
            }
        }

        return new DungeonPieceDef(name, prefabKey, gridWidth, gridHeight, gridDepth,
            rotatable, sockets, tags, weight);
    }

    public void registerDef(DungeonPieceDef def) {
        defsById.put(def.name(), def);

        Set<String> seenHashes = new HashSet<>();
        int maxRotation = def.rotatable() ? 4 : 1;
        for (int r = 0; r < maxRotation; r++) {
            DungeonPieceVariant variant = new DungeonPieceVariant(def, r);
            if (seenHashes.add(variant.getSocketHash())) {
                variants.add(variant);
            }
        }
    }

    public DungeonPieceDef getDef(String name) {
        return defsById.get(name);
    }

    public Collection<DungeonPieceDef> getAllDefs() {
        return defsById.values();
    }

    public List<DungeonPieceVariant> getAllVariants() {
        return variants;
    }

    public int getDefCount() {
        return defsById.size();
    }

    public int getVariantCount() {
        return variants.size();
    }
}
