package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Tier;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls gear-pool affixes on a spawning mob, stores the result on the mob's
 * {@link Nat20MobAffixes} component, applies STAT-type modifiers to the mob's
 * EntityStatMap, and sets a rarity-colored nameplate for BOSS/DUNGEON_BOSS.
 *
 * Replaces the old ability-handler-based mob-affix manager. EFFECT-type affixes
 * are applied by the existing player combat systems once they've been refactored
 * to read from the unified affix source (player gear OR mob component).
 */
public class Nat20MobAffixManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String MOD_KEY_PREFIX = "nat20:mob:affix:";

    private final Nat20AffixRegistry affixRegistry;
    private final Nat20MobAffixRoller roller;
    private final Nat20MobNameGenerator nameGenerator;

    public Nat20MobAffixManager(Nat20AffixRegistry affixRegistry, Nat20MobNameGenerator nameGenerator) {
        this.affixRegistry = affixRegistry;
        this.roller = new Nat20MobAffixRoller(affixRegistry);
        this.nameGenerator = nameGenerator;
    }

    /**
     * Roll and apply mob affixes. Writes the rolled list to Nat20MobAffixes,
     * applies STAT-type modifiers via EntityStatMap, and sets a nameplate for
     * BOSS / DUNGEON_BOSS roles (independent of whether any affixes rolled).
     */
    public void rollAndApply(Ref<EntityStore> mobRef, Store<EntityStore> store,
                             Tier role, DifficultyTier difficulty) {
        // Nameplate is tier-gated but independent of affix pool state.
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

        int slotBudget = Natural20.getInstance().getScalingConfig().affixCountFor(role, difficulty);
        if (slotBudget <= 0) return;

        Random rng = ThreadLocalRandom.current();
        List<RolledAffix> rolled = roller.roll(slotBudget, difficulty, rng);
        if (rolled.isEmpty()) {
            LOGGER.atWarning().log("No mob-eligible affixes available for role=%s difficulty=%s", role, difficulty);
            return;
        }

        // Persist rolled affixes on the mob (combat systems read this via Natural20.getMobAffixesType()).
        Nat20MobAffixes affixes = new Nat20MobAffixes();
        affixes.setAffixes(rolled);
        try {
            store.putComponent(mobRef, Natural20.getMobAffixesType(), affixes);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to put Nat20MobAffixes on mob %s", mobRef);
        }

        // Apply STAT-type affixes as EntityStatMap modifiers immediately.
        EntityStatMap statMap = store.getComponent(mobRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            for (RolledAffix r : rolled) {
                Nat20AffixDef def = affixRegistry.get(r.id());
                if (def == null) continue;
                if (!"STAT".equalsIgnoreCase(def.type().name())) continue;
                applyStatModifier(statMap, def, r);
            }
        }

        LOGGER.atInfo().log("Applied %d affix(es) role=%s difficulty=%s: %s",
                rolled.size(), role, difficulty,
                rolled.stream().map(RolledAffix::id).toList());
    }

    private void applyStatModifier(EntityStatMap statMap, Nat20AffixDef def, RolledAffix rolled) {
        String statName = def.targetStat();
        if (statName == null || statName.isEmpty()) return;
        int statIndex = EntityStatType.getAssetMap().getIndex(statName);
        if (statIndex < 0) {
            LOGGER.atWarning().log("Mob affix '%s' targets unregistered stat '%s': skipping", def.id(), statName);
            return;
        }

        StaticModifier.CalculationType calcType = "MULTIPLICATIVE".equalsIgnoreCase(def.modifierType())
                ? StaticModifier.CalculationType.MULTIPLICATIVE
                : StaticModifier.CalculationType.ADDITIVE;

        String key = MOD_KEY_PREFIX + def.id();
        StaticModifier mod = new StaticModifier(
                Modifier.ModifierTarget.MAX,
                calcType,
                (float) rolled.midLevel());
        statMap.putModifier(statIndex, key, mod);
    }
}
