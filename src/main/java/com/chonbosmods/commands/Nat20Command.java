package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class Nat20Command extends AbstractCommandCollection {

    public Nat20Command() {
        super("nat20", "Natural 20 commands");
        addSubCommand(new PlaceCommand());
        addSubCommand(new SpawnNpcCommand());
        addSubCommand(new BlockNamesCommand());
        addSubCommand(new ModelsCommand());
        addSubCommand(new RolesCommand());
        addSubCommand(new ProbeCommand());
        addSubCommand(new LootCommand());
        addSubCommand(new LootInspectCommand());
        addSubCommand(new TooltipTestCommand());
        addSubCommand(new CompareTestCommand());
        addSubCommand(new SettlementsCommand());
        addSubCommand(new KillNpcCommand());
    }
}
