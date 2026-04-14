package com.chonbosmods.mining;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.Nat20Softcap;
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
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Haste: increases block-break damage-per-tick by a percentage when the player is holding
 * a tool with the nat20:haste affix.
 *
 * <p>Hooks {@link DamageBlockEvent} which the vanilla engine fires on the player entity before
 * applying damage to the target block. We multiply {@code event.getDamage()} by {@code 1 + bonus}
 * and write it back via {@code setDamage()}.
 *
 * <p>The bonus is the affix's interpolated value, optionally scaled by DEX modifier and softcapped.
 */
public class Nat20HasteSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:haste";
    private static final double SOFTCAP_K = 0.40;

    private final Nat20LootSystem lootSystem;

    public Nat20HasteSystem(Nat20LootSystem lootSystem) {
        super(DamageBlockEvent.class);
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       DamageBlockEvent event) {
        if (event.isCancelled()) return;

        ItemStack tool = event.getItemInHand();
        if (tool == null || tool.isEmpty()) return;

        Nat20LootData lootData = tool.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        // Debug override: uniqueItemId="debug:haste:<number>" applies the raw bonus directly,
        // bypassing stat scaling and softcap. Used by /nat20 testhaste for visible speed tests.
        String uid = lootData.getUniqueItemId();
        if (uid != null && uid.startsWith("debug:haste:")) {
            try {
                double debugBonus = Double.parseDouble(uid.substring("debug:haste:".length()));
                float original = event.getDamage();
                event.setDamage((float) (original * (1.0 + debugBonus)));
                LOGGER.atInfo().log("[Haste DEBUG] bonus=%.2f damage %.2f -> %.2f", debugBonus, original, event.getDamage());
                return;
            } catch (NumberFormatException ignored) {
                // fall through to normal path
            }
        }

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            Ref<EntityStore> playerRef = chunk.getReferenceTo(entityIndex);
            PlayerStats stats = resolvePlayerStats(playerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            float original = event.getDamage();
            float boosted = (float) (original * (1.0 + effectiveValue));
            event.setDamage(boosted);
            return;
        }
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
