package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class GridPrefabCommand extends AbstractCommandCollection {

    public GridPrefabCommand() {
        super("gridprefab", "Dungeon prefab authoring commands");
        addSubCommand(new GridPrefabSaveCommand());
        addSubCommand(new GridPrefabSaveConnectorCommand());
        addSubCommand(new GridPrefabPreviewCommand());
        addSubCommand(new GridPrefabListCommand());
        addSubCommand(new GridPrefabValidateCommand());
    }
}
