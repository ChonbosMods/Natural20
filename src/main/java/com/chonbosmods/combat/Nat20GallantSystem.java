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
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gallant: armor affix. When the wearer is struck by melee, chance to apply a
 * damage-reduction debuff EntityEffect on the ATTACKER. Reads the defender's armor,
 * not the attacker's weapon. Companion Filter Group system reduces attacker's outgoing damage.
 */
public class Nat20GallantSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:gallant";
    private static final String EFFECT_ID = "Nat20GallantEffect";
    private static final double SOFTCAP_K = 0.30;

    private final Nat20LootSystem lootSystem;
    private EntityEffect effect;
    private boolean effectResolved;

    // Track gallant debuff on entities (for Filter Group companion)
    private final ConcurrentHashMap<Ref<EntityStore>, GallantState> debuffedEntities = new ConcurrentHashMap<>();

    record GallantState(double reductionMultiplier, long expiryMs) {}

    public Nat20GallantSystem(Nat20LootSystem lootSystem) {
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

        // The "target" being hit is the player wearing gallant armor
        Ref<EntityStore> defenderRef = chunk.getReferenceTo(entityIndex);
        Player defenderPlayer = store.getComponent(defenderRef, Player.getComponentType());
        if (defenderPlayer == null) return;

        // Need an attacker to apply the debuff to
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Scan defender's armor for gallant affix
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, defenderRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalProcChance = 0;
        double totalReduction = 0;

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            for (RolledAffix rolledAffix : lootData.getAffixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) continue;

                double procChance = parseProcChance(def.procChance());
                totalProcChance = Math.max(totalProcChance, procChance); // Use highest proc chance, don't stack

                AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
                if (range == null) continue;

                double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
                double effectiveValue = baseValue;
                PlayerStats stats = resolvePlayerStats(defenderRef, store);
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                totalReduction += effectiveValue;
            }
        }

        if (totalProcChance <= 0 || totalReduction <= 0) return;

        totalReduction = Nat20Softcap.softcap(totalReduction, SOFTCAP_K);

        double roll = ThreadLocalRandom.current().nextDouble();
        UUID defenderUuid = defenderPlayer.getPlayerRef().getUuid();

        if (CombatDebugSystem.isEnabled(defenderUuid)) {
            LOGGER.atInfo().log("[Gallant] check: proc=%.1f%% roll=%.3f reduction=%.1f%%",
                    totalProcChance * 100, roll, totalReduction * 100);
        }

        if (roll > totalProcChance) return;

        if (!resolveEffect()) return;

        EffectControllerComponent effectCtrl =
                store.getComponent(attackerRef, EffectControllerComponent.getComponentType());
        if (effectCtrl == null) return;

        // Only apply visual EntityEffect on first application to prevent particle stacking
        GallantState previous = debuffedEntities.put(attackerRef,
                new GallantState(totalReduction, System.currentTimeMillis() + 7000));
        boolean isNew = previous == null || System.currentTimeMillis() > previous.expiryMs;
        if (isNew) {
            effectCtrl.addEffect(attackerRef, effect, commandBuffer);
        }

        if (CombatDebugSystem.isEnabled(defenderUuid)) {
            LOGGER.atInfo().log("[Gallant] %s: attacker=%s reduction=%.1f%%",
                    isNew ? "proc" : "refreshed", attackerRef, totalReduction * 100);
        }
    }

    /**
     * Called by a Filter Group companion if needed (shares state with FearReduceSystem pattern).
     * Returns the damage reduction if entity has gallant debuff, 0 otherwise.
     */
    public double getGallantReduction(Ref<EntityStore> attackerRef) {
        GallantState state = debuffedEntities.get(attackerRef);
        if (state == null) return 0;
        if (System.currentTimeMillis() > state.expiryMs) {
            debuffedEntities.remove(attackerRef);
            return 0;
        }
        return state.reductionMultiplier;
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
            return 0.0;
        }
    }

    private boolean resolveEffect() {
        if (effectResolved) return effect != null;
        effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        effectResolved = true;
        if (effect == null) {
            LOGGER.atWarning().log("[Gallant] EntityEffect '%s' not found", EFFECT_ID);
        }
        return effect != null;
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
