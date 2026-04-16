package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.chonbosmods.loot.mob.abilities.MobAbilityHandler;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.chonbosmods.loot.registry.Nat20MobAffixRegistry;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Tier;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls and applies mob affixes when an NPC is spawned at a given encounter tier.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Filter the affix pool by tier and roll a random subset</li>
 *   <li>Apply per-affix stat multipliers via {@link EntityStatMap}</li>
 *   <li>Fire {@link MobAbilityHandler#onSpawn} for each affix's ability type</li>
 *   <li>Track applied affixes per mob for loot drops and nameplate display</li>
 * </ul>
 */
public class Nat20MobAffixManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String MODIFIER_KEY_PREFIX = "nat20:mobaffix:";

    private final Nat20MobAffixRegistry registry;
    private final Nat20MobNameGenerator nameGenerator;
    private final Map<String, MobAbilityHandler> abilityHandlers = new HashMap<>();
    private final Map<Ref<EntityStore>, List<Nat20MobAffixDef>> appliedAffixes = new ConcurrentHashMap<>();

    public Nat20MobAffixManager(Nat20MobAffixRegistry registry, Nat20MobNameGenerator nameGenerator) {
        this.registry = registry;
        this.nameGenerator = nameGenerator;
    }

    /**
     * Register an ability handler for a given ability type string (e.g., "fiery", "frostborn").
     * Handlers are invoked during {@link #rollAndApply} and later by event listeners.
     */
    public void registerAbilityHandler(String abilityType, MobAbilityHandler handler) {
        abilityHandlers.put(abilityType, handler);
    }

    /**
     * Get the ability handler registered for the given type, or null if none.
     */
    @Nullable
    public MobAbilityHandler getAbilityHandler(String abilityType) {
        return abilityHandlers.get(abilityType);
    }

    /**
     * Roll random affixes for a mob based on its role and difficulty tier, apply stat
     * modifiers, and fire ability onSpawn callbacks. Nameplate is only assigned for
     * BOSS / DUNGEON_BOSS roles.
     *
     * @param mobRef     the entity reference for the mob
     * @param store      the entity store containing the mob
     * @param role       the mob's role (REGULAR / CHAMPION / BOSS / DUNGEON_BOSS)
     * @param difficulty the rolled difficulty tier (UNCOMMON / RARE / EPIC / LEGENDARY)
     */
    public void rollAndApply(Ref<EntityStore> mobRef, Store<EntityStore> store,
                             Tier role, DifficultyTier difficulty) {
        int maxAffixes = Natural20.getInstance().getScalingConfig().affixCountFor(role, difficulty);
        if (maxAffixes <= 0) return;

        List<Nat20MobAffixDef> pool = registry.getByMinTier(role.ordinal());
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("No mob affixes for role=%s difficulty=%s", role, difficulty);
            return;
        }

        List<Nat20MobAffixDef> rolled = rollAffixes(pool, maxAffixes);

        EntityStatMap statMap = store.getComponent(mobRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            for (Nat20MobAffixDef affix : rolled) applyAffixStatMultipliers(statMap, affix);
        } else {
            LOGGER.atWarning().log("EntityStatMap missing on mob, skipping affix modifiers");
        }

        for (Nat20MobAffixDef affix : rolled) {
            if (affix.abilityType() != null && !affix.abilityType().isEmpty()) {
                MobAbilityHandler handler = abilityHandlers.get(affix.abilityType());
                if (handler != null) {
                    try { handler.onSpawn(mobRef, store, affix); }
                    catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log(
                            "Ability handler '%s' threw on onSpawn for affix '%s'",
                            affix.abilityType(), affix.id());
                    }
                }
            }
        }

        appliedAffixes.put(mobRef, List.copyOf(rolled));

        // Nameplate only for BOSS / DUNGEON_BOSS
        if (role == Tier.BOSS || role == Tier.DUNGEON_BOSS) {
            String eliteName = nameGenerator.generate(difficulty);
            if (eliteName != null) {
                try {
                    store.putComponent(mobRef, Nameplate.getComponentType(), new Nameplate(eliteName));
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to set elite nameplate for mob %s", mobRef);
                }
            }
        }

        LOGGER.atInfo().log("Applied %d affix(es) role=%s difficulty=%s: %s",
                rolled.size(), role, difficulty,
                rolled.stream().map(Nat20MobAffixDef::displayName).toList());
    }

    /**
     * Get the list of affixes applied to a mob by its entity reference,
     * or null if the mob has no tracked affixes.
     */
    @Nullable
    public List<Nat20MobAffixDef> getAppliedAffixes(Ref<EntityStore> mobRef) {
        return appliedAffixes.get(mobRef);
    }

    /**
     * Remove tracking data for a mob (call on despawn/death to prevent memory leaks).
     * Delegates to each ability handler's clearMob() to clean up per-mob state.
     */
    public void clearMob(Ref<EntityStore> mobRef) {
        List<Nat20MobAffixDef> affixes = appliedAffixes.remove(mobRef);
        if (affixes != null) {
            for (Nat20MobAffixDef affix : affixes) {
                if (affix.abilityType() != null && !affix.abilityType().isEmpty()) {
                    MobAbilityHandler handler = abilityHandlers.get(affix.abilityType());
                    if (handler != null) {
                        try {
                            handler.clearMob(mobRef);
                        } catch (Exception e) {
                            LOGGER.atSevere().withCause(e).log(
                                    "Ability handler '%s' threw on clearMob for affix '%s'",
                                    affix.abilityType(), affix.id());
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the number of mobs currently tracked with affixes.
     */
    public int getTrackedMobCount() {
        return appliedAffixes.size();
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private List<Nat20MobAffixDef> rollAffixes(List<Nat20MobAffixDef> pool, int maxCount) {
        int count = Math.min(maxCount, pool.size());
        if (count == pool.size()) {
            return new ArrayList<>(pool);
        }

        // Shuffle a copy and take the first N entries
        List<Nat20MobAffixDef> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return new ArrayList<>(shuffled.subList(0, count));
    }

    private void applyAffixStatMultipliers(EntityStatMap statMap, Nat20MobAffixDef affix) {
        for (var entry : affix.statMultipliers().entrySet()) {
            String statName = entry.getKey();
            double multiplier = entry.getValue();

            int statIndex = resolveStatIndex(statName);
            if (statIndex == AssetMapWithIndexes.NOT_FOUND) {
                LOGGER.atWarning().log("Unknown stat '%s' in mob affix '%s', skipping",
                        statName, affix.id());
                continue;
            }

            String key = MODIFIER_KEY_PREFIX + affix.id() + ":" + statName;
            StaticModifier mod = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.MULTIPLICATIVE,
                    (float) multiplier);
            statMap.putModifier(statIndex, key, mod);
        }
    }

    private int resolveStatIndex(String statName) {
        try {
            var assetMap = EntityStatType.getAssetMap();
            return assetMap.getIndex(statName);
        } catch (Exception e) {
            return AssetMapWithIndexes.NOT_FOUND;
        }
    }
}
