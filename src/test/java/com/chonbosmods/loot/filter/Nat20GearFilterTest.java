package com.chonbosmods.loot.filter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class Nat20GearFilterTest {

    private static Nat20GearFilter filter;

    @BeforeAll
    static void load() {
        InputStream in = Nat20GearFilterTest.class.getResourceAsStream("/loot/gear_filter_test.json");
        filter = Nat20GearFilter.loadFrom(in);
    }

    @Test
    void blocklistRejects() {
        assertTrue(filter.resolveTier("Weapon_Banned_Item").isEmpty());
    }

    @Test
    void perItemOverrideWinsOverToken() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Cutlass").orElseThrow();
        assertEquals(new Nat20GearFilter.IlvlBand(8, 22), r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void allowlistSuppliesExplicitCategory() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Mod:Custom_Plasma").orElseThrow();
        assertEquals(new Nat20GearFilter.IlvlBand(22, 38), r.ilvlBand());
        assertEquals("ranged_weapon", r.category());
    }

    @Test
    void tokenMatchUsesPrefixInferredCategory() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Iron").orElseThrow();
        assertEquals(new Nat20GearFilter.IlvlBand(8, 26), r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void longestTokenWins() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Armor_Silversteel_Chest").orElseThrow();
        assertEquals(new Nat20GearFilter.IlvlBand(22, 38), r.ilvlBand());
    }

    @Test
    void noMatchRejects() {
        assertTrue(filter.resolveTier("Weapon_Mystery_Item").isEmpty());
    }

    @Test
    void shieldCategorisedAsArmorViaPrefixInference() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Shield_Tribal").orElseThrow();
        assertEquals("armor", r.category());
    }

    @Test
    void allowsIlvlInBand() {
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 8));
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 26));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 7));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 27));
    }

    @Test
    void failClosedOnParseError() {
        InputStream broken = new java.io.ByteArrayInputStream("{ not valid json".getBytes());
        Nat20GearFilter f = Nat20GearFilter.loadFrom(broken);
        assertFalse(f.isAllowed("Weapon_Sword_Iron", 10));
        assertTrue(f.resolveTier("Weapon_Sword_Iron").isEmpty());
    }

    @Test
    void blocklistBeatsAllowlist() {
        assertTrue(filter.resolveTier("Mod:Disputed_Item").isEmpty());
    }
}
