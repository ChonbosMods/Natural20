package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.*;

/**
 * ECS damage event system that tracks kills of POI-spawned mobs for quest objectives.
 *
 * <p>When a mob's health drops to 0, this system checks if the victim's UUID matches any
 * active quest's POI mob UUID list (stored as a comma-separated string in the quest's
 * variable bindings under "poi_mob_uuids"). Kill credit is gated on the quest's
 * {@code poi_mob_state} being "ACTIVE".
 *
 * <p>All deaths while ACTIVE are credited except {@link DamageCause#SUFFOCATION}, which
 * triggers a replacement spawn instead (the mob likely clipped into terrain).
 *
 * <p>Environmental kills (where the attacker is not the quest holder) are handled by
 * scanning all tracked online players to find whose quest owns the dead mob.
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

        // Check if health dropped to 0
        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;
        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) return;
        if (statMap.get(healthIndex).get() > 0) return;

        // Get victim UUID
        NPCEntity victimNpc = store.getComponent(victimRef, NPCEntity.getComponentType());
        if (victimNpc == null) return;
        String victimUUID = victimNpc.getUuid().toString();

        // Try to get the killer as a player (may be null for environmental kills)
        Ref<EntityStore> killerRef = null;
        Nat20PlayerData killerPlayerData = null;
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            killerRef = entitySource.getRef();
            killerPlayerData = store.getComponent(killerRef, Natural20.getPlayerDataType());
        }

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        if (killerPlayerData != null) {
            // Direct player kill: check that player's quests
            processKill(store, questSystem, killerRef, killerPlayerData, victimUUID, damage);
        } else {
            // Environmental kill: scan all tracked players to find whose quest owns this mob
            World world = Natural20.getInstance().getDefaultWorld();
            if (world == null) return;

            POIProximitySystem proxSystem = Natural20.getInstance().getPOIProximitySystem();
            if (proxSystem == null) return;

            for (UUID playerUuid : proxSystem.getTrackedPlayers()) {
                Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
                if (playerRef == null) continue;

                Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
                if (playerData == null) continue;

                if (processKill(store, questSystem, playerRef, playerData, victimUUID, damage)) {
                    return;
                }
            }
        }
    }

    /**
     * Check a single player's quests for a matching mob UUID with ACTIVE state.
     * If found, either credits the kill or spawns a replacement (for suffocation).
     *
     * @return true if the kill was handled (stop scanning other players)
     */
    private boolean processKill(Store<EntityStore> store, QuestSystem questSystem,
                                Ref<EntityStore> playerRef, Nat20PlayerData playerData,
                                String victimUUID, Damage damage) {
        QuestStateManager stateManager = questSystem.getStateManager();
        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);

        for (QuestInstance quest : quests.values()) {
            Map<String, String> b = quest.getVariableBindings();

            // Must be in ACTIVE state
            if (!"ACTIVE".equals(b.get("poi_mob_state"))) continue;

            String mobUUIDs = b.get("poi_mob_uuids");
            if (mobUUIDs == null || !mobUUIDs.contains(victimUUID)) continue;

            // Found a matching quest: check damage cause
            DamageCause cause = damage.getCause();
            if (cause == DamageCause.SUFFOCATION || cause == DamageCause.ENVIRONMENT) {
                // Terrain/environment damage: don't credit, spawn replacement
                LOGGER.atInfo().log("POI mob died to %s: quest=%s victim=%s, spawning replacement",
                    cause.getId(), quest.getQuestId(), victimUUID);
                removeMobUUID(b, victimUUID);
                stateManager.saveActiveQuests(playerData, quests);
                spawnReplacement(quest, b, playerData, quests);
                return true;
            }

            // Normal kill: credit progress
            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || obj.isComplete()) continue;
            if (obj.getLocationId() == null || !obj.getLocationId().startsWith("poi:")) continue;
            if (obj.getType() != ObjectiveType.KILL_MOBS) continue;

            obj.incrementProgress(1);
            LOGGER.atInfo().log("POI kill tracked: quest=%s victim=%s progress=%d/%d",
                quest.getQuestId(), victimUUID, obj.getCurrentCount(), obj.getRequiredCount());

            if (obj.isComplete()) {
                LOGGER.atInfo().log("SUCCESS: POI kill objective complete for quest %s (%d/%d kills)",
                    quest.getQuestId(), obj.getCurrentCount(), obj.getRequiredCount());

                boolean firstReady = quest.markPhaseReadyForTurnIn();
                LOGGER.atInfo().log("Quest %s objective complete via POI kill (conflict %d): awaiting turn-in",
                    quest.getQuestId(), quest.getConflictCount());
                stateManager.saveActiveQuests(playerData, quests);

                // Set turn-in particle on source NPC
                setTurnInParticle(quest);

                // Refresh markers + show banner: swaps POI marker -> return marker at settlement
                Player player = store.getComponent(playerRef, Player.getComponentType());
                if (player != null) {
                    QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), playerData);
                    if (firstReady) {
                        QuestCompletionBanner.show(player.getPlayerRef(), quest);
                    }
                }
            } else {
                stateManager.saveActiveQuests(playerData, quests);
            }
            return true;
        }
        return false;
    }

    /**
     * Remove a single mob UUID from the comma-separated poi_mob_uuids binding.
     */
    private static void setTurnInParticle(QuestInstance quest) {
        com.chonbosmods.settlement.SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;
        com.chonbosmods.settlement.SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;
        com.chonbosmods.settlement.NpcRecord npcRecord = settlement.getNpcByName(quest.getSourceNpcId());
        if (npcRecord == null) return;
        npcRecord.setMarkerState("QUEST_TURN_IN");
        settlements.saveAsync();
        if (npcRecord.getEntityUUID() != null) {
            com.chonbosmods.marker.QuestMarkerManager.INSTANCE.syncMarker(
                npcRecord.getEntityUUID(),
                com.chonbosmods.data.Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        }
    }

    private void removeMobUUID(Map<String, String> b, String uuid) {
        String uuids = b.getOrDefault("poi_mob_uuids", "");
        List<String> list = new ArrayList<>(Arrays.asList(uuids.split(",")));
        list.remove(uuid);
        b.put("poi_mob_uuids", String.join(",", list));
    }

    /**
     * Spawn a replacement mob at the POI location using the quest's spawn descriptor.
     * Adds the new mob's UUID to poi_mob_uuids.
     */
    /**
     * Defer spawning a replacement mob to the next tick.
     * Cannot spawn from inside a DamageEventSystem handler (store is processing).
     */
    private void spawnReplacement(QuestInstance quest, Map<String, String> b,
                                   Nat20PlayerData playerData, Map<String, QuestInstance> quests) {
        POIPopulationListener.SpawnDescriptor desc =
            POIPopulationListener.SpawnDescriptor.parse(b.get("poi_spawn_descriptor"));
        if (desc == null) return;
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        QuestStateManager sm = Natural20.getInstance().getQuestSystem().getStateManager();
        world.execute(() -> {
            List<String> spawned = Natural20.getInstance().getPOIPopulationListener()
                .spawnMobs(world, desc.mobRole(), 1, desc.poiX(), desc.poiY(), desc.poiZ());
            if (!spawned.isEmpty()) {
                // Re-read quest bindings since we're on a deferred tick
                Map<String, QuestInstance> freshQuests = sm.getActiveQuests(playerData);
                QuestInstance freshQuest = freshQuests.get(quest.getQuestId());
                if (freshQuest != null) {
                    String existing = freshQuest.getVariableBindings().getOrDefault("poi_mob_uuids", "");
                    String updated = existing.isEmpty() ? spawned.getFirst() : existing + "," + spawned.getFirst();
                    freshQuest.getVariableBindings().put("poi_mob_uuids", updated);
                    sm.saveActiveQuests(playerData, freshQuests);
                }
            }
        });
    }
}
