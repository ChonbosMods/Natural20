package com.chonbosmods.loot;

import com.chonbosmods.loot.effects.EffectHandlerRegistry;
import com.chonbosmods.loot.effects.Nat20AffixEventListener;
import com.chonbosmods.loot.mob.Nat20MobAffixManager;
import com.chonbosmods.loot.mob.Nat20MobLootListener;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20ItemRegistry;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.chonbosmods.loot.registry.Nat20NamePoolRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.nio.file.Path;

public class Nat20LootSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20RarityRegistry rarityRegistry = new Nat20RarityRegistry();
    private final Nat20AffixRegistry affixRegistry = new Nat20AffixRegistry();
    private final Nat20GemRegistry gemRegistry = new Nat20GemRegistry();
    private final Nat20LootEntryRegistry lootEntryRegistry = new Nat20LootEntryRegistry();
    private final Nat20NamePoolRegistry namePoolRegistry = new Nat20NamePoolRegistry();
    private final EffectHandlerRegistry effectHandlerRegistry = new EffectHandlerRegistry();
    private final Nat20MobNameGenerator mobNameGenerator = new Nat20MobNameGenerator();
    private final Nat20MobAffixManager mobAffixManager;
    private final Nat20MobLootListener mobLootListener;
    private final Nat20AffixEventListener affixEventListener;
    private final Nat20LootPipeline pipeline;
    private final Nat20ModifierManager modifierManager;
    private final Nat20ItemRenderer itemRenderer;
    private final Nat20ItemRegistry itemRegistry;
    private final Nat20ItemGarbageCollector garbageCollector;

    public Nat20LootSystem() {
        this.mobAffixManager = new Nat20MobAffixManager(affixRegistry, mobNameGenerator);
        this.mobLootListener = new Nat20MobLootListener(this);
        this.affixEventListener = new Nat20AffixEventListener(this);
        this.modifierManager = new Nat20ModifierManager(rarityRegistry, affixRegistry, gemRegistry);
        this.itemRenderer = new Nat20ItemRenderer(rarityRegistry, affixRegistry, gemRegistry);
        this.itemRegistry = new Nat20ItemRegistry(itemRenderer);
        this.garbageCollector = new Nat20ItemGarbageCollector(itemRegistry);
        this.pipeline = new Nat20LootPipeline(rarityRegistry, affixRegistry, itemRegistry, namePoolRegistry);
    }

    /**
     * Register ECS event systems for EFFECT/ABILITY affix processing.
     * Call this during plugin setup.
     */
    public void registerSystems(ComponentRegistryProxy<EntityStore> entityStoreRegistry) {
        affixEventListener.register(entityStoreRegistry);
    }

    public void loadAll(@Nullable Path lootDataDir) {
        Path raritiesDir = lootDataDir != null ? lootDataDir.resolve("rarities") : null;
        Path affixesDir = lootDataDir != null ? lootDataDir.resolve("affixes") : null;
        Path gemsDir = lootDataDir != null ? lootDataDir.resolve("gems") : null;
        Path entriesDir = lootDataDir != null ? lootDataDir.resolve("entries") : null;

        rarityRegistry.loadAll(raritiesDir);
        affixRegistry.loadAll(affixesDir);
        gemRegistry.loadAll(gemsDir);
        lootEntryRegistry.loadAll(entriesDir);
        namePoolRegistry.loadAll();
        mobNameGenerator.load();
        // itemRegistry.init is called separately from the world-ready hook in Natural20
        // so the persistent nat20_items.json lives in the world directory, not the
        // plugin directory (so wiping the world also wipes generated affix items).

        LOGGER.atInfo().log("Loot system loaded: %d rarities, %d affixes, %d gems, %d entry tags, %d name pools",
            rarityRegistry.getLoadedCount(),
            affixRegistry.getLoadedCount(),
            gemRegistry.getLoadedCount(),
            lootEntryRegistry.getLoadedCount(),
            namePoolRegistry.getLoadedCount()
        );
    }

    public Nat20RarityRegistry getRarityRegistry() { return rarityRegistry; }
    public Nat20AffixRegistry getAffixRegistry() { return affixRegistry; }
    public Nat20GemRegistry getGemRegistry() { return gemRegistry; }
    public Nat20LootEntryRegistry getLootEntryRegistry() { return lootEntryRegistry; }
    public Nat20NamePoolRegistry getNamePoolRegistry() { return namePoolRegistry; }
    public EffectHandlerRegistry getEffectHandlerRegistry() { return effectHandlerRegistry; }
    public Nat20MobAffixManager getMobAffixManager() { return mobAffixManager; }
    public Nat20MobNameGenerator getMobNameGenerator() { return mobNameGenerator; }
    public Nat20MobLootListener getMobLootListener() { return mobLootListener; }
    public Nat20LootPipeline getPipeline() { return pipeline; }
    public Nat20ModifierManager getModifierManager() { return modifierManager; }
    public Nat20ItemRenderer getItemRenderer() { return itemRenderer; }
    public Nat20ItemRegistry getItemRegistry() { return itemRegistry; }
    public Nat20ItemGarbageCollector getGarbageCollector() { return garbageCollector; }
}
