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
     * Roll a fresh affix set for a mob based on role + difficulty, then apply it.
     * Used by spawn paths that want an independent roll per mob (e.g., the boss
     * of a group, or future out-in-the-wild single-mob spawns).
     */
    public void rollAndApply(Ref<EntityStore> mobRef, Store<EntityStore> store,
                             Tier role, DifficultyTier difficulty) {
        List<RolledAffix> rolled = rollAffixes(role, difficulty);
        applyAffixes(mobRef, store, role, difficulty, rolled);
    }

    /**
     * Roll an affix set ONCE for shared use across multiple mobs (e.g., every
     * champion in a Nat20 spawn group gets the same set; the boss rolls its own
     * via {@link #rollAndApply}). Returns empty list if the pool is empty or the
     * slot budget is zero.
     */
    public List<RolledAffix> rollAffixes(Tier role, DifficultyTier difficulty) {
        int slotBudget = Natural20.getInstance().getScalingConfig().affixCountFor(role, difficulty);
        if (slotBudget <= 0) return List.of();
        return roller.roll(slotBudget, difficulty, ThreadLocalRandom.current());
    }

    /**
     * Apply a pre-rolled affix set to a mob. Writes {@link Nat20MobAffixes},
     * applies STAT-type modifiers via EntityStatMap, and sets a nameplate for
     * BOSS / DUNGEON_BOSS roles (independent of whether any affixes rolled).
     * The nameplate is generated per-call so each boss in a group still gets
     * a unique rarity-colored name.
     */
    public void applyAffixes(Ref<EntityStore> mobRef, Store<EntityStore> store,
                             Tier role, DifficultyTier difficulty, List<RolledAffix> rolled) {
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

        if (rolled == null || rolled.isEmpty()) {
            LOGGER.atFine().log("No affixes to apply on mob %s (role=%s difficulty=%s)",
                    mobRef, role, difficulty);
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

        LOGGER.atFine().log("Applied %d affix(es) role=%s difficulty=%s: %s",
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
