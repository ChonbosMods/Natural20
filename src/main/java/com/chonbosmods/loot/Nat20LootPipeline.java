package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.LootRuleEntry;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20ItemRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.google.common.flogger.FluentLogger;

import java.util.*;

public class Nat20LootPipeline {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;
    private final Nat20ItemRegistry itemRegistry;

    public Nat20LootPipeline(Nat20RarityRegistry rarityRegistry, Nat20AffixRegistry affixRegistry, Nat20ItemRegistry itemRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
        this.itemRegistry = itemRegistry;
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
                nameBuilder.append(getAffixDisplayWord(def)).append(" ");
                break;
            }
        }

        nameBuilder.append(baseName);

        // Find suffix affix
        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.SUFFIX && suffixSource == null) {
                suffixSource = affix.id();
                nameBuilder.append(" of ").append(getAffixDisplayWord(def));
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
                nameBuilder.append(getAffixDisplayWord(def)).append(" ");
                break;
            }
        }

        nameBuilder.append(baseName);

        for (var affix : rolledAffixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def != null && def.namePosition() == NamePosition.SUFFIX && suffixSource == null) {
                suffixSource = affix.id();
                nameBuilder.append(" of ").append(getAffixDisplayWord(def));
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
                int idx = random.nextInt(pool.size());
                Nat20AffixDef chosen = pool.remove(idx);
                usedAffixIds.add(chosen.id());
                result.add(new RolledAffix(chosen.id(), random.nextDouble() * maxLootLevel));
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

    private int rollSockets(Nat20RarityDef rarity, Random random) {
        if (rarity.maxSockets() <= 0) return 0;
        int sockets = 0;
        for (int i = 0; i < rarity.maxSockets(); i++) {
            if (random.nextDouble() < 0.5) sockets++;
        }
        return sockets;
    }

    /**
     * Build a plain-text description for the item tooltip (Path 1: stored once at creation).
     * Includes affix stat lines, socket slots, and stat requirements.
     */
    private String buildDescription(List<RolledAffix> affixes, String rarityId, int sockets, Nat20RarityDef rarity) {
        StringBuilder desc = new StringBuilder();

        // Affix lines
        for (RolledAffix affix : affixes) {
            Nat20AffixDef def = affixRegistry.get(affix.id());
            if (def == null) continue;
            AffixValueRange range = def.getValuesForRarity(rarityId);
            if (range == null) continue;
            double value = range.interpolate(affix.level());
            if ("MULTIPLICATIVE".equals(def.modifierType())) {
                desc.append(String.format("%.1f%% %s", value, def.targetStat()));
            } else {
                desc.append(String.format("+%.1f %s", value, def.targetStat()));
            }
            if (def.statScaling() != null) {
                desc.append(" (").append(def.statScaling().primary().name()).append(")");
            }
            desc.append("\n");
        }

        // Socket line
        if (sockets > 0) {
            desc.append("Sockets:");
            for (int i = 0; i < sockets; i++) {
                desc.append(" [Empty]");
            }
            desc.append("\n");
        }

        // Requirement line
        if (rarity.statRequirement() > 0 && !affixes.isEmpty()) {
            Nat20AffixDef firstDef = affixRegistry.get(affixes.getFirst().id());
            if (firstDef != null && firstDef.statScaling() != null) {
                desc.append("Requires: ").append(firstDef.statScaling().primary().name())
                    .append(" ").append(rarity.statRequirement()).append("\n");
            }
        }

        return desc.toString().stripTrailing();
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
