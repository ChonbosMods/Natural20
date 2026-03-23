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

import java.util.UUID;

/**
 * Listens for damage on settlement NPCs and handles player disposition tracking.
 *
 * Guard alerting is now handled by the behavior tree's Beacon system:
 * when any settlement NPC takes damage, it broadcasts "Under_Attack" via
 * Component_Instruction_Damage_Check, and nearby Guards receive the beacon
 * in their Idle state and transition to Investigate/Combat.
 *
 * This system only handles:
 * 1. Player disposition tracking (decrease on attack)
 * 2. Dialogue cancellation (end active dialogue on damage)
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

        // For player attackers: decrease disposition on ALL settlement NPCs
        if (isPlayerAttacker) {
            SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
            if (registry != null) {
                SettlementRecord settlement = registry.getByCell(cellKey);
                if (settlement != null) {
                    UUID worldUUID = settlement.getWorldUUID();
                    var world = registry.getCachedWorld(worldUUID);
                    if (world != null) {
                        for (NpcRecord npcRecord : settlement.getNpcs()) {
                            UUID npcUUID = npcRecord.getEntityUUID();
                            if (npcUUID == null) continue;

                            Ref<EntityStore> npcRef = world.getEntityRef(npcUUID);
                            if (npcRef == null || !npcRef.isValid()) continue;

                            Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
                            if (npcData != null) {
                                int current = npcData.getDefaultDisposition();
                                int updated = Math.max(DISPOSITION_FLOOR, current + DISPOSITION_PER_HIT);
                                npcData.setDefaultDisposition(updated);
                                npcRecord.setDisposition(updated);
                            }
                        }
                    }
                }
                registry.saveAsync();
            }
        }

        // Cancel any active dialogue with the attacked NPC
        Natural20.getInstance().getDialogueManager().endSessionForNpc(victimRef);

        if (isPlayerAttacker) {
            UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
            LOGGER.atInfo().log("Player %s attacked settlement NPC '%s': disposition -%d",
                    playerUuid, victimData.getGeneratedName(), Math.abs(DISPOSITION_PER_HIT));
        }
    }
}
