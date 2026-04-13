package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Filter Group companion for Rally: amplifies outgoing damage from players
 * that have an active rally buff.
 */
public class Nat20RallyAmplifySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20RallySystem rallySystem;

    public Nat20RallyAmplifySystem(Nat20RallySystem rallySystem) {
        this.rallySystem = rallySystem;
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

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
        double bonus = rallySystem.getRallyBonus(attackerUuid);
        if (bonus <= 0) return;

        float original = damage.getAmount();
        float amplified = (float) (original * (1.0 + bonus));
        damage.setAmount(amplified);

        if (CombatDebugSystem.isEnabled(attackerUuid)) {
            LOGGER.atInfo().log("[Rally:Amplify] player=%s bonus=+%.1f%% damage=%.1f->%.1f",
                    attackerUuid.toString().substring(0, 8), bonus * 100, original, amplified);
        }
    }
}
