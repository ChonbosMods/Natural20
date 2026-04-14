package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
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
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Flat elemental damage affixes: fire, frost, poison, void.
 * On player melee hit, fires a secondary Damage event with the matching elemental
 * DamageCause and the affix's flat damage value. No stat scaling on the affix itself:
 * INT amplifies downstream via Nat20ScoreDamageSystem.
 */
public class Nat20ElementalDamageSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float TORSO_OFFSET_Y = 0.9f;

    private static final String FIRE_ID = "nat20:fire";
    private static final String FROST_ID = "nat20:frost";
    private static final String POISON_ID = "nat20:poison";
    private static final String VOID_ID = "nat20:void";

    private static final String PARTICLE_FIRE = "Nat20_FireHit";
    private static final String PARTICLE_FROST = "Nat20_FrostHit";
    private static final String PARTICLE_POISON = "Nat20_PoisonHit";
    private static final String PARTICLE_VOID = "Nat20_VoidHit";

    private final Nat20LootSystem lootSystem;

    private int fireCauseIdx = Integer.MIN_VALUE;
    private int iceCauseIdx = Integer.MIN_VALUE;
    private int poisonCauseIdx = Integer.MIN_VALUE;
    private int voidCauseIdx = Integer.MIN_VALUE;
    private boolean causesResolved;

    public Nat20ElementalDamageSystem(Nat20LootSystem lootSystem) {
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

        if (!causesResolved) {
            fireCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Fire");
            iceCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Ice");
            poisonCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Poison");
            voidCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Void");
            causesResolved = true;
            LOGGER.atInfo().log("[ElemDmg] resolved causes: fire=%d ice=%d poison=%d void=%d",
                    fireCauseIdx, iceCauseIdx, poisonCauseIdx, voidCauseIdx);
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        // Check if the incoming damage is already one of our elemental causes to avoid
        // infinite recursion (our secondary damage would re-trigger this system)
        int incomingCause = damage.getDamageCauseIndex();
        if (incomingCause == fireCauseIdx || incomingCause == iceCauseIdx
                || incomingCause == poisonCauseIdx || incomingCause == voidCauseIdx) {
            return;
        }

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            String id = rolledAffix.id();
            int causeIdx;
            String particleId;

            if (FIRE_ID.equals(id) && fireCauseIdx >= 0) {
                causeIdx = fireCauseIdx;
                particleId = PARTICLE_FIRE;
            } else if (FROST_ID.equals(id) && iceCauseIdx >= 0) {
                causeIdx = iceCauseIdx;
                particleId = PARTICLE_FROST;
            } else if (POISON_ID.equals(id) && poisonCauseIdx >= 0) {
                causeIdx = poisonCauseIdx;
                particleId = PARTICLE_POISON;
            } else if (VOID_ID.equals(id) && voidCauseIdx >= 0) {
                causeIdx = voidCauseIdx;
                particleId = PARTICLE_VOID;
            } else {
                continue;
            }

            Nat20AffixDef def = affixRegistry.get(id);
            if (def == null) continue;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) continue;

            float flatDamage = (float) range.interpolate(lootData.getLootLevel());
            if (flatDamage <= 0f) continue;

            // Fire secondary damage event with elemental cause
            commandBuffer.invoke(targetRef,
                    new Damage(new Damage.EntitySource(attackerRef), causeIdx, flatDamage));

            // Spawn elemental hit particle at target
            TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                try {
                    ParticleUtil.spawnParticleEffect(particleId,
                            new Vector3d(pos.getX(), pos.getY() + TORSO_OFFSET_Y, pos.getZ()), store);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("[ElemDmg] particle spawn failed: %s", particleId);
                }
            }

            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[ElemDmg] %s: player=%s flat=%.1f cause=%s",
                        id, attackerUuid.toString().substring(0, 8), flatDamage, id);
            }
        }
    }
}
