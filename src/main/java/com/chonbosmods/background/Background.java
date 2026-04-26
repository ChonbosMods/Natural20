package com.chonbosmods.background;

import com.chonbosmods.stats.Stat;

import java.util.List;

/**
 * One of 15 character backgrounds the player picks via Jiub on first join.
 * Each background grants +3 to its primary stat, +3 to its secondary, and
 * delivers a Common-tier starter kit. The 15 entries cover every unordered
 * pair of the 6 stats (6 choose 2 = 15).
 */
public enum Background {
    SOLDIER     ("Soldier",     Stat.STR, Stat.CON, List.of(weapon("Weapon_Sword_Iron"),       armor("Weapon_Shield_Wood"))),
    SAILOR      ("Sailor",      Stat.STR, Stat.DEX, List.of(weapon("Weapon_Sword_Cutlass"),    armor("Armor_Cloth_Linen_Chest"))),
    VETERAN     ("Veteran",     Stat.STR, Stat.INT, List.of(weapon("Weapon_Battleaxe_Iron"),   armor("Armor_Leather_Medium_Chest"))),
    FOLK_HERO   ("Folk Hero",   Stat.STR, Stat.WIS, List.of(weapon("Weapon_Longsword_Iron"),   armor("Armor_Leather_Light_Chest"))),
    NOBLE       ("Noble",       Stat.STR, Stat.CHA, List.of(weapon("Weapon_Longsword_Iron"),   armor("Armor_Cloth_Silk_Chest"))),
    CRIMINAL    ("Criminal",    Stat.DEX, Stat.CON, List.of(weapon("Weapon_Daggers_Iron"),     armor("Armor_Leather_Light_Head"))),
    ARTISAN     ("Artisan",     Stat.DEX, Stat.INT, List.of(weapon("Weapon_Club_Iron"),        armor("Armor_Cloth_Cotton_Hands"))),
    SCOUT       ("Scout",       Stat.DEX, Stat.WIS, List.of(weapon("Weapon_Shortbow_Iron"),    arrows(), armor("Armor_Leather_Soft_Head"))),
    ENTERTAINER ("Entertainer", Stat.DEX, Stat.CHA, List.of(weapon("Weapon_Crossbow_Iron"),    arrows(), armor("Armor_Cloth_Cotton_Chest"))),
    HERMIT      ("Hermit",      Stat.CON, Stat.INT, List.of(weapon("Weapon_Staff_Bo_Bamboo"),  armor("Armor_Wool_Chest"))),
    OUTLANDER   ("Outlander",   Stat.CON, Stat.WIS, List.of(weapon("Weapon_Axe_Iron"),         armor("Armor_Leather_Heavy_Legs"))),
    URCHIN      ("Urchin",      Stat.CON, Stat.CHA, List.of(weapon("Weapon_Club_Iron"),        armor("Armor_Cloth_Cotton_Legs"))),
    SAGE        ("Sage",        Stat.INT, Stat.WIS, List.of(weapon("Weapon_Staff_Bone"),       armor("Armor_Cloth_Linen_Chest"))),
    CHARLATAN   ("Charlatan",   Stat.INT, Stat.CHA, List.of(weapon("Weapon_Staff_Onion"),      armor("Armor_Cloth_Cotton_Chest"))),
    ACOLYTE     ("Acolyte",     Stat.WIS, Stat.CHA, List.of(weapon("Weapon_Mace_Iron"),        armor("Armor_Wool_Head")));

    private static KitItem weapon(String itemId) { return new KitItem(itemId, 1, true); }
    private static KitItem armor(String itemId)  { return new KitItem(itemId, 1, true); }
    private static KitItem arrows() { return new KitItem("Weapon_Arrow_Crude", 75, false); }

    private final String displayName;
    private final Stat primary;
    private final Stat secondary;
    private final List<KitItem> kit;

    Background(String displayName, Stat primary, Stat secondary, List<KitItem> kit) {
        this.displayName = displayName;
        this.primary = primary;
        this.secondary = secondary;
        this.kit = List.copyOf(kit);
    }

    public String displayName() { return displayName; }
    public Stat primary() { return primary; }
    public Stat secondary() { return secondary; }
    public List<KitItem> kit() { return kit; }
}
