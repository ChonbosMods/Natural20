package com.chonbosmods.cave;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent data for a discovered underground air pocket.
 * Serialized/deserialized by Gson: no Hytale codecs.
 */
public class CaveVoidRecord {

    private int centerX;
    private int centerY;
    private int centerZ;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int volume;
    private List<int[]> floorPositions;
    private long chunkKey;
    private boolean claimed;
    private String claimedBySettlement;

    /** No-arg constructor for Gson deserialization. */
    public CaveVoidRecord() {}

    public CaveVoidRecord(int centerX, int centerY, int centerZ,
                          int minX, int minY, int minZ,
                          int maxX, int maxY, int maxZ,
                          int volume, List<int[]> floorPositions,
                          long chunkKey) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.volume = volume;
        this.floorPositions = floorPositions;
        this.chunkKey = chunkKey;
    }

    public int getCenterX() { return centerX; }

    public void setCenterX(int centerX) { this.centerX = centerX; }

    public int getCenterY() { return centerY; }

    public void setCenterY(int centerY) { this.centerY = centerY; }

    public int getCenterZ() { return centerZ; }

    public void setCenterZ(int centerZ) { this.centerZ = centerZ; }

    public int getMinX() { return minX; }

    public void setMinX(int minX) { this.minX = minX; }

    public int getMinY() { return minY; }

    public void setMinY(int minY) { this.minY = minY; }

    public int getMinZ() { return minZ; }

    public void setMinZ(int minZ) { this.minZ = minZ; }

    public int getMaxX() { return maxX; }

    public void setMaxX(int maxX) { this.maxX = maxX; }

    public int getMaxY() { return maxY; }

    public void setMaxY(int maxY) { this.maxY = maxY; }

    public int getMaxZ() { return maxZ; }

    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    public int getVolume() { return volume; }

    public void setVolume(int volume) { this.volume = volume; }

    public List<int[]> getFloorPositions() { return floorPositions; }

    public void setFloorPositions(List<int[]> floorPositions) { this.floorPositions = floorPositions; }

    public long getChunkKey() { return chunkKey; }

    public void setChunkKey(long chunkKey) { this.chunkKey = chunkKey; }

    public boolean isClaimed() { return claimed; }

    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    public String getClaimedBySettlement() { return claimedBySettlement; }

    public void setClaimedBySettlement(String claimedBySettlement) { this.claimedBySettlement = claimedBySettlement; }

    /** Marks this void as claimed by the given settlement. */
    public void claim(String cellKey) {
        this.claimed = true;
        this.claimedBySettlement = cellKey;
    }

    /** Returns horizontal euclidean distance from center to the given point. */
    public int distanceTo(int x, int z) {
        int dx = centerX - x;
        int dz = centerZ - z;
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    /** Expands bounding box to include other, keeps higher volume, merges floor positions. */
    public void merge(CaveVoidRecord other) {
        this.minX = Math.min(this.minX, other.minX);
        this.minY = Math.min(this.minY, other.minY);
        this.minZ = Math.min(this.minZ, other.minZ);
        this.maxX = Math.max(this.maxX, other.maxX);
        this.maxY = Math.max(this.maxY, other.maxY);
        this.maxZ = Math.max(this.maxZ, other.maxZ);
        this.volume = Math.max(this.volume, other.volume);
        if (this.floorPositions == null) {
            this.floorPositions = new ArrayList<>();
        }
        if (other.floorPositions != null) {
            this.floorPositions.addAll(other.floorPositions);
        }
    }
}
