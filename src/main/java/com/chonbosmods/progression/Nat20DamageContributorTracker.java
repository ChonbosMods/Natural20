package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory record of which players have recently damaged which Nat20-scaled mobs.
 *
 * <p>Read by {@link Nat20XpOnKillSystem} and
 * {@code Nat20MobLootDropSystem} at lethal-damage time to decide who to award XP
 * to and whether to drop Nat20 enhanced loot at all. Entries expire after
 * {@link #WINDOW_MS} to prevent tag-and-run exploits and to avoid leaking memory
 * for mobs that wander off and die to environment.
 *
 * <p>Prune-on-read only: there is no background sweep. Chunk unloads clear the
 * mob's contributions within {@link #WINDOW_MS} because the next read (if any)
 * drops stale entries. Mobs that are never killed and never observed again
 * leak until server restart; the scale is bounded enough to not matter.
 */
public final class Nat20DamageContributorTracker {

    public static final long WINDOW_MS = 30_000L;

    private final Map<UUID, Map<UUID, Contribution>> contribsByMob = new ConcurrentHashMap<>();

    /** Record a player's damage contribution against a mob. Idempotent for repeat hits. */
    public void recordContribution(UUID mobUuid, UUID playerUuid, Ref<EntityStore> playerRef, long nowMs) {
        if (mobUuid == null || playerUuid == null || playerRef == null) return;
        contribsByMob
                .computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>())
                .put(playerUuid, new Contribution(playerRef, nowMs));
    }

    /**
     * Belt-and-suspenders helper used by XP/loot systems on the lethal damage event,
     * so a one-shot kill whose only damage event is the killing blow still registers
     * the attacker as a contributor regardless of ECS system invocation order.
     */
    public void recordFromDamage(Ref<EntityStore> victimRef, Damage damage,
                                  Store<EntityStore> store, long nowMs) {
        if (victimRef == null || damage == null) return;
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;
        if (store.getComponent(attackerRef, Natural20.getPlayerDataType()) == null) return;

        UUID mobUuid = uuidOf(victimRef, store);
        UUID playerUuid = uuidOf(attackerRef, store);
        if (mobUuid == null || playerUuid == null) return;
        recordContribution(mobUuid, playerUuid, attackerRef, nowMs);
    }

    /** Resolve the stable entity UUID from the store via {@link UUIDComponent}. */
    public static UUID uuidOf(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return null;
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComp != null ? uuidComp.getUuid() : null;
    }

    /**
     * Live player refs whose last damage against {@code mobUuid} falls inside the
     * 30s window and whose entity is still valid in the store. Stale entries are
     * pruned as a side effect. Returns an empty list if no contributors remain.
     */
    public List<Ref<EntityStore>> getContributors(UUID mobUuid, long nowMs) {
        if (mobUuid == null) return List.of();
        Map<UUID, Contribution> perMob = contribsByMob.get(mobUuid);
        if (perMob == null || perMob.isEmpty()) return List.of();

        long cutoff = nowMs - WINDOW_MS;
        List<Ref<EntityStore>> alive = new ArrayList<>(perMob.size());
        perMob.entrySet().removeIf(entry -> {
            Contribution c = entry.getValue();
            if (c.lastMs < cutoff) return true;
            if (!c.playerRef.isValid()) return true;
            alive.add(c.playerRef);
            return false;
        });
        if (perMob.isEmpty()) contribsByMob.remove(mobUuid);
        return alive;
    }

    /** Drop any tracked contributions for a given mob (e.g. on explicit despawn). */
    public void evict(UUID mobUuid) {
        if (mobUuid == null) return;
        contribsByMob.remove(mobUuid);
    }

    private record Contribution(Ref<EntityStore> playerRef, long lastMs) {}
}
