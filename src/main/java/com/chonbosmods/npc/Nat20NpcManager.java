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
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

        int npcIndex = 0;
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

            // Generate name deterministically from settlement + NPC index (known before spawn)
            String name = Nat20NameGenerator.generate(java.util.Objects.hash(cellKey, npcIndex));
            npcIndex++;

            // Create skin model before spawn so it's passed through the engine's model system
            Model skinModel = createSkinModel(name);

            Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, def.rotation(), skinModel, null, null);

            if (result != null) {
                Ref<EntityStore> npcRef = result.first();
                NPCEntity npcEntity = result.second();

                // Attach Nat20NpcData component with generated name
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

                // Apply skin component (appearance data, model already set via spawnEntity)
                applySkinComponent(store, npcRef, name);

                // Equip guard armor
                if (roleName.equals("Guard")) {
                    equipGuardArmor(npcEntity);
                }

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

        // Create skin model before spawn so it's passed through the engine's model system
        Model skinModel = createSkinModel(record.getGeneratedName());

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, skinModel, null, null);

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

        // Apply skin component (appearance data, model already set via spawnEntity)
        applySkinComponent(store, npcRef, record.getGeneratedName());

        // Equip guard armor
        if (roleName.equals("Guard")) {
            equipGuardArmor(npcEntity);
        }

        UUID newUUID = npcEntity.getUuid();
        record.setEntityUUID(newUUID);

        LOGGER.atInfo().log("Respawned " + displayName + " at " +
            (int) spawnPos.getX() + ", " + (int) spawnPos.getY() + ", " + (int) spawnPos.getZ());
        return newUUID;
    }

    /**
     * Equip a Guard NPC with cobalt armor.
     * Armor slots: 0=helmet, 1=chestplate, 2=gauntlets, 3=greaves.
     */
    private void equipGuardArmor(NPCEntity npcEntity) {
        try {
            var armor = npcEntity.getInventory().getArmor();
            armor.setItemStackForSlot((short) 0, new ItemStack("Armor_Cobalt_Head"));
            armor.setItemStackForSlot((short) 1, new ItemStack("Armor_Cobalt_Chest"));
            armor.setItemStackForSlot((short) 2, new ItemStack("Armor_Cobalt_Hands"));
            armor.setItemStackForSlot((short) 3, new ItemStack("Armor_Cobalt_Legs"));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to equip guard armor");
        }
    }

    /**
     * Create a player-model from a deterministic skin for an NPC.
     * Passed to spawnEntity so the engine handles model persistence natively.
     */
    private Model createSkinModel(String generatedName) {
        Random rng = new Random(generatedName.hashCode());
        com.hypixel.hytale.protocol.PlayerSkin skin = CosmeticsModule.get().generateRandomSkin(rng);
        return CosmeticsModule.get().createModel(skin, 1.0f);
    }

    /**
     * Apply the PlayerSkinComponent for appearance data.
     * Model is already set via spawnEntity; this provides the skin texture/metadata.
     */
    private void applySkinComponent(Store<EntityStore> store, Ref<EntityStore> npcRef, String generatedName) {
        Random rng = new Random(generatedName.hashCode());
        com.hypixel.hytale.protocol.PlayerSkin skin = CosmeticsModule.get().generateRandomSkin(rng);

        store.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(skin));
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
        // Scan downward from world ceiling: same approach as settlement placement
        for (int y = 256; y >= 0; y--) {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y + 1.0;
            }
        }

        // Final fallback
        LOGGER.atWarning().log("[Nat20] No ground found at %d, %d near originY=%d: using fallback Y", x, z, originY);
        return originY + 1.0;
    }
}
