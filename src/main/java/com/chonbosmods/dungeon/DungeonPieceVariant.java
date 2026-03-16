package com.chonbosmods.dungeon;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DungeonPieceVariant {

    private final DungeonPieceDef baseDef;
    private final int rotation;
    private final int gridWidth;
    private final int gridDepth;
    private final List<SocketEntry> sockets;
    private final String socketHash;

    public DungeonPieceVariant(DungeonPieceDef baseDef, int rotation) {
        this.baseDef = baseDef;
        this.rotation = rotation;

        if (rotation % 2 == 0) {
            this.gridWidth = baseDef.gridWidth();
            this.gridDepth = baseDef.gridDepth();
        } else {
            this.gridWidth = baseDef.gridDepth();
            this.gridDepth = baseDef.gridWidth();
        }

        this.sockets = new ArrayList<>();
        for (SocketEntry s : baseDef.sockets()) {
            sockets.add(transformSocket(s, rotation, baseDef.gridWidth(), baseDef.gridDepth()));
        }

        this.socketHash = computeHash();
    }

    private static SocketEntry transformSocket(SocketEntry s, int rotation,
                                                int origWidth, int origDepth) {
        int lx = s.localX();
        int lz = s.localZ();
        return switch (rotation) {
            case 0 -> s;
            case 1 -> new SocketEntry(
                lz, (origWidth - 1) - lx,
                s.face().rotateCW(1), s.type());
            case 2 -> new SocketEntry(
                (origWidth - 1) - lx, (origDepth - 1) - lz,
                s.face().rotateCW(2), s.type());
            case 3 -> new SocketEntry(
                (origDepth - 1) - lz, lx,
                s.face().rotateCW(3), s.type());
            default -> throw new IllegalArgumentException("Invalid rotation: " + rotation);
        };
    }

    private String computeHash() {
        List<SocketEntry> sorted = new ArrayList<>(sockets);
        sorted.sort(Comparator.comparingInt(SocketEntry::localX)
            .thenComparingInt(SocketEntry::localZ)
            .thenComparing(SocketEntry::face)
            .thenComparing(SocketEntry::type));
        StringBuilder sb = new StringBuilder();
        sb.append(gridWidth).append('x').append(gridDepth);
        for (SocketEntry s : sorted) {
            sb.append('|').append(s.localX()).append(',').append(s.localZ())
              .append(',').append(s.face()).append(',').append(s.type());
        }
        return sb.toString();
    }

    public Rotation toSdkRotation() {
        return switch (rotation) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

    public DungeonPieceDef getBaseDef() { return baseDef; }
    public int getRotation() { return rotation; }
    public int getGridWidth() { return gridWidth; }
    public int getGridDepth() { return gridDepth; }
    public int getGridHeight() { return baseDef.gridHeight(); }
    public List<SocketEntry> getSockets() { return sockets; }
    public String getSocketHash() { return socketHash; }
    public String getName() { return baseDef.name(); }
    public String getPrefabKey() { return baseDef.prefabKey(); }
    public double getWeight() { return baseDef.weight(); }
    public List<String> getTags() { return baseDef.tags(); }
}
