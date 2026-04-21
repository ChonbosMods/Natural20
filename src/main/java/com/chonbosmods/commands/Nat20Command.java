package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class Nat20Command extends AbstractCommandCollection {

    public Nat20Command() {
        super("nat20", "Natural 20 commands");
        addSubCommand(new PlaceCommand());
        addSubCommand(new SpawnNpcCommand());
        addSubCommand(new SpawnGroupCommand());
        addSubCommand(new SpawnTierCommand());
        addSubCommand(new BlockNamesCommand());
        addSubCommand(new ModelsCommand());
        addSubCommand(new RolesCommand());
        addSubCommand(new HostileDumpCommand());
        addSubCommand(new ThemeHereCommand());
        addSubCommand(new ZoneDumpCommand());
        addSubCommand(new BiomeDumpCommand());
        addSubCommand(new ProbeCommand());
        addSubCommand(new LootCommand());
        addSubCommand(new SettlementsCommand());
        addSubCommand(new KillNpcCommand());
        addSubCommand(new PlacePrefabsCommand());
        addSubCommand(new PlaceMarkerPrefabCommand());
        addSubCommand(new PlaceAllCommand());
        addSubCommand(new PlaceNorthCommand());
        addSubCommand(new PlaceSouthCommand());
        addSubCommand(new PlaceEastCommand());
        addSubCommand(new PlaceWestCommand());
        addSubCommand(new SyncPrefabsCommand());
        addSubCommand(new PlacePiecesCommand());
        addSubCommand(new WhereAmICommand());
        addSubCommand(new CaveVoidsCommand());
        addSubCommand(new WaypointTestCommand());
        addSubCommand(new ItemNamesCommand());
        addSubCommand(new EventTitleTestCommand());
        addSubCommand(new QuestTpCommand());
        addSubCommand(new KillMobsQuestTpCommand());
        addSubCommand(new SetStatsCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new CombatTestCommand());
        addSubCommand(new TestWeaponCommand());
        addSubCommand(new TestArmorCommand());
        addSubCommand(new TestCritWeaponCommand());
        addSubCommand(new TestToolCommand());
        addSubCommand(new TestDelveIndestructibleCommand());
        addSubCommand(new TestDelveFortifiedCommand());
        addSubCommand(new TestResonanceTelekinesisCommand());
        addSubCommand(new SetManaCommand());
        addSubCommand(new XpAddCommand());
        addSubCommand(new XpSetCommand());
        addSubCommand(new LevelSetCommand());
        addSubCommand(new StatsCommand());
        addSubCommand(new CharacterCommand());
    }
}
