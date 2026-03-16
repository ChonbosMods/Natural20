package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import java.util.*;

/**
 * Core dungeon generation algorithm. Orchestrates five phases:
 * entrance placement, growth loop, guarantee placements, dead-end capping,
 * and connector pass.
 */
public class DungeonGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private record OpenSocket(PlacedPiece piece, int cellX, int cellZ, Face face) {}

    private record CandidatePlacement(DungeonPieceVariant variant, int gridOriginX, int gridOriginZ,
                                      int matchSocketLocalX, int matchSocketLocalZ) {}

    private final DungeonSystem system;
    private final DungeonGeneratorConfig config;
    private final Random random;

    private final OccupancyGrid grid = new OccupancyGrid();
    private final List<PlacedPiece> placedPieces = new ArrayList<>();
    private final List<ConnectionRecord> connections = new ArrayList<>();
    private final List<OpenSocket> openSockets = new ArrayList<>();
    private final Map<String, OpenSocket> reservedSockets = new LinkedHashMap<>();

    public DungeonGenerator(DungeonSystem system, DungeonGeneratorConfig config, Random random) {
        this.system = system;
        this.config = config;
        this.random = random;
    }

    /**
     * Run all generation phases. Must be called from within world.execute().
     */
    public void generate(World world, ComponentAccessor<EntityStore> accessor) {
        LOGGER.atInfo().log("Starting dungeon generation: min=%d max=%d anchor=(%d,%d,%d) guarantees=%s",
            config.minPieces(), config.maxPieces(),
            config.worldAnchor().getX(), config.worldAnchor().getY(), config.worldAnchor().getZ(),
            config.guaranteeTags());

        placeEntrance(world, accessor);
        growthLoop(world, accessor);
        guaranteePlacements(world, accessor);
        capDeadEnds(world, accessor);
        connectorPass(world, accessor);

        LOGGER.atInfo().log("Dungeon generation complete: %d pieces placed, %d connections, %d remaining open sockets",
            placedPieces.size(), connections.size(), openSockets.size());
    }

    // ---- Phase 1: Place Entrance ----

    private void placeEntrance(World world, ComponentAccessor<EntityStore> accessor) {
        List<DungeonPieceVariant> entranceVariants = system.getPieceRegistry().getAllVariants().stream()
            .filter(v -> v.getTags().contains("entrance"))
            .toList();

        if (entranceVariants.isEmpty()) {
            LOGGER.atSevere().log("No entrance variants found: cannot generate dungeon");
            return;
        }

        DungeonPieceVariant entrance = weightedRandomPick(entranceVariants);
        PlacedPiece placed = placePiece(entrance, 0, 0, 0, config.worldAnchor(), world, accessor);

        if (placed == null) {
            LOGGER.atSevere().log("Failed to place entrance piece");
            return;
        }

        LOGGER.atInfo().log("Placed entrance: %s (rot=%d) at grid (0,0,0)",
            entrance.getName(), entrance.getRotation());

        // Reserve sockets for guarantee tags
        for (String tag : config.guaranteeTags()) {
            reserveBestSocket(tag);
        }
    }

    // ---- Phase 2: Growth Loop ----

    private void growthLoop(World world, ComponentAccessor<EntityStore> accessor) {
        int stallCount = 0;

        while (placedPieces.size() < config.maxPieces()) {
            // Filter to unreserved open sockets
            List<OpenSocket> unreserved = openSockets.stream()
                .filter(s -> !reservedSockets.containsValue(s))
                .toList();

            if (unreserved.isEmpty()) {
                LOGGER.atInfo().log("No unreserved open sockets remain: ending growth loop");
                break;
            }

            // Pick a random unreserved socket as anchor
            OpenSocket anchor = unreserved.get(random.nextInt(unreserved.size()));

            // Target grid cell
            int targetX = anchor.cellX() + anchor.piece().gridX() + anchor.face().dx();
            int targetZ = anchor.cellZ() + anchor.piece().gridZ() + anchor.face().dz();

            // Find valid placements
            List<CandidatePlacement> candidates = findValidPlacements(anchor, targetX, targetZ, null);

            if (candidates.isEmpty()) {
                stallCount++;
                // Move this socket to the end to avoid re-picking immediately
                openSockets.remove(anchor);
                openSockets.add(anchor);

                if (stallCount >= unreserved.size()) {
                    LOGGER.atInfo().log("All unreserved sockets tried without success: ending growth loop");
                    break;
                }
                continue;
            }

            stallCount = 0;

            // Weighted random pick from candidates
            CandidatePlacement pick = weightedRandomPickCandidate(candidates);

            // Compute block origin
            Vector3i newOrigin = computeBlockOrigin(anchor, pick);

            // Place the piece
            PlacedPiece newPiece = placePiece(pick.variant(), pick.gridOriginX(), 0, pick.gridOriginZ(),
                newOrigin, world, accessor);

            if (newPiece == null) {
                continue;
            }

            // Record connection
            Face oppositeFace = anchor.face().opposite();
            connections.add(new ConnectionRecord(
                anchor.piece(), anchor.cellX(), anchor.cellZ(), anchor.face(),
                newPiece, pick.matchSocketLocalX(), pick.matchSocketLocalZ(), oppositeFace
            ));

            // Remove anchor socket from open list
            openSockets.remove(anchor);
            // Also remove from reserved if it was there (shouldn't be in growth loop, but safety)
            reservedSockets.values().remove(anchor);

            // Remove the matching socket on the new piece
            openSockets.removeIf(s ->
                s.piece() == newPiece
                && s.cellX() == pick.matchSocketLocalX()
                && s.cellZ() == pick.matchSocketLocalZ()
                && s.face() == oppositeFace
            );

            LOGGER.atInfo().log("Growth: placed %s (rot=%d) at grid (%d,0,%d), piece count=%d",
                pick.variant().getName(), pick.variant().getRotation(),
                pick.gridOriginX(), pick.gridOriginZ(), placedPieces.size());
        }
    }

    // ---- Phase 3: Guarantee Placements ----

    private void guaranteePlacements(World world, ComponentAccessor<EntityStore> accessor) {
        for (var entry : new ArrayList<>(reservedSockets.entrySet())) {
            String tag = entry.getKey();
            OpenSocket reserved = entry.getValue();

            if (!openSockets.contains(reserved)) {
                LOGGER.atWarning().log("Reserved socket for tag '%s' is no longer open: skipping", tag);
                continue;
            }

            int targetX = reserved.cellX() + reserved.piece().gridX() + reserved.face().dx();
            int targetZ = reserved.cellZ() + reserved.piece().gridZ() + reserved.face().dz();

            List<CandidatePlacement> candidates = findValidPlacements(reserved, targetX, targetZ, tag);

            if (candidates.isEmpty()) {
                LOGGER.atWarning().log("No valid placement found for guarantee tag '%s': skipping", tag);
                continue;
            }

            CandidatePlacement pick = weightedRandomPickCandidate(candidates);
            Vector3i newOrigin = computeBlockOrigin(reserved, pick);

            PlacedPiece newPiece = placePiece(pick.variant(), pick.gridOriginX(), 0, pick.gridOriginZ(),
                newOrigin, world, accessor);

            if (newPiece == null) {
                LOGGER.atWarning().log("Failed to paste guarantee piece for tag '%s'", tag);
                continue;
            }

            Face oppositeFace = reserved.face().opposite();
            connections.add(new ConnectionRecord(
                reserved.piece(), reserved.cellX(), reserved.cellZ(), reserved.face(),
                newPiece, pick.matchSocketLocalX(), pick.matchSocketLocalZ(), oppositeFace
            ));

            openSockets.remove(reserved);

            openSockets.removeIf(s ->
                s.piece() == newPiece
                && s.cellX() == pick.matchSocketLocalX()
                && s.cellZ() == pick.matchSocketLocalZ()
                && s.face() == oppositeFace
            );

            LOGGER.atInfo().log("Guarantee: placed %s for tag '%s' at grid (%d,0,%d)",
                pick.variant().getName(), tag, pick.gridOriginX(), pick.gridOriginZ());
        }
    }

    // ---- Phase 4: Cap Dead Ends ----

    private void capDeadEnds(World world, ComponentAccessor<EntityStore> accessor) {
        for (OpenSocket socket : new ArrayList<>(openSockets)) {
            int targetX = socket.cellX() + socket.piece().gridX() + socket.face().dx();
            int targetZ = socket.cellZ() + socket.piece().gridZ() + socket.face().dz();

            List<CandidatePlacement> candidates = findValidPlacements(socket, targetX, targetZ, "dead_end");

            if (candidates.isEmpty()) {
                continue;
            }

            CandidatePlacement pick = weightedRandomPickCandidate(candidates);
            Vector3i newOrigin = computeBlockOrigin(socket, pick);

            PlacedPiece newPiece = placePiece(pick.variant(), pick.gridOriginX(), 0, pick.gridOriginZ(),
                newOrigin, world, accessor);

            if (newPiece == null) {
                continue;
            }

            Face oppositeFace = socket.face().opposite();
            connections.add(new ConnectionRecord(
                socket.piece(), socket.cellX(), socket.cellZ(), socket.face(),
                newPiece, pick.matchSocketLocalX(), pick.matchSocketLocalZ(), oppositeFace
            ));

            openSockets.remove(socket);

            openSockets.removeIf(s ->
                s.piece() == newPiece
                && s.cellX() == pick.matchSocketLocalX()
                && s.cellZ() == pick.matchSocketLocalZ()
                && s.face() == oppositeFace
            );
        }

        LOGGER.atInfo().log("Dead-end capping complete: %d open sockets remain", openSockets.size());
    }

    // ---- Phase 5: Connector Pass ----

    private void connectorPass(World world, ComponentAccessor<EntityStore> accessor) {
        ConnectorRegistry connectorRegistry = system.getConnectorRegistry();
        if (connectorRegistry.getDefCount() == 0) {
            LOGGER.atInfo().log("No connectors registered: skipping connector pass");
            return;
        }

        for (ConnectionRecord conn : connections) {
            ConnectorDef connector = connectorRegistry.selectRandom(random);
            IPrefabBuffer buffer = system.getPrefabBuffer(connector.prefabKey());
            if (buffer == null) {
                LOGGER.atWarning().log("Failed to load connector prefab: %s", connector.prefabKey());
                continue;
            }

            Vector3i pos = computeConnectorPosition(conn);

            // Connectors are authored north-south (5 wide on X, 2 deep on Z).
            // For east-west connections, rotate 90 degrees.
            boolean isEastWest = conn.faceA() == Face.EAST || conn.faceA() == Face.WEST;
            Rotation rotation = isEastWest ? Rotation.Ninety : Rotation.None;

            PrefabUtil.paste(buffer, world, pos, rotation, true, random, 0, accessor);
        }

        LOGGER.atInfo().log("Placed %d connectors", connections.size());
    }

    // ---- Helper Methods ----

    /**
     * Place a piece at the given grid coordinates and block origin.
     * Pastes the prefab into the world and updates the occupancy grid and open socket list.
     * Returns the PlacedPiece, or null if the prefab buffer could not be loaded.
     */
    private PlacedPiece placePiece(DungeonPieceVariant variant, int gridX, int gridY, int gridZ,
                                    Vector3i blockOrigin, World world,
                                    ComponentAccessor<EntityStore> accessor) {
        IPrefabBuffer buffer = system.getPrefabBuffer(variant.getPrefabKey());
        if (buffer == null) {
            LOGGER.atWarning().log("Failed to load prefab: %s", variant.getPrefabKey());
            return null;
        }

        PrefabUtil.paste(buffer, world, blockOrigin, variant.toSdkRotation(), true, random, 0, accessor);

        PlacedPiece placed = new PlacedPiece(variant, gridX, gridY, gridZ, blockOrigin);
        placedPieces.add(placed);

        grid.claimAll(gridX, gridY, gridZ, variant.getGridWidth(), variant.getGridHeight(), variant.getGridDepth());

        // Register open sockets that are on the perimeter
        for (SocketEntry socket : variant.getSockets()) {
            if (socket.isOpen() && isPerimeterFace(socket, variant)) {
                openSockets.add(new OpenSocket(placed, socket.localX(), socket.localZ(), socket.face()));
            }
        }

        return placed;
    }

    /**
     * Check if a socket is on the perimeter of its piece.
     */
    private boolean isPerimeterFace(SocketEntry socket, DungeonPieceVariant variant) {
        return switch (socket.face()) {
            case NORTH -> socket.localZ() == 0;
            case SOUTH -> socket.localZ() == variant.getGridDepth() - 1;
            case WEST  -> socket.localX() == 0;
            case EAST  -> socket.localX() == variant.getGridWidth() - 1;
        };
    }

    /**
     * Find all valid placements that can connect to the given anchor socket.
     * If requiredTag is non-null, only variants with that tag are considered.
     */
    private List<CandidatePlacement> findValidPlacements(OpenSocket anchor, int targetX, int targetZ,
                                                          String requiredTag) {
        Face oppositeFace = anchor.face().opposite();
        List<CandidatePlacement> candidates = new ArrayList<>();

        for (DungeonPieceVariant variant : system.getPieceRegistry().getAllVariants()) {
            if (requiredTag != null && !variant.getTags().contains(requiredTag)) {
                continue;
            }

            for (SocketEntry socket : variant.getSockets()) {
                if (!socket.isOpen() || socket.face() != oppositeFace) {
                    continue;
                }

                if (!isPerimeterFace(socket, variant)) {
                    continue;
                }

                // Compute the implied grid origin if this socket were aligned with the target cell
                int gridOriginX = targetX - socket.localX();
                int gridOriginZ = targetZ - socket.localZ();

                // Check that all cells are free
                if (grid.canPlace(gridOriginX, 0, gridOriginZ,
                    variant.getGridWidth(), variant.getGridHeight(), variant.getGridDepth())) {
                    candidates.add(new CandidatePlacement(variant, gridOriginX, gridOriginZ,
                        socket.localX(), socket.localZ()));
                }
            }
        }

        return candidates;
    }

    /**
     * Compute the block-space origin for a new piece being connected to an anchor.
     */
    private Vector3i computeBlockOrigin(OpenSocket anchor, CandidatePlacement placement) {
        PlacedPiece anchorPiece = anchor.piece();
        int anchorBlockW = anchorPiece.variant().getGridWidth() * 5;
        int anchorBlockD = anchorPiece.variant().getGridDepth() * 5;
        int ax = anchorPiece.blockOrigin().getX();
        int ay = anchorPiece.blockOrigin().getY();
        int az = anchorPiece.blockOrigin().getZ();
        int newBlockW = placement.variant().getGridWidth() * 5;
        int newBlockD = placement.variant().getGridDepth() * 5;

        return switch (anchor.face()) {
            case EAST -> new Vector3i(
                ax + anchorBlockW,
                ay,
                az + (placement.gridOriginZ() - anchorPiece.gridZ()) * 5
            );
            case WEST -> new Vector3i(
                ax - newBlockW,
                ay,
                az + (placement.gridOriginZ() - anchorPiece.gridZ()) * 5
            );
            case SOUTH -> new Vector3i(
                ax + (placement.gridOriginX() - anchorPiece.gridX()) * 5,
                ay,
                az + anchorBlockD
            );
            case NORTH -> new Vector3i(
                ax + (placement.gridOriginX() - anchorPiece.gridX()) * 5,
                ay,
                az - newBlockD
            );
        };
    }

    /**
     * Compute the world position where a connector prefab should be pasted.
     */
    private Vector3i computeConnectorPosition(ConnectionRecord conn) {
        PlacedPiece pieceA = conn.pieceA();
        int ax = pieceA.blockOrigin().getX();
        int ay = pieceA.blockOrigin().getY();
        int az = pieceA.blockOrigin().getZ();
        int aBlockW = pieceA.variant().getGridWidth() * 5;
        int aBlockD = pieceA.variant().getGridDepth() * 5;

        return switch (conn.faceA()) {
            case EAST  -> new Vector3i(ax + aBlockW - 1, ay + 1, az + conn.cellAz() * 5);
            case WEST  -> new Vector3i(ax - 1,           ay + 1, az + conn.cellAz() * 5);
            case SOUTH -> new Vector3i(ax + conn.cellAx() * 5, ay + 1, az + aBlockD - 1);
            case NORTH -> new Vector3i(ax + conn.cellAx() * 5, ay + 1, az - 1);
        };
    }

    /**
     * Reserve the best open socket for a guarantee tag. "Best" is the unreserved socket
     * whose target cell has the most free neighbors in the grid, giving the guaranteed
     * piece room to breathe.
     */
    private void reserveBestSocket(String tag) {
        OpenSocket best = null;
        int bestFreeNeighbors = -1;

        for (OpenSocket socket : openSockets) {
            if (reservedSockets.containsValue(socket)) {
                continue;
            }

            int targetX = socket.cellX() + socket.piece().gridX() + socket.face().dx();
            int targetZ = socket.cellZ() + socket.piece().gridZ() + socket.face().dz();

            int freeCount = 0;
            for (Face face : Face.values()) {
                if (grid.isFree(targetX + face.dx(), 0, targetZ + face.dz())) {
                    freeCount++;
                }
            }

            if (freeCount > bestFreeNeighbors) {
                bestFreeNeighbors = freeCount;
                best = socket;
            }
        }

        if (best != null) {
            reservedSockets.put(tag, best);
            LOGGER.atInfo().log("Reserved socket for guarantee '%s': face=%s at piece %s cell (%d,%d), free neighbors=%d",
                tag, best.face(), best.piece().variant().getName(), best.cellX(), best.cellZ(), bestFreeNeighbors);
        } else {
            LOGGER.atWarning().log("No open socket available to reserve for guarantee tag '%s'", tag);
        }
    }

    /**
     * Weighted random selection from a list of variants.
     */
    private DungeonPieceVariant weightedRandomPick(List<DungeonPieceVariant> variants) {
        double totalWeight = 0;
        for (DungeonPieceVariant v : variants) {
            totalWeight += v.getWeight();
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (DungeonPieceVariant v : variants) {
            cumulative += v.getWeight();
            if (roll < cumulative) return v;
        }
        return variants.getLast();
    }

    /**
     * Weighted random selection from a list of candidate placements.
     */
    private CandidatePlacement weightedRandomPickCandidate(List<CandidatePlacement> candidates) {
        double totalWeight = 0;
        for (CandidatePlacement c : candidates) {
            totalWeight += c.variant().getWeight();
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (CandidatePlacement c : candidates) {
            cumulative += c.variant().getWeight();
            if (roll < cumulative) return c;
        }
        return candidates.getLast();
    }

    public List<PlacedPiece> getPlacedPieces() {
        return Collections.unmodifiableList(placedPieces);
    }

    public List<ConnectionRecord> getConnections() {
        return Collections.unmodifiableList(connections);
    }
}
