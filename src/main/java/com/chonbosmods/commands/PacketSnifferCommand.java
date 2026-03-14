package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Debug command: /nat20 sniff
 *
 * Toggles a packet sniffer that logs ALL inbound packet class names to chat.
 * Use this to discover what packets are sent when pressing Tab (inventory key).
 */
public class PacketSnifferCommand extends AbstractPlayerCommand {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, Boolean> activeSniffers = new ConcurrentHashMap<>();
    private static final AtomicReference<PacketFilter> registeredFilter = new AtomicReference<>();

    public PacketSnifferCommand() {
        super("sniff", "Toggle packet sniffer (logs all inbound packets to chat)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        UUID uuid = playerRef.getUuid();

        if (activeSniffers.containsKey(uuid)) {
            activeSniffers.remove(uuid);
            // Deregister filter if no active sniffers remain
            if (activeSniffers.isEmpty()) {
                PacketFilter filter = registeredFilter.getAndSet(null);
                if (filter != null) {
                    PacketAdapters.deregisterInbound(filter);
                }
            }
            context.sendMessage(Message.raw("Packet sniffer OFF"));
            return;
        }

        // Register filter if first sniffer
        if (activeSniffers.isEmpty()) {
            PacketFilter filter = PacketAdapters.registerInbound((PlayerPacketFilter) (pRef, packet) -> {
                UUID snifferUuid = pRef.getUuid();
                if (!activeSniffers.containsKey(snifferUuid)) {
                    return false;
                }

                String className = packet.getClass().getSimpleName();

                // Skip noisy movement/position packets
                if ("ClientMovement".equals(className) ||
                    "ClientPosition".equals(className) ||
                    "ClientInput".equals(className)) {
                    return false;
                }

                // Log to console
                LOGGER.atInfo().log("[SNIFF] %s from %s", className, pRef.getUsername());

                // Also send to player chat
                pRef.sendMessage(Message.raw("[SNIFF] " + className).color("#88ff88"));

                // Extra detail for SyncInteractionChains
                if (packet instanceof SyncInteractionChains chains) {
                    if (chains.updates != null) {
                        for (SyncInteractionChain chain : chains.updates) {
                            if (chain != null) {
                                String detail = "  -> InteractionType: " + chain.interactionType;
                                pRef.sendMessage(Message.raw(detail).color("#ffff88"));
                                LOGGER.atInfo().log("[SNIFF] %s", detail);
                            }
                        }
                    }
                }

                return false; // Never consume packets
            });
            registeredFilter.set(filter);
        }

        activeSniffers.put(uuid, true);
        context.sendMessage(Message.raw("Packet sniffer ON: press Tab and watch chat. Run again to stop."));
    }

    /**
     * Call from plugin shutdown to clean up.
     */
    public static void cleanup() {
        activeSniffers.clear();
        PacketFilter filter = registeredFilter.getAndSet(null);
        if (filter != null) {
            PacketAdapters.deregisterInbound(filter);
        }
    }
}
