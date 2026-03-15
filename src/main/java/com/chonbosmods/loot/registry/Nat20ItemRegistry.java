package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.display.Nat20TooltipStringBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.Universe;

import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Nat20ItemRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAT20_PREFIX = "nat20:";

    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis() & 0xFFFFFFFFL);
    private final Map<String, RegistryEntry> registered = new ConcurrentHashMap<>();
    private final Nat20ItemRenderer itemRenderer;
    private Path registryFile;

    @Nullable
    private Field languagesField;
    @Nullable
    private Field cachedLanguagesField;
    @Nullable
    private Field itemIdField;
    @Nullable
    private Field itemQualityIdField;
    @Nullable
    private Field itemTranslationPropertiesField;
    @Nullable
    private java.lang.reflect.Method processConfigMethod;

    public Nat20ItemRegistry(Nat20ItemRenderer itemRenderer) {
        this.itemRenderer = itemRenderer;
    }

    public void init(Path dataDir) {
        this.registryFile = dataDir.resolve("nat20_items.json");
        initReflection();
        injectRarityLabels();
        loadFromDisk();
    }

    @Nullable
    public String registerItem(String baseItemId, String rarityQualityId, Nat20LootData lootData) {
        String uniqueId = generateUniqueId(baseItemId, lootData.getRarity());

        Item baseItem = Item.getAssetMap().getAsset(baseItemId);
        if (baseItem == null) {
            LOGGER.atWarning().log("Base item not found: %s", baseItemId);
            return null;
        }

        Nat20ItemDisplayData displayData = itemRenderer.resolve(lootData, null);
        String description = "";
        if (displayData != null) {
            description = Nat20TooltipStringBuilder.buildDescription(displayData);
        }

        // Name: literal text (client falls back on I18n miss)
        // Description: I18n key (injected into server map, broadcast to clients via cache invalidation)
        String itemName = lootData.getGeneratedName();
        String descKey = "server.nat20.item." + uniqueId + ".description";
        injectI18n("en-US", descKey, description);

        Item variant = new Item(baseItem);
        if (!setItemFields(variant, uniqueId, rarityQualityId, itemName, descKey)) {
            LOGGER.atSevere().log("Failed to set fields on item variant: %s", uniqueId);
            return null;
        }

        RegistryEntry entry = new RegistryEntry(
                baseItemId, rarityQualityId, lootData.getGeneratedName(),
                description, System.currentTimeMillis() / 1000L
        );
        registered.put(uniqueId, entry);
        saveToDisk();

        // Register on a separate thread to avoid deadlock:
        // loadAssets() acquires AssetRegistry.ASSET_LOCK which conflicts with
        // the ECS thread context that commands execute on.
        HytaleServer.SCHEDULED_EXECUTOR.submit(() -> {
            try {
                // Send just our new description key to connected clients
                broadcastTranslations(Map.of(descKey, entry.description()));
                // Then register the item asset
                Item.getAssetStore().loadAssets(uniqueId, List.of(variant));
                LOGGER.atInfo().log("Registered unique item: %s (base=%s)", uniqueId, baseItemId);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to register item asset: %s", uniqueId);
            }
        });

        return uniqueId;
    }

    public void unregisterItem(String uniqueId) {
        RegistryEntry entry = registered.remove(uniqueId);
        if (entry == null) return;

        try {
            Item.getAssetStore().removeAssets(List.of(uniqueId));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove item asset: %s", uniqueId);
        }

        String descKey = "server.nat20.item." + uniqueId + ".description";
        removeI18n("en-US", descKey);

        saveToDisk();
        LOGGER.atInfo().log("Unregistered unique item: %s", uniqueId);
    }

    public void rehydrateAll() {
        if (registered.isEmpty()) return;

        for (Map.Entry<String, RegistryEntry> e : registered.entrySet()) {
            String uniqueId = e.getKey();
            RegistryEntry entry = e.getValue();

            Item baseItem = Item.getAssetMap().getAsset(entry.baseItemId);
            if (baseItem == null) {
                LOGGER.atWarning().log("Cannot rehydrate %s: base item %s not found", uniqueId, entry.baseItemId);
                continue;
            }

            // Inject I18n BEFORE loadAssets so toPacket() can resolve the key
            String descKey = "server.nat20.item." + uniqueId + ".description";
            injectI18n("en-US", descKey, entry.description);

            Item variant = new Item(baseItem);
            if (!setItemFields(variant, uniqueId, entry.qualityId, entry.generatedName, descKey)) {
                LOGGER.atWarning().log("Failed to set fields on rehydrated item: %s", uniqueId);
                continue;
            }

            try {
                Item.getAssetStore().loadAssets(uniqueId, List.of(variant));
            } catch (Exception ex) {
                LOGGER.atWarning().withCause(ex).log("Failed to rehydrate item: %s", uniqueId);
                continue;
            }
        }

        LOGGER.atInfo().log("Rehydrated %d unique item definitions", registered.size());
    }

    public void reinjectAllI18n() {
        for (Map.Entry<String, RegistryEntry> e : registered.entrySet()) {
            String uniqueId = e.getKey();
            RegistryEntry entry = e.getValue();
            String descKey = "server.nat20.item." + uniqueId + ".description";
            injectI18n("en-US", descKey, entry.description);
        }
    }

    public boolean isNat20Item(String itemId) {
        return itemId != null && itemId.startsWith(NAT20_PREFIX);
    }

    public boolean isRegistered(String uniqueId) {
        return registered.containsKey(uniqueId);
    }

    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(registered.keySet());
    }

    public int getRegisteredCount() {
        return registered.size();
    }

    /**
     * Inject rarity display names into I18n at boot time (before cache is built).
     */
    private void injectRarityLabels() {
        Map<String, String> labels = Map.of(
                "server.nat20.rarity.common", "Common",
                "server.nat20.rarity.uncommon", "Uncommon",
                "server.nat20.rarity.rare", "Rare",
                "server.nat20.rarity.epic", "Epic",
                "server.nat20.rarity.legendary", "Legendary"
        );
        for (var entry : labels.entrySet()) {
            injectI18n("en-US", entry.getKey(), entry.getValue());
        }
        LOGGER.atInfo().log("Injected %d rarity labels into I18n", labels.size());
    }

    private void initReflection() {
        try {
            languagesField = I18nModule.class.getDeclaredField("languages");
            languagesField.setAccessible(true);
            cachedLanguagesField = I18nModule.class.getDeclaredField("cachedLanguages");
            cachedLanguagesField.setAccessible(true);
            LOGGER.atInfo().log("I18n reflection initialized successfully");
        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().log("Failed to access I18nModule reflection: I18n tooltip injection disabled");
            languagesField = null;
            cachedLanguagesField = null;
        }

        try {
            itemIdField = Item.class.getDeclaredField("id");
            itemIdField.setAccessible(true);
            itemQualityIdField = Item.class.getDeclaredField("qualityId");
            itemQualityIdField.setAccessible(true);
            itemTranslationPropertiesField = Item.class.getDeclaredField("translationProperties");
            itemTranslationPropertiesField.setAccessible(true);
            processConfigMethod = Item.class.getDeclaredMethod("processConfig");
            processConfigMethod.setAccessible(true);
            LOGGER.atInfo().log("Item field reflection initialized successfully");
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            LOGGER.atSevere().log("Failed to access Item protected fields: item registration disabled");
            itemIdField = null;
            itemQualityIdField = null;
            itemTranslationPropertiesField = null;
        }
    }

    private boolean setItemFields(Item item, String id, String qualityId, String displayName, String description) {
        if (itemIdField == null || itemQualityIdField == null || itemTranslationPropertiesField == null) return false;
        try {
            itemIdField.set(item, id);
            itemQualityIdField.set(item, qualityId);
            itemTranslationPropertiesField.set(item, new ItemTranslationProperties(displayName, description));
            // processConfig resolves qualityId → qualityIndex for client rendering
            if (processConfigMethod != null) {
                processConfigMethod.invoke(item);
            }
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to set Item fields via reflection");
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Map<String, Map<String, String>> getLanguagesMap() {
        if (languagesField == null) return null;
        try {
            return (Map<String, Map<String, String>>) languagesField.get(I18nModule.get());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read I18nModule.languages");
            return null;
        }
    }

    private void injectI18n(String language, String key, String value) {
        Map<String, Map<String, String>> languages = getLanguagesMap();
        if (languages == null) return;
        languages.computeIfAbsent(language, k -> new HashMap<>()).put(key, value);
    }

    private void removeI18n(String language, String key) {
        Map<String, Map<String, String>> languages = getLanguagesMap();
        if (languages == null) return;
        Map<String, String> langMap = languages.get(language);
        if (langMap != null) {
            langMap.remove(key);
        }
    }

    /**
     * Invalidate the cached language snapshot (so reconnecting players get our keys)
     * and send just the new entries to connected clients via AddOrUpdate.
     */
    @SuppressWarnings("unchecked")
    private void broadcastTranslations(Map<String, String> entries) {
        try {
            // Clear the cached snapshot so future getMessages/sendTranslations includes our keys
            if (cachedLanguagesField != null) {
                Map<String, ?> cached = (Map<String, ?>) cachedLanguagesField.get(I18nModule.get());
                if (cached != null) {
                    cached.remove("en-US");
                }
            }

            // Send ONLY the new entries to connected clients (preserves all existing translations)
            UpdateTranslations packet = new UpdateTranslations(UpdateType.AddOrUpdate, entries);
            Universe.get().broadcastPacketNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to broadcast translations");
        }
    }

    private String generateUniqueId(String baseItemId, String rarityId) {
        String name = baseItemId;
        int colon = baseItemId.indexOf(':');
        if (colon >= 0) {
            name = baseItemId.substring(colon + 1);
        }

        StringBuilder snake = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                snake.append('_');
            }
            snake.append(Character.toLowerCase(ch));
        }

        String hash = String.format("%08x", counter.getAndIncrement());
        return NAT20_PREFIX + snake + "_" + rarityId.toLowerCase() + "_" + hash;
    }

    private void loadFromDisk() {
        if (registryFile == null || !Files.exists(registryFile)) return;
        try {
            String json = Files.readString(registryFile);
            Type type = new TypeToken<Map<String, RegistryEntry>>() {}.getType();
            Map<String, RegistryEntry> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                registered.putAll(loaded);
                LOGGER.atInfo().log("Loaded %d item entries from registry file", loaded.size());
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load item registry from disk");
        }
    }

    private void saveToDisk() {
        if (registryFile == null) return;
        try {
            Files.createDirectories(registryFile.getParent());
            Files.writeString(registryFile, GSON.toJson(registered));
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save item registry to disk");
        }
    }

    public record RegistryEntry(
            String baseItemId,
            String qualityId,
            String generatedName,
            String description,
            long createdAt
    ) {}
}
