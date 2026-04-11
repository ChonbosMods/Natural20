package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Deep Wounds affix system: on melee hit, has a chance to inflict a bleed DOT on the target.
 * The bleed deals periodic health drain over 5 seconds (10 ticks at 500ms intervals).
 *
 * <p>Runs in the Inspect Group so it observes finalized damage without modifying it.
 * Overlap behavior: a new bleed replaces any existing bleed on the same target (reset ticks).
 */
public class Nat20DeepWoundsSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:deep_wounds";
    private static final int BLEED_TICKS = 10;
    private static final long TICK_INTERVAL_MS = 500L;

    private final Nat20LootSystem lootSystem;
    private final ConcurrentHashMap<Ref<EntityStore>, BleedState> activeBleeds = new ConcurrentHashMap<>();
    private ScheduledExecutorService bleedExecutor;
    public Nat20DeepWoundsSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        // Extract attacker: must be a player via EntitySource
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();

        // Get attacker's weapon
        ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        // Scan weapon affixes for deep_wounds
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            // Parse proc chance and roll
            double procChance = parseProcChance(def.procChance());
            if (procChance <= 0) return;
            if (ThreadLocalRandom.current().nextDouble() > procChance) return;

            // Compute effective bleed total
            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            PlayerStats playerStats = resolvePlayerStats(attackerRef, store);
            if (playerStats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }

            double bleedTotal = Nat20Softcap.softcap(effectiveValue, 12.0);
            double perTickDamage = bleedTotal / BLEED_TICKS;

            // Apply bleed to target (replace if already bleeding)
            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
            activeBleeds.put(targetRef, new BleedState(perTickDamage, BLEED_TICKS));

            // Debug logging
            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[DeepWounds] proc: attacker=%s base=%.2f effective=%.2f total=%.2f perTick=%.2f ticks=%d",
                        attackerUuid.toString().substring(0, 8),
                        baseValue, effectiveValue, bleedTotal, perTickDamage, BLEED_TICKS);
            }

            // Only process the first matching affix
            return;
        }
    }

    /**
     * Start the bleed tick executor. Called from Natural20.start() after loot system is loaded.
     */
    public void startBleedTicker() {
        bleedExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-deep-wounds-bleed");
            t.setDaemon(true);
            return t;
        });
        bleedExecutor.scheduleAtFixedRate(this::tickBleeds, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Shut down the bleed executor. Called from Natural20.shutdown().
     */
    public void shutdown() {
        if (bleedExecutor != null) {
            bleedExecutor.shutdownNow();
        }
        activeBleeds.clear();
    }

    /**
     * Periodic bleed tick: dispatches to the world thread to apply health drain.
     */
    private void tickBleeds() {
        if (activeBleeds.isEmpty()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int healthIdx = EntityStatType.getAssetMap().getIndex("Health");
            if (healthIdx < 0) return;

            Iterator<Map.Entry<Ref<EntityStore>, BleedState>> it = activeBleeds.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Ref<EntityStore>, BleedState> entry = it.next();
                Ref<EntityStore> targetRef = entry.getKey();
                BleedState state = entry.getValue();

                if (!targetRef.isValid()) {
                    it.remove();
                    continue;
                }

                EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
                if (statMap == null) {
                    it.remove();
                    continue;
                }

                statMap.subtractStatValue(healthIdx, (float) state.perTickDamage);
                state.remainingTicks--;

                if (state.remainingTicks <= 0) {
                    it.remove();
                }
            }
        });
    }

    /**
     * Parse a proc chance string like "25%" into a double (0.25).
     * Returns 0 if the string is null or unparseable.
     */
    private static double parseProcChance(@Nullable String procChanceStr) {
        if (procChanceStr == null || procChanceStr.isEmpty()) return 0.0;
        try {
            String trimmed = procChanceStr.strip();
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100.0;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            LOGGER.atWarning().log("Failed to parse proc chance: '%s'", procChanceStr);
            return 0.0;
        }
    }

    /**
     * Resolve the player's D&D stats for affix scaling, or null if unavailable.
     */
    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mutable bleed state tracked per bleeding entity.
     */
    private static class BleedState {
        final double perTickDamage;
        int remainingTicks;

        BleedState(double perTickDamage, int remainingTicks) {
            this.perTickDamage = perTickDamage;
            this.remainingTicks = remainingTicks;
        }
    }
}
