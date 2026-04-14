package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.LootRuleEntry;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20ItemRegistry;
import com.chonbosmods.loot.registry.Nat20NamePoolRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.google.common.flogger.FluentLogger;

import java.util.*;

public class Nat20LootPipeline {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;
    private final Nat20ItemRegistry itemRegistry;
    private final Nat20NamePoolRegistry namePoolRegistry;

    public Nat20LootPipeline(Nat20RarityRegistry rarityRegistry, Nat20AffixRegistry affixRegistry,
                              Nat20ItemRegistry itemRegistry, Nat20NamePoolRegistry namePoolRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
        this.itemRegistry = itemRegistry;
        this.namePoolRegistry = namePoolRegistry;
    }

    /** Backward-compatible overload: defaults ilvl to 10. Will be removed when callers are updated. */
    public Nat20LootData generate(String itemId, String baseName, String categoryKey, Random random) {
        return generate(itemId, baseName, categoryKey, random, 10);
    }

    /**
     * Generate loot data for an item.
     *
     * @param itemId the Hytale item ID (e.g., "Hytale:IronSword")
     * @param baseName the base item display name (e.g., "Iron Sword")
     * @param categoryKey the equipment category key (e.g., "melee_weapon")
     * @param random the random source
     * @param ilvl item level: caps how strong affix rolls can be (maxLootLevel = min(1.0, ilvl/20.0))
     * @return populated Nat20LootData, or null if no rarities are loaded
     */
    public Nat20LootData generate(String itemId, String baseName, String categoryKey, Random random, int ilvl) {
        // Step 1: Select rarity
        Nat20RarityDef rarity = rarityRegistry.selectRandom(random);
        if (rarity == null) {
            LOGGER.atWarning().log("No rarity definitions loaded, cannot generate loot for %s", itemId);
            return null;
        }

        // Step 2: Roll loot level (capped by ilvl)
        double maxLootLevel = Math.min(1.0, ilvl / 20.0);
        double lootLevel = random.nextDouble() * maxLootLevel;

        // Step 3: Roll affixes based on rarity loot rules
        List<RolledAffix> rolledAffixes = rollAffixes(rarity, categoryKey, random, maxLootLevel);

        // Step 4: Allocate sockets
        int sockets = rollSockets(rarity, random);

        // Step 5: Generate name
        String prefixSource = null;
        String suffixSource = null;
        StringBuilder nameBuilder = new StringBuilder();

        // Find prefix affix
        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.PREFIX && prefixSource == null) {
                prefixSource = affix.id();
                nameBuilder.append(getDisplayName(def, rarity.id(), random)).append(" ");
                break;
            }
        }

        nameBuilder.append(baseName);

        // Find suffix affix
        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.SUFFIX && suffixSource == null) {
                suffixSource = affix.id();
                nameBuilder.append(" of ").append(getDisplayName(def, rarity.id(), random));
                break;
            }
        }

        String generatedName = nameBuilder.toString();

        // Step 6: Resolve variant item ID
        String variantItemId = resolveVariantId(itemId, rarity.id());

        // Step 7: Build description
        String description = buildDescription(rolledAffixes, rarity.id(), sockets, rarity);

        // Step 8: Assemble Nat20LootData
        Nat20LootData data = new Nat20LootData();
        data.setVersion(Nat20LootData.CURRENT_VERSION);
        data.setRarity(rarity.id());
        data.setLootLevel(lootLevel);
        data.setItemLevel(ilvl);
        data.setAffixes(rolledAffixes);
        data.setSockets(sockets);
        data.setGems(new ArrayList<>());
        data.setGeneratedName(generatedName);
        data.setNamePrefixSource(prefixSource);
        data.setNameSuffixSource(suffixSource);
        data.setDescription(description);
        data.setVariantItemId(variantItemId);

        // Register unique item for per-instance tooltip
        // Use vanilla quality ID so the client renders the built-in slot border
        String qualityId = rarity.id();
        if (itemRegistry != null) {
            String uniqueId = itemRegistry.registerItem(itemId, qualityId, data);
            data.setUniqueItemId(uniqueId);
        }

        LOGGER.atInfo().log("Generated loot: %s [%s] variant=%s with %d affixes, %d sockets (lootLevel=%.2f, ilvl=%d)",
            generatedName, rarity.id(), variantItemId, rolledAffixes.size(), sockets, lootLevel, ilvl);

        return data;
    }

    /** Backward-compatible overload: defaults ilvl to 10. Will be removed when callers are updated. */
    public Nat20LootData generate(String itemId, String baseName, String categoryKey,
                                   int minRarityTier, int maxRarityTier, Random random) {
        return generate(itemId, baseName, categoryKey, minRarityTier, maxRarityTier, random, 10);
    }

    /**
     * Generate loot data for an item with rarity tier clamping.
     * Only rarities whose qualityValue falls within [minRarityTier, maxRarityTier] are eligible.
     *
     * @param itemId the Hytale item ID (e.g., "Hytale:IronSword")
     * @param baseName the base item display name (e.g., "Iron Sword")
     * @param categoryKey the equipment category key (e.g., "melee_weapon")
     * @param minRarityTier minimum qualityValue for eligible rarities (inclusive)
     * @param maxRarityTier maximum qualityValue for eligible rarities (inclusive)
     * @param random the random source
     * @param ilvl item level: caps how strong affix rolls can be (maxLootLevel = min(1.0, ilvl/20.0))
     * @return populated Nat20LootData, or null if no rarities are loaded
     */
    public Nat20LootData generate(String itemId, String baseName, String categoryKey,
                                   int minRarityTier, int maxRarityTier, Random random, int ilvl) {
        // Step 1: Select rarity within tier range
        Nat20RarityDef rarity = rarityRegistry.selectRandom(random, minRarityTier, maxRarityTier);
        if (rarity == null) {
            LOGGER.atWarning().log("No rarity definitions loaded, cannot generate loot for %s", itemId);
            return null;
        }

        // Step 2: Roll loot level (capped by ilvl)
        double maxLootLevel = Math.min(1.0, ilvl / 20.0);
        double lootLevel = random.nextDouble() * maxLootLevel;

        // Step 3: Roll affixes based on rarity loot rules
        List<RolledAffix> rolledAffixes = rollAffixes(rarity, categoryKey, random, maxLootLevel);

        // Step 4: Allocate sockets
        int sockets = rollSockets(rarity, random);

        // Step 5: Generate name
        String prefixSource = null;
        String suffixSource = null;
        StringBuilder nameBuilder = new StringBuilder();

        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.PREFIX && prefixSource == null) {
                prefixSource = affix.id();
                nameBuilder.append(getDisplayName(def, rarity.id(), random)).append(" ");
                break;
            }
        }

        nameBuilder.append(baseName);

        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.SUFFIX && suffixSource == null) {
                suffixSource = affix.id();
                nameBuilder.append(" of ").append(getDisplayName(def, rarity.id(), random));
                break;
            }
        }

        String generatedName = nameBuilder.toString();

        // Step 6: Resolve variant item ID
        String variantItemId = resolveVariantId(itemId, rarity.id());

        // Step 7: Build description
        String description = buildDescription(rolledAffixes, rarity.id(), sockets, rarity);

        // Step 8: Assemble Nat20LootData
        Nat20LootData data = new Nat20LootData();
        data.setVersion(Nat20LootData.CURRENT_VERSION);
        data.setRarity(rarity.id());
        data.setLootLevel(lootLevel);
        data.setItemLevel(ilvl);
        data.setAffixes(rolledAffixes);
        data.setSockets(sockets);
        data.setGems(new ArrayList<>());
        data.setGeneratedName(generatedName);
        data.setNamePrefixSource(prefixSource);
        data.setNameSuffixSource(suffixSource);
        data.setDescription(description);
        data.setVariantItemId(variantItemId);

        // Register unique item for per-instance tooltip
        // Use vanilla quality ID so the client renders the built-in slot border
        String qualityId = rarity.id();
        if (itemRegistry != null) {
            String uniqueId = itemRegistry.registerItem(itemId, qualityId, data);
            data.setUniqueItemId(uniqueId);
        }

        LOGGER.atInfo().log("Generated loot: %s [%s] variant=%s with %d affixes, %d sockets (lootLevel=%.2f, ilvl=%d, tierRange=[%d,%d])",
            generatedName, rarity.id(), variantItemId, rolledAffixes.size(), sockets, lootLevel, ilvl, minRarityTier, maxRarityTier);

        return data;
    }

    private List<RolledAffix> rollAffixes(Nat20RarityDef rarity, String categoryKey, Random random, double maxLootLevel) {
        List<RolledAffix> result = new ArrayList<>();
        Set<String> usedAffixIds = new HashSet<>();

        for (LootRuleEntry rule : rarity.lootRules()) {
            if (random.nextDouble() > rule.probability()) continue;

            List<Nat20AffixDef> pool = affixRegistry.getPool(rule.type(), categoryKey, rarity.id());
            pool = new ArrayList<>(pool);
            pool.removeIf(a -> usedAffixIds.contains(a.id()));
            pool.removeIf(a -> a.exclusiveWith() != null
                && !Collections.disjoint(a.exclusiveWith(), usedAffixIds));

            for (int i = 0; i < rule.count() && !pool.isEmpty(); i++) {
                Nat20AffixDef chosen = weightedPick(pool, random);
                pool.remove(chosen);
                usedAffixIds.add(chosen.id());
                double lo = random.nextDouble() * maxLootLevel;
                double hi = random.nextDouble() * maxLootLevel;
                result.add(new RolledAffix(chosen.id(), Math.min(lo, hi), Math.max(lo, hi)));
                // Exclusion is symmetric: choosing A blocks B if either side declares the relationship
                pool.removeIf(a -> {
                    if (chosen.exclusiveWith() != null && chosen.exclusiveWith().contains(a.id())) return true;
                    return a.exclusiveWith() != null && a.exclusiveWith().contains(chosen.id());
                });
            }
        }

        // Cap at rarity max
        while (result.size() > rarity.maxAffixes()) {
            result.removeLast();
        }

        return result;
    }

    /**
     * Weighted random selection from an affix pool using each affix's frequency.
     * Higher frequency = more likely to be picked.
     */
    private Nat20AffixDef weightedPick(List<Nat20AffixDef> pool, Random random) {
        int totalWeight = 0;
        for (var def : pool) {
            totalWeight += def.frequency();
        }
        int roll = random.nextInt(totalWeight);
        int accumulated = 0;
        for (var def : pool) {
            accumulated += def.frequency();
            if (accumulated > roll) return def;
        }
        return pool.getLast(); // Shouldn't reach here, but safety fallback
    }

    /**
     * Derive min/max rarity quality values from item level.
     * ilvl 1-5:  Common-Rare,    ilvl 6-10: Common-Epic,
     * ilvl 11-15: Uncommon-Legendary, ilvl 16-20: Rare-Legendary.
     * @return int[2] = {minQualityValue, maxQualityValue}
     */
    public static int[] rarityGateForIlvl(int ilvl) {
        if (ilvl <= 5) return new int[]{1, 3};       // Common-Rare
        if (ilvl <= 10) return new int[]{1, 4};      // Common-Epic
        if (ilvl <= 15) return new int[]{2, 5};      // Uncommon-Legendary
        return new int[]{3, 5};                       // Rare-Legendary
    }

    private int rollSockets(Nat20RarityDef rarity, Random random) {
        // TODO(post-MVP): sockets are disabled — all rarity JSONs set MaxSockets=0 for now.
        // When the socket/gem system is designed, raise MaxSockets in the rarity configs and
        // this method resumes rolling automatically.
        if (rarity.maxSockets() <= 0) return 0;
        int sockets = 0;
        for (int i = 0; i < rarity.maxSockets(); i++) {
            if (random.nextDouble() < 0.5) sockets++;
        }
        return sockets;
    }

    /**
     * Legacy pre-bake of the tooltip description stored on {@code Nat20LootData.description}.
     * The canonical rendering happens in {@link com.chonbosmods.loot.registry.Nat20ItemRegistry}
     * via {@link com.chonbosmods.loot.Nat20ItemRenderer} + {@code Nat20TooltipStringBuilder};
     * this stub is kept only so older consumers reading {@code getDescription()} directly
     * don't crash. Returns an empty string.
     */
    private String buildDescription(List<RolledAffix> affixes, String rarityId, int sockets, Nat20RarityDef rarity) {
        return "";
    }

    /**
     * Resolve the variant item ID for a given base item and rarity.
     *
     * Converts the base item ID to a nat20-namespaced variant ID using the same
     * algorithm as the codegen script (generate_variants.py):
     * 1. Strip namespace: "Hytale:IronSword" -> "IronSword"
     * 2. PascalCase to snake_case: "IronSword" -> "iron_sword"
     * 3. Append rarity (lowercased): "iron_sword" + "_" + "rare" = "iron_sword_rare"
     * 4. Add namespace: "nat20:iron_sword_rare"
     *
     * @param itemId the base Hytale item ID (e.g., "Hytale:IronSword")
     * @param rarityId the rarity ID (e.g., "Rare")
     * @return the variant item ID (e.g., "nat20:iron_sword_rare")
     */
    String resolveVariantId(String itemId, String rarityId) {
        // Strip namespace
        String name = itemId;
        int colon = itemId.indexOf(':');
        if (colon >= 0) {
            name = itemId.substring(colon + 1);
        }

        // Convert PascalCase to snake_case
        String snakeName = pascalToSnakeCase(name);

        // Build variant ID: nat20:<snake_name>_<rarity_lower>
        return "nat20:" + snakeName + "_" + rarityId.toLowerCase();
    }

    /**
     * Convert a PascalCase string to snake_case.
     * e.g., "IronSword" -> "iron_sword", "IronPickaxe" -> "iron_pickaxe"
     */
    static String pascalToSnakeCase(String input) {
        StringBuilder result = new StringBuilder(input.length() + 4);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(ch));
        }
        return result.toString();
    }

    /**
     * Get a display name for an affix, trying tiered name pools first and
     * falling back to localization key extraction.
     */
    private String getDisplayName(Nat20AffixDef def, String rarityId, Random random) {
        // Try tiered name pool first
        String affixIdShort = def.id().startsWith("nat20:") ? def.id().substring(6) : def.id();
        if (namePoolRegistry != null) {
            String poolName = namePoolRegistry.getRandomName(affixIdShort, rarityId, random);
            if (poolName != null) return poolName;
        }
        // Fallback: extract from localization key
        return getAffixDisplayWord(def);
    }

    /**
     * Extract a display word from the affix's display name localization key.
     * e.g., "server.nat20.affix.violent" -> "Violent"
     */
    private String getAffixDisplayWord(Nat20AffixDef def) {
        String displayName = def.displayName();
        int lastDot = displayName.lastIndexOf('.');
        String word = (lastDot >= 0) ? displayName.substring(lastDot + 1) : displayName;
        // Handle underscores as word separators: "the_titan" → "The Titan"
        String[] parts = word.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
