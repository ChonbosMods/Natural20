package com.chonbosmods.dungeon;

public record SocketEntry(int localX, int localZ, Face face, String type) {
    public static final String OPEN = "open";
    public static final String SEALED = "sealed";

    public boolean isOpen() {
        return OPEN.equals(type);
    }
}
