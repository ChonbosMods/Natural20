package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.ui.CharacterSheetManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Single entry point for awarding XP. Writes Nat20PlayerData, fires level-up
 * side effects (HP modifier, banner), and sends the transient "+N XP" message.
 */
public final class Nat20XpService {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|XpSvc");

    private final PlayerLevelHpSystem hpSystem;

    public Nat20XpService(PlayerLevelHpSystem hpSystem) {
        this.hpSystem = hpSystem;
    }

    public void award(Player player, Ref<EntityStore> playerRef, Store<EntityStore> store,
                      int amount, String reason) {
        if (amount <= 0) return;
        Nat20PlayerData data = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (data == null) {
            LOGGER.atWarning().log("No Nat20PlayerData for player %s, skipping XP award", playerRef);
            return;
        }
        int levelDelta = data.addXp(amount);

        // Transient text feedback (replace with floaty numbers in a later sprint).
        player.sendMessage(Message.raw("+" + amount + " XP"));

        LOGGER.atInfo().log("+%d XP (%s) -> total=%d lvl=%d (+%d)",
                amount, reason, data.getTotalXp(), data.getLevel(), levelDelta);

        if (levelDelta > 0) {
            hpSystem.updatePlayerMaxHp(playerRef, store);
            LevelUpBanner.show(player.getPlayerRef(), player.getDisplayName(), data.getLevel());
            // Re-render the Character Sheet if open (Task 20). Fires once per
            // level cross, not per XP grant, because addXp returned a positive
            // delta. Same world-thread context as the rest of award().
            CharacterSheetManager mgr = CharacterSheetManager.get();
            if (mgr != null) {
                mgr.onLevelUp(player.getPlayerRef().getUuid());
            }
        }
    }
}
