package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Filter Group companion for Weakness: amplifies elemental damage on targets
 * that have a matching elemental weakness debuff active.
 */
public class Nat20WeaknessAmplifySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20WeaknessApplySystem weaknessSystem;

    private int fireCauseIdx = Integer.MIN_VALUE;
    private int iceCauseIdx = Integer.MIN_VALUE;
    private int poisonCauseIdx = Integer.MIN_VALUE;
    private int voidCauseIdx = Integer.MIN_VALUE;
    // Vanilla elemental causes
    private int vanillaFireIdx = Integer.MIN_VALUE;
    private int vanillaIceIdx = Integer.MIN_VALUE;
    private int vanillaPoisonIdx = Integer.MIN_VALUE;
    private boolean causesResolved;

    public Nat20WeaknessAmplifySystem(Nat20WeaknessApplySystem weaknessSystem) {
        this.weaknessSystem = weaknessSystem;
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

        if (!causesResolved) {
            var assetMap = DamageCause.getAssetMap();
            fireCauseIdx = assetMap.getIndex("Nat20Fire");
            iceCauseIdx = assetMap.getIndex("Nat20Ice");
            poisonCauseIdx = assetMap.getIndex("Nat20Poison");
            voidCauseIdx = assetMap.getIndex("Nat20Void");
            vanillaFireIdx = assetMap.getIndex("Fire");
            vanillaIceIdx = assetMap.getIndex("Ice");
            vanillaPoisonIdx = assetMap.getIndex("Poison");
            causesResolved = true;
        }

        int causeIdx = damage.getDamageCauseIndex();
        Nat20WeaknessApplySystem.Element element;

        if ((causeIdx == fireCauseIdx && fireCauseIdx >= 0)
                || (causeIdx == vanillaFireIdx && vanillaFireIdx >= 0)) {
            element = Nat20WeaknessApplySystem.Element.FIRE;
        } else if ((causeIdx == iceCauseIdx && iceCauseIdx >= 0)
                || (causeIdx == vanillaIceIdx && vanillaIceIdx >= 0)) {
            element = Nat20WeaknessApplySystem.Element.FROST;
        } else if ((causeIdx == poisonCauseIdx && poisonCauseIdx >= 0)
                || (causeIdx == vanillaPoisonIdx && vanillaPoisonIdx >= 0)) {
            element = Nat20WeaknessApplySystem.Element.POISON;
        } else if (causeIdx == voidCauseIdx && voidCauseIdx >= 0) {
            element = Nat20WeaknessApplySystem.Element.VOID;
        } else {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        double multiplier = weaknessSystem.getWeaknessMultiplier(targetRef, element);
        if (multiplier <= 0) return;

        float original = damage.getAmount();
        float amplified = (float) (original * (1.0 + multiplier));
        damage.setAmount(amplified);

        LOGGER.atInfo().log("[Weakness:Amplify] %s target=%s +%.1f%% damage=%.1f->%.1f",
                element, targetRef, multiplier * 100, original, amplified);
    }
}
