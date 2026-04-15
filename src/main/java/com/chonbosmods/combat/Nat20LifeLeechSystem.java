package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20AffixScaling;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Life Leech: steal a percentage of damage dealt as health.
 * Fires every hit, no proc chance. DEX scaling.
 */
public class Nat20LifeLeechSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:life_leech";
    private static final String PARTICLE = "Nat20_LifeLeech";
    private static final double SOFTCAP_K = 0.20;
    private static final float TORSO_OFFSET_Y = 0.9f;

    private final Nat20LootSystem lootSystem;
    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    public Nat20LifeLeechSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        // Skip DOT tick damage: weapon affixes should not re-trigger on periodic damage
        if (Nat20DotTickSystem.isDotTickDamage(damage)) return;

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double basePercent = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
            double effectivePercent = basePercent;

            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                effectivePercent = basePercent * (1.0 + modifier * def.statScaling().factor());
            }
            effectivePercent = Nat20Softcap.softcap(effectivePercent, SOFTCAP_K);

            float healAmount = (float) (damage.getAmount() * effectivePercent);
            if (healAmount <= 0f) return;

            if (!statResolved) {
                healthIdx = EntityStatType.getAssetMap().getIndex("Health");
                statResolved = true;
            }
            if (healthIdx < 0) return;

            EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
            if (statMap == null) return;

            statMap.addStatValue(healthIdx, healAmount);

            // Particle on attacker
            TransformComponent transform = store.getComponent(attackerRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                try {
                    ParticleUtil.spawnParticleEffect(PARTICLE,
                            new Vector3d(pos.getX(), pos.getY() + TORSO_OFFSET_Y, pos.getZ()), store);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("[LifeLeech] particle failed");
                }
            }

            UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[LifeLeech] player=%s leech=%.1f%% dmg=%.1f heal=%.2f",
                        attackerUuid.toString().substring(0, 8),
                        effectivePercent * 100, damage.getAmount(), healAmount);
            }
            return;
        }
    }

    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
