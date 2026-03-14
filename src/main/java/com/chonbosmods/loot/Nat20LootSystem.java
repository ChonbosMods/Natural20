package com.chonbosmods.loot;

import com.chonbosmods.loot.effects.EffectHandlerRegistry;
import com.chonbosmods.loot.effects.Nat20AffixEventListener;
import com.chonbosmods.loot.effects.RadialMiningHandler;
import com.chonbosmods.loot.effects.RevitalizingEffectHandler;
import com.chonbosmods.loot.effects.TelepathicHandler;
import com.chonbosmods.loot.effects.ThunderstruckEffectHandler;
import com.chonbosmods.loot.effects.VampiricEffectHandler;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.chonbosmods.loot.registry.Nat20MobAffixRegistry;
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
    private final Nat20MobAffixRegistry mobAffixRegistry = new Nat20MobAffixRegistry();
    private final EffectHandlerRegistry effectHandlerRegistry = new EffectHandlerRegistry();
    private final Nat20AffixEventListener affixEventListener;
    private final Nat20LootPipeline pipeline;
    private final Nat20ModifierManager modifierManager;
    private final Nat20ItemRenderer itemRenderer;

    public Nat20LootSystem() {
        this.affixEventListener = new Nat20AffixEventListener(this);
        this.pipeline = new Nat20LootPipeline(rarityRegistry, affixRegistry);
        this.modifierManager = new Nat20ModifierManager(rarityRegistry, affixRegistry, gemRegistry);
        this.itemRenderer = new Nat20ItemRenderer(rarityRegistry, affixRegistry, gemRegistry);
        registerEffectHandlers();
    }

    private void registerEffectHandlers() {
        effectHandlerRegistry.register("nat20:vampiric", new VampiricEffectHandler());
        effectHandlerRegistry.register("nat20:thunderstruck", new ThunderstruckEffectHandler());
        effectHandlerRegistry.register("nat20:revitalizing", new RevitalizingEffectHandler());
        effectHandlerRegistry.register("nat20:radial", new RadialMiningHandler());
        effectHandlerRegistry.register("nat20:telepathic", new TelepathicHandler());
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
        Path mobAffixesDir = lootDataDir != null ? lootDataDir.resolve("mob_affixes") : null;

        rarityRegistry.loadAll(raritiesDir);
        affixRegistry.loadAll(affixesDir);
        gemRegistry.loadAll(gemsDir);
        lootEntryRegistry.loadAll(entriesDir);
        mobAffixRegistry.loadAll(mobAffixesDir);

        LOGGER.atInfo().log("Loot system loaded: %d rarities, %d affixes, %d gems, %d entry tags, %d mob affixes",
            rarityRegistry.getLoadedCount(),
            affixRegistry.getLoadedCount(),
            gemRegistry.getLoadedCount(),
            lootEntryRegistry.getLoadedCount(),
            mobAffixRegistry.getLoadedCount()
        );
    }

    public Nat20RarityRegistry getRarityRegistry() { return rarityRegistry; }
    public Nat20AffixRegistry getAffixRegistry() { return affixRegistry; }
    public Nat20GemRegistry getGemRegistry() { return gemRegistry; }
    public Nat20LootEntryRegistry getLootEntryRegistry() { return lootEntryRegistry; }
    public Nat20MobAffixRegistry getMobAffixRegistry() { return mobAffixRegistry; }
    public EffectHandlerRegistry getEffectHandlerRegistry() { return effectHandlerRegistry; }
    public Nat20LootPipeline getPipeline() { return pipeline; }
    public Nat20ModifierManager getModifierManager() { return modifierManager; }
    public Nat20ItemRenderer getItemRenderer() { return itemRenderer; }
}
