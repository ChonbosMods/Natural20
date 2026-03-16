package com.chonbosmods.dungeon;

public enum Face {
    NORTH, SOUTH, EAST, WEST;

    public Face opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public Face rotateCW(int quarterTurns) {
        Face[] order = {NORTH, EAST, SOUTH, WEST};
        int idx = switch (this) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
        };
        return order[Math.floorMod(idx + quarterTurns, 4)];
    }

    public int dx() {
        return switch (this) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public int dz() {
        return switch (this) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }
}
