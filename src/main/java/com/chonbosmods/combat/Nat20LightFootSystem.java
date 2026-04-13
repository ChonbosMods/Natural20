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
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Light Foot: reduces stamina drain while sprinting by compensating the drain.
 * Sprint drain is hardcoded at -1.0 stamina/sec in the Stamina stat Regenerating config.
 * This system detects sprinting via position delta and adds stamina back proportionally.
 *
 * DEX-scaled. Armor affix.
 */
public class Nat20LightFootSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:light_foot";
    private static final double SOFTCAP_K = 0.50;
    private static final float SPRINT_DRAIN_PER_SEC = 1.0f;
    // Sprint speed threshold (squared distance per tick for ~5.5 m/s sprint)
    private static final double SPRINT_THRESHOLD_SQ = 0.02;
    private static final int LOG_INTERVAL_TICKS = 40;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private final ConcurrentHashMap<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();

    private int staminaIdx = Integer.MIN_VALUE;
    private boolean statResolved;
    private int tickCounter;

    public Nat20LightFootSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (dt <= 0.0f) return;

        if (!statResolved) {
            staminaIdx = EntityStatType.getAssetMap().getIndex("Stamina");
            statResolved = true;
        }
        if (staminaIdx < 0) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID uuid = uuidComp.getUuid();

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d pos = transform.getPosition();
        Vector3d lastPos = lastPositions.put(uuid, new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
        if (lastPos == null) return;

        // Check if sprinting (moving fast on XZ plane)
        double dx = pos.getX() - lastPos.getX();
        double dz = pos.getZ() - lastPos.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < SPRINT_THRESHOLD_SQ) return; // Not sprinting

        // Check stamina is actually draining (below max = probably sprinting)
        EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        float currentStamina = statMap.get(staminaIdx).get();
        float maxStamina = statMap.get(staminaIdx).getMax();
        if (currentStamina >= maxStamina) return; // Full stamina = not draining

        // Scan armor for light foot affix
        double totalReduction = scanArmorForAffix(playerRef, store);
        if (totalReduction <= 0) return;
        totalReduction = Nat20Softcap.softcap(totalReduction, SOFTCAP_K);

        // Compensate: add back a portion of the drain
        float compensation = (float) (dt * SPRINT_DRAIN_PER_SEC * totalReduction);
        statMap.addStatValue(staminaIdx, compensation);

        tickCounter++;
        if (tickCounter >= LOG_INTERVAL_TICKS) {
            tickCounter = 0;
            if (CombatDebugSystem.isEnabled(uuid)) {
                LOGGER.atInfo().log("[LightFoot] player=%s reduction=%.1f%% compensation=%.4f/tick stamina=%.1f",
                        uuid.toString().substring(0, 8), totalReduction * 100, compensation, currentStamina);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        lastPositions.remove(uuid);
    }

    private double scanArmorForAffix(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, playerRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer == null) return 0;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double total = 0;

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            for (RolledAffix rolledAffix : lootData.getAffixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) continue;

                AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
                if (range == null) continue;

                double baseValue = range.interpolate(lootData.getLootLevel());
                double effectiveValue = baseValue;
                PlayerStats stats = resolvePlayerStats(playerRef, store);
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                total += effectiveValue;
            }
        }
        return total;
    }

    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
