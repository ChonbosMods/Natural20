package com.chonbosmods.loot;

public enum GemPurity {
    FLAWED("Flawed"),
    STANDARD("Standard"),
    PRISTINE("Pristine");

    private final String key;

    GemPurity(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static GemPurity fromKey(String key) {
        for (var p : values()) {
            if (p.key.equals(key)) return p;
        }
        return STANDARD;
    }
}
