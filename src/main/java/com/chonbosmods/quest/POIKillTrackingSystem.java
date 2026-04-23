package com.chonbosmods.quest;

// TRACKER_REFRESH: no in-world tracker UI exists; the waypoint is the only cached surface
// and is refreshed below via QuestMarkerProvider.refreshMarkers. DialogueResolver reads
// objective fields live, so any dialogue-side tokens like {boss_name} re-resolve on render.

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.progression.Nat20DamageContributorTracker;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.PoiGroupDirection;
import com.chonbosmods.quest.poi.SlotRecord;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ECS damage event system that credits kills of group-spawned mobs toward POI
 * quest objectives.
 *
 * <p>Identity is keyed off {@link Nat20MobGroupMemberComponent} on the victim rather
 * than quest bindings, so reconciliation-respawned mobs (fresh UUIDs) stay eligible.
 * Dispatch direction is read from {@link MobGroupRecord#getDirection()}: frozen at
 * first-spawn by {@code POIGroupSpawnCoordinator}.
 *
 * <p>Credit rule: a kill credits the objective if the quest owner contributed
 * any damage against the mob within {@link Nat20DamageContributorTracker#WINDOW_MS}
 * of death, regardless of who or what landed the killing blow. This covers
 * env deaths (lava, fall) on mobs the player had engaged, AI-on-AI kills on
 * kited mobs, and direct player kills. A mob that dies without any recent
 * owner damage (e.g. wanders off, falls uncontested) is marked dead with no
 * credit; the kill is effectively lost.
 */
public class POIKillTrackingSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POIKill");
    private static final Query<EntityStore> QUERY = Query.any();

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);

        // Health gate
        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;
        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) return;
        if (statMap.get(healthIndex).get() > 0) return;

        // Membership gate: only group-spawned mobs credit quests.
        Nat20MobGroupMemberComponent member =
                store.getComponent(victimRef, Natural20.getMobGroupMemberType());
        if (member == null) return;

        Nat20MobGroupRegistry registry = Natural20.getInstance().getMobGroupRegistry();
        if (registry == null) return;

        MobGroupRecord record = registry.get(member.getGroupKey());
        if (record == null) return; // stale component after quest-complete registry cleanup

        SlotRecord slot = findSlot(record, member.getSlotIndex());
        if (slot == null) return;
        if (slot.isDead()) return; // double-credit guard

        // Every death marks the slot dead (reconciliation will not respawn it).
        registry.markSlotDead(record.getGroupKey(), slot.getSlotIndex());

        // Credit if the quest owner contributed damage to this mob within the
        // contributor tracker's 30s window. Covers direct kills (lethal blow from
        // owner) and indirect kills (mob wandered off and died to lava or another
        // mob after the owner softened it up).
        if (ownerContributedRecently(store, victimRef, damage, record)) {
            creditOwner(store, record, slot);
        } else {
            LOGGER.atFine().log("No owner-damage contribution in window: quest=%s slot=%d",
                    record.getQuestId(), slot.getSlotIndex());
        }
    }

    /**
     * True when the record's owner appears in the contributor tracker for this mob
     * within {@link Nat20DamageContributorTracker#WINDOW_MS}. The lethal damage is
     * also recorded defensively so a one-shot kill still registers.
     */
    private static boolean ownerContributedRecently(Store<EntityStore> store,
                                                    Ref<EntityStore> victimRef,
                                                    Damage damage,
                                                    MobGroupRecord record) {
        Nat20DamageContributorTracker tracker = Natural20.getInstance().getContributorTracker();
        if (tracker == null) return false;

        long now = System.currentTimeMillis();
        tracker.recordFromDamage(victimRef, damage, store, now);

        UUID mobUuid = Nat20DamageContributorTracker.uuidOf(victimRef, store);
        if (mobUuid == null) return false;

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(record.getOwnerPlayerUuid());
        } catch (Exception e) {
            return false;
        }

        List<Ref<EntityStore>> contributors = tracker.getContributors(mobUuid, now);
        for (Ref<EntityStore> ref : contributors) {
            if (ownerUuid.equals(Nat20DamageContributorTracker.uuidOf(ref, store))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve the owner player + quest, dispatch kill credit per direction, handle
     * quest-complete plumbing (banner, XP, turn-in marker, waypoint refresh).
     */
    private void creditOwner(Store<EntityStore> store, MobGroupRecord record, SlotRecord slot) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(record.getOwnerPlayerUuid());
        } catch (Exception e) {
            LOGGER.atWarning().log("Malformed ownerPlayerUuid on record %s", record.getGroupKey());
            return;
        }

        Ref<EntityStore> playerRef = world.getEntityRef(ownerUuid);
        if (playerRef == null) return; // owner offline; slot stays dead, progress is lost this session
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;
        QuestStateManager stateManager = questSystem.getStateManager();

        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);
        QuestInstance quest = quests.get(record.getQuestId());
        if (quest == null) return;

        ObjectiveInstance obj = quest.getCurrentObjective();
        if (obj == null || obj.isComplete()) return;
        if (obj.getType() != ObjectiveType.KILL_MOBS && obj.getType() != ObjectiveType.KILL_BOSS) return;

        // Phase-match guard. Records are keyed by (player, quest, conflictCount). Once
        // the quest advances, older-phase guard mobs stay in the world but must not
        // credit the new phase's kill objective.
        if (record.getPoiSlotIdx() != quest.getConflictCount()) {
            LOGGER.atFine().log("stale-phase kill ignored: quest=%s recordPhase=%d currentPhase=%d",
                    quest.getQuestId(), record.getPoiSlotIdx(), quest.getConflictCount());
            return;
        }

        boolean credit = (record.getDirection() == PoiGroupDirection.KILL_COUNT) || slot.isBoss();
        if (!credit) {
            LOGGER.atFine().log("KILL_BOSS champion kill (no credit): quest=%s slot=%d",
                    quest.getQuestId(), slot.getSlotIndex());
            return;
        }

        obj.incrementProgress(1);
        LOGGER.atInfo().log("POI kill credited: quest=%s direction=%s slot=%d progress=%d/%d",
                quest.getQuestId(), record.getDirection(), slot.getSlotIndex(),
                obj.getCurrentCount(), obj.getRequiredCount());

        if (!obj.isComplete()) {
            stateManager.saveActiveQuests(playerData, quests);
            return;
        }

        LOGGER.atInfo().log("SUCCESS: POI kill objective complete for quest %s (%d/%d kills)",
                quest.getQuestId(), obj.getCurrentCount(), obj.getRequiredCount());

        boolean firstReady = quest.markPhaseReadyForTurnIn();
        stateManager.saveActiveQuests(playerData, quests);

        // Party proximity gate: identify accepters who are out of range at the
        // phase-completion moment. Anchor on the killer's current position
        // (same pattern as Nat20QuestProximityGate.resolvePosition). No-op for
        // solo / single-accepter quests. Under Option B (2026-04-22), missed
        // accepters stay on the quest; the returned set is used to filter
        // per-phase reward dispense below and persisted on the quest for the
        // item-dispense filter at CONTINUE_QUEST.
        double[] anchor = null;
        TransformComponent tx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tx != null) {
            Vector3d pos = tx.getPosition();
            if (pos != null) {
                anchor = new double[]{pos.getX(), pos.getY(), pos.getZ()};
            }
        }
        java.util.Set<UUID> missed = java.util.Collections.emptySet();
        if (anchor != null) {
            missed = Nat20QuestProximityGate.check(quest, ownerUuid, anchor, world, Natural20.getInstance());
            quest.markMissedForPhase(quest.getConflictCount(), missed);
            // Re-save so the missed set persists across session restarts.
            stateManager.saveActiveQuests(playerData, quests);
        }

        // Set turn-in particle on source NPC so the player knows where to return.
        setTurnInParticle(quest);

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            if (firstReady) {
                QuestCompletionBanner.show(player.getPlayerRef(), quest);
            }
            // Waypoint refresh for every online accepter so phase-ready
            // propagates to the map / quest log without requiring a /sheet
            // toggle. XP is NOT awarded here: TURN_IN_V2.dispensePhaseXp owns
            // the per-phase grant. Idempotent if banner already fired.
            Nat20QuestRewardDispatcher.refreshMarkersForAccepters(quest, world);
        }
    }

    private static SlotRecord findSlot(MobGroupRecord record, int slotIndex) {
        for (SlotRecord slot : record.getSlots()) {
            if (slot.getSlotIndex() == slotIndex) return slot;
        }
        return null;
    }

    private static void setTurnInParticle(QuestInstance quest) {
        com.chonbosmods.settlement.SettlementRegistry settlements =
                Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;
        com.chonbosmods.settlement.SettlementRecord settlement =
                settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;
        com.chonbosmods.settlement.NpcRecord npcRecord =
                settlement.getNpcByName(quest.getSourceNpcId());
        if (npcRecord == null) return;
        npcRecord.setMarkerState("QUEST_TURN_IN");
        settlements.saveAsync();
        if (npcRecord.getEntityUUID() != null) {
            com.chonbosmods.marker.QuestMarkerManager.INSTANCE.syncMarker(
                    npcRecord.getEntityUUID(),
                    com.chonbosmods.data.Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        }
    }
}
