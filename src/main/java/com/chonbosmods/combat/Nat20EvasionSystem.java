package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
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
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evasion: chance to fully dodge melee attacks. Filter Group system.
 * Scans armor, sums evasion chance, applies softcap, rolls.
 */
public class Nat20EvasionSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:evasion";
    private static final String PARTICLE = "Nat20_Evasion";
    private static final String DODGE_SOUND = "SFX_Toad_Rhino_Tongue_Whoosh";
    private static final double SOFTCAP_K = 0.20;
    private static final float TORSO_OFFSET_Y = 0.9f;

    private final Nat20LootSystem lootSystem;
    private int dodgeSoundIdx = Integer.MIN_VALUE;

    public Nat20EvasionSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        // Only melee attacks from entities
        if (!(damage.getSource() instanceof Damage.EntitySource)) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) return;

        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, targetRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalChance = 0;

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            for (RolledAffix rolledAffix : lootData.getAffixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) continue;

                AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
                if (range == null) continue;

                double baseValue = range.interpolate(rolledAffix.midLevel());
                double effectiveValue = baseValue;
                PlayerStats stats = resolvePlayerStats(targetRef, store);
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                totalChance += effectiveValue;
            }
        }

        if (totalChance <= 0) return;
        if (totalChance < 1.0) {
            totalChance = Nat20Softcap.softcap(totalChance, SOFTCAP_K);
        } else {
            totalChance = 1.0; // Force 100% for testing
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        UUID targetUuid = targetPlayer.getPlayerRef().getUuid();

        if (CombatDebugSystem.isEnabled(targetUuid)) {
            LOGGER.atInfo().log("[Evasion] chance=%.1f%% roll=%.3f dodge=%s",
                    totalChance * 100, roll, roll <= totalChance);
        }

        if (roll > totalChance) return;

        // Dodge! Cancel the entire damage event to prevent flinch, hit particles, and knockback
        damage.setCancelled(true);
        damage.setAmount(0f);
        // Strip knockback to prevent the hop
        try {
            damage.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
        } catch (Exception ignored) {}
        try {
            damage.putMetaObject(Damage.HIT_ANGLE, 0f);
        } catch (Exception ignored) {}

        // Whoosh particle + dodge sound on player
        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            try {
                ParticleUtil.spawnParticleEffect(PARTICLE,
                        new Vector3d(x, y + TORSO_OFFSET_Y, z), store);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Evasion] particle failed");
            }
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
