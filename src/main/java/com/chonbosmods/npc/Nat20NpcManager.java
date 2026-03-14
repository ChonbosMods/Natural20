package com.chonbosmods.npc;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Nat20NpcManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|NpcManager");

    private static final List<String> ARTISAN_POOL = List.of(
        "ArtisanBlacksmith",
        "ArtisanAlchemist",
        "ArtisanCook"
    );

    /**
     * Spawn all NPCs defined by the given settlement type at positions relative to origin.
     * Handles RANDOM_ARTISAN deduplication and ground-level scanning.
     *
     * @param store   the entity store
     * @param world   the world to scan for ground and spawn into
     * @param type    the settlement type whose NPC spawn defs to use
     * @param origin  the world-space origin of the settlement
     * @param cellKey the settlement cell key for persistence
     * @return list of NpcRecords for all successfully spawned NPCs
     */
    public List<NpcRecord> spawnSettlementNpcs(
            Store<EntityStore> store, World world,
            SettlementType type, Vector3d origin, String cellKey) {

        List<NpcRecord> spawned = new ArrayList<>();
        List<String> usedArtisans = new ArrayList<>();

        for (NpcSpawnDef def : type.getNpcSpawns()) {
            String roleName = resolveRole(def.role(), usedArtisans);

            int roleIndex = NPCPlugin.get().getIndex(roleName);
            if (roleIndex < 0) {
                LOGGER.atWarning().log("[Nat20] Role not registered in NPCPlugin: " + roleName);
                continue;
            }

            double spawnX = origin.getX() + def.xOffset() + 0.5;
            double spawnZ = origin.getZ() + def.zOffset() + 0.5;
            double spawnY = findGroundY(world, (int) (origin.getX() + def.xOffset()),
                    (int) (origin.getZ() + def.zOffset()), (int) origin.getY());

            Vector3d spawnPos = new Vector3d(spawnX, spawnY, spawnZ);

            Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, def.rotation(), null, null);

            if (result != null) {
                Ref<EntityStore> npcRef = result.first();
                NPCEntity npcEntity = result.second();

                // Attach Nat20NpcData component with generated name
                String name = Nat20NameGenerator.generate(npcEntity.getUuid().getMostSignificantBits());
                Nat20NpcData npcData = store.addComponent(npcRef, Natural20.getNpcDataType());
                npcData.setGeneratedName(name);
                npcData.setRoleName(roleName);
                npcData.setSettlementCellKey(cellKey);

                // Set leash point so NPC stays near spawn
                npcEntity.setLeashPoint(spawnPos);

                // Create NpcRecord for persistence
                NpcRecord npcRecord = new NpcRecord(
                    roleName, npcEntity.getUuid(),
                    spawnX, spawnY, spawnZ,
                    def.rotation().getX(), def.rotation().getY(), def.rotation().getZ(),
                    def.leashRadius(), name);
                spawned.add(npcRecord);

                // Set nameplate using Nameplate component (overrides role's DisplayNames)
                String displayName = formatDisplayName(name, roleName);
                store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));

                LOGGER.atInfo().log("[Nat20] Spawned " + displayName + " at " +
                    (int) spawnX + ", " + (int) spawnY + ", " + (int) spawnZ);
            } else {
                LOGGER.atWarning().log("[Nat20] Failed to spawn " + roleName + " at " +
                    (int) spawnX + ", " + (int) spawnY + ", " + (int) spawnZ);
            }
        }

        LOGGER.atInfo().log("[Nat20] Spawned " + spawned.size() + "/" +
            type.getNpcSpawns().size() + " NPCs for " + type);
        return spawned;
    }

    private String formatDisplayName(String name, String roleName) {
        // Convert role like "ArtisanBlacksmith" -> "Blacksmith", "Guard" -> "Guard"
        String displayRole = roleName;
        if (roleName.startsWith("Artisan")) {
            displayRole = roleName.substring("Artisan".length());
        }
        return name + " the " + displayRole;
    }

    /**
     * Respawn a single NPC from a persisted NpcRecord.
     * Used by the death/respawn system to restore NPCs after a cooldown.
     *
     * @param store  the entity store
     * @param world  the world to spawn into
     * @param record the NpcRecord describing the NPC to respawn
     * @return the new UUID of the respawned entity, or null on failure
     */
    public UUID respawnNpc(Store<EntityStore> store, World world, NpcRecord record) {
        String roleName = record.getRole();
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Cannot respawn: role not found: " + roleName);
            return null;
        }

        Vector3d spawnPos = new Vector3d(record.getSpawnX(), record.getSpawnY(), record.getSpawnZ());
        Vector3f rotation = new Vector3f(record.getRotX(), record.getRotY(), record.getRotZ());

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, null, null);

        if (result == null) {
            LOGGER.atWarning().log("Failed to respawn " + roleName);
            return null;
        }

        Ref<EntityStore> npcRef = result.first();
        NPCEntity npcEntity = result.second();

        // Restore identity
        Nat20NpcData npcData = store.addComponent(npcRef, Natural20.getNpcDataType());
        npcData.setGeneratedName(record.getGeneratedName());
        npcData.setRoleName(roleName);

        // Set leash
        npcEntity.setLeashPoint(spawnPos);

        // Set nameplate using Nameplate component (overrides role's DisplayNames)
        String displayName = formatDisplayName(record.getGeneratedName(), roleName);
        store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));

        UUID newUUID = npcEntity.getUuid();
        record.setEntityUUID(newUUID);

        LOGGER.atInfo().log("Respawned " + displayName + " at " +
            (int) spawnPos.getX() + ", " + (int) spawnPos.getY() + ", " + (int) spawnPos.getZ());
        return newUUID;
    }

    /**
     * Resolve a role name: if RANDOM_ARTISAN, pick from the artisan pool
     * preferring unused artisans. Falls back to repeats if all are used.
     */
    private String resolveRole(String role, List<String> usedArtisans) {
        if (!"RANDOM_ARTISAN".equals(role)) {
            return role;
        }

        // Collect artisans not yet used
        List<String> available = new ArrayList<>();
        for (String artisan : ARTISAN_POOL) {
            if (!usedArtisans.contains(artisan)) {
                available.add(artisan);
            }
        }

        // If all artisans used, allow repeats
        if (available.isEmpty()) {
            available = ARTISAN_POOL;
        }

        String chosen = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        usedArtisans.add(chosen);
        return chosen;
    }

    /**
     * Find the ground-level Y coordinate for an NPC spawn point.
     * Scans downward first, then upward, with a final fallback.
     *
     * @param world   the world to scan
     * @param x       block X coordinate
     * @param z       block Z coordinate
     * @param originY the settlement origin Y
     * @return the Y coordinate to place the NPC (groundY + 1.0)
     */
    private double findGroundY(World world, int x, int z, int originY) {
        // Scan downward from originY + 32 to originY - 32
        int ceiling = originY + 32;
        int floor = originY - 32;

        for (int y = ceiling; y >= floor; y--) {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y + 1.0;
            }
        }

        // Scan upward from originY to originY + 10
        for (int y = originY; y <= originY + 10; y++) {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y + 1.0;
            }
        }

        // Final fallback
        LOGGER.atWarning().log( "[Nat20] No ground found at " + x + ", " + z +
            " near originY=" + originY + ": using fallback Y");
        return originY + 1.0;
    }
}
