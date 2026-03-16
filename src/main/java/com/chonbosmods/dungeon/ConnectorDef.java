package com.chonbosmods.dungeon;

import java.util.List;

public record ConnectorDef(
    String name,
    String prefabKey,
    List<String> tags,
    double weight
) {}
