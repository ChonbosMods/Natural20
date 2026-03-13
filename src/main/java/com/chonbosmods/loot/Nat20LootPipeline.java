package com.chonbosmods.loot;

import com.chonbosmods.loot.def.LootRuleEntry;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.google.common.flogger.FluentLogger;

import java.util.*;

public class Nat20LootPipeline {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;

    public Nat20LootPipeline(Nat20RarityRegistry rarityRegistry, Nat20AffixRegistry affixRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
    }

    /**
     * Generate loot data for an item.
     *
     * @param itemId the Hytale item ID (e.g., "Hytale:IronSword")
     * @param baseName the base item display name (e.g., "Iron Sword")
     * @param categoryKey the equipment category key (e.g., "melee_weapon")
     * @param random the random source
     * @return populated Nat20LootData, or null if no rarities are loaded
     */
    public Nat20LootData generate(String itemId, String baseName, String categoryKey, Random random) {
        // Step 1: Select rarity
        Nat20RarityDef rarity = rarityRegistry.selectRandom(random);
        if (rarity == null) {
            LOGGER.atWarning().log("No rarity definitions loaded, cannot generate loot for %s", itemId);
            return null;
        }

        // Step 2: Roll loot level
        double lootLevel = random.nextDouble();

        // Step 3: Roll affixes based on rarity loot rules
        List<RolledAffix> rolledAffixes = rollAffixes(rarity, categoryKey, random);

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

        // Step 6: Assemble Nat20LootData
        Nat20LootData data = new Nat20LootData();
        data.setVersion(Nat20LootData.CURRENT_VERSION);
        data.setRarity(rarity.id());
        data.setLootLevel(lootLevel);
        data.setAffixes(rolledAffixes);
        data.setSockets(sockets);
        data.setGems(new ArrayList<>());
        data.setGeneratedName(generatedName);
        data.setNamePrefixSource(prefixSource);
        data.setNameSuffixSource(suffixSource);

        LOGGER.atInfo().log("Generated loot: %s [%s] with %d affixes, %d sockets (lootLevel=%.2f)",
            generatedName, rarity.id(), rolledAffixes.size(), sockets, lootLevel);

        return data;
    }

    private List<RolledAffix> rollAffixes(Nat20RarityDef rarity, String categoryKey, Random random) {
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
                result.add(new RolledAffix(chosen.id(), random.nextDouble()));
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
     * Extract a display word from the affix's display name localization key.
     * e.g., "server.nat20.affix.violent" -> "Violent"
     */
    private String getAffixDisplayWord(Nat20AffixDef def) {
        String displayName = def.displayName();
        int lastDot = displayName.lastIndexOf('.');
        String word = (lastDot >= 0) ? displayName.substring(lastDot + 1) : displayName;
        if (!word.isEmpty()) {
            word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }
}
