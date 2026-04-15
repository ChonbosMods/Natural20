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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.List;

/**
 * Awards XP to all players within {@code awardRadiusBlocks} when an NPCEntity
 * dies (lethal damage). Filters non-mob entities by Nat20MobLevel presence.
 */
public class Nat20XpOnKillSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|XpKill");
    private static final Query<EntityStore> QUERY = Query.any();

    private final MobScalingConfig config;
    private final Nat20XpService xpService;

    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    public Nat20XpOnKillSystem(MobScalingConfig config, Nat20XpService xpService) {
        this.config = config;
        this.xpService = xpService;
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

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);

        // Lethal-damage check (Rally pattern: HP <= incoming damage means this swing kills).
        if (!statResolved) {
            healthIdx = EntityStatType.getAssetMap().getIndex("Health");
            statResolved = true;
        }
        if (healthIdx < 0) return;

        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;
        float currentHp = statMap.get(healthIdx).get();
        if (currentHp > damage.getAmount()) return;

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

        TransformComponent transform = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (transform == null) return;

        List<Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(
                transform.getPosition(), config.awardRadiusBlocks(), store);

        String reason = "kill:" + level.getTier() + "@mlvl" + mlvl;
        for (Ref<EntityStore> ref : nearby) {
            if (!ref.isValid()) continue;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) continue;
            xpService.award(player, ref, store, xp, reason);
        }
    }
}
