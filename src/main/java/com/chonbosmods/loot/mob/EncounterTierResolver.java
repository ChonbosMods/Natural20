package com.chonbosmods.loot.mob;

import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Determines the encounter tier for a spawned NPC.
 *
 * Resolution order:
 * 1. Explicit tier tag from spawn config (e.g., "nat20:tier:elite")
 * 2. Role-name mapping via {@link #registerRoleTier}
 * 3. Default: NORMAL
 *
 * TODO: add zone-based distance calculation once world-position context is available
 */
public class EncounterTierResolver {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final String TIER_TAG_PREFIX = "nat20:tier:";

    private final Map<String, EncounterTier> roleTierMap = new HashMap<>();

    /**
     * Register a fixed tier mapping for a specific role name.
     * This lets content authors assign tiers to roles without spawn-config tags.
     */
    public void registerRoleTier(String roleName, EncounterTier tier) {
        roleTierMap.put(roleName, tier);
        LOGGER.atFine().log("Registered role tier: %s -> %s", roleName, tier);
    }

    /**
     * Resolve the encounter tier for an NPC given optional spawn tags and its role name.
     *
     * @param tags     spawn-config tags (nullable); checked for a "nat20:tier:<name>" entry
     * @param roleName the NPC's role name used as a fallback lookup
     * @return the resolved tier, defaulting to NORMAL
     */
    public EncounterTier resolve(@Nullable Iterable<String> tags, String roleName) {
        // 1. Check spawn-config tags for an explicit tier override
        if (tags != null) {
            for (String tag : tags) {
                if (tag.startsWith(TIER_TAG_PREFIX)) {
                    String tierName = tag.substring(TIER_TAG_PREFIX.length());
                    EncounterTier tier = EncounterTier.fromName(tierName);
                    LOGGER.atFine().log("Resolved tier from tag '%s': %s", tag, tier);
                    return tier;
                }
            }
        }

        // 2. Fall back to role-name mapping
        EncounterTier roleTier = resolveFromRoleName(roleName);
        if (roleTier != null) {
            LOGGER.atFine().log("Resolved tier from role '%s': %s", roleName, roleTier);
            return roleTier;
        }

        // 3. Default
        // TODO: zone-based distance calculation (distance from world spawn -> higher tier)
        return EncounterTier.NORMAL;
    }

    /**
     * Look up a tier by role name. Returns null if no mapping is registered.
     * Extend this method or use {@link #registerRoleTier} to add role-based overrides.
     */
    @Nullable
    public EncounterTier resolveFromRoleName(String roleName) {
        return roleTierMap.get(roleName);
    }
}
