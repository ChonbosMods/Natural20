package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.chonbosmods.dungeon.Face;
import com.chonbosmods.dungeon.GridPrefabUtil;
import com.chonbosmods.dungeon.SocketEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
 * Saves the block region based on the player's crosshair target and facing direction.
 * Usage: /gridprefab save <name> <gridW> <gridH> <gridD> [rotatable]
 *
 * The player looks at a block (crosshair target). The prefab region starts 1 block
 * above the targeted block, extending forward (player facing) and to the right.
 * Blocks are stored in canonical south-facing orientation.
 */
public class GridPrefabSaveCommand extends AbstractPlayerCommand {

    private static final int CELL_SIZE = 5;
    private static final String MARKER_BLOCK_KEY = "Ore_Thorium_Mud";
    private static final String EMPTY_BLOCK_KEY = "Empty";
    private static final double RAYCAST_MAX_DISTANCE = 50.0;
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

        // Get player position and rotation
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = rot.getY();
        float pitch = rot.getX();

        Face facing = GridPrefabUtil.getCardinalFacing(yaw);
        Vector3d direction = GridPrefabUtil.getDirection(yaw, pitch);

        // Eye position is approximately 1.6 blocks above foot position
        Vector3d eyePos = new Vector3d(pos.getX(), pos.getY() + 1.6, pos.getZ());

        int blockW = gridW * CELL_SIZE;
        int blockH = gridH * CELL_SIZE;
        int blockD = gridD * CELL_SIZE;

        // Capture final values for lambda
        final boolean rotatableFinal = rotatable;
        String prefabKey = "Nat20/dungeon/" + name;

        // All block access must happen on the world thread
        world.execute(() -> {
            // Raycast to find target block
            Vector3i target = GridPrefabUtil.getTargetBlock(world, eyePos, direction, RAYCAST_MAX_DISTANCE);
            if (target == null) {
                context.sendMessage(Message.raw("No block in view (look at a block to set the origin)."));
                return;
            }

            Vector3i origin = GridPrefabUtil.computeRegionOrigin(target, facing, blockW, blockD);
            int[] extents = GridPrefabUtil.getWorldExtents(facing, blockW, blockD);
            int worldExtX = extents[0];
            int worldExtZ = extents[1];

            context.sendMessage(Message.raw("Scanning " + blockW + "x" + blockH + "x" + blockD +
                " from " + origin.getX() + "," + origin.getY() + "," + origin.getZ() +
                " facing " + facing + " (" + worldExtX + "x" + blockH + "x" + worldExtZ + " world)..."));

            List<String> warnings = new ArrayList<>();

            // Read all blocks and rotations in world space, store in canonical local space
            String[][][] blocks = new String[blockW][blockH][blockD];
            int[][][] rotations = new int[blockW][blockH][blockD];
            for (int wx = 0; wx < worldExtX; wx++) {
                for (int y = 0; y < blockH; y++) {
                    for (int wz = 0; wz < worldExtZ; wz++) {
                        int worldX = origin.getX() + wx;
                        int worldY = origin.getY() + y;
                        int worldZ = origin.getZ() + wz;
                        BlockType bt = world.getBlockType(worldX, worldY, worldZ);
                        if (bt != null && !EMPTY_BLOCK_KEY.equals(bt.getId())) {
                            int[] local = GridPrefabUtil.worldToLocal(wx, wz, facing, worldExtX, worldExtZ);
                            blocks[local[0]][y][local[1]] = bt.getId();
                            rotations[local[0]][y][local[1]] = world.getBlockRotationIndex(worldX, worldY, worldZ);
                        }
                    }
                }
            }

            // Detect sockets on perimeter cell-faces (in canonical local space)
            List<SocketEntry> sockets = new ArrayList<>();
            for (int cx = 0; cx < gridW; cx++) {
                for (int cz = 0; cz < gridD; cz++) {
                    int cellBlockX = cx * CELL_SIZE;
                    int cellBlockZ = cz * CELL_SIZE;

                    if (cz == 0) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.NORTH, blockH));
                    }
                    if (cz == gridD - 1) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.SOUTH, blockH));
                    }
                    if (cx == 0) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.WEST, blockH));
                    }
                    if (cx == gridW - 1) {
                        sockets.add(detectSocket(blocks, cellBlockX, cellBlockZ, Face.EAST, blockH));
                    }
                }
            }

            // Replace marker blocks with dominant wall material
            int markersReplaced = replaceMarkerBlocks(world, blocks, rotations, origin, facing,
                blockW, blockH, blockD, worldExtX, worldExtZ, warnings);

            // Validate wall integrity
            validateWalls(blocks, gridW, gridD, blockH, warnings);

            // Write .blocks.json
            writeBlockData(name, blocks, rotations, blockW, blockH, blockD, warnings);

            // Write metadata JSON
            writeMetadata(name, prefabKey, gridW, gridH, gridD, rotatableFinal, sockets, warnings);

            // Register in piece registry
            DungeonPieceDef def = new DungeonPieceDef(
                name, prefabKey, gridW, gridH, gridD, rotatableFinal,
                sockets, List.of(), 1.0
            );
            Natural20.getInstance().getDungeonSystem().getPieceRegistry().registerDef(def);

            // Report results
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
     * Operates in canonical local space, then writes replacements back to world space.
     * Returns the number of blocks replaced.
     */
    private int replaceMarkerBlocks(World world, String[][][] blocks, int[][][] rotations,
                                     Vector3i origin, Face facing,
                                     int blockW, int blockH, int blockD,
                                     int worldExtX, int worldExtZ,
                                     List<String> warnings) {
        int replaced = 0;
        for (int lx = 0; lx < blockW; lx++) {
            for (int lz = 0; lz < blockD; lz++) {
                // Find marker blocks in this column
                List<Integer> markerYs = new ArrayList<>();
                for (int y = 0; y < blockH; y++) {
                    if (MARKER_BLOCK_KEY.equals(blocks[lx][y][lz])) {
                        markerYs.add(y);
                    }
                }
                if (markerYs.isEmpty()) continue;

                // Find dominant wall material in this column (non-null, non-marker)
                String dominant = findDominantMaterial(blocks, lx, lz, blockH);
                if (dominant == null) {
                    warnings.add("No dominant material at column (" + lx + "," + lz +
                        "): " + markerYs.size() + " marker blocks left unreplaced");
                    continue;
                }

                // Replace marker blocks in local space and update world
                int[] worldOffset = GridPrefabUtil.localToWorld(lx, lz, facing, blockW, blockD);
                for (int y : markerYs) {
                    try {
                        int worldX = origin.getX() + worldOffset[0];
                        int worldY = origin.getY() + y;
                        int worldZ = origin.getZ() + worldOffset[1];
                        world.setBlock(worldX, worldY, worldZ, dominant);
                        blocks[lx][y][lz] = dominant;
                        replaced++;
                    } catch (Exception e) {
                        warnings.add("Failed to replace marker at local (" +
                            lx + "," + y + "," + lz + "): " + e.getMessage());
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
     * Writes block data as a .blocks.json file to the dungeon_pieces data directory.
     * Each non-air block is stored with its position, type ID, and rotation index.
     */
    private void writeBlockData(String name, String[][][] blocks, int[][][] rotations,
                                 int blockW, int blockH, int blockD,
                                 List<String> warnings) {
        Path dataDir = Natural20.getInstance().getDungeonSystem().getDataDir();
        if (dataDir == null) {
            warnings.add("Dungeon data directory not initialized: block data file not written");
            return;
        }

        Path blockFile = dataDir.resolve("dungeon_pieces").resolve(name + ".blocks.json");
        try {
            Files.createDirectories(blockFile.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("width", blockW);
            obj.addProperty("height", blockH);
            obj.addProperty("depth", blockD);

            JsonArray blocksArr = new JsonArray();
            int blockCount = 0;
            for (int x = 0; x < blockW; x++) {
                for (int y = 0; y < blockH; y++) {
                    for (int z = 0; z < blockD; z++) {
                        if (blocks[x][y][z] != null) {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("x", x);
                            entry.addProperty("y", y);
                            entry.addProperty("z", z);
                            entry.addProperty("id", blocks[x][y][z]);
                            entry.addProperty("rot", rotations[x][y][z]);
                            blocksArr.add(entry);
                            blockCount++;
                        }
                    }
                }
            }
            obj.add("blocks", blocksArr);

            Files.writeString(blockFile, GSON.toJson(obj), StandardCharsets.UTF_8);
            warnings.add("Block data: " + blockCount + " blocks written to " + blockFile.getFileName());
        } catch (IOException e) {
            warnings.add("Failed to write block data file: " + e.getMessage());
        }
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
