package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ECS damage event system that monitors settlement NPC deaths and schedules respawns.
 *
 * <p>When a settlement NPC's health drops to 0 after taking damage, this system:
 * <ol>
 *   <li>Marks the NPC as dead in the settlement registry (clears entityUUID)</li>
 *   <li>Saves the registry asynchronously</li>
 *   <li>Schedules a respawn after 30 seconds on the world thread</li>
 * </ol>
 *
 * <p>Settlement NPCs are identified by having a {@link Nat20NpcData} component with a
 * non-null {@code settlementCellKey}. Non-settlement entities are ignored.
 *
 * <p>Note: NPCs default to invulnerable via role JSON. This system only fires when
 * invulnerability is removed (e.g., via {@code /nat20 killnpc}).
 */
public class SettlementNpcDeathSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|NpcDeath");

    private static final Query<EntityStore> QUERY = Query.any();

    private static final int RESPAWN_DELAY_SECONDS = 30;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nat20-npc-respawn");
                t.setDaemon(true);
                return t;
            });

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);

        // Only care about entities with settlement NPC data
        Nat20NpcData npcData = store.getComponent(entityRef, Natural20.getNpcDataType());
        if (npcData == null) return;

        String cellKey = npcData.getSettlementCellKey();
        if (cellKey == null) return;

        // Check if health has dropped to 0 or below
        EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) return;

        float currentHealth = statMap.get(healthIndex).get();
        if (currentHealth > 0) return;

        // NPC is dead: find it in the registry
        NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
        if (npcEntity == null) return;

        UUID entityUUID = npcEntity.getUuid();
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return;

        NpcRecord npcRecord = registry.getNpcByUUID(entityUUID);
        if (npcRecord == null) {
            LOGGER.atWarning().log("Settlement NPC died but no NpcRecord found for UUID " + entityUUID);
            return;
        }

        // Mark dead: clear entity UUID
        npcRecord.setEntityUUID(null);
        registry.saveAsync();

        String name = npcData.getGeneratedName();
        String role = npcData.getRoleName();
        LOGGER.atInfo().log("Settlement NPC died: " + name + " (" + role + ") at cell " + cellKey
                + ": scheduling respawn in " + RESPAWN_DELAY_SECONDS + "s");

        // Capture world reference for the scheduled respawn
        World world = npcEntity.getWorld();

        // Schedule respawn after delay
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    try {
                        Store<EntityStore> respawnStore = world.getEntityStore().getStore();
                        UUID newUUID = Natural20.getInstance().getNpcManager()
                                .respawnNpc(respawnStore, world, npcRecord);

                        if (newUUID != null) {
                            registry.saveAsync();
                            LOGGER.atInfo().log("Respawned " + name + " (" + role + ") with new UUID " + newUUID);
                        } else {
                            LOGGER.atWarning().log("Failed to respawn " + name + " (" + role + ") at cell " + cellKey);
                        }
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error during NPC respawn for " + name);
                    }
                });
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Error scheduling world-thread respawn for " + name);
            }
        }, RESPAWN_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
