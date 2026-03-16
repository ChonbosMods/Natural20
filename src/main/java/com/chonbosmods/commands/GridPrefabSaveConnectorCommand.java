package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.ConnectorDef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the block region at the player's position as a dungeon connector prefab.
 * Usage: /gridprefab saveconnector <name>
 *
 * Connectors are always 5x4x2 (5 wide, 4 tall, 2 deep). Scans the region,
 * validates that at least one air block exists, and writes both a placeholder
 * prefab file and metadata JSON.
 */
public class GridPrefabSaveConnectorCommand extends AbstractPlayerCommand {

    private static final int WIDTH = 5;
    private static final int HEIGHT = 4;
    private static final int DEPTH = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Connector name (no spaces)", ArgTypes.STRING);

    public GridPrefabSaveConnectorCommand() {
        super("saveconnector", "Save a block region as a dungeon connector prefab");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String name = nameArg.get(context);

        // Get player position as origin
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        int originX = (int) pos.getX();
        int originY = (int) pos.getY();
        int originZ = (int) pos.getZ();

        context.sendMessage(Message.raw("Scanning " + WIDTH + "x" + HEIGHT + "x" + DEPTH +
            " connector at " + originX + ", " + originY + ", " + originZ + "..."));

        world.execute(() -> {
            List<String> warnings = new ArrayList<>();

            // Step 1: Scan blocks and validate at least one air block exists
            boolean hasAir = false;
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    for (int z = 0; z < DEPTH; z++) {
                        BlockType bt = world.getBlockType(originX + x, originY + y, originZ + z);
                        if (bt == null) {
                            hasAir = true;
                        }
                    }
                }
            }

            if (!hasAir) {
                warnings.add("Region is entirely solid: connectors should have at least one air block for passability");
            }

            // Step 2: Write placeholder prefab file
            String prefabKey = "Nat20/dungeon/connectors/" + name;
            writePlaceholderPrefab(name, prefabKey, warnings);

            // Step 3: Write metadata JSON
            writeMetadata(name, prefabKey, warnings);

            // Step 4: Register in connector registry
            ConnectorDef def = new ConnectorDef(name, prefabKey, List.of(), 5.0);
            Natural20.getInstance().getDungeonSystem().getConnectorRegistry().registerDef(def);

            // Step 5: Report results
            StringBuilder report = new StringBuilder();
            report.append("Saved connector '").append(name).append("'");

            context.sendMessage(Message.raw(report.toString()));

            if (!warnings.isEmpty()) {
                for (String warning : warnings) {
                    context.sendMessage(Message.raw("[WARN] " + warning));
                }
            }
        });
    }

    /**
     * Writes a placeholder prefab JSON file for the connector.
     */
    private void writePlaceholderPrefab(String name, String prefabKey, List<String> warnings) {
        Path prefabDir = resolvePrefabDir();
        if (prefabDir == null) {
            warnings.add("Could not resolve assets/Server/Prefabs/ directory: prefab file not written");
            return;
        }

        Path prefabFile = prefabDir.resolve(prefabKey + ".prefab.json");
        try {
            Files.createDirectories(prefabFile.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("_comment", "Placeholder: replace with exported prefab data");
            obj.addProperty("name", name);
            obj.addProperty("prefabKey", prefabKey);

            Files.writeString(prefabFile, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Failed to write prefab file: " + e.getMessage());
        }
    }

    /**
     * Resolves the assets/Server/Prefabs/ directory by walking up from the
     * plugin file path.
     */
    private Path resolvePrefabDir() {
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return null;

        Path candidate = pluginFile;
        for (int i = 0; i < 4; i++) {
            Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs");
            if (Files.isDirectory(assetsDir) || Files.isDirectory(candidate.resolve("assets"))) {
                return assetsDir;
            }
            candidate = candidate.getParent();
            if (candidate == null) break;
        }

        // Last resort: create relative to plugin file
        return pluginFile.resolve("assets").resolve("Server").resolve("Prefabs");
    }

    /**
     * Writes the connector metadata JSON to the data directory.
     */
    private void writeMetadata(String name, String prefabKey, List<String> warnings) {
        Path dataDir = Natural20.getInstance().getDungeonSystem().getDataDir();
        if (dataDir == null) {
            warnings.add("Dungeon data directory not initialized: metadata file not written");
            return;
        }

        Path metadataFile = dataDir.resolve("dungeon_connectors").resolve(name + ".json");
        try {
            Files.createDirectories(metadataFile.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("prefabKey", prefabKey);
            obj.addProperty("type", "connector");
            obj.add("tags", new JsonArray());
            obj.addProperty("weight", 5.0);
            obj.add("theme", null);

            Files.writeString(metadataFile, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Failed to write metadata file: " + e.getMessage());
        }
    }
}
