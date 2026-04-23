package com.chonbosmods.party;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Party-size mlvl scaling. Pure function form for testability.
 * The wiring-layer call (apply) uses the live PartyRegistry; test code uses
 * computeEffectiveMlvl directly with a hand-picked nearbyCount.
 */
public final class Nat20PartyMlvlScaler {
    private Nat20PartyMlvlScaler() {}

    public static int computeEffectiveMlvl(int baseMlvl, int nearbyCount) {
        int bump = Math.min(Math.max(0, nearbyCount - 1), Nat20PartyTuning.MLVL_PARTY_CAP);
        return baseMlvl + bump;
    }

    /**
     * Returns just the party-size bump (0 for solo, capped at
     * {@link Nat20PartyTuning#MLVL_PARTY_CAP}). Live-runtime only: delegates to
     * {@link #apply(int, UUID, World)} with baseMlvl=0 so the return is the
     * bump in isolation. Safe on null/offline/missing-transform (returns 0).
     */
    public static int computeBump(UUID triggeringPlayer, World world) {
        return apply(0, triggeringPlayer, world);
    }

    /**
     * Live-runtime overload. Resolves the triggering player's party + positions
     * from {@link Nat20PartyRegistry} and the ECS store, counts members within
     * {@link Nat20PartyTuning#NAT20_PARTY_PROXIMITY}, and delegates to
     * {@link #computeEffectiveMlvl(int, int)} for the actual math. Pure
     * calculation: does not mutate any state.
     *
     * <p>Defensive on offline / missing-transform cases: returns {@code baseMlvl}
     * if the triggering player cannot be resolved (shouldn't happen in practice
     * since this is called from a spawn event where the trigger just acted).
     *
     * <p>Must run on the world thread: reads {@link TransformComponent} via the
     * entity store.
     */
    public static int apply(int baseMlvl, UUID triggeringPlayer, World world) {
        if (triggeringPlayer == null || world == null) return baseMlvl;

        Natural20 plugin = Natural20.getInstance();
        if (plugin == null) return baseMlvl;
        Nat20PartyRegistry registry = plugin.getPartyRegistry();
        if (registry == null) return baseMlvl;

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Vector3d anchor = resolvePosition(triggeringPlayer, world, entityStore);
        if (anchor == null) return baseMlvl;

        Nat20Party party = registry.getParty(triggeringPlayer);
        List<UUID> members = party.getMembers();

        Set<UUID> online = new HashSet<>();
        for (UUID m : members) {
            if (registry.isOnline(m)) online.add(m);
        }

        int nearby = NearbyPartyCount.count(
                members,
                online,
                uuid -> {
                    Vector3d pos = resolvePosition(uuid, world, entityStore);
                    if (pos == null) return null;
                    return pos.distanceTo(anchor);
                },
                Nat20PartyTuning.NAT20_PARTY_PROXIMITY);

        return computeEffectiveMlvl(baseMlvl, nearby);
    }

    private static Vector3d resolvePosition(UUID uuid, World world, Store<EntityStore> store) {
        Ref<EntityStore> entityRef = world.getEntityRef(uuid);
        if (entityRef == null) return null;
        TransformComponent tx = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (tx == null) return null;
        return tx.getPosition();
    }
}
