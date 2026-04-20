package com.chonbosmods.commands;

import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Random;

/**
 * Paste any prefab via the Nat20 marker-aware pipeline at the player's feet. Reports
 * marker counts in chat so authors can verify a freshly-saved prefab contains the
 * expected Nat20_* markers. Unlike {@code /nat20 place}, this accepts an arbitrary
 * prefab key rather than a {@code SettlementType}.
 */
public class PlaceMarkerPrefabCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PlaceMarker");

    private final RequiredArg<String> prefabKeyArg =
        withRequiredArg("prefabKey", "Prefab key e.g. Nat20/tree1", ArgTypes.STRING);

    public PlaceMarkerPrefabCommand() {
        super("placemarker", "Paste a Nat20 prefab at your feet via the marker-aware pipeline");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String key = prefabKeyArg.get(ctx);

        Path path = PrefabStore.get().findAssetPrefabPath(key);
        if (path == null) {
            ctx.sendMessage(Message.raw("Prefab not found: " + key));
            return;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(path);
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Failed to load buffer: " + e.getMessage()));
            LOGGER.atWarning().withCause(e).log("Buffer load failed for %s", key);
            return;
        }

        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        if (tf == null || tf.getPosition() == null) {
            ctx.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        Vector3d pos = tf.getPosition();
        Vector3i anchorPos = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        ctx.sendMessage(Message.raw("Pasting " + key + " at (" +
            anchorPos.getX() + ", " + anchorPos.getY() + ", " + anchorPos.getZ() + ")..."));

        Nat20PrefabPaster.paste(buffer, world, anchorPos, Rotation.None, new Random(), store)
            .whenComplete((placed, error) -> {
                if (error != null) {
                    ctx.sendMessage(Message.raw("Paste failed: " + error.getMessage()));
                    LOGGER.atWarning().withCause(error).log("Paste failed for %s", key);
                    return;
                }
                if (placed == null) {
                    ctx.sendMessage(Message.raw("Paste returned no markers (see server log)."));
                    return;
                }
                report(ctx, placed);
            });
    }

    private static void report(CommandContext ctx, PlacedMarkers placed) {
        ctx.sendMessage(Message.raw(String.format(
            "Placed. Anchor=%s Direction=%s NPC=%d MobGroup=%d Chest=%d",
            placed.anchorWorld(),
            placed.directionVectorWorld(),
            placed.npcSpawnsWorld().size(),
            placed.mobGroupSpawnsWorld().size(),
            placed.chestSpawnsWorld().size())));
    }
}
