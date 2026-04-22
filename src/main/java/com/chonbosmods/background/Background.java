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
    SOLDIER     ("Soldier",     Stat.STR, Stat.CON, List.of(weapon("Weapon_Sword_Crude"), weapon("Weapon_Shield_Wood"))),
    SAILOR      ("Sailor",      Stat.STR, Stat.DEX, List.of(weapon("Weapon_Spear_Crude"))),
    VETERAN     ("Veteran",     Stat.STR, Stat.INT, List.of(weapon("Weapon_Battleaxe_Crude"))),
    FOLK_HERO   ("Folk Hero",   Stat.STR, Stat.WIS, List.of(weapon("Weapon_Longsword_Crude"))),
    NOBLE       ("Noble",       Stat.STR, Stat.CHA, List.of(weapon("Weapon_Longsword_Crude"))),
    CRIMINAL    ("Criminal",    Stat.DEX, Stat.CON, List.of(weapon("Weapon_Daggers_Crude"))),
    ARTISAN     ("Artisan",     Stat.DEX, Stat.INT, List.of(weapon("Weapon_Club_Crude"))),
    SCOUT       ("Scout",       Stat.DEX, Stat.WIS, List.of(weapon("Weapon_Shortbow_Crude"), arrows())),
    ENTERTAINER ("Entertainer", Stat.DEX, Stat.CHA, List.of(weapon("Weapon_Crossbow_Iron"),  arrows())),
    HERMIT      ("Hermit",      Stat.CON, Stat.INT, List.of(weapon("Weapon_Staff_Bo_Bamboo"))),
    OUTLANDER   ("Outlander",   Stat.CON, Stat.WIS, List.of(weapon("Weapon_Axe_Crude"))),
    URCHIN      ("Urchin",      Stat.CON, Stat.CHA, List.of(weapon("Weapon_Club_Crude"))),
    SAGE        ("Sage",        Stat.INT, Stat.WIS, List.of(weapon("Weapon_Staff_Bone"))),
    CHARLATAN   ("Charlatan",   Stat.INT, Stat.CHA, List.of(weapon("Weapon_Staff_Onion"))),
    ACOLYTE     ("Acolyte",     Stat.WIS, Stat.CHA, List.of(weapon("Weapon_Mace_Crude")));

    private static KitItem weapon(String itemId) { return new KitItem(itemId, 1, true); }
    private static KitItem arrows() { return new KitItem("Weapon_Arrow_Crude", 50, false); }

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
