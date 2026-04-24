package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

public class SpawnTierCommand extends AbstractPlayerCommand {

    private static final Map<String, String> MOB_ALIASES = Map.of(
        "goblin",   "Goblin_Scrapper",
        "trork",    "Trork_Warrior",
        "skeleton", "Skeleton_Sand_Soldier"
    );

    private final RequiredArg<String> mobArg =
        withRequiredArg("mob", "mob type: goblin | trork | skeleton", ArgTypes.STRING);
    private final RequiredArg<String> roleArg =
        withRequiredArg("role", "role: champion | boss | dungeon_boss", ArgTypes.STRING);
    private final RequiredArg<String> difficultyArg =
        withRequiredArg("difficulty", "difficulty: uncommon | rare | epic | legendary", ArgTypes.STRING);
    private final OptionalArg<Integer> countArg =
        withOptionalArg("champions", "champion count (3-7, default from config)", ArgTypes.INTEGER);

    public SpawnTierCommand() {
        super("spawntier", "Spawn a mob group with forced difficulty tier");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String role = MOB_ALIASES.get(mobArg.get(context).toLowerCase());
        if (role == null) {
            context.sendMessage(Message.raw("Unknown mob. Use: goblin | trork | skeleton"));
            return;
        }

        String roleKind = roleArg.get(context).toLowerCase();
        boolean isDungeonBoss = "dungeon_boss".equals(roleKind);
        if (!isDungeonBoss && !"champion".equals(roleKind) && !"boss".equals(roleKind)) {
            context.sendMessage(Message.raw("Unknown role. Use: champion | boss | dungeon_boss"));
            return;
        }

        DifficultyTier forced = DifficultyTier.fromName(difficultyArg.get(context));
        if (forced == null) {
            context.sendMessage(Message.raw("Unknown difficulty. Use: uncommon | rare | epic | legendary"));
            return;
        }

        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        if (tf == null) {
            context.sendMessage(Message.raw("No position."));
            return;
        }
        Vector3d pos = tf.getPosition();
        Vector3d anchor = new Vector3d(pos.getX() + 4, pos.getY(), pos.getZ());

        var config = Natural20.getInstance().getScalingConfig();
        int count;
        if (context.provided(countArg)) {
            count = countArg.get(context);
        } else {
            double anchorDist = Math.sqrt(anchor.getX() * anchor.getX() + anchor.getZ() * anchor.getZ());
            int anchorAreaLevel = config.areaLevelForDistance(anchorDist);
            count = config.championCountFor(anchorAreaLevel, java.util.concurrent.ThreadLocalRandom.current());
        }

        Nat20MobGroupSpawner spawner = Natural20.getInstance().getMobGroupSpawner();
        Nat20MobGroupSpawner.SpawnResult r = spawner.spawnGroup(
                world, role, count, anchor, forced, isDungeonBoss);

        if (r == null) {
            context.sendMessage(Message.raw("Spawn failed."));
            return;
        }
        context.sendMessage(Message.raw(String.format(
                "Spawned %dx %s + 1 %s (group=%s, boss=%s)",
                r.champions().size(), role,
                isDungeonBoss ? "dungeon boss" : "boss",
                r.groupDifficulty(), r.bossDifficulty())));
    }
}
