package com.chonbosmods.combat;

import com.chonbosmods.party.Nat20Party;
import com.chonbosmods.party.Nat20PartyRegistry;
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
 * Cancels damage between members of the same Nat20 party. Filter Group system,
 * stateless: reads live registry state at hit time so party join/leave applies
 * on the next swing. Server-wide PvP setting is untouched.
 */
public class Nat20PartyPvpFilter extends DamageEventSystem {

    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20PartyRegistry partyRegistry;

    public Nat20PartyPvpFilter(Nat20PartyRegistry partyRegistry) {
        this.partyRegistry = partyRegistry;
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
        if (damage.isCancelled()) return;
        if (!(damage.getSource() instanceof Damage.EntitySource src)) return;

        Ref<EntityStore> attackerRef = src.getRef();
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        Player target = store.getComponent(targetRef, Player.getComponentType());
        if (attacker == null || target == null) return;

        UUID a = attacker.getPlayerRef().getUuid();
        UUID t = target.getPlayerRef().getUuid();
        if (a.equals(t)) return;

        Nat20Party pa = partyRegistry.getParty(a);
        Nat20Party pt = partyRegistry.getParty(t);
        if (pa == null || pt == null) return;
        if (!pa.getPartyId().equals(pt.getPartyId())) return;

        damage.setCancelled(true);
    }
}
