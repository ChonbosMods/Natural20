package com.chonbosmods.npc;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
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
            SettlementType type, Vector3d origin, String cellKey, long nameSalt) {

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

            // Generate name deterministically from settlement + NPC index + world-unique salt
            String name = Nat20NameGenerator.generate(cellKey.hashCode() * 31L + npcIndex + nameSalt);
            npcIndex++;

            // Create model from unmodified skin: engine serialization breaks
            // on modified skins (beard/hair changes cause scale=0 on chunk reload)
            Random skinRng = new Random(name.hashCode());
            com.hypixel.hytale.protocol.PlayerSkin baseSkin =
                CosmeticsModule.get().generateRandomSkin(skinRng);
            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                CosmeticsModule.get().createModel(baseSkin, 1.0f);

            Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, def.rotation(), model, null);

            if (result != null) {
                Ref<EntityStore> npcRef = result.first();
                NPCEntity npcEntity = result.second();

                NpcRecord npcRecord = new NpcRecord(
                    roleName, npcEntity.getUuid(),
                    spawnX, spawnY, spawnZ,
                    def.rotation().getX(), def.rotation().getY(), def.rotation().getZ(),
                    def.leashRadius(), name);
                int dispMin = Math.max(0, def.dispositionMin());
                int dispMax = Math.min(100, def.dispositionMax());
                if (dispMax < dispMin) dispMax = dispMin;
                npcRecord.setDisposition(dispMin == dispMax
                    ? dispMin
                    : ThreadLocalRandom.current().nextInt(dispMin, dispMax + 1));

                applyNpcComponents(store, npcRef, npcEntity, npcRecord, cellKey, false, world);
                spawned.add(npcRecord);

                LOGGER.atFine().log("[Nat20] Spawned " + formatDisplayName(name, roleName) + " at " +
                    (int) spawnX + ", " + (int) spawnY + ", " + (int) spawnZ);
            } else {
                LOGGER.atWarning().log("[Nat20] Failed to spawn " + roleName + " at " +
                    (int) spawnX + ", " + (int) spawnY + ", " + (int) spawnZ);
            }
        }

        LOGGER.atFine().log("[Nat20] Spawned " + spawned.size() + "/" +
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
    public UUID respawnNpc(Store<EntityStore> store, World world, NpcRecord record, String settlementCellKey) {
        String roleName = record.getRole();
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Cannot respawn: role not found: " + roleName);
            return null;
        }

        Vector3d spawnPos = new Vector3d(record.getSpawnX(), record.getSpawnY(), record.getSpawnZ());
        Vector3f rotation = new Vector3f(record.getRotX(), record.getRotY(), record.getRotZ());

        // Create model from unmodified skin: engine serialization breaks
        // on modified skins (beard/hair changes cause scale=0 on chunk reload)
        Random skinRng = new Random(record.getGeneratedName().hashCode());
        com.hypixel.hytale.protocol.PlayerSkin baseSkin =
            CosmeticsModule.get().generateRandomSkin(skinRng);
        com.hypixel.hytale.server.core.asset.type.model.config.Model model =
            CosmeticsModule.get().createModel(baseSkin, 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result == null) {
            LOGGER.atWarning().log("Failed to respawn " + roleName);
            return null;
        }

        Ref<EntityStore> npcRef = result.first();
        NPCEntity npcEntity = result.second();

        applyNpcComponents(store, npcRef, npcEntity, record, settlementCellKey, false, world);

        UUID newUUID = npcEntity.getUuid();
        record.setEntityUUID(newUUID);

        LOGGER.atFine().log("Respawned %s (%s) with new UUID %s",
            record.getGeneratedName(), roleName, newUUID);
        return newUUID;
    }

    /**
     * Reattach custom components to a surviving NPC entity that lost them on chunk reload.
     * Preserves the entity UUID (no respawn needed).
     *
     * @param store    the entity store
     * @param npcRef   the existing entity reference
     * @param record   the NpcRecord with identity/state data
     * @param cellKey  the settlement cell key
     * @return true if reattach succeeded
     */
    public boolean reattachNpc(Store<EntityStore> store, Ref<EntityStore> npcRef,
                                NpcRecord record, String cellKey, World world) {
        NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npcEntity == null) {
            LOGGER.atWarning().log("Cannot reattach: entity has no NPCEntity component for %s",
                record.getGeneratedName());
            return false;
        }

        applyNpcComponents(store, npcRef, npcEntity, record, cellKey, false, world);

        LOGGER.atFine().log("Reattached components to %s (%s) UUID %s",
            record.getGeneratedName(), record.getRole(), record.getEntityUUID());
        return true;
    }

    /**
     * Apply all custom components to an NPC entity from its NpcRecord.
     * Used by spawn, respawn, and reattach flows.
     *
     * @param reapplyModel if true, re-creates and applies ModelComponent. Needed after
     *                     chunk reload (PersistentModel reconstructs a bare Player model).
     *                     Should be false on initial spawn (spawnEntity already set up the model).
     */
    private void applyNpcComponents(Store<EntityStore> store, Ref<EntityStore> npcRef,
                                     NPCEntity npcEntity, NpcRecord record, String cellKey,
                                     boolean reapplyModel, World world) {
        String name = record.getGeneratedName();
        String roleName = record.getRole();

        // Attach or update Nat20NpcData
        Nat20NpcData existing = store.getComponent(npcRef, Natural20.getNpcDataType());
        Nat20NpcData npcData;
        if (existing != null) {
            npcData = existing;
        } else {
            npcData = store.addComponent(npcRef, Natural20.getNpcDataType());
        }
        npcData.setGeneratedName(name);
        npcData.setRoleName(roleName);
        npcData.setSettlementCellKey(cellKey);
        npcData.setDefaultDisposition(record.getDisposition());
        npcData.setDialogueState(record.getDialogueState());
        npcData.setFlags(record.getFlags());

        // Sync quest marker state from persisted NpcRecord (survives chunk reload/respawn)
        QuestMarkerManager.INSTANCE.syncFromRecord(npcEntity.getUuid(), record);
        String persisted = record.getMarkerState();
        if ("QUEST_TURN_IN".equals(persisted)) {
            npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        } else if (record.getPreGeneratedQuest() != null) {
            npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.QUEST_AVAILABLE);
        } else {
            npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.NONE);
        }

        // Set nameplate
        if (name != null) {
            store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(name));
        }

        if (reapplyModel) {
            // Re-apply model after chunk reload (PersistentModel reconstructs
            // a bare Player model that lacks skin data and attachments)
            Random skinRng = new Random(name.hashCode());
            com.hypixel.hytale.protocol.PlayerSkin baseSkin =
                CosmeticsModule.get().generateRandomSkin(skinRng);
            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                CosmeticsModule.get().createModel(baseSkin, 1.0f);
            store.putComponent(npcRef,
                com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(model));
        }

        // Skin with cosmetic modifications (beard reduction, hair color matching)
        Random skinRng = new Random(name.hashCode());
        com.hypixel.hytale.protocol.PlayerSkin displaySkin = generateNpcSkin(skinRng);
        store.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(displaySkin));

        // Leash
        Vector3d spawnPos = new Vector3d(record.getSpawnX(), record.getSpawnY(), record.getSpawnZ());
        npcEntity.setLeashPoint(spawnPos);

        // Guard armor
        if (roleName.equals("Guard")) {
            equipGuardArmor(npcEntity);
        }
    }

    /**
     * Generate a random player skin with reduced beard chance (~20% instead of SDK's ~50%)
     * and forced hair color matching between haircut, eyebrows, and facial hair.
     */
    public static com.hypixel.hytale.protocol.PlayerSkin generateNpcSkin(Random rng) {
        com.hypixel.hytale.protocol.PlayerSkin skin = CosmeticsModule.get().generateRandomSkin(rng);

        // Reduce beard chance: SDK gives ~50%, we want ~20%
        if (skin.facialHair != null && rng.nextInt(5) != 0) {
            skin.facialHair = null;
        }

        // Force hair color matching: copy haircut's texture onto eyebrows and facialHair
        if (skin.haircut != null) {
            String hairTexture = extractTexture(skin.haircut);
            if (hairTexture != null) {
                if (skin.eyebrows != null) {
                    skin.eyebrows = replaceTexture(skin.eyebrows, hairTexture);
                }
                if (skin.facialHair != null) {
                    skin.facialHair = replaceTexture(skin.facialHair, hairTexture);
                }
            }
        }

        return skin;
    }

    /** Extract the texture portion (second dot-segment) from a skin part string like "assetId.textureId" or "assetId.textureId.variantId". */
    private static String extractTexture(String partString) {
        String[] parts = partString.split("\\.");
        return parts.length >= 2 ? parts[1] : null;
    }

    /** Replace the texture portion of a skin part string, preserving asset and variant. */
    private static String replaceTexture(String partString, String newTexture) {
        String[] parts = partString.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + newTexture + "." + parts[2];
        } else if (parts.length == 2) {
            return parts[0] + "." + newTexture;
        }
        return partString;
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
     * Scans downward from world ceiling with a final fallback.
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
