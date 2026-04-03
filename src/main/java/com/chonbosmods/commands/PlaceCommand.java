package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class PlaceCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> typeArg =
        withRequiredArg("type", "Settlement type: town, outpost, cart", ArgTypes.STRING);

    public PlaceCommand() {
        super("place", "Place a settlement structure at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String typeName = typeArg.get(context).toUpperCase();

        SettlementType type;
        try {
            type = SettlementType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            context.sendMessage(Message.raw("Unknown type: " + typeName + ". Use: town, outpost, cart"));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i blockPos = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        context.sendMessage(Message.raw("Placing " + type + " at " +
            blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + "..."));

        world.execute(() -> {
            Natural20.getInstance().getPlacer().place(
                world, blockPos, type, Rotation.None, store, new Random()
            );

            Vector3d origin = new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            // Generate a cell key for manual placements
            String cellKey = "manual_" + blockPos.getX() + "_" + blockPos.getZ();

            List<NpcRecord> npcRecords =
                Natural20.getInstance().getNpcManager()
                    .spawnSettlementNpcs(store, world, type, origin, cellKey);

            // Register in settlement registry
            SettlementRecord record = new SettlementRecord(
                cellKey, UUID.nameUUIDFromBytes(world.getName().getBytes()),
                blockPos.getX(), blockPos.getY(), blockPos.getZ(), type);
            record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode()));
            record.getNpcs().addAll(npcRecords);
            Natural20.getInstance().getSettlementRegistry().register(record);
        });
    }
}
