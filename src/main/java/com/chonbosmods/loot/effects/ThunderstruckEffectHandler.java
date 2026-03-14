package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chain lightning proc: on hit, has a chance to deal secondary damage to nearby entities.
 *
 * <p>The {@code effectiveValue} represents the chain lightning damage amount.
 * Proc chance and cooldown are read from the affix definition's {@code procChance}
 * and {@code cooldown} fields.
 */
public class ThunderstruckEffectHandler implements EffectHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final int MAX_CHAIN_TARGETS = 3;
    private static final double CHAIN_RANGE_BLOCKS = 5.0;

    /** Per-player cooldown tracking: player UUID to last proc timestamp in millis. */
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public void onHit(Nat20AffixDef def, double effectiveValue, Damage event) {
        if (effectiveValue <= 0) return;

        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        // Parse proc chance from def (e.g., "30%" -> 0.30)
        double procChance = parseProcChance(def.procChance());
        if (procChance <= 0) return;

        // Parse cooldown from def (e.g., "2.0s" -> 2000ms)
        long cooldownMs = parseCooldownMs(def.cooldown());

        // Check cooldown per-player using the entity source ref's ID
        // TODO: Extract player UUID from entitySource.getRef() at runtime.
        // For now, use the source's hash as a stand-in key.
        UUID playerKey = UUID.nameUUIDFromBytes(
                String.valueOf(entitySource.getRef().hashCode()).getBytes());

        long now = System.currentTimeMillis();
        Long lastProc = cooldowns.get(playerKey);
        if (lastProc != null && (now - lastProc) < cooldownMs) return;

        // Roll proc chance
        if (ThreadLocalRandom.current().nextDouble() > procChance) return;

        // Record cooldown
        cooldowns.put(playerKey, now);

        // TODO: Find nearby living entities within CHAIN_RANGE_BLOCKS of the target,
        // deal effectiveValue damage to up to MAX_CHAIN_TARGETS of them.
        // Requires entity spatial query API (e.g., World.getEntitiesInRadius or similar)
        // and a way to apply damage to arbitrary entities.
        LOGGER.atInfo().log("Thunderstruck proc: chain lightning for %.2f damage to up to %d targets within %.1f blocks",
                effectiveValue, MAX_CHAIN_TARGETS, CHAIN_RANGE_BLOCKS);
    }

    /**
     * Parse a proc chance string like "30%" into a double (0.30).
     * Returns 0 if the string is null or unparseable.
     */
    private static double parseProcChance(@Nullable String procChanceStr) {
        if (procChanceStr == null || procChanceStr.isEmpty()) return 0.0;
        try {
            String trimmed = procChanceStr.strip();
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100.0;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            LOGGER.atWarning().log("Failed to parse proc chance: '%s'", procChanceStr);
            return 0.0;
        }
    }

    /**
     * Parse a cooldown string like "2.0s" into milliseconds (2000).
     * Returns 0 if the string is null or unparseable.
     */
    private static long parseCooldownMs(@Nullable String cooldownStr) {
        if (cooldownStr == null || cooldownStr.isEmpty()) return 0L;
        try {
            String trimmed = cooldownStr.strip();
            if (trimmed.endsWith("s")) {
                double seconds = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
                return (long) (seconds * 1000.0);
            }
            return (long) (Double.parseDouble(trimmed) * 1000.0);
        } catch (NumberFormatException e) {
            LOGGER.atWarning().log("Failed to parse cooldown: '%s'", cooldownStr);
            return 0L;
        }
    }
}
