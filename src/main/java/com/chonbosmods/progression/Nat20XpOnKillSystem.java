package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Awards XP to every player who dealt damage to an NPCEntity within the last
 * {@link Nat20DamageContributorTracker#WINDOW_MS} when it dies. Each contributor
 * receives the full XP amount (no proportional split). Mobs that die without a
 * living player contributor award nothing: e.g. a bear killing a chicken.
 */
public class Nat20XpOnKillSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|XpKill");
    private static final Query<EntityStore> QUERY = Query.any();

    private final MobScalingConfig config;
    private final Nat20XpService xpService;
    private final Nat20DamageContributorTracker contributorTracker;

    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    // Dedup: one XP award per mob death, even if multiple damage events fire on a
    // corpse in the same tick (affix procs, DOTs, splash). Entries age out after
    // 60s so long-lived servers don't accumulate ghosts.
    private static final long DEATH_DEDUP_TTL_MS = 60_000L;
    private final Map<UUID, Long> processedDeaths = new ConcurrentHashMap<>();

    public Nat20XpOnKillSystem(MobScalingConfig config, Nat20XpService xpService,
                               Nat20DamageContributorTracker contributorTracker) {
        this.config = config;
        this.xpService = xpService;
        this.contributorTracker = contributorTracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);

        if (!statResolved) {
            healthIdx = EntityStatType.getAssetMap().getIndex("Health");
            statResolved = true;
        }
        if (healthIdx < 0) return;

        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;
        float currentHp = statMap.get(healthIdx).get();
        // Fire only after HP has actually reached 0: robust to Evasion cancellation
        // and other pre-damage mitigators. Matches POIKillTrackingSystem's pattern
        // (Rally pre-damage prediction was causing double-awards when Evasion
        // cancelled the lethal hit after our handler ran).
        if (currentHp > 0f) return;

        // Filter to scaled mobs (non-mob entities don't carry Nat20MobLevel).
        Nat20MobLevel level = store.getComponent(victimRef, Natural20.getMobLevelType());
        if (level == null) return;

        // Skip settlement NPCs (shopkeepers, residents): no XP for griefing peaceful villagers.
        Nat20NpcData npcData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (npcData != null && npcData.getSettlementCellKey() != null) return;

        int mlvl = config.mlvlForTier(level.getAreaLevel(), level.getTier());
        double weight = config.killXpWeight(level.getTier());
        int xp = Nat20XpMath.mobKillXp(mlvl, weight);
        if (xp <= 0) return;

        long now = System.currentTimeMillis();
        UUID mobUuid = Nat20DamageContributorTracker.uuidOf(victimRef, store);
        if (mobUuid == null) return;

        // One award per mob death. HP can stay at 0 across multiple damage events
        // (DOT ticks, splash) before the entity is removed; without this dedup
        // each event would re-award XP.
        if (!markProcessed(mobUuid, now)) return;

        List<Ref<EntityStore>> contributors = contributorTracker.getContributors(mobUuid, now);
        if (contributors.isEmpty()) {
            LOGGER.atFine().log("Mob %s died with no player contributors; no XP awarded", victimRef);
            return;
        }

        String reason = "kill:" + level.getTier() + "@mlvl" + mlvl;
        for (Ref<EntityStore> ref : contributors) {
            if (!ref.isValid()) continue;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) continue;
            xpService.award(player, ref, store, xp, reason);
        }
    }

    private boolean markProcessed(UUID mobUuid, long nowMs) {
        processedDeaths.entrySet().removeIf(e -> e.getValue() < nowMs - DEATH_DEDUP_TTL_MS);
        return processedDeaths.putIfAbsent(mobUuid, nowMs) == null;
    }
}
