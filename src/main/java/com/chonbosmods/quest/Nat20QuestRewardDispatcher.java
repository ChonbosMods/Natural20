package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.ItemUtils;
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
 * Option B pivot). The CONTINUE_QUEST item-dispense path and objective
 * completion sites route their waypoint / item distribution through the
 * helpers here instead of only touching the triggering player.
 *
 * <p>Rules:
 * <ul>
 *   <li>XP: awarded exclusively by {@code DialogueActionRegistry.dispensePhaseXp}
 *       at TURN_IN_V2 (not here). That path iterates accepters itself so every
 *       online non-missed player gets level + difficulty-scaled XP.</li>
 *   <li>Items: every online non-missed accepter OTHER than the triggering
 *       player gets a FRESH per-player roll via {@link AffixRewardRoller#roll}
 *       at the phase's stored tier and a per-player ilvl computed by
 *       {@link QuestRewardIlvl#reward(int, int, int)} from each recipient's
 *       own mlvl, the quest's {@code areaLevelAtSpawn}, and {@code ilvlBonus}.
 *       The triggering player is rolled separately by DAR's
 *       {@code dispensePhaseReward}. Party members get distinct items AND
 *       distinct ilvls scaled to their own level.</li>
 *   <li>Waypoints: {@link #refreshMarkersForAccepters} refreshes the cache for
 *       every online accepter at phase-ready / accept / phase-advance moments
 *       so non-triggering party members don't need a /sheet toggle.</li>
 *   <li>Offline accepters and missed accepters are skipped silently.</li>
 *   <li>Legacy quests (rewardTier null/blank or areaLevel &lt;= 0 on
 *       {@link QuestInstance.PhaseReward}) predate this pivot: other accepters
 *       are skipped with a SEVERE log (no tier/area to roll against).</li>
 * </ul>
 *
 * <p>All methods must be invoked on the world thread (ECS component reads and
 * {@code giveItem} are world-thread-only).
 */
public final class Nat20QuestRewardDispatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestReward");

    private Nat20QuestRewardDispatcher() {}

    /**
     * Refresh the quest-marker cache for every online accepter. Used at sites
     * that don't dispense XP (e.g. quest accept, phase-ready transitions),
     * so every party member's waypoint updates immediately without requiring
     * them to toggle /sheet.
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
     * OTHER than the triggering player. The triggering player is rolled by
     * DAR's {@code dispensePhaseReward}. Each non-triggering accepter gets a
     * FRESH per-player roll at the phase's stored tier and a per-player ilvl
     * computed by {@link QuestRewardIlvl#reward(int, int, int)} from each
     * recipient's own mlvl, so party members get distinct items AND distinct
     * ilvls scaled to their own level.
     *
     * <p>Legacy quests (rewardTier null/blank or areaLevel &lt;= 0) predate
     * this pivot: other accepters are skipped with a SEVERE log line.
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
        int areaLevel = reward.getAreaLevelAtSpawn();
        int ilvlBonus = reward.getIlvlBonus();
        boolean legacy = (tier == null || tier.isBlank() || areaLevel <= 0);

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
                    "Legacy quest %s phase %d has null/blank tier or non-positive areaLevel "
                        + "(tier=%s, areaLevel=%d, ilvlBonus=%d); skipping multi-accepter "
                        + "roll for player %s.",
                    quest.getQuestId(), phaseIndex, tier, areaLevel, ilvlBonus, uuid);
                continue;
            }

            Nat20PlayerData peerData = store.getComponent(ref, Natural20.getPlayerDataType());
            int peerLevel = peerData != null ? peerData.getLevel() : 1;
            int ilvl = QuestRewardIlvl.reward(peerLevel, areaLevel, ilvlBonus);

            ItemStack rerolled;
            try {
                rerolled = AffixRewardRoller.roll(tier, ilvl, random);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                    "AffixRewardRoller.roll threw for quest %s phase %d, player %s "
                        + "(tier=%s, ilvl=%d, peerLevel=%d, areaLevel=%d, ilvlBonus=%d); "
                        + "skipping per-player roll dispense",
                    quest.getQuestId(), phaseIndex, uuid, tier, ilvl,
                    peerLevel, areaLevel, ilvlBonus);
                continue;
            }

            ItemStackTransaction tx;
            try {
                tx = peer.giveItem(rerolled, ref, store);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                    "Multi-accepter giveItem threw for quest %s phase %d peer %s; falling back to ground drop",
                    quest.getQuestId(), phaseIndex, uuid);
                tx = null;
            }

            boolean fullyDelivered = (tx != null && tx.succeeded());
            if (!fullyDelivered) {
                ItemStack toDrop = (tx != null && tx.getRemainder() != null) ? tx.getRemainder() : rerolled;
                try {
                    ItemUtils.dropItem(ref, toDrop, store);
                    LOGGER.atInfo().log(
                        "Multi-accepter dropped reward at peer %s feet for quest %s phase %d (item=%s x%d): inventory was full",
                        uuid, quest.getQuestId(), phaseIndex, toDrop.getItemId(), toDrop.getQuantity());
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log(
                        "Multi-accepter ground drop FAILED for quest %s phase %d peer %s; reward LOST (item=%s)",
                        quest.getQuestId(), phaseIndex, uuid, toDrop.getItemId());
                }
                // Fall through to success-side logging below; ground drop is a delivery success
                // from the player's perspective.
            }

            String peerItemName = "";
            Nat20LootData peerLootData = rerolled.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (peerLootData != null && peerLootData.getGeneratedName() != null) {
                peerItemName = peerLootData.getGeneratedName();
            }
            LOGGER.atInfo().log(
                "Multi-accepter dispensed phase %d roll for quest %s: %s x%d to player %s "
                    + "(%s; tier=%s, ilvl=%d, peerLevel=%d, areaLevel=%d, ilvlBonus=%d)",
                phaseIndex, quest.getQuestId(), rerolled.getItemId(),
                rerolled.getQuantity(), uuid, peerItemName, tier, ilvl,
                peerLevel, areaLevel, ilvlBonus);
        }
    }
}
