package com.chonbosmods.dungeon;

import java.util.List;

public record BlockData(int width, int height, int depth, List<BlockEntry> blocks) {
    public record BlockEntry(int x, int y, int z, String id, int rot) {}
}
