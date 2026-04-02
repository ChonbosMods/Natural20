package com.chonbosmods.dialogue;

public enum ValenceType {
    POSITIVE, NEGATIVE, NEUTRAL;

    public static ValenceType defaultValue() {
        return NEUTRAL;
    }

    /**
     * Parse from string, returning NEUTRAL for null or unrecognized values.
     */
    public static ValenceType fromString(String value) {
        if (value == null) return NEUTRAL;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
