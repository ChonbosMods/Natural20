package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.def.Nat20AffixDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Nat20AffixRegistryTest {

    @Test
    void parsedAffixIsIlvlScalableByDefault() {
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadAll(null);

        // nat20:rally is bundled and has no IlvlScalable field; should default to true
        Nat20AffixDef def = reg.get("nat20:rally");
        assertNotNull(def, "nat20:rally must load from classpath");
        assertTrue(def.ilvlScalable(),
            "absent IlvlScalable field should default to true");
    }

    @Test
    void parsedAffixHonorsExplicitIlvlScalableFalse(@TempDir Path tempDir) throws IOException {
        // Write a test JSON with IlvlScalable: false into the override dir.
        String json = """
            {
              "Id": "nat20:test_static",
              "Frequency": 1,
              "Type": "STAT",
              "DisplayName": "x",
              "NamePosition": "PREFIX",
              "Categories": ["melee_weapon"],
              "TargetStat": "Test",
              "ModifierType": "ADDITIVE",
              "IlvlScalable": false,
              "ValuesPerRarity": {
                "Rare": { "Min": 1.0, "Max": 1.0 }
              }
            }
            """;
        Files.writeString(tempDir.resolve("test_static.json"), json);

        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadAll(tempDir);

        Nat20AffixDef def = reg.get("nat20:test_static");
        assertNotNull(def, "test_static must load from override directory");
        assertFalse(def.ilvlScalable(),
            "explicit IlvlScalable: false must round-trip through parser");
    }

    @Test
    void scoreAffixesExcludedFromCommonAndUncommonPools() {
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadAll(null);

        String[] stats = {"str", "dex", "con", "int", "wis", "cha"};
        String[] excludedRarities = {"Common", "Uncommon"};
        String[] categories = {"melee_weapon", "ranged_weapon", "armor", "tool"};

        for (String stat : stats) {
            String id = "nat20:score_" + stat;
            for (String rarity : excludedRarities) {
                for (String category : categories) {
                    List<Nat20AffixDef> pool = reg.getPool(AffixType.STAT, category, rarity);
                    assertFalse(pool.stream().anyMatch(d -> d.id().equals(id)),
                        id + " must not appear in pool: rarity=" + rarity + ", category=" + category);
                }
            }
        }
    }

    @Test
    void scoreAffixesAppearInRareEpicLegendaryPools() {
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadAll(null);

        String[] stats = {"str", "dex", "con", "int", "wis", "cha"};
        String[] includedRarities = {"Rare", "Epic", "Legendary"};

        for (String stat : stats) {
            String id = "nat20:score_" + stat;
            for (String rarity : includedRarities) {
                // Score affixes have all four categories; check at least one
                List<Nat20AffixDef> pool = reg.getPool(AffixType.STAT, "melee_weapon", rarity);
                assertTrue(pool.stream().anyMatch(d -> d.id().equals(id)),
                    id + " should appear in pool: rarity=" + rarity);
            }
        }
    }

    @Test
    void scoreAffixesAreNotIlvlScalable() {
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadAll(null);

        for (String stat : new String[]{"str", "dex", "con", "int", "wis", "cha"}) {
            Nat20AffixDef def = reg.get("nat20:score_" + stat);
            assertNotNull(def, "score_" + stat + " must load");
            assertFalse(def.ilvlScalable(),
                "score_" + stat + " must have IlvlScalable: false");
        }
    }
}
