package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.party.Nat20PartyRegistry;
import com.chonbosmods.party.Nat20PendingBannerStore;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Live-runtime adapter between {@link Nat20QuestProximityEnforcer} (pure) and
 * the running server (party registry, ECS store, player-ref lookup, banner
 * dispatch, offline side-car store).
 *
 * <p>Every objective-completion site calls {@link #check} on the world thread
 * immediately after the objective transitions from incomplete to complete and
 * before any phase-advance / reward / banner. The gate walks the quest's
 * accepters, identifies anyone outside the proximity radius or offline, fires
 * a Quest-Missed banner at the online missed accepters, and returns the set
 * of missed UUIDs so the caller can skip per-phase rewards for them. Accepters
 * are never dropped from the quest: under Option B (2026-04-22 pivot) they
 * stay on for subsequent phases and simply miss this phase's XP + item.
 * Offline missed accepters are silent (no offline queue).
 *
 * <p>Design reference: {@code docs/plans/2026-04-22-party-quest-proximity-and-mlvl-scaling-impl.md}
 * Task 10.
 */
public final class Nat20QuestProximityGate {

    private Nat20QuestProximityGate() {}

    /**
     * Entry point for every objective-completion site. Must run on the world
     * thread: reads {@link TransformComponent} via the entity store, and the
     * {@link Nat20PartyQuestStore} mutation path is not thread-safe outside
     * the world thread.
     *
     * @param quest            the quest whose objective just completed
     * @param triggeringPlayer the player who completed the objective (never evicted)
     * @param anchorXyz        the world-space anchor for the proximity radius
     *                         (typically the triggering player's own position,
     *                         or the kill / interaction target position)
     * @param world            the world in which the event fired
     * @param plugin           the running plugin instance (for accessor + side-car store)
     */
    public static Set<UUID> check(
            QuestInstance quest,
            UUID triggeringPlayer,
            double[] anchorXyz,
            World world,
            Natural20 plugin) {
        if (quest == null || triggeringPlayer == null || anchorXyz == null
                || world == null || plugin == null) {
            return Collections.emptySet();
        }

        Nat20PartyRegistry registry = plugin.getPartyRegistry();
        Nat20PartyQuestStore store = plugin.getPartyQuestStore();
        if (registry == null || store == null) return Collections.emptySet();

        return Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
                quest,
                triggeringPlayer,
                anchorXyz,
                uuid -> resolvePosition(uuid, world),
                registry::isOnline,
                store,
                (uuid, pending) -> firePlayerBanner(uuid, pending, world, plugin),
                (uuid, pending) -> queueOfflineBanner(uuid, pending, plugin));
    }

    /**
     * Convenience overload for objective-completion sites that already have the
     * triggering player's Ref and Store in scope. Resolves the anchor from
     * TransformComponent internally and short-circuits cleanly on missing components.
     * Intended for the four tracking-system wire-ins (KILL/COLLECT/FETCH/TALK).
     */
    public static Set<UUID> checkAtEntity(
            QuestInstance quest,
            UUID triggeringPlayer,
            Ref<EntityStore> entityRef,
            Store<EntityStore> store,
            World world,
            Natural20 plugin) {
        if (entityRef == null || store == null || world == null) return Collections.emptySet();
        TransformComponent tx = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (tx == null) return Collections.emptySet();
        Vector3d pos = tx.getPosition();
        if (pos == null) return Collections.emptySet();
        double[] anchor = new double[]{pos.getX(), pos.getY(), pos.getZ()};
        return check(quest, triggeringPlayer, anchor, world, plugin);
    }

    /**
     * Resolve an online player's current world-space position. Mirrors
     * {@code AmbientSpawnSystem.collectPlayerPositions()} (lines 112-122) and
     * {@code POIKillTrackingSystem} (lines 156-160): walk {@code world.getEntityRef(uuid)}
     * then read {@link TransformComponent} from the entity store. Returns
     * {@link Optional#empty()} if the player is offline in this world or the
     * entity has no TransformComponent (shouldn't happen for players).
     */
    private static Optional<double[]> resolvePosition(UUID uuid, World world) {
        Ref<EntityStore> entityRef = world.getEntityRef(uuid);
        if (entityRef == null) return Optional.empty();
        Store<EntityStore> store = world.getEntityStore().getStore();
        TransformComponent tx = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (tx == null) return Optional.empty();
        Vector3d pos = tx.getPosition();
        if (pos == null) return Optional.empty();
        return Optional.of(new double[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    /**
     * Fire a Quest-Missed banner to an online player. Walks
     * {@code world.getPlayerRefs()} to resolve the {@link PlayerRef}, matching
     * the pattern used by {@code CharacterSheetPage.findPlayerRef} (lines 714-719).
     * Null-guarded against the race where the player disconnects between the
     * enforcer's {@code isOnline} check and this dispatch: in that window the
     * {@link PlayerRef} lookup returns null, so we fall through to
     * {@link #queueOfflineBanner} to preserve the banner on the side-car store.
     * The player will see it on next login via Task 8's PlayerReadyEvent drain.
     */
    private static void firePlayerBanner(
            UUID uuid, PendingQuestMissedBanner pending, World world, Natural20 plugin) {
        PlayerRef ref = findPlayerRef(world, uuid);
        if (ref == null) {
            // Player went offline between enforcer's isOnline check and this dispatch.
            // Preserve banner via offline queue so they see it on next login.
            queueOfflineBanner(uuid, pending, plugin);
            return;
        }
        QuestMissedBanner.show(ref, pending.topicHeader());
    }

    /**
     * Queue a pending banner on the offline-side-car store. The player's
     * {@link com.chonbosmods.data.Nat20PlayerData} component is not loaded
     * while they are offline, so writing to the in-component field path is
     * unsafe. {@link Nat20PendingBannerStore} persists to
     * {@code pending_banners.json} and is drained by the PlayerReadyEvent
     * handler in {@code Natural20}.
     */
    private static void queueOfflineBanner(UUID uuid, PendingQuestMissedBanner pending, Natural20 plugin) {
        Nat20PendingBannerStore bannerStore = plugin.getPendingBannerStore();
        if (bannerStore == null) return;
        bannerStore.queue(uuid, pending);
    }

    private static PlayerRef findPlayerRef(World world, UUID uuid) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (uuid.equals(pr.getUuid())) return pr;
        }
        return null;
    }
}
