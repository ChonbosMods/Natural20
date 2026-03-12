package com.chonbosmods.loot;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
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

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final String ARMOR_PREFIX = "armor_";
    private static final String HOTBAR_PREFIX = "hotbar_";

    private final Nat20LootSystem lootSystem;

    /**
     * Cache of currently-equipped loot data per player UUID per slot name.
     * Used to remove old modifiers when a slot changes, since the event fires
     * after the inventory mutation (so the old item is no longer accessible).
     */
    private final Map<UUID, Map<String, Nat20LootData>> equippedCache = new ConcurrentHashMap<>();

    public Nat20EquipmentListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    public void register(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
    }

    /**
     * Remove all cached data for a player (call on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        equippedCache.remove(playerId);
    }

    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Transaction transaction = event.getTransaction();
        if (!transaction.succeeded()) return;

        Inventory inventory = player.getInventory();
        ItemContainer changedContainer = event.getItemContainer();

        // Determine which container changed and its slot prefix
        String slotPrefix;
        if (changedContainer == inventory.getArmor()) {
            slotPrefix = ARMOR_PREFIX;
        } else if (changedContainer == inventory.getHotbar()) {
            slotPrefix = HOTBAR_PREFIX;
        } else {
            return; // Only process armor and hotbar changes
        }

        // Resolve store and ref once for all component lookups
        Store<EntityStore> store;
        Ref<EntityStore> ref;
        try {
            store = player.getWorld().getEntityStore().getStore();
            ref = player.getReference();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to resolve entity store for player");
            return;
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        UUID playerId = player.getPlayerRef().getUuid();
        Map<String, Nat20LootData> playerCache = equippedCache.computeIfAbsent(playerId,
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
            Nat20LootData oldLootData = playerCache.remove(slotName);
            if (oldLootData != null) {
                modifierManager.removeModifiers(statMap, slotName, oldLootData);
            }

            // Apply new modifiers if the new item has loot data
            ItemStack newStack = changedContainer.getItemStack(slot);
            if (newStack == null) continue;

            Nat20LootData newLootData = newStack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (newLootData == null) continue;

            String categoryKey = resolveCategoryKey(entryRegistry, newStack);
            if (categoryKey == null) continue;

            modifierManager.applyModifiers(statMap, newStack, slotName, categoryKey, playerStats);
            playerCache.put(slotName, newLootData);
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
