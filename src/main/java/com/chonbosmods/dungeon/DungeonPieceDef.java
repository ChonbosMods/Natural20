package com.chonbosmods.dungeon;

import java.util.List;

public record DungeonPieceDef(
    String name,
    String prefabKey,
    int gridWidth,
    int gridHeight,
    int gridDepth,
    boolean rotatable,
    List<SocketEntry> sockets,
    List<String> tags,
    double weight
) {}
