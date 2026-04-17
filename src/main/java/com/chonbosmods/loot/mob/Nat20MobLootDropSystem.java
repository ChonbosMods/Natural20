package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.progression.Nat20DamageContributorTracker;
import com.chonbosmods.progression.Nat20MobLevel;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires {@link Nat20MobLootListener#onMobDeath} when a Nat20-tiered mob's HP
 * reaches zero. Uses the post-damage HP check (matches POIKillTrackingSystem)
 * with a per-mob dedup set, since HP can remain at 0 across multiple damage
 * events before the entity is removed.
 *
 * <p>Enhanced Nat20 drops are gated on at least one player having contributed
 * damage within the last 30s: environmental deaths and AI-on-AI kills skip the
 * Nat20 loot layer entirely. Native Hytale ItemDropList drops are not our code
 * and continue to fire regardless.
 */
public class Nat20MobLootDropSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobLoot");
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20LootSystem lootSystem;
    private final Nat20DamageContributorTracker contributorTracker;
    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    private static final long DEATH_DEDUP_TTL_MS = 60_000L;
    private final Map<UUID, Long> processedDeaths = new ConcurrentHashMap<>();

    public Nat20MobLootDropSystem(Nat20LootSystem lootSystem,
                                   Nat20DamageContributorTracker contributorTracker) {
        this.lootSystem = lootSystem;
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

        if (!statResolved) {
            healthIdx = EntityStatType.getAssetMap().getIndex("Health");
            statResolved = true;
        }
        if (healthIdx < 0) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);
        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        float currentHp = statMap.get(healthIdx).get();
        // Fire only when HP has actually reached 0: avoids false positives when
        // another damage system (Evasion, Absorption) cancels or reduces damage
        // after our handler would run. Matches POIKillTrackingSystem.
        if (currentHp > 0f) return;

        Nat20MobLevel level = store.getComponent(victimRef, Natural20.getMobLevelType());
        if (level == null) return;

        // Skip settlement NPCs: shopkeepers, guards, etc.
        Nat20NpcData npcData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (npcData != null && npcData.getSettlementCellKey() != null) return;

        long now = System.currentTimeMillis();
        UUID mobUuid = Nat20DamageContributorTracker.uuidOf(victimRef, store);
        if (mobUuid == null) return;

        // One drop roll per mob death: HP can linger at 0 for several damage
        // events before the entity is removed.
        if (!markProcessed(mobUuid, now)) return;

        if (contributorTracker.getContributors(mobUuid, now).isEmpty()) {
            LOGGER.atFine().log("Mob %s died with no player contributors; skipping Nat20 drops",
                    victimRef);
            return;
        }

        try {
            lootSystem.getMobLootListener().onMobDeath(victimRef, store, cb);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Loot drop failed for mob %s", victimRef);
        }
    }

    private boolean markProcessed(UUID mobUuid, long nowMs) {
        processedDeaths.entrySet().removeIf(e -> e.getValue() < nowMs - DEATH_DEDUP_TTL_MS);
        return processedDeaths.putIfAbsent(mobUuid, nowMs) == null;
    }
}
