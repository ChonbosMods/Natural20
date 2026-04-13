package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
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
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Elemental proc DOT affixes: ignite, cold, infect, corrupt.
 * On player melee hit, rolls WIS-scaled proc chance. On proc, applies the matching
 * EntityEffect via EffectControllerComponent.addEffect(). Reapplication refreshes
 * duration (no stacking). Same pattern as Nat20DeepWoundsSystem.
 */
public class Nat20ElementalDotSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private static final String IGNITE_ID = "nat20:ignite";
    private static final String COLD_ID = "nat20:cold";
    private static final String INFECT_ID = "nat20:infect";
    private static final String CORRUPT_ID = "nat20:corrupt";

    private static final String IGNITE_EFFECT = "Nat20IgniteEffect";
    private static final String COLD_EFFECT = "Nat20ColdEffect";
    private static final String INFECT_EFFECT = "Nat20InfectEffect";
    private static final String CORRUPT_EFFECT = "Nat20CorruptEffect";

    private static final float DOT_DURATION = 20.0f;

    private final Nat20LootSystem lootSystem;
    private final Nat20DotTickSystem dotTickSystem;

    private EntityEffect igniteEffect, coldEffect, infectEffect, corruptEffect;
    private boolean effectsResolved;

    public Nat20ElementalDotSystem(Nat20LootSystem lootSystem, Nat20DotTickSystem dotTickSystem) {
        this.lootSystem = lootSystem;
        this.dotTickSystem = dotTickSystem;
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
        if (damage.isCancelled()) return;

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

        if (!effectsResolved) {
            igniteEffect = EntityEffect.getAssetMap().getAsset(IGNITE_EFFECT);
            coldEffect = EntityEffect.getAssetMap().getAsset(COLD_EFFECT);
            infectEffect = EntityEffect.getAssetMap().getAsset(INFECT_EFFECT);
            corruptEffect = EntityEffect.getAssetMap().getAsset(CORRUPT_EFFECT);
            effectsResolved = true;
            LOGGER.atInfo().log("[ElemDot] resolved effects: ignite=%s cold=%s infect=%s corrupt=%s",
                    igniteEffect != null, coldEffect != null, infectEffect != null, corruptEffect != null);
        }

        UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            String id = rolledAffix.id();
            EntityEffect effect;

            if (IGNITE_ID.equals(id)) {
                effect = igniteEffect;
            } else if (COLD_ID.equals(id)) {
                effect = coldEffect;
            } else if (INFECT_ID.equals(id)) {
                effect = infectEffect;
            } else if (CORRUPT_ID.equals(id)) {
                effect = corruptEffect;
            } else {
                continue;
            }

            if (effect == null) {
                LOGGER.atWarning().log("[ElemDot] effect unavailable for %s", id);
                continue;
            }

            Nat20AffixDef def = affixRegistry.get(id);
            if (def == null) continue;

            double procChance = parseProcChance(def.procChance());
            if (procChance <= 0) continue;

            // WIS scaling on proc chance
            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                procChance *= (1.0 + modifier * def.statScaling().factor());
            }
            procChance = Math.min(procChance, 1.0);

            double roll = ThreadLocalRandom.current().nextDouble();
            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[ElemDot] %s: chance=%.1f%% roll=%.3f proc=%s",
                        id, procChance * 100, roll, roll <= procChance);
            }
            if (roll > procChance) continue;

            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
            EffectControllerComponent effectCtrl =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (effectCtrl == null) {
                LOGGER.atWarning().log("[ElemDot] target has no EffectControllerComponent");
                continue;
            }

            // Register with unified tick system so all DOTs on this entity tick in sync
            Nat20DotTickSystem.DotType dotType = switch (id) {
                case IGNITE_ID -> Nat20DotTickSystem.DotType.IGNITE;
                case COLD_ID -> Nat20DotTickSystem.DotType.COLD;
                case INFECT_ID -> Nat20DotTickSystem.DotType.INFECT;
                case CORRUPT_ID -> Nat20DotTickSystem.DotType.CORRUPT;
                default -> null;
            };
            boolean isNew = dotType != null
                    && dotTickSystem.registerDot(targetRef, dotType, attackerRef, DOT_DURATION);

            // Only apply visual EntityEffect on first application to prevent particle stacking
            if (isNew) {
                effectCtrl.addEffect(targetRef, effect, commandBuffer);
            }

            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[ElemDot] %s new=%s target=%s", id, isNew, targetRef);
            }
        }
    }

    private static double parseProcChance(@Nullable String procChanceStr) {
        if (procChanceStr == null || procChanceStr.isEmpty()) return 0.0;
        try {
            String trimmed = procChanceStr.strip();
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100.0;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            LOGGER.atWarning().log("[ElemDot] failed to parse proc chance: '%s'", procChanceStr);
            return 0.0;
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
