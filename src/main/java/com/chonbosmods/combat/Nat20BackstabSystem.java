package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20AffixScaling;
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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Backstab: bonus damage when the attacker hits the target's back. Uses
 * angle-based detection (dot product of target's facing direction vs.
 * direction to attacker) with a 120-degree rear arc threshold.
 */
public class Nat20BackstabSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:backstab";
    private static final double SOFTCAP_K = 1.0;
    // Backstab triggers when attacker is in the rear 120-degree arc
    private static final double BACKSTAB_DOT_THRESHOLD = -0.5; // cos(120°)

    private final Nat20LootSystem lootSystem;

    public Nat20BackstabSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        // Skip DOT tick damage: weapon affixes should not re-trigger on periodic damage
        if (Nat20DotTickSystem.isDotTickDamage(damage)) return;

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

            // Check if the target is NOT facing the attacker (backstab condition)
            if (!isBackstab(store, attackerRef, targetRef)) return;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getPowerModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            float original = damage.getAmount();
            float boosted = (float) (original * (1.0 + effectiveValue));
            damage.setAmount(boosted);

            UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[Backstab] player=%s bonus=+%.1f%% damage=%.1f->%.1f",
                        attackerUuid.toString().substring(0, 8),
                        effectiveValue * 100, original, boosted);
            }
            return;
        }
    }

    /**
     * Determines if the attacker is behind the target using angle-based check.
     * Compares the target's facing direction (from yaw) against the direction
     * from target to attacker. If attacker is in rear arc, returns true.
     */
    private boolean isBackstab(Store<EntityStore> store, Ref<EntityStore> attackerRef,
                                Ref<EntityStore> targetRef) {
        TransformComponent attackerTransform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (attackerTransform == null || targetTransform == null) return false;

        Vector3d attackerPos = attackerTransform.getPosition();
        Vector3d targetPos = targetTransform.getPosition();

        // Direction from target to attacker (XZ plane)
        double dx = attackerPos.getX() - targetPos.getX();
        double dz = attackerPos.getZ() - targetPos.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01) return false;
        double toAttackerX = dx / dist;
        double toAttackerZ = dz / dist;

        // Target's facing direction from yaw (radians)
        float yaw = targetTransform.getRotation().getYaw();
        double facingX = -Math.sin(Math.toRadians(yaw));
        double facingZ = Math.cos(Math.toRadians(yaw));

        // Dot product: positive = attacker in front, negative = attacker behind
        double dot = facingX * toAttackerX + facingZ * toAttackerZ;

        return dot < BACKSTAB_DOT_THRESHOLD;
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
