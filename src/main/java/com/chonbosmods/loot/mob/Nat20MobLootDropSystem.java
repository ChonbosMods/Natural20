package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.loot.Nat20LootSystem;
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

/**
 * Fires {@link Nat20MobLootListener#onMobDeath} when damage brings a Nat20-tiered
 * mob's HP to zero. Uses the same lethal-damage detection as Nat20XpOnKillSystem
 * (Rally pattern: incoming >= currentHp). Settlement NPCs are skipped.
 */
public class Nat20MobLootDropSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobLoot");
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20LootSystem lootSystem;
    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    public Nat20MobLootDropSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        if (!statResolved) {
            healthIdx = EntityStatType.getAssetMap().getIndex("Health");
            statResolved = true;
        }
        if (healthIdx < 0) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);
        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        float currentHp = statMap.get(healthIdx).get();
        if (currentHp > damage.getAmount()) return;

        Nat20MobLevel level = store.getComponent(victimRef, Natural20.getMobLevelType());
        if (level == null) return;

        // Skip settlement NPCs: shopkeepers, guards, etc.
        Nat20NpcData npcData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (npcData != null && npcData.getSettlementCellKey() != null) return;

        try {
            lootSystem.getMobLootListener().onMobDeath(victimRef, store, cb);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Loot drop failed for mob %s", victimRef);
        }
    }
}
