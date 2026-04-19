package com.chonbosmods.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Singleton manager for the per-player Character Sheet UI page.
 *
 * <p>Task 6 scaffolds the singleton + toggle plumbing only: the actual
 * {@code CharacterSheetPage} construction happens in Task 7. Task 7 will
 * swap {@link #openPages} to a {@code Map<UUID, CharacterSheetPage>} once
 * refresh hooks (Tasks 20, 21) need to retrieve the live page instance.
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

    private final Set<UUID> openPages = new HashSet<>();

    private CharacterSheetManager() {
    }

    public void toggle(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        UUID uuid = player.getUuid();
        if (openPages.contains(uuid)) {
            close(player);
        } else {
            open(player, ref, store, world);
        }
    }

    public void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        LOGGER.atInfo().log("CharacterSheet.open player=%s", player.getUuid());
        // Task 7 will construct + show CharacterSheetPage here.
        openPages.add(player.getUuid());
    }

    public void close(Player player) {
        LOGGER.atInfo().log("CharacterSheet.close player=%s", player.getUuid());
        openPages.remove(player.getUuid());
    }

    public boolean isOpen(UUID uuid) {
        return openPages.contains(uuid);
    }
}
