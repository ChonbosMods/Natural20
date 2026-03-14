package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Intercepts the Tab key (ClientOpenWindow packet) and opens our custom
 * inventory page instead of the vanilla inventory.
 */
public class Nat20InventoryInterceptor {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private PacketFilter filter;

    public void register() {
        filter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onPacket);
        LOGGER.atInfo().log("Inventory interceptor registered");
    }

    public void deregister() {
        if (filter != null) {
            PacketAdapters.deregisterInbound(filter);
            filter = null;
            LOGGER.atInfo().log("Inventory interceptor deregistered");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean onPacket(PlayerRef playerRef, Packet packet) {
        String className = packet.getClass().getSimpleName();

        if (!"ClientOpenWindow".equals(className)) {
            return false;
        }

        LOGGER.atInfo().log("Intercepted ClientOpenWindow from %s", playerRef.getUsername());

        // Bridge from network thread to ECS context via PlayerRef → Ref → Store → World
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                LOGGER.atWarning().log("Invalid ref for %s, passing through", playerRef.getUsername());
                return false;
            }

            Store<EntityStore> store = ref.getStore();
            World world = ((EntityStore) store.getExternalData()).getWorld();
            if (world == null) {
                LOGGER.atWarning().log("Null world for %s, passing through", playerRef.getUsername());
                return false;
            }

            // Schedule page open on the ECS thread
            world.execute(() -> {
                try {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        LOGGER.atWarning().log("Null player in world.execute for %s", playerRef.getUsername());
                        return;
                    }

                    Nat20InventoryPage page = new Nat20InventoryPage(playerRef);
                    player.getPageManager().openCustomPage(ref, store, page);
                    LOGGER.atInfo().log("Opened custom inventory for %s", playerRef.getUsername());
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to open custom inventory for %s", playerRef.getUsername());
                }
            });
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to bridge to ECS context for %s", playerRef.getUsername());
            return false;
        }

        // Block vanilla inventory from opening
        return true;
    }
}
