package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.chonbosmods.dungeon.Face;
import com.chonbosmods.dungeon.SocketEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.saving.PrefabSaver;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.saving.PrefabSaverSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
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
import java.util.*;

/**
 * Saves the block region at the player's position as a dungeon prefab piece.
 * Usage: /gridprefab save <name> <gridW> <gridH> <gridD> [rotatable]
 *
 * Scans blocks in a gridW*5 x gridH*5 x gridD*5 region, detects sockets
 * on perimeter cell-faces, replaces marker blocks with dominant wall material,
 * validates wall integrity, and writes block data + metadata JSON files.
 */
public class GridPrefabSaveCommand extends AbstractPlayerCommand {

    private static final int CELL_SIZE = 5;
    private static final String MARKER_BLOCK_KEY = "Ore_Thorium_Mud";
    private static final String EMPTY_BLOCK_KEY = "Empty";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Prefab name (no spaces)", ArgTypes.STRING);
    private final RequiredArg<String> gridWArg =
        withRequiredArg("gridW", "Grid width in cells", ArgTypes.STRING);
    private final RequiredArg<String> gridHArg =
        withRequiredArg("gridH", "Grid height in cells", ArgTypes.STRING);
    private final RequiredArg<String> gridDArg =
        withRequiredArg("gridD", "Grid depth in cells", ArgTypes.STRING);
    private final OptionalArg<String> rotatableArg =
        withOptionalArg("rotatable", "Allow rotation (true/false, default true)", ArgTypes.STRING);

    public GridPrefabSaveCommand() {
        super("save", "Save a block region as a dungeon prefab piece");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        // Parse arguments
        String name = nameArg.get(context);
        int gridW, gridH, gridD;
        try {
            gridW = Integer.parseInt(gridWArg.get(context));
            gridH = Integer.parseInt(gridHArg.get(context));
            gridD = Integer.parseInt(gridDArg.get(context));
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("gridW, gridH, gridD must be integers."));
            return;
        }
        if (gridW < 1 || gridH < 1 || gridD < 1) {
            context.sendMessage(Message.raw("Grid dimensions must be at least 1."));
            return;
        }

        boolean rotatable = true;
        if (context.provided(rotatableArg)) {
            rotatable = Boolean.parseBoolean(rotatableArg.get(context));
        }

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

        int blockW = gridW * CELL_SIZE;
        int blockH = gridH * CELL_SIZE;
        int blockD = gridD * CELL_SIZE;

        context.sendMessage(Message.raw("Scanning " + blockW + "x" + blockH + "x" + blockD +
            " blocks at " + originX + ", " + originY + ", " + originZ + "..."));

        // Capture final values for lambda
        final boolean rotatableFinal = rotatable;

        world.execute(() -> {
            List<String> warnings = new ArrayList<>();

            // Step 1: Read all blocks
            String[][][] blocks = new String[blockW][blockH][blockD];
            for (int x = 0; x < blockW; x++) {
                for (int y = 0; y < blockH; y++) {
                    for (int z = 0; z < blockD; z++) {
                        BlockType bt = world.getBlockType(originX + x, originY + y, originZ + z);
                        if (bt != null && !EMPTY_BLOCK_KEY.equals(bt.getId())) {
                            blocks[x][y][z] = bt.getId();
                        }
                    }
                }
            }

            // Step 2: Detect sockets on perimeter cell-faces
            List<SocketEntry> sockets = new ArrayList<>();
            for (int cx = 0; cx < gridW; cx++) {
                for (int cz = 0; cz < gridD; cz++) {
                    int cellBlockX = cx * CELL_SIZE;
                    int cellBlockZ = cz * CELL_SIZE;

                    // North face: only if cell is at z=0
                    if (cz == 0) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.NORTH, blockH));
                    }
                    // South face: only if cell is at z=gridD-1
                    if (cz == gridD - 1) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.SOUTH, blockH));
                    }
                    // West face: only if cell is at x=0
                    if (cx == 0) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.WEST, blockH));
                    }
                    // East face: only if cell is at x=gridW-1
                    if (cx == gridW - 1) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.EAST, blockH));
                    }
                }
            }

            // Step 3: Replace marker blocks with dominant wall material
            int markersReplaced = replaceMarkerBlocks(world, blocks, originX, originY, originZ,
                blockW, blockH, blockD, warnings);

            // Step 4: Validate wall integrity
            validateWalls(blocks, gridW, gridD, blockH, warnings);

            // Step 5: Save prefab using vanilla PrefabSaver pipeline
            String prefabKey = "Nat20/dungeon/" + name;
            savePrefab(context, world, name, originX, originY, originZ,
                blockW, blockH, blockD, warnings);

            // Step 6: Write metadata JSON
            writeMetadata(name, prefabKey, gridW, gridH, gridD, rotatableFinal, sockets, warnings);

            // Step 7: Register in piece registry
            DungeonPieceDef def = new DungeonPieceDef(
                name, prefabKey, gridW, gridH, gridD, rotatableFinal,
                sockets, List.of(), 1.0
            );
            Natural20.getInstance().getDungeonSystem().getPieceRegistry().registerDef(def);

            // Step 8: Report results
            long openCount = sockets.stream().filter(SocketEntry::isOpen).count();
            long sealedCount = sockets.size() - openCount;

            StringBuilder report = new StringBuilder();
            report.append("Saved prefab '").append(name).append("': ");
            report.append(sockets.size()).append(" sockets (");
            report.append(openCount).append(" open, ");
            report.append(sealedCount).append(" sealed)");
            if (markersReplaced > 0) {
                report.append(", replaced ").append(markersReplaced).append(" marker blocks");
            }

            context.sendMessage(Message.raw(report.toString()));

            if (!warnings.isEmpty()) {
                for (String warning : warnings) {
                    context.sendMessage(Message.raw("[WARN] " + warning));
                }
            }
        });
    }

    /**
     * Detects whether a socket zone contains marker blocks, determining if
     * the socket is sealed (marker present) or open (no marker).
     */
    private SocketEntry detectSocket(String[][][] blocks, int cellBlockX, int cellBlockZ,
                                      Face face, int blockH) {
        int localX = cellBlockX / CELL_SIZE;
        int localZ = cellBlockZ / CELL_SIZE;

        // Socket zone: 3 blocks wide, 4 blocks tall (y=1 to y=4)
        boolean hasMarker = false;

        for (int dy = 1; dy <= 4 && dy < blockH; dy++) {
            for (int i = 0; i < 3; i++) {
                int bx, bz;
                switch (face) {
                    case NORTH -> {
                        // z=0 wall: x = cellBlockX+1 to cellBlockX+3, z = cellBlockZ
                        bx = cellBlockX + 1 + i;
                        bz = cellBlockZ;
                    }
                    case SOUTH -> {
                        // z=4 wall: x = cellBlockX+1 to cellBlockX+3, z = cellBlockZ+4
                        bx = cellBlockX + 1 + i;
                        bz = cellBlockZ + 4;
                    }
                    case WEST -> {
                        // x=0 wall: x = cellBlockX, z = cellBlockZ+1 to cellBlockZ+3
                        bx = cellBlockX;
                        bz = cellBlockZ + 1 + i;
                    }
                    case EAST -> {
                        // x=4 wall: x = cellBlockX+4, z = cellBlockZ+1 to cellBlockZ+3
                        bx = cellBlockX + 4;
                        bz = cellBlockZ + 1 + i;
                    }
                    default -> { continue; }
                }

                if (bx >= 0 && bx < blocks.length && bz >= 0 && bz < blocks[0][0].length) {
                    String blockKey = blocks[bx][dy][bz];
                    if (MARKER_BLOCK_KEY.equals(blockKey)) {
                        hasMarker = true;
                    }
                }
            }
        }

        return new SocketEntry(localX, localZ, face,
            hasMarker ? SocketEntry.SEALED : SocketEntry.OPEN);
    }

    /**
     * Replaces marker blocks with the dominant wall material at each column.
     * Returns the number of blocks replaced.
     */
    private int replaceMarkerBlocks(World world, String[][][] blocks,
                                     int originX, int originY, int originZ,
                                     int blockW, int blockH, int blockD,
                                     List<String> warnings) {
        int replaced = 0;
        for (int x = 0; x < blockW; x++) {
            for (int z = 0; z < blockD; z++) {
                // Find marker blocks in this column
                List<Integer> markerYs = new ArrayList<>();
                for (int y = 0; y < blockH; y++) {
                    if (MARKER_BLOCK_KEY.equals(blocks[x][y][z])) {
                        markerYs.add(y);
                    }
                }
                if (markerYs.isEmpty()) continue;

                // Find dominant wall material in this column (non-null, non-marker)
                String dominant = findDominantMaterial(blocks, x, z, blockH);
                if (dominant == null) {
                    warnings.add("No dominant material at column (" + x + "," + z +
                        "): " + markerYs.size() + " marker blocks left unreplaced");
                    continue;
                }

                // Replace marker blocks with dominant material
                for (int y : markerYs) {
                    try {
                        world.setBlock(originX + x, originY + y, originZ + z, dominant);
                        blocks[x][y][z] = dominant;
                        replaced++;
                    } catch (Exception e) {
                        warnings.add("Failed to replace block at (" +
                            (originX + x) + "," + (originY + y) + "," + (originZ + z) + "): " +
                            e.getMessage());
                    }
                }
            }
        }
        return replaced;
    }

    /**
     * Finds the most common non-null, non-marker block type in a column.
     */
    private String findDominantMaterial(String[][][] blocks, int x, int z, int blockH) {
        Map<String, Integer> counts = new HashMap<>();
        for (int y = 0; y < blockH; y++) {
            String key = blocks[x][y][z];
            if (key != null && !MARKER_BLOCK_KEY.equals(key)) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Validates that perimeter walls have no air gaps at y=0 through y=4.
     * Adds warnings for any gaps found.
     */
    private void validateWalls(String[][][] blocks, int gridW, int gridD,
                                int blockH, List<String> warnings) {
        int blockW = gridW * CELL_SIZE;
        int blockD = gridD * CELL_SIZE;
        int wallTop = Math.min(CELL_SIZE, blockH);
        int gaps = 0;

        // North wall (z=0)
        for (int x = 0; x < blockW; x++) {
            for (int y = 0; y < wallTop; y++) {
                if (blocks[x][y][0] == null) gaps++;
            }
        }

        // South wall (z=blockD-1)
        for (int x = 0; x < blockW; x++) {
            for (int y = 0; y < wallTop; y++) {
                if (blocks[x][y][blockD - 1] == null) gaps++;
            }
        }

        // West wall (x=0)
        for (int z = 0; z < blockD; z++) {
            for (int y = 0; y < wallTop; y++) {
                if (blocks[0][y][z] == null) gaps++;
            }
        }

        // East wall (x=blockW-1)
        for (int z = 0; z < blockD; z++) {
            for (int y = 0; y < wallTop; y++) {
                if (blocks[blockW - 1][y][z] == null) gaps++;
            }
        }

        if (gaps > 0) {
            warnings.add("Wall integrity: " + gaps + " air gaps found in perimeter walls (y=0 to y=" +
                (wallTop - 1) + ")");
        }
    }

    /**
     * Saves the block region as a .prefab.json file using the vanilla PrefabSaver pipeline.
     * This captures blocks with rotation, filler, multi-block furniture state, and fluids.
     */
    private void savePrefab(CommandContext context, World world, String name,
                             int originX, int originY, int originZ,
                             int blockW, int blockH, int blockD,
                             List<String> warnings) {
        Path prefabPath = resolvePrefabPath("dungeon/" + name);
        if (prefabPath == null) {
            warnings.add("Could not resolve prefab output path");
            return;
        }

        try {
            Files.createDirectories(prefabPath.getParent());
        } catch (IOException e) {
            warnings.add("Failed to create prefab directory: " + e.getMessage());
            return;
        }

        Vector3i minPoint = new Vector3i(originX, originY, originZ);
        Vector3i maxPoint = new Vector3i(originX + blockW - 1, originY + blockH - 1, originZ + blockD - 1);
        Vector3i anchor = new Vector3i(0, 0, 0);

        PrefabSaverSettings settings = new PrefabSaverSettings();
        settings.setBlocks(true);
        settings.setEntities(false);
        settings.setOverwriteExisting(true);

        PrefabSaver.savePrefab(context.sender(), world, prefabPath, minPoint, maxPoint,
                anchor, anchor, anchor, settings)
            .thenAccept(success -> {
                if (success) {
                    context.sendMessage(Message.raw("Prefab saved: " + prefabPath.getFileName()));
                } else {
                    context.sendMessage(Message.raw("[WARN] Prefab save may have failed"));
                }
            });
    }

    /**
     * Resolves a prefab key to a file path under assets/Server/Prefabs/Nat20/.
     * Walks up from the plugin file path to find the assets directory (dev mode).
     */
    private Path resolvePrefabPath(String relativePath) {
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return null;

        Path candidate = pluginFile;
        for (int i = 0; i < 4; i++) {
            Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                .resolve("Nat20");
            if (Files.exists(assetsDir) || Files.exists(candidate.resolve("assets"))) {
                return assetsDir.resolve(relativePath + ".prefab.json");
            }
            candidate = candidate.getParent();
            if (candidate == null) break;
        }

        return null;
    }

    /**
     * Writes the dungeon piece metadata JSON to the data directory.
     */
    private void writeMetadata(String name, String prefabKey, int gridW, int gridH, int gridD,
                                boolean rotatable, List<SocketEntry> sockets, List<String> warnings) {
        Path dataDir = Natural20.getInstance().getDungeonSystem().getDataDir();
        if (dataDir == null) {
            warnings.add("Dungeon data directory not initialized: metadata file not written");
            return;
        }

        Path metadataFile = dataDir.resolve("dungeon_pieces").resolve(name + ".json");
        try {
            Files.createDirectories(metadataFile.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("prefabKey", prefabKey);
            obj.addProperty("gridWidth", gridW);
            obj.addProperty("gridHeight", gridH);
            obj.addProperty("gridDepth", gridD);
            obj.addProperty("rotatable", rotatable);

            JsonArray socketsArr = new JsonArray();
            for (SocketEntry s : sockets) {
                JsonObject so = new JsonObject();
                so.addProperty("localX", s.localX());
                so.addProperty("localZ", s.localZ());
                so.addProperty("face", s.face().name().toLowerCase());
                so.addProperty("type", s.type());
                socketsArr.add(so);
            }
            obj.add("sockets", socketsArr);

            obj.add("tags", new JsonArray());
            obj.addProperty("weight", 1.0);
            obj.add("theme", null);

            Files.writeString(metadataFile, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Failed to write metadata file: " + e.getMessage());
        }
    }
}
