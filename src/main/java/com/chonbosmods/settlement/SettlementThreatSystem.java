package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;

/**
 * Listens for damage on settlement NPCs from non-hostile sources (players, neutral mobs).
 * Marks the attacker as a hostile target on the damaged NPC and all Guards in the same
 * settlement. For player attackers, decreases disposition across the settlement.
 */
public class SettlementThreatSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Threat");

    private static final int DISPOSITION_PER_HIT = -5;
    private static final int DISPOSITION_FLOOR = -100;

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

        // Only care about settlement NPCs
        Nat20NpcData victimData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (victimData == null) return;
        String cellKey = victimData.getSettlementCellKey();
        if (cellKey == null) return;

        // Get the attacker ref from the damage source
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Check if attacker is a player
        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        boolean isPlayerAttacker = attackerPlayer != null;

        // Skip if attacker is another settlement NPC (friendly fire)
        Nat20NpcData attackerNpcData = store.getComponent(attackerRef, Natural20.getNpcDataType());
        if (attackerNpcData != null && attackerNpcData.getSettlementCellKey() != null) return;

        // Mark attacker as target on the damaged NPC
        NPCEntity victimNpc = store.getComponent(victimRef, NPCEntity.getComponentType());
        if (victimNpc != null) {
            victimNpc.onFlockSetTarget("LockedTargetClose", attackerRef);
        }

        // Find settlement and mark attacker on all Guards
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return;

        SettlementRecord settlement = registry.getByCell(cellKey);
        if (settlement == null) return;

        UUID worldUUID = settlement.getWorldUUID();
        var world = registry.getCachedWorld(worldUUID);
        if (world == null) return;

        for (NpcRecord npcRecord : settlement.getNpcs()) {
            UUID npcUUID = npcRecord.getEntityUUID();
            if (npcUUID == null) continue;

            // Mark attacker on Guards
            if (npcRecord.getRole().equals("Guard")) {
                Ref<EntityStore> guardRef = world.getEntityRef(npcUUID);
                if (guardRef != null && guardRef.isValid()) {
                    NPCEntity guardNpc = store.getComponent(guardRef, NPCEntity.getComponentType());
                    if (guardNpc != null) {
                        guardNpc.onFlockSetTarget("LockedTargetClose", attackerRef);
                    }
                }
            }

            // For player attackers: decrease disposition on ALL settlement NPCs
            if (isPlayerAttacker) {
                Ref<EntityStore> npcRef = world.getEntityRef(npcUUID);
                if (npcRef != null && npcRef.isValid()) {
                    Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
                    if (npcData != null) {
                        int current = npcData.getDefaultDisposition();
                        int updated = Math.max(DISPOSITION_FLOOR, current + DISPOSITION_PER_HIT);
                        npcData.setDefaultDisposition(updated);
                    }
                }
            }
        }

        // Cancel any active dialogue with the attacked NPC
        Natural20.getInstance().getDialogueManager().endSessionForNpc(victimRef);

        // Track threat for the cooldown system (applies to all attacker types)
        SettlementThreatClearSystem clearSystem = Natural20.getInstance().getThreatClearSystem();
        if (clearSystem != null) {
            clearSystem.recordThreat(victimRef, attackerRef, worldUUID);
            // Also record threat on all guards
            for (NpcRecord npcRecord : settlement.getNpcs()) {
                if (npcRecord.getRole().equals("Guard") && npcRecord.getEntityUUID() != null) {
                    Ref<EntityStore> guardRef = world.getEntityRef(npcRecord.getEntityUUID());
                    if (guardRef != null && guardRef.isValid()) {
                        clearSystem.recordThreat(guardRef, attackerRef, worldUUID);
                    }
                }
            }
        }

        if (isPlayerAttacker) {
            UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
            LOGGER.atInfo().log("Player %s attacked settlement NPC '%s': marked hostile, disposition -%d",
                    playerUuid, victimData.getGeneratedName(), Math.abs(DISPOSITION_PER_HIT));
        } else {
            LOGGER.atInfo().log("Non-hostile entity attacked settlement NPC '%s': marked as threat",
                    victimData.getGeneratedName());
        }
    }
}
