package com.chonbosmods.loot;

public enum EquipmentCategory {
    MELEE_WEAPON("melee_weapon"),
    RANGED_WEAPON("ranged_weapon"),
    ARMOR("armor"),
    TOOL("tool"),
    UTILITY("utility");

    private final String key;

    EquipmentCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static EquipmentCategory fromKey(String key) {
        for (var cat : values()) {
            if (cat.key.equals(key)) return cat;
        }
        return null;
    }
}
