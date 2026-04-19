package com.chonbosmods.progression.ambient;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.mob.Nat20MobAffixManager;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.GroupSource;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.progression.Nat20MobScaleSystem;
import com.chonbosmods.progression.Nat20MobThemeRegistry;
import com.chonbosmods.progression.Tier;
import com.chonbosmods.quest.poi.MobGroupChunkListener;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.PoiGroupDirection;
import com.chonbosmods.quest.poi.SlotRecord;
import com.chonbosmods.world.Nat20BiomeLookup;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrator for ambient surface group spawns. Owns per-player cooldown state, subscribes
 * to chunk-load events (via the plugin's event registry), delegates anchor finding and spawn
 * to collaborators.
 *
 * <p>Full spawn path wired in Task 9: on each chunk-load event, enumerate online players,
 * gate by distance + cooldown, roll chance, find a valid anchor, pick a themed mob role,
 * build a {@link MobGroupRecord} mirroring {@code POIGroupSpawnCoordinator.firstSpawn},
 * persist, spawn, fire lightning FX, and burn the player's cooldown.
 */
public final class AmbientSpawnSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Ambient");

    private final AmbientSpawnConfig cfg;
    private final AmbientAnchorFinder anchorFinder;
    private final Nat20MobGroupRegistry registry;
    private final Nat20MobGroupSpawner spawner;
    private final MobGroupChunkListener chunkListener;
    private final CooldownMap cooldowns = new CooldownMap();

    public AmbientSpawnSystem(AmbientSpawnConfig cfg,
                              AmbientAnchorFinder anchorFinder,
                              Nat20MobGroupRegistry registry,
                              Nat20MobGroupSpawner spawner,
                              MobGroupChunkListener chunkListener) {
        this.cfg = cfg;
        this.anchorFinder = anchorFinder;
        this.registry = registry;
        this.spawner = spawner;
        this.chunkListener = chunkListener;
    }

    /**
     * Called from the plugin's ChunkPreLoadProcessEvent handler. Rolls once per eligible player
     * whose cooldown has expired and who's within {@link AmbientSpawnConfig#maxDistanceFromPlayer()}
     * of the chunk center. At most one spawn per chunk-load event (first eligible player wins).
     */
    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        world.execute(() -> doChunkLoadOnWorldThread(world, chunkBlockX, chunkBlockZ));
    }

    private void doChunkLoadOnWorldThread(World world, int chunkBlockX, int chunkBlockZ) {
        double cx = chunkBlockX + 16.0;
        double cz = chunkBlockZ + 16.0;
        long now = System.currentTimeMillis();
        double maxD = cfg.maxDistanceFromPlayer();
        double maxDSq = maxD * maxD;

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Mirror POIProximitySystem.tick: iterate tracked players, resolve entity ref +
        // TransformComponent for the authoritative ECS position. Here we source UUIDs from
        // world.getPlayerRefs() rather than a plugin-owned set, because the ambient system
        // doesn't need to track connect/disconnect events separately.
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) continue;

            Ref<EntityStore> entityRef = world.getEntityRef(playerUuid);
            if (entityRef == null) continue;

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;
            Vector3d pos = transform.getPosition();
            if (pos == null) continue;

            double px = pos.getX();
            double pz = pos.getZ();
            double dx = px - cx;
            double dz = pz - cz;
            if (dx * dx + dz * dz > maxDSq) continue;

            if (!cooldowns.canRoll(playerUuid, now)) continue;

            if (trySpawnForPlayer(world, playerUuid, new Vector3d(px, pos.getY(), pz), now)) {
                return; // At most one ambient spawn per chunk-load event.
            }
        }
    }

    /**
     * @return true iff the system committed to a spawn (next world-thread tick will persist,
     *         spawn, and burn the cooldown). false if the roll aborted before commit (no
     *         cooldown burn).
     */
    private boolean trySpawnForPlayer(World world, UUID playerUuid, Vector3d playerPos, long now) {
        Random rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= cfg.rollChance()) return false;

        AmbientAnchorFinder.SurfaceProbe surface = buildSurfaceProbe(world);
        AmbientAnchorFinder.HeadroomProbe headroom = buildHeadroomProbe(world);
        Optional<Vector3d> anchorOpt = anchorFinder.find(surface, headroom, playerPos, rng);
        if (anchorOpt.isEmpty()) return false; // No cooldown burn on anchor-find failure.
        Vector3d anchor = anchorOpt.get();

        String zoneName = Nat20BiomeLookup.getZoneName(world, anchor.getX(), anchor.getZ());
        Nat20MobThemeRegistry themes = Natural20.getInstance().getMobThemeRegistry();
        String mobRole = themes.pickMob(zoneName, rng);
        if (mobRole == null) {
            LOGGER.atFine().log("Ambient theme returned null for zone=%s", zoneName);
            return false;
        }

        spawnAmbientGroup(world, playerUuid, anchor, mobRole, now);
        return true;
    }

    /**
     * World-thread: build the record, persist, play FX, and drive the initial spawn.
     * Mirrors {@code POIGroupSpawnCoordinator.firstSpawn} for all fields NOT ambient-specific
     * (affix pre-roll, boss naming, slot list, champion count, difficulty tier).
     */
    private void spawnAmbientGroup(World world, UUID playerUuid, Vector3d anchor,
                                   String mobRole, long now) {
        MobScalingConfig scalingCfg = Natural20.getInstance().getScalingConfig();
        Nat20MobScaleSystem scaleSystem = Natural20.getInstance().getMobScaleSystem();
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();
        Nat20MobAffixManager affixMgr = lootSystem.getMobAffixManager();

        Random rng = ThreadLocalRandom.current();

        int championCount = rng.nextInt(
                scalingCfg.groupMinChampions(), scalingCfg.groupMaxChampions() + 1);

        DifficultyTier groupDiff = scaleSystem.rollDifficultyWeighted(rng);
        DifficultyTier bossDiff = groupDiff;
        if (groupDiff == DifficultyTier.EPIC
                && rng.nextInt(100) < scalingCfg.bossLegendaryChance()) {
            bossDiff = DifficultyTier.LEGENDARY;
        }
        DifficultyTier championDiff = (groupDiff == DifficultyTier.LEGENDARY)
                ? DifficultyTier.EPIC : groupDiff;

        List<RolledAffix> sharedChampionAffixes = affixMgr.rollAffixes(Tier.CHAMPION, championDiff);
        List<RolledAffix> bossAffixes = affixMgr.rollAffixes(Tier.BOSS, bossDiff);

        int anchorChunkX = (int) Math.floor(anchor.getX() / 32.0);
        int anchorChunkZ = (int) Math.floor(anchor.getZ() / 32.0);
        long generationId = now;
        long worldSeed = world.getWorldConfig().getSeed();
        String groupKey = "ambient:" + worldSeed + ":" + anchorChunkX + ":" + anchorChunkZ
                + ":" + generationId;

        long nameSeed = Objects.hash(groupKey, bossDiff.name());
        String bossName = lootSystem.getMobNameGenerator().generate(bossDiff, new Random(nameSeed));
        if (bossName == null || bossName.isEmpty()) bossName = "Unnamed";

        MobGroupRecord record = new MobGroupRecord();
        record.setGroupKey(groupKey);
        record.setOwnerPlayerUuid(null);
        record.setQuestId(null);
        record.setPoiSlotIdx(-1);
        record.setMobRole(mobRole);
        record.setAnchor(anchor.getX(), anchor.getY(), anchor.getZ());
        record.setSpawnGenerationId(generationId);
        record.setGroupDifficulty(groupDiff);
        record.setBossDifficulty(bossDiff);
        // KILL_COUNT is a placeholder: MobGroupRecord.direction is a required field authored
        // for POI quest kill tracking. Ambient records (ownerPlayerUuid=null) are never
        // consulted by POIKillTrackingSystem, so the value is inert here.
        record.setDirection(PoiGroupDirection.KILL_COUNT);
        record.setChampionCount(championCount);
        record.setBossName(bossName);
        record.setSharedChampionAffixes(sharedChampionAffixes);
        record.setBossAffixes(bossAffixes);

        List<SlotRecord> slots = new ArrayList<>(championCount + 1);
        for (int i = 0; i < championCount; i++) slots.add(new SlotRecord(i, false));
        slots.add(new SlotRecord(championCount, true));
        record.setSlots(slots);

        record.setCreatedAtMillis(now);
        record.setLastSeenMillis(now);
        record.setSource(GroupSource.AMBIENT);

        registry.put(record);
        AmbientLightningEffect.play(world, anchor);
        spawner.spawnFromRecord(world, record);
        registry.saveAsync();

        cooldowns.markRolled(playerUuid, now, cfg.cooldownMillis());

        LOGGER.atInfo().log(
                "AmbientSpawn role=%s groupDiff=%s bossDiff=%s champions=%d anchor=(%d,%d,%d) player=%s",
                mobRole, groupDiff, bossDiff, championCount,
                (int) anchor.getX(), (int) anchor.getY(), (int) anchor.getZ(), playerUuid);
    }

    /**
     * Top-down surface probe. Scans from y=256 down; first non-air block wins and is accepted
     * iff its material is {@link BlockMaterial#Solid}. Any other non-air material (water, lava,
     * fluids, leaves, partial blocks: anything the SDK doesn't call Solid) causes the probe to
     * return {@link AmbientAnchorFinder.SurfaceProbe#INVALID}, rejecting the column.
     *
     * <p>BlockMaterial in this SDK only exposes {@code Empty} and {@code Solid}: there is no
     * Air enum value. Air is represented by {@code world.getBlockType(x,y,z) == null}, matching
     * the convention used elsewhere in the plugin (see {@code CaveVoidScanner.isNonVoid}).
     */
    private AmbientAnchorFinder.SurfaceProbe buildSurfaceProbe(World world) {
        return (x, z) -> {
            try {
                for (int y = 256; y >= 0; y--) {
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt == null) continue; // air: keep scanning down
                    BlockMaterial mat = bt.getMaterial();
                    if (mat == BlockMaterial.Solid) return y + 1;
                    // First non-air non-solid hit (fluid/foliage/partial/etc): reject.
                    return AmbientAnchorFinder.SurfaceProbe.INVALID;
                }
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log("Ambient probe failed at (%d, %d)", x, z);
                // fall through to return the conservative/reject value
            }
            return AmbientAnchorFinder.SurfaceProbe.INVALID;
        };
    }

    /**
     * Headroom probe: requires 3 empty-space blocks above the surface Y. Null BlockType or
     * non-Solid material counts as passable (air / fluid / foliage all fit the "no collision
     * with a mob body" bar for this purpose).
     */
    private AmbientAnchorFinder.HeadroomProbe buildHeadroomProbe(World world) {
        return (x, surfaceY, z) -> {
            try {
                for (int dy = 0; dy < 3; dy++) {
                    BlockType bt = world.getBlockType(x, surfaceY + dy, z);
                    if (bt == null) continue; // air, fine
                    if (bt.getMaterial() == BlockMaterial.Solid) return false;
                }
                return true;
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log("Ambient probe failed at (%d, %d)", x, z);
                // fall through to return the conservative/reject value
                return false;
            }
        };
    }

    /** Package-private for unit tests. Keyed by player UUID; stores expiryMillis (absolute wall-clock). */
    static final class CooldownMap {
        private final ConcurrentHashMap<UUID, Long> expiry = new ConcurrentHashMap<>();

        boolean canRoll(UUID playerUuid, long nowMillis) {
            Long exp = expiry.get(playerUuid);
            return exp == null || nowMillis >= exp;
        }

        void markRolled(UUID playerUuid, long nowMillis, long cooldownMillis) {
            expiry.put(playerUuid, nowMillis + cooldownMillis);
        }
    }
}
