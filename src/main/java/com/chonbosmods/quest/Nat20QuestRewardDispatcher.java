package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.progression.Nat20XpMath;
import com.chonbosmods.progression.Nat20XpService;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.google.gson.Gson;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Shared per-phase reward dispatcher for multi-accepter quests (2026-04-22
 * Option B pivot). All four objective-completion sites (KILL/COLLECT/FETCH/TALK)
 * plus the CONTINUE_QUEST item-dispense path route through the helpers here
 * instead of awarding only to the triggering player.
 *
 * <p>Rules:
 * <ul>
 *   <li>XP: every online accepter not in the phase's missed-set gets full
 *       {@link Nat20XpMath#questPhaseXp} for their own level.</li>
 *   <li>Items: the triggering player gets the pre-rolled reward (preserved
 *       single-player path); every OTHER online non-missed accepter gets a
 *       fresh reroll via {@link AffixRewardRoller#roll} at the phase's stored
 *       tier + ilvl so party members don't all get identical items.</li>
 *   <li>Offline accepters and missed accepters are skipped silently.</li>
 *   <li>Legacy quests (rewardTier == null on {@link QuestInstance.PhaseReward})
 *       predate this pivot: the triggering player still gets their stored
 *       pre-rolled item, but other accepters are skipped with a SEVERE log
 *       (no tier/ilvl to reroll against).</li>
 * </ul>
 *
 * <p>All methods must be invoked on the world thread (ECS component reads and
 * {@code giveItem} are world-thread-only).
 */
public final class Nat20QuestRewardDispatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestReward");
    private static final Gson REWARD_DATA_GSON = new Gson();

    private Nat20QuestRewardDispatcher() {}

    /**
     * Award per-phase XP to every online accepter not in {@code missed}. Each
     * accepter's level is read from their own {@link Nat20PlayerData} so a
     * high-level party carrying a low-level accepter doesn't short-change the
     * low-level player's XP grant (and vice versa).
     *
     * <p>Also refreshes the waypoint marker cache for every online accepter so
     * their map/quest-log reflects the new phase state immediately: fixes the
     * smoke-test bug where a non-triggering party member's waypoint was stuck
     * on the previous phase until they toggled /sheet tracking.
     */
    public static void dispenseXpToAccepters(QuestInstance quest, Set<UUID> missed, World world) {
        if (quest == null || world == null) return;
        Nat20XpService xpService = Natural20.getInstance().getXpService();
        if (xpService == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (UUID uuid : quest.getAccepters()) {
            if (missed != null && missed.contains(uuid)) continue;

            Ref<EntityStore> ref = world.getEntityRef(uuid);
            if (ref == null) continue; // offline in this world

            Player p = store.getComponent(ref, Player.getComponentType());
            if (p == null) continue;

            Nat20PlayerData pd = store.getComponent(ref, Natural20.getPlayerDataType());
            if (pd == null) continue;

            int xp = Nat20XpMath.questPhaseXp(pd.getLevel());
            xpService.award(p, ref, store, xp, "quest:" + quest.getQuestId());

            // Refresh this player's waypoint cache so the map / quest-log
            // reflect the phase transition.
            QuestMarkerProvider.refreshMarkers(uuid, pd);
        }
    }

    /**
     * Refresh the quest-marker cache for every online accepter. Used at sites
     * that don't dispense XP (e.g. quest accept), so every party member's
     * waypoint updates immediately without requiring them to toggle /sheet.
     */
    public static void refreshMarkersForAccepters(QuestInstance quest, World world) {
        if (quest == null || world == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID uuid : quest.getAccepters()) {
            Ref<EntityStore> ref = world.getEntityRef(uuid);
            if (ref == null) continue;
            Nat20PlayerData pd = store.getComponent(ref, Natural20.getPlayerDataType());
            if (pd == null) continue;
            QuestMarkerProvider.refreshMarkers(uuid, pd);
        }
    }

    /**
     * Dispense the per-phase item reward to every online non-missed accepter
     * OTHER than the triggering player. The triggering player is assumed to
     * have already received the stored pre-rolled reward via the legacy path
     * ({@code dispensePhaseReward} in {@code DialogueActionRegistry}). Each
     * non-triggering accepter gets a FRESH reroll at the phase's stored
     * tier + ilvl so party members don't all get identical weapons.
     *
     * <p>Legacy quests (rewardTier == null) predate this pivot: other
     * accepters are skipped with a SEVERE log line.
     */
    public static void dispenseItemsToOtherAccepters(QuestInstance quest,
                                                     int phaseIndex,
                                                     UUID triggeringPlayer,
                                                     World world) {
        if (quest == null || world == null || triggeringPlayer == null) return;
        QuestInstance.PhaseReward reward = quest.getPhaseReward(phaseIndex);
        if (reward == null) return;

        Set<UUID> missed = quest.getMissedForPhase(phaseIndex);
        Store<EntityStore> store = world.getEntityStore().getStore();
        String tier = reward.getRewardTier();
        int ilvl = reward.getRewardIlvl();
        boolean legacy = (tier == null || tier.isBlank() || ilvl <= 0);

        Random random = new Random();

        for (UUID uuid : quest.getAccepters()) {
            if (uuid.equals(triggeringPlayer)) continue;
            if (missed.contains(uuid)) continue;

            Ref<EntityStore> ref = world.getEntityRef(uuid);
            if (ref == null) continue; // offline

            Player peer = store.getComponent(ref, Player.getComponentType());
            if (peer == null) continue;

            if (legacy) {
                LOGGER.atSevere().log(
                    "Legacy quest %s phase %d has null/zero tier/ilvl (tier=%s, ilvl=%d); "
                        + "skipping multi-accepter reroll for player %s. "
                        + "Only the dialogue-holder gets the stored pre-rolled item on this quest.",
                    quest.getQuestId(), phaseIndex, tier, ilvl, uuid);
                continue;
            }

            ItemStack rerolled;
            try {
                rerolled = AffixRewardRoller.roll(tier, ilvl, random);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                    "AffixRewardRoller.roll threw for quest %s phase %d, player %s "
                        + "(tier=%s, ilvl=%d); skipping reroll dispense",
                    quest.getQuestId(), phaseIndex, uuid, tier, ilvl);
                continue;
            }

            ItemStackTransaction tx;
            try {
                tx = peer.giveItem(rerolled, ref, store);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                    "Multi-accepter giveItem threw for quest %s phase %d, player %s",
                    quest.getQuestId(), phaseIndex, uuid);
                continue;
            }

            if (tx == null || !tx.succeeded()) {
                ItemStack remainder = tx != null ? tx.getRemainder() : null;
                int remainderQty = remainder != null ? remainder.getQuantity() : rerolled.getQuantity();
                LOGGER.atSevere().log(
                    "Multi-accepter dispense REFUSED for quest %s phase %d, item %s, player %s: "
                        + "giveItem !succeeded (remainder=%d). Inventory likely full.",
                    quest.getQuestId(), phaseIndex, rerolled.getItemId(), uuid, remainderQty);
                continue;
            }

            String peerItemName = "";
            Nat20LootData peerData = rerolled.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (peerData != null && peerData.getGeneratedName() != null) {
                peerItemName = peerData.getGeneratedName();
            }
            LOGGER.atInfo().log(
                "Multi-accepter dispensed phase %d reroll for quest %s: %s x%d to player %s (%s)",
                phaseIndex, quest.getQuestId(), rerolled.getItemId(),
                rerolled.getQuantity(), uuid, peerItemName);
        }
    }
}
