package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Map;

/**
 * ECS damage event system that tracks kills of POI-spawned mobs for quest objectives.
 *
 * <p>When a mob's health drops to 0, this system checks if the victim's UUID matches any
 * active quest's POI mob UUID list (stored as a comma-separated string in the quest's
 * variable bindings under "poi_mob_uuids"). If a match is found and the killer is a player,
 * the corresponding kill objective's progress is incremented.
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

        // Get killer: must be a player
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> killerRef = entitySource.getRef();

        Nat20PlayerData playerData = store.getComponent(killerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        // Check all active quests for this player
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        QuestStateManager stateManager = questSystem.getStateManager();
        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);

        for (QuestInstance quest : quests.values()) {
            String mobUUIDs = quest.getVariableBindings().get("poi_mob_uuids");
            if (mobUUIDs == null || !mobUUIDs.contains(victimUUID)) continue;

            // Found a matching quest: report kill progress
            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.isComplete()) continue;
                if (obj.getLocationId() == null || !obj.getLocationId().startsWith("poi:")) continue;
                if (obj.getType() != ObjectiveType.KILL_MOBS) continue;

                obj.incrementProgress(1);
                LOGGER.atInfo().log("POI kill tracked: quest=%s victim=%s progress=%d/%d",
                    quest.getQuestId(), victimUUID, obj.getCurrentCount(), obj.getRequiredCount());

                if (obj.isComplete()) {
                    LOGGER.atInfo().log("SUCCESS: POI kill objective complete for quest %s (%d/%d kills)",
                        quest.getQuestId(), obj.getCurrentCount(), obj.getRequiredCount());
                }

                // Save and check phase completion
                if (phase.isComplete()) {
                    boolean isFinalPhase = quest.getCurrentPhaseIndex() >= quest.getPhases().size() - 1;
                    questSystem.getRewardManager().awardPhaseXP(playerData, phase, isFinalPhase, quest.getPhases().size());

                    if (isFinalPhase) {
                        LOGGER.atInfo().log("Quest %s completed via POI kill", quest.getQuestId());
                        stateManager.markQuestCompleted(playerData, quest.getQuestId());
                    } else {
                        quest.advancePhase();
                        LOGGER.atInfo().log("Quest %s advanced to phase %d via POI kill",
                            quest.getQuestId(), quest.getCurrentPhaseIndex());
                        stateManager.saveActiveQuests(playerData, quests);
                    }
                } else {
                    stateManager.saveActiveQuests(playerData, quests);
                }
                return;
            }
        }
    }
}
