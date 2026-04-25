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
    void blocklist_rejects() {
        assertTrue(filter.resolveTier("Weapon_Banned_Item").isEmpty());
    }

    @Test
    void per_item_override_wins_over_token() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Cutlass").orElseThrow();
        assertArrayEquals(new int[]{8, 22}, r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void allowlist_supplies_explicit_category() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Mod:Custom_Plasma").orElseThrow();
        assertArrayEquals(new int[]{22, 38}, r.ilvlBand());
        assertEquals("ranged_weapon", r.category());
    }

    @Test
    void token_match_uses_prefix_inferred_category() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Iron").orElseThrow();
        assertArrayEquals(new int[]{8, 26}, r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void longest_token_wins() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Armor_Silversteel_Chest").orElseThrow();
        assertArrayEquals(new int[]{22, 38}, r.ilvlBand());
    }

    @Test
    void no_match_rejects() {
        assertTrue(filter.resolveTier("Weapon_Mystery_Item").isEmpty());
    }

    @Test
    void shield_categorised_as_armor_via_prefix_inference() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Shield_Tribal").orElseThrow();
        assertEquals("armor", r.category());
    }

    @Test
    void allows_ilvl_in_band() {
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 8));
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 26));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 7));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 27));
    }

    @Test
    void fail_closed_on_parse_error() {
        InputStream broken = new java.io.ByteArrayInputStream("{ not valid json".getBytes());
        Nat20GearFilter f = Nat20GearFilter.loadFrom(broken);
        assertFalse(f.isAllowed("Weapon_Sword_Iron", 10));
        assertTrue(f.resolveTier("Weapon_Sword_Iron").isEmpty());
    }
}
