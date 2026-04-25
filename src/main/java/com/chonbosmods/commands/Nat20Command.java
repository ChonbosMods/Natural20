package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class Nat20Command extends AbstractCommandCollection {

    public Nat20Command() {
        super("nat20", "Natural 20 commands");
        // Spawning
        addSubCommand(new SpawnNpcCommand());
        addSubCommand(new SpawnGroupCommand());
        addSubCommand(new SpawnTierCommand());
        addSubCommand(new KillNpcCommand());

        // Player tuning
        addSubCommand(new SetStatsCommand());
        addSubCommand(new SetManaCommand());
        addSubCommand(new XpAddCommand());
        addSubCommand(new XpSetCommand());
        addSubCommand(new LevelSetCommand());
        addSubCommand(new StatsCommand());
        addSubCommand(new CharacterCommand());

        // Loot inspection
        addSubCommand(new LootCommand());

        // Quest tooling
        addSubCommand(new QuestTpCommand());

        // World inspection
        addSubCommand(new SettlementsCommand());
    }
}
