package com.chonbosmods.stats;

public enum Stat {
    STR("#FF4444", "Strength"),
    DEX("#44DD66", "Dexterity"),
    CON("#DD8833", "Constitution"),
    INT("#4488FF", "Intelligence"),
    WIS("#CCCCDD", "Wisdom"),
    CHA("#BB66FF", "Charisma");

    private final String color;
    private final String fullName;

    Stat(String color, String fullName) {
        this.color = color;
        this.fullName = fullName;
    }

    public int index() {
        return ordinal();
    }

    public String color() {
        return color;
    }

    public String fullName() {
        return fullName;
    }

    public String bracket() {
        return "[" + name() + "]";
    }

    /** Resolve a stat color from a statPrefix string like "CHA" or "[CHA]". */
    public static String colorFor(String statPrefix) {
        if (statPrefix == null) return "#FF8888";
        String clean = statPrefix.replace("[", "").replace("]", "").trim().toUpperCase();
        try {
            return valueOf(clean).color;
        } catch (IllegalArgumentException e) {
            return "#FF8888";
        }
    }
}
