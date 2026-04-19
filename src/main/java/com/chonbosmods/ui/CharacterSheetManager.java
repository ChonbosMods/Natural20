package com.chonbosmods.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Singleton manager for the per-player Character Sheet UI page.
 *
 * <p>Tracks one {@link CharacterSheetPage} per player. The map is the source
 * of truth for "is open"; the page's {@link CharacterSheetPage#onDismiss}
 * calls back into {@link #handlePageClosed} to drop the entry when the
 * client closes the page (e.g. ESC). Manager-initiated {@link #close} both
 * dismisses the client page AND removes the entry, and is idempotent.
 */
public final class CharacterSheetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CharacterSheet");

    private static CharacterSheetManager INSTANCE;

    public static CharacterSheetManager get() {
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new CharacterSheetManager();
    }

    private final Map<UUID, CharacterSheetPage> openPages = new HashMap<>();

    private CharacterSheetManager() {
    }

    public void toggle(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        UUID uuid = player.getUuid();
        if (openPages.containsKey(uuid)) {
            close(player);
        } else {
            open(player, ref, store, world);
        }
    }

    public void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        UUID uuid = player.getUuid();
        if (openPages.containsKey(uuid)) {
            LOGGER.atFine().log("CharacterSheet.open already open player=%s", uuid);
            return;
        }
        LOGGER.atInfo().log("CharacterSheet.open player=%s", uuid);
        CharacterSheetPage page = new CharacterSheetPage(player.getPlayerRef());
        openPages.put(uuid, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    public void close(Player player) {
        UUID uuid = player.getUuid();
        CharacterSheetPage page = openPages.remove(uuid);
        if (page == null) return;
        LOGGER.atInfo().log("CharacterSheet.close player=%s", uuid);
        page.closePage();
    }

    /**
     * Called by {@link CharacterSheetPage#onDismiss} when the client closes the
     * page (e.g. ESC). Drops the map entry without re-dismissing on the client.
     */
    public void handlePageClosed(UUID uuid) {
        if (openPages.remove(uuid) != null) {
            LOGGER.atFine().log("CharacterSheet.handlePageClosed player=%s", uuid);
        }
    }

    public boolean isOpen(UUID uuid) {
        return openPages.containsKey(uuid);
    }

    /**
     * Called by {@link com.chonbosmods.progression.Nat20XpService#award} after a
     * level cross. Re-renders the character sheet if open for that player so the
     * XP bar, level label, Unspent Points banner, and ability +/- enable states
     * pick up the new applied snapshot. The page's {@code pendingDelta} preview
     * is preserved across rebuild by design (Task 20).
     */
    public void onLevelUp(UUID playerUuid) {
        CharacterSheetPage page = openPages.get(playerUuid);
        if (page == null) return;
        page.refresh();
    }

    /**
     * Called by {@link com.chonbosmods.quest.QuestStateManager#markQuestCompleted}
     * after the persist. Re-renders the quest list so the just-completed quest
     * leaves the Active tab and is rendered at index 0 of the Completed tab on
     * the next render of that tab (Task 21).
     */
    public void onQuestCompleted(UUID playerUuid) {
        CharacterSheetPage page = openPages.get(playerUuid);
        if (page == null) return;
        page.refresh();
    }
}
