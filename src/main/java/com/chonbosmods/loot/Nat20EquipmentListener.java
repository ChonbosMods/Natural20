package com.chonbosmods.loot;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.Nat20ScoreDirtyFlag;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for equipment changes on players and applies/removes Nat20 loot stat modifiers
 * via the modifier manager. Maintains a per-player cache of equipped loot data so that
 * old modifiers can be cleanly removed when items are swapped out.
 */
public class Nat20EquipmentListener {

    public record EquippedEntry(Nat20LootData lootData, @Nullable Nat20ItemDisplayData displayData) {}

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final String ARMOR_PREFIX = "armor_";
    private static final String HOTBAR_PREFIX = "hotbar_";

    private final Nat20LootSystem lootSystem;

    /**
     * Cache of currently-equipped loot data per player UUID per slot name.
     * Used to remove old modifiers when a slot changes, since the event fires
     * after the inventory mutation (so the old item is no longer accessible).
     */
    private final Map<UUID, Map<String, EquippedEntry>> equippedCache = new ConcurrentHashMap<>();

    public Nat20EquipmentListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    /**
     * Create the ECS event system to be registered via entityStoreRegistry.registerSystem().
     */
    public Nat20InventoryChangeSystem createSystem() {
        return new Nat20InventoryChangeSystem();
    }

    /**
     * Get all currently-equipped loot data for a player, keyed by slot name.
     * Used by Nat20ScoreBonusSystem to recompute affix modifiers when scores change.
     */
    public Map<String, EquippedEntry> getEquippedItems(UUID playerId) {
        return equippedCache.getOrDefault(playerId, Map.of());
    }

    /**
     * Remove all cached data for a player (call on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        equippedCache.remove(playerId);
    }

    /**
     * Get the cached display data for a player's equipped item in a given slot.
     */
    @Nullable
    public Nat20ItemDisplayData getEquippedDisplayData(UUID playerId, String slotName) {
        Map<String, EquippedEntry> playerCache = equippedCache.get(playerId);
        if (playerCache == null) return null;
        EquippedEntry entry = playerCache.get(slotName);
        return entry != null ? entry.displayData() : null;
    }

    public class Nat20InventoryChangeSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

        private static final Query<EntityStore> QUERY = Query.any();

        Nat20InventoryChangeSystem() {
            super(InventoryChangeEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                           InventoryChangeEvent event) {
            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            Transaction transaction = event.getTransaction();
            if (!transaction.succeeded()) return;

            // Determine which container changed and its slot prefix
            ComponentType<EntityStore, ?> componentType = event.getComponentType();
            String slotPrefix;
            if (componentType == InventoryComponent.Armor.getComponentType()) {
                slotPrefix = ARMOR_PREFIX;
            } else if (componentType == InventoryComponent.Hotbar.getComponentType()) {
                slotPrefix = HOTBAR_PREFIX;
            } else {
                return; // Only process armor and hotbar changes
            }

            ItemContainer changedContainer = event.getItemContainer();

            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;

            UUID playerId = player.getPlayerRef().getUuid();
            Map<String, EquippedEntry> playerCache = equippedCache.computeIfAbsent(playerId,
                    k -> new ConcurrentHashMap<>());

            // Resolve player stats for scaling
            Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
            PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

            Nat20ModifierManager modifierManager = lootSystem.getModifierManager();
            Nat20LootEntryRegistry entryRegistry = lootSystem.getLootEntryRegistry();
            short capacity = changedContainer.getCapacity();

            for (short slot = 0; slot < capacity; slot++) {
                if (!transaction.wasSlotModified(slot)) continue;

                String slotName = slotPrefix + slot;

                // Remove old modifiers using cached loot data
                EquippedEntry oldEntry = playerCache.remove(slotName);
                if (oldEntry != null) {
                    modifierManager.removeModifiers(statMap, slotName, oldEntry.lootData(), playerData, playerId);
                }

                // Apply new modifiers if the new item has loot data
                ItemStack newStack = changedContainer.getItemStack(slot);
                if (newStack == null) continue;

                Nat20LootData newLootData = newStack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
                if (newLootData == null) continue;

                String categoryKey = resolveCategoryKey(entryRegistry, newStack);
                if (categoryKey == null) continue;

                modifierManager.applyModifiers(statMap, newStack, slotName, categoryKey, playerStats, playerData, playerId);
                Nat20ItemDisplayData displayData = lootSystem.getItemRenderer().resolve(newStack, playerStats);
                playerCache.put(slotName, new EquippedEntry(newLootData, displayData));
            }
            Nat20ScoreDirtyFlag.markDirty(playerId);
        }
    }

    @Nullable
    private String resolveCategoryKey(Nat20LootEntryRegistry entryRegistry, ItemStack stack) {
        String itemId = stack.getItemId();

        // Check manual registry first
        String manualKey = entryRegistry.getManualCategoryKey(itemId);
        if (manualKey != null) return manualKey;

        // Auto-detect from item asset data
        return autoDetectCategory(itemId);
    }

    @Nullable
    private String autoDetectCategory(String itemId) {
        try {
            var item = Item.getAssetMap().getAsset(itemId);
            if (item == null) return null;
            if (item.getWeapon() != null) return "melee_weapon";
            if (item.getArmor() != null) return "armor";
            if (item.getTool() != null) return "tool";
            if (item.getUtility() != null) return "utility";
        } catch (Exception e) {
            // Asset not found
        }
        return null;
    }
}
