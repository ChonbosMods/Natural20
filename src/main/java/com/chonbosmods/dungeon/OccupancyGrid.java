package com.chonbosmods.dungeon;

import java.util.HashMap;
import java.util.Map;

public class OccupancyGrid {

    private final Map<Long, Boolean> cells = new HashMap<>();

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
    }

    public boolean isFree(int x, int y, int z) {
        return !cells.containsKey(key(x, y, z));
    }

    public void claim(int x, int y, int z) {
        cells.put(key(x, y, z), true);
    }

    public boolean canPlace(int originX, int originY, int originZ,
                             int gridW, int gridH, int gridD) {
        for (int dx = 0; dx < gridW; dx++)
            for (int dy = 0; dy < gridH; dy++)
                for (int dz = 0; dz < gridD; dz++)
                    if (!isFree(originX + dx, originY + dy, originZ + dz))
                        return false;
        return true;
    }

    public void claimAll(int originX, int originY, int originZ,
                          int gridW, int gridH, int gridD) {
        for (int dx = 0; dx < gridW; dx++)
            for (int dy = 0; dy < gridH; dy++)
                for (int dz = 0; dz < gridD; dz++)
                    claim(originX + dx, originY + dy, originZ + dz);
    }
}
