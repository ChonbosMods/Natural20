package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.def.Nat20AffixDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
