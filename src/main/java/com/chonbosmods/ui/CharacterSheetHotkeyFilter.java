package com.chonbosmods.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.CancelInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Intercepts the inbound {@link SyncInteractionChains} packet to bind the
 * keyboard "9" key (hotbar slot index 8) to opening the Character Sheet.
 *
 * <p>Hytale exposes no plugin-facing keybind API. The official workaround
 * (documented at {@code hytalemodding.dev/en/docs/guides/plugin/customizing-hotbar-actions})
 * hijacks the "swap to hotbar slot N" interaction chain that the client sends
 * whenever the player presses a number key. We watch for chains where:
 *
 * <ul>
 *   <li>{@code interactionType == InteractionType.SwapFrom}</li>
 *   <li>{@code data.targetSlot == 8} (the "9" key, 0-indexed)</li>
 *   <li>{@code initial == true} (first tick of the chain only)</li>
 * </ul>
 *
 * <p>For each match we strip the chain from the packet, send
 * {@link CancelInteractionChain} + {@link SetActiveSlot} to undo the client's
 * optimistic prediction (the client locally switched to slot 8 BEFORE sending),
 * and dispatch {@link CharacterSheetManager#toggle} on the world thread.
 *
 * <p>Other chains in the same packet are preserved by mutating
 * {@code syncPacket.updates} in place: a single packet often bundles a block
 * placement + an attack + a slot swap, and a blanket {@code return true} would
 * silently drop the legitimate interactions.
 *
 * <p>Threading: {@link #test} runs on the network thread. The packet writes
 * ({@code writeNoCache}) are safe there. ECS access (the page open) is hopped
 * to the world thread via {@code world.execute}. Per-player debounce uses
 * {@link ConcurrentHashMap} since multiple network threads may invoke the
 * filter concurrently.
 *
 * <p>Tradeoff: pressing "9" always opens the sheet, even if hotbar slot 9 holds
 * a usable item. Documented and accepted per design.
 */
public final class CharacterSheetHotkeyFilter implements PlayerPacketFilter {

    private static final HytaleLogger LOGGER =
            HytaleLogger.get("Nat20|CharacterSheetHotkey");

    /** Slot index 8 corresponds to the "9" key (hotbar is 0-indexed). */
    private static final int HOTKEY_SLOT = 8;

    /** Minimum milliseconds between two opens from the same player. */
    private static final long DEBOUNCE_MS = 350;

    private final ConcurrentMap<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!(packet instanceof SyncInteractionChains syncPacket)) {
            return false;
        }
        SyncInteractionChain[] updates = syncPacket.updates;
        if (updates == null || updates.length == 0) {
            return false;
        }

        SyncInteractionChain hijack = null;
        List<SyncInteractionChain> keep = new ArrayList<>(updates.length);
        for (SyncInteractionChain chain : updates) {
            if (hijack == null
                    && chain != null
                    && chain.interactionType == InteractionType.SwapFrom
                    && chain.data != null
                    && chain.data.targetSlot == HOTKEY_SLOT
                    && chain.initial) {
                hijack = chain;
            } else {
                keep.add(chain);
            }
        }
        if (hijack == null) {
            return false;
        }

        // Snapshot fields before world-thread dispatch: the chain object may be
        // mutated or recycled once the network thread returns from this method.
        final int originalSlot = hijack.activeHotbarSlot;
        final int chainId = hijack.chainId;
        final ForkedChainId forkedId = hijack.forkedId;
        final UUID uuid = playerRef.getUuid();

        long now = System.currentTimeMillis();
        Long prev = lastOpen.get(uuid);
        boolean debounced = prev != null && (now - prev) < DEBOUNCE_MS;

        // Always repair client prediction, even on debounce: the client has
        // already optimistically switched its visible slot, and we MUST snap
        // it back to the original slot to avoid a stuck half-state.
        repairClientPrediction(playerRef, originalSlot, chainId, forkedId);

        if (!debounced) {
            // Only refresh the debounce window on dispatched opens. If we wrote
            // `now` on every press (including debounced ones), held keys or
            // rapid taps would keep resetting the window and starve real opens.
            lastOpen.put(uuid, now);
            dispatchToggle(playerRef);
        } else {
            LOGGER.atFine().log("Debounced character sheet hotkey for %s", uuid);
        }

        if (keep.isEmpty()) {
            // Whole packet was just the hijacked chain: block it entirely.
            return true;
        }
        // Strip the hijacked chain from the packet and forward the remainder.
        syncPacket.updates = keep.toArray(new SyncInteractionChain[0]);
        return false;
    }

    /**
     * Send Cancel + SetActiveSlot to roll the client out of its optimistic
     * slot-8 prediction. Both packets are clientbound and safe to write from
     * the network thread.
     */
    private static void repairClientPrediction(PlayerRef playerRef,
                                                int originalSlot,
                                                int chainId,
                                                ForkedChainId forkedId) {
        try {
            playerRef.getPacketHandler().writeNoCache(
                    new CancelInteractionChain(chainId, forkedId));
            playerRef.getPacketHandler().writeNoCache(
                    new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, originalSlot));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to repair client prediction for player %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Hop to the world thread, re-validate the player ref, and hand off to
     * the manager. ECS / Store access asserts world-thread affinity, so this
     * must NOT be called inline from {@link #test}.
     */
    private static void dispatchToggle(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            CharacterSheetManager mgr = CharacterSheetManager.get();
            if (mgr == null) {
                LOGGER.atWarning().log(
                        "CharacterSheetManager singleton missing on hotkey toggle");
                return;
            }
            mgr.toggle(player, ref, store, world);
        });
    }
}
