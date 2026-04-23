package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SettlementWorldGenListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|WorldGen");

    private static final int CELL_SIZE = 512;
    private static final double JITTER_MIN = 0.25;
    private static final double JITTER_MAX = 0.75;
    private static final long SEED_OFFSET = 827364510L;

    /**
     * Cached cell key containing the world spawn point. The settlement placed there
     * is hand-rolled (see {@link #tryPlaceSpawnSettlement}): the piece placer is
     * skipped and NPCs are spawned directly so the tutorial is guaranteed to start.
     * Resolved lazily from the world's SpawnProvider on first chunk load.
     */
    private volatile String cachedSpawnCellKey;

    /** Diagonal offset from world spawn for the guaranteed tutorial settlement anchor.
     *  Far enough that the player isn't standing inside the NPC ring, close enough
     *  that the NPCs are immediately visible on drop-in. */
    private static final int SPAWN_SETTLEMENT_OFFSET_X = 40;
    private static final int SPAWN_SETTLEMENT_OFFSET_Z = 40;

    /** Number of NPC spawn markers synthesised in a ring around the spawn anchor. */
    private static final int SPAWN_SETTLEMENT_NPC_COUNT = 5;

    /** Radius of the NPC ring (blocks) around the spawn anchor. */
    private static final double SPAWN_SETTLEMENT_RING_RADIUS = 8.0;

    /** Hytale chunks are 32x32 blocks. */
    private static final int CHUNK_BLOCK_SIZE = 32;

    private final SettlementRegistry registry;
    private final SettlementPlacer placer;

    /**
     * Per-session set of cell keys whose settlement placement has failed at least
     * once. Populated when the center fails validation or no pieces ground.
     * Blocks re-attempts for this server run so chunk reloads of the same cell
     * don't repeat the same work + log spam. Cleared on restart, so a single
     * retry per boot cycle is possible if the failure was transient.
     */
    private final java.util.Set<String> failedCells =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SettlementWorldGenListener(SettlementRegistry registry, SettlementPlacer placer) {
        this.registry = registry;
        this.placer = placer;
    }

    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        // Cache world reference for the rotation ticker
        UUID worldUUID = UUID.nameUUIDFromBytes(world.getName().getBytes());
        registry.cacheWorld(worldUUID, world);

        // Determine which grid cell this chunk falls in
        int cellX = Math.floorDiv(chunkBlockX, CELL_SIZE);
        int cellZ = Math.floorDiv(chunkBlockZ, CELL_SIZE);
        String cellKey = cellX + "," + cellZ;

        // Settlement already placed: check if NPCs need respawning
        if (registry.hasCell(cellKey)) {
            checkAndRespawnNpcs(world, cellKey);
            return;
        }

        // Previously failed this session: don't retry (avoids log spam + wasted
        // work on chunk reloads of the same unbuildable cell).
        if (failedCells.contains(cellKey)) {
            return;
        }

        // Spawn cell takes a dedicated guaranteed path: NPCs (including Celius)
        // are placed directly at a fixed offset from world spawn, skipping the
        // piece placer + slope check so the tutorial always has somewhere to go.
        // Non-spawn cells keep the normal jitter + piece-placement flow below.
        if (resolveSpawnCellKey(world).equals(cellKey)) {
            tryPlaceSpawnSettlement(world, worldUUID, cellKey, chunkBlockX, chunkBlockZ);
            return;
        }

        // Compute deterministic settlement position for this cell
        long seed = (long) cellX * 341873128712L + (long) cellZ * 132897987541L + SEED_OFFSET;
        Random rng = new Random(seed);

        int cellOriginX = cellX * CELL_SIZE;
        int cellOriginZ = cellZ * CELL_SIZE;
        int settlementX = cellOriginX
            + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));
        int settlementZ = cellOriginZ
            + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));

        // Only the chunk containing the settlement center triggers placement
        if (!isChunkContaining(chunkBlockX, chunkBlockZ, settlementX, settlementZ)) {
            return;
        }

        SettlementType type = SettlementType.TOWN;

        LOGGER.atInfo().log("Placing %s at cell %s position %d, %d",
            type, cellKey, settlementX, settlementZ);

        // Mark as placed immediately to prevent double-placement
        SettlementRecord record = new SettlementRecord(
            cellKey, worldUUID,
            settlementX, 0, settlementZ,
            type);
        record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode(), registry.getUsedNames()));
        registry.register(record);

        // Place structure and spawn NPCs on world thread
        world.execute(() -> {
            var store = world.getEntityStore().getStore();
            // Footprint-aware ground check: probe halfX/halfZ matching the
            // settlement's footprint, reject only on actual cliffs/peaks (slope
            // threshold scales with footprint width so rolling hills still
            // qualify). Without this, centers often land on a thin pillar where
            // a 1-point probe succeeds but every piece around it fails to
            // ground, leaving zero pieces placed.
            int half = type.getFootprint() / 2;
            int slopeThreshold = half; // e.g. TOWN footprint=32 → 16 blocks allowed across 32 wide (~27 deg)
            Nat20HeightmapSampler.SampleResult centerSample = Nat20HeightmapSampler.sample(
                world, settlementX, settlementZ, half, half,
                Nat20HeightmapSampler.Mode.ENTRY_ANCHOR, slopeThreshold);
            if (centerSample.y() <= 0) {
                LOGGER.atInfo().log(
                    "No valid ground at settlement center (%d, %d) for cell %s; skipping",
                    settlementX, settlementZ, cellKey);
                registry.unregister(cellKey);
                failedCells.add(cellKey);
                return;
            }
            if (centerSample.tooSteep()) {
                LOGGER.atInfo().log(
                    "Settlement center (%d, %d) too steep (slope=%d > %d) for cell %s; skipping",
                    settlementX, settlementZ, centerSample.slopeDelta(), slopeThreshold, cellKey);
                registry.unregister(cellKey);
                failedCells.add(cellKey);
                return;
            }
            int groundY = centerSample.y();
            Vector3i anchorPos = new Vector3i(settlementX, groundY, settlementZ);

            placer.place(world, anchorPos, type, Rotation.None, store, new Random(seed))
                .whenComplete((placed, error) -> world.execute(() -> {
                    if (error != null || placed == null) {
                        LOGGER.atSevere().withCause(error).log(
                            "Settlement placement failed for cell %s; removing tentative record",
                            cellKey);
                        registry.unregister(cellKey);
                        failedCells.add(cellKey);
                        return;
                    }

                    List<NpcRecord> spawned = SettlementNpcFanOut.spawn(
                        store, world, type, placed.npcSpawnsWorld(),
                        cellKey, record.getPlacedAt());

                    record.setPosY(groundY);
                    record.getNpcs().addAll(spawned);

                    // Celius rename belongs to the spawn cell exclusively, which is
                    // handled by tryPlaceSpawnSettlement above. Non-spawn cells
                    // never reach this branch with a Celius flag.
                    // Capture every Nat20_Chest_Spawn marker so passive fetch quests can
                    // claim one and spawn the quest chest at the authored location instead
                    // of falling back to the settlement center.
                    for (com.hypixel.hytale.math.vector.Vector3d c : placed.chestSpawnsWorld()) {
                        record.addChestSpawn((int) c.getX(), (int) c.getY(), (int) c.getZ());
                    }
                    registry.saveAsync();

                    Natural20.getInstance().onSettlementCreated(record, world);
                    LOGGER.atFine().log("Settlement placed at %d, %d, %d with %d NPCs",
                        settlementX, groundY, settlementZ, spawned.size());
                }));
        });
    }

    /**
     * Guaranteed spawn-cell settlement: skip the piece placer and spawn NPCs
     * directly in a ring around a fixed offset from world spawn. This path is
     * tolerant of any terrain because each NPC's ground Y is sampled individually;
     * there's no all-or-nothing piece assembly. Celius is the first Guard in the
     * returned list (promoted before the record is saved).
     *
     * <p>Only the chunk containing the anchor point triggers placement. If that
     * chunk isn't the one firing this callback, we early-return and let the
     * correct chunk load later.
     */
    private void tryPlaceSpawnSettlement(World world, UUID worldUUID, String cellKey,
                                         int chunkBlockX, int chunkBlockZ) {
        Vector3d spawnPos;
        try {
            Transform t = world.getWorldConfig().getSpawnProvider()
                .getSpawnPoint(world, new UUID(0L, 0L));
            spawnPos = t.getPosition();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Spawn settlement: failed to resolve world spawn; skipping cell %s", cellKey);
            return;
        }

        int anchorX = (int) spawnPos.getX() + SPAWN_SETTLEMENT_OFFSET_X;
        int anchorZ = (int) spawnPos.getZ() + SPAWN_SETTLEMENT_OFFSET_Z;
        if (!isChunkContaining(chunkBlockX, chunkBlockZ, anchorX, anchorZ)) {
            return;
        }

        SettlementType type = SettlementType.TOWN;
        LOGGER.atInfo().log(
            "Placing guaranteed spawn settlement at cell %s anchor (%d, %d)",
            cellKey, anchorX, anchorZ);

        SettlementRecord record = new SettlementRecord(
            cellKey, worldUUID, anchorX, 0, anchorZ, type);
        record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode(), registry.getUsedNames()));
        registry.register(record);

        world.execute(() -> {
            var store = world.getEntityStore().getStore();

            Nat20HeightmapSampler.SampleResult centerSample = Nat20HeightmapSampler.sample(
                world, anchorX, anchorZ, 0, 0, Nat20HeightmapSampler.Mode.MEDIAN);
            int groundY = centerSample.y() > 0 ? centerSample.y() : (int) spawnPos.getY();

            // Ring of N NPC spawn markers around the anchor; each marker's Y is
            // sampled locally so NPCs stand on the real surface.
            List<Vector3d> markers = new ArrayList<>(SPAWN_SETTLEMENT_NPC_COUNT);
            for (int i = 0; i < SPAWN_SETTLEMENT_NPC_COUNT; i++) {
                double theta = 2.0 * Math.PI * i / SPAWN_SETTLEMENT_NPC_COUNT;
                int mx = anchorX + (int) Math.round(Math.cos(theta) * SPAWN_SETTLEMENT_RING_RADIUS);
                int mz = anchorZ + (int) Math.round(Math.sin(theta) * SPAWN_SETTLEMENT_RING_RADIUS);
                Nat20HeightmapSampler.SampleResult mSample = Nat20HeightmapSampler.sample(
                    world, mx, mz, 0, 0, Nat20HeightmapSampler.Mode.MEDIAN);
                int my = mSample.y() > 0 ? mSample.y() : groundY;
                markers.add(new Vector3d(mx, my, mz));
            }

            record.setPosY(groundY);

            List<NpcRecord> spawned = SettlementNpcFanOut.spawn(
                store, world, type, markers, cellKey, record.getPlacedAt());
            record.getNpcs().addAll(spawned);

            renameFirstGuardToCelius(store, world, spawned);

            registry.saveAsync();
            Natural20.getInstance().onSettlementCreated(record, world);
            LOGGER.atInfo().log(
                "Spawn settlement placed at (%d, %d, %d) with %d NPCs (Celius included)",
                anchorX, groundY, anchorZ, spawned.size());
        });
    }

    /**
     * Cooldown: don't re-check the same cell more than once per 30 seconds.
     * Allows re-checking after chunks have unloaded and reloaded.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastCheckTime =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHECK_COOLDOWN_MS = 500;

    /**
     * Check NPCs for an existing settlement and recover them if needed.
     * 3-tier recovery:
     *   1. Entity exists with Nat20NpcData: intact, skip
     *   2. Entity exists without Nat20NpcData: reattach components (preserves UUID)
     *   3. Entity missing: full respawn (new UUID)
     */
    private void checkAndRespawnNpcs(World world, String cellKey) {
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckTime.get(cellKey);
        if (lastCheck != null && now - lastCheck < CHECK_COOLDOWN_MS) return;
        lastCheckTime.put(cellKey, now);

        SettlementRecord record = registry.getByCell(cellKey);
        if (record == null || record.getNpcs().isEmpty()) return;

        world.execute(() -> {
            var store = world.getEntityStore().getStore();

            int reattached = 0;
            int respawned = 0;
            int intact = 0;

            for (NpcRecord npc : record.getNpcs()) {
                if (npc.getEntityUUID() == null) {
                    // Dead NPC (UUID cleared by death system): skip
                    continue;
                }

                // Skip reconciliation if the NPC's chunk isn't loaded yet.
                // Prevents duplicate spawns when reconciliation runs before
                // the NPC's chunk has loaded (getEntityRef returns null for
                // entities in unloaded chunks, triggering a false Tier 3 respawn).
                int npcChunkX = (int) Math.floor(npc.getSpawnX()) >> 5;
                int npcChunkZ = (int) Math.floor(npc.getSpawnZ()) >> 5;
                long chunkKey = ((long) npcChunkX << 32) | (npcChunkZ & 0xFFFFFFFFL);
                if (world.getChunkIfLoaded(chunkKey) == null) {
                    continue;
                }

                Ref<EntityStore> npcRef = null;
                boolean entityExists = false;
                try {
                    npcRef = world.getEntityRef(npc.getEntityUUID());
                    entityExists = npcRef != null && store.getComponent(npcRef,
                        com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()) != null;
                } catch (Exception ignored) {}

                if (entityExists) {
                    // Entity survived: check if custom data is intact
                    Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
                    if (npcData != null && npcData.getGeneratedName() != null) {
                        // Tier 1: entity and data intact. Re-apply skin because
                        // chunk reload reconstructs a bare Player model from
                        // PersistentModel, stripping skin/attachments.
                        Natural20.getInstance().getNpcManager()
                            .reattachNpc(store, npcRef, npc, cellKey, world);
                        intact++;
                    } else {
                        // Tier 2: entity exists but lost custom components, reattach
                        boolean ok = Natural20.getInstance().getNpcManager()
                            .reattachNpc(store, npcRef, npc, cellKey, world);
                        if (ok) {
                            reattached++;
                        } else {
                            // Reattach failed: kill the old entity to prevent naked ghost,
                            // then respawn with a new entity
                            npc.setEntityUUID(null);
                            try {
                                store.tryRemoveComponent(npcRef, Invulnerable.getComponentType());
                                EntityStatMap oldStats = store.getComponent(npcRef, EntityStatMap.getComponentType());
                                if (oldStats != null) {
                                    int hi = EntityStatType.getAssetMap().getIndex("Health");
                                    if (hi >= 0) oldStats.minimizeStatValue(hi);
                                }
                            } catch (Exception ignored) {}
                            UUID newUUID = Natural20.getInstance().getNpcManager()
                                .respawnNpc(store, world, npc, cellKey);
                            if (newUUID != null) respawned++;
                        }
                    }
                } else {
                    // Tier 3: entity is gone, full respawn
                    UUID newUUID = Natural20.getInstance().getNpcManager()
                        .respawnNpc(store, world, npc, cellKey);
                    if (newUUID != null) respawned++;
                }
            }

            if (reattached > 0 || respawned > 0) {
                LOGGER.atFine().log("Settlement %s: %d intact, %d reattached, %d respawned",
                    cellKey, intact, reattached, respawned);
                registry.saveAsync();
            }
        });
    }

    /** Check if a block position falls within the given chunk. */
    private boolean isChunkContaining(int chunkBlockX, int chunkBlockZ, int blockX, int blockZ) {
        return chunkBlockX <= blockX && blockX < chunkBlockX + CHUNK_BLOCK_SIZE &&
               chunkBlockZ <= blockZ && blockZ < chunkBlockZ + CHUNK_BLOCK_SIZE;
    }

    /**
     * Resolve the cell key that contains the world spawn. Cached after first success.
     * Used to pick which cell's settlement hosts Celius and skips jitter.
     * Falls back to "0,0" if the spawn provider throws; that fallback is harmless
     * because the eventual (0,0) placement will still behave like any other cell.
     */
    private String resolveSpawnCellKey(World world) {
        String cached = cachedSpawnCellKey;
        if (cached != null) return cached;
        try {
            Transform t = world.getWorldConfig().getSpawnProvider()
                .getSpawnPoint(world, new UUID(0L, 0L));
            Vector3d p = t.getPosition();
            int sx = Math.floorDiv((int) p.getX(), CELL_SIZE);
            int sz = Math.floorDiv((int) p.getZ(), CELL_SIZE);
            String key = sx + "," + sz;
            cachedSpawnCellKey = key;
            LOGGER.atInfo().log("Spawn cell resolved to %s (world spawn at %.0f, %.0f, %.0f)",
                key, p.getX(), p.getY(), p.getZ());
            return key;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to resolve spawn cell key; falling back to 0,0");
            cachedSpawnCellKey = "0,0";
            return cachedSpawnCellKey;
        }
    }

    /**
     * Rename the first Guard in the spawn settlement to "Celius Gravus" and flag him
     * so DialogueManager can short-circuit to his bespoke dialogue graph. Mutates both
     * the persisted NpcRecord and the live Nat20NpcData component for the current
     * world-thread session.
     */
    private void renameFirstGuardToCelius(
            com.hypixel.hytale.component.Store<EntityStore> store,
            World world, List<NpcRecord> spawned) {
        NpcRecord guard = null;
        for (NpcRecord npc : spawned) {
            if ("Guard".equals(npc.getRole())) {
                guard = npc;
                break;
            }
        }
        if (guard == null) {
            LOGGER.atWarning().log(
                "Spawn settlement has no Guard to promote to Celius Gravus; tutorial NPC missing");
            return;
        }

        guard.setGeneratedName("Celius Gravus");
        guard.setCeliusGravus(true);

        if (guard.getEntityUUID() == null) return;
        Ref<EntityStore> guardRef = world.getEntityRef(guard.getEntityUUID());
        if (guardRef == null) return;
        var npcData = store.getComponent(guardRef,
            com.chonbosmods.Natural20.getNpcDataType());
        if (npcData == null) return;
        npcData.setGeneratedName("Celius Gravus");
        npcData.setCeliusGravus(true);
        npcData.setFlags(guard.getFlags());

        LOGGER.atInfo().log("Spawn settlement: Guard promoted to Celius Gravus (UUID %s)",
            guard.getEntityUUID());
    }
}
