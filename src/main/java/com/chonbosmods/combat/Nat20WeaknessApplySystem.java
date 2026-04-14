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
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elemental Weakness application: on melee hit, applies element-tinted EntityEffect
 * on target and stores weakness multiplier for the companion amplify system.
 * Handles all four elements in one system.
 */
public class Nat20WeaknessApplySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final double SOFTCAP_K = 0.60;

    private static final String FIRE_WEAKNESS_ID = "nat20:fire_weakness";
    private static final String FROST_WEAKNESS_ID = "nat20:frost_weakness";
    private static final String VOID_WEAKNESS_ID = "nat20:void_weakness";
    private static final String POISON_WEAKNESS_ID = "nat20:poison_weakness";

    public enum Element { FIRE, FROST, VOID, POISON }

    record WeaknessState(double multiplier, long expiryMs) {}

    private final Nat20LootSystem lootSystem;
    private final ConcurrentHashMap<Ref<EntityStore>, EnumMap<Element, WeaknessState>> weaknessMap = new ConcurrentHashMap<>();

    private EntityEffect fireEffect, frostEffect, voidEffect, poisonEffect;
    private boolean effectsResolved;

    public Nat20WeaknessApplySystem(Nat20LootSystem lootSystem) {
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
        if (damage.isCancelled()) return;

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

        if (!effectsResolved) {
            fireEffect = EntityEffect.getAssetMap().getAsset("Nat20FireWeaknessEffect");
            frostEffect = EntityEffect.getAssetMap().getAsset("Nat20FrostWeaknessEffect");
            voidEffect = EntityEffect.getAssetMap().getAsset("Nat20VoidWeaknessEffect");
            poisonEffect = EntityEffect.getAssetMap().getAsset("Nat20PoisonWeaknessEffect");
            effectsResolved = true;
        }

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            String id = rolledAffix.id();
            Element element;
            EntityEffect effect;

            if (FIRE_WEAKNESS_ID.equals(id)) { element = Element.FIRE; effect = fireEffect; }
            else if (FROST_WEAKNESS_ID.equals(id)) { element = Element.FROST; effect = frostEffect; }
            else if (VOID_WEAKNESS_ID.equals(id)) { element = Element.VOID; effect = voidEffect; }
            else if (POISON_WEAKNESS_ID.equals(id)) { element = Element.POISON; effect = poisonEffect; }
            else continue;

            if (effect == null) continue;

            Nat20AffixDef def = affixRegistry.get(id);
            if (def == null) continue;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) continue;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

            EnumMap<Element, WeaknessState> elementMap = weaknessMap.computeIfAbsent(
                    targetRef, k -> new EnumMap<>(Element.class));
            WeaknessState previous = elementMap.put(element,
                    new WeaknessState(effectiveValue, System.currentTimeMillis() + 10000));
            boolean isNew = previous == null || System.currentTimeMillis() > previous.expiryMs;
            if (isNew) {
                EffectControllerComponent effectCtrl =
                        store.getComponent(targetRef, EffectControllerComponent.getComponentType());
                if (effectCtrl != null) {
                    effectCtrl.addEffect(targetRef, effect, commandBuffer);
                }
            }

            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[Weakness] %s %s: target=%s multiplier=%.1f%%",
                        isNew ? "applied" : "refreshed",
                        element, targetRef, effectiveValue * 100);
            }
        }
    }

    /**
     * Called by Nat20WeaknessAmplifySystem. Returns multiplier if target has
     * weakness matching the given element, 0 otherwise.
     */
    public double getWeaknessMultiplier(Ref<EntityStore> targetRef, Element element) {
        Map<Element, WeaknessState> elementMap = weaknessMap.get(targetRef);
        if (elementMap == null) return 0;
        WeaknessState state = elementMap.get(element);
        if (state == null) return 0;
        if (System.currentTimeMillis() > state.expiryMs) {
            elementMap.remove(element);
            return 0;
        }
        return state.multiplier;
    }

    public void removePlayer(UUID uuid) {
        // Weakness is keyed by target ref, not player UUID. Stale entries expire naturally.
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
