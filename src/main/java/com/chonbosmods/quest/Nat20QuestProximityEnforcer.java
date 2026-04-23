package com.chonbosmods.quest;

import com.chonbosmods.party.Nat20PartyProximityEvictor;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import java.util.*;
import java.util.function.*;

public final class Nat20QuestProximityEnforcer {
    private Nat20QuestProximityEnforcer() {}

    @FunctionalInterface
    public interface OnlineBannerDispatcher {
        void fire(UUID playerUuid, PendingQuestMissedBanner pending);
    }

    /**
     * Retained for binary compatibility and future re-enablement. Option B
     * (non-terminal gate) no longer queues offline banners: if an accepter is
     * offline at phase completion, they silently miss THAT phase only. The
     * interface stays so the caller site and tests retain their shape while
     * the offline-queue pathway is dormant.
     */
    @FunctionalInterface
    public interface OfflineBannerQueuer {
        void queue(UUID playerUuid, PendingQuestMissedBanner pending);
    }

    /**
     * Walk the quest's accepters and compute the set that are out-of-range or
     * offline at the phase-completion moment. Online missed accepters get an
     * immediate Quest-Missed banner; offline missed accepters are silent
     * (Option B: "if players are offline when a quest is turned in TOUGH
     * LUCK" per 2026-04-22 pivot). Accepters are NEVER terminated: they stay
     * on the quest for subsequent phases and the returned set is the caller's
     * responsibility to remember (see {@link QuestInstance#markMissedForPhase}).
     *
     * <p>The triggering player is never considered missed.
     *
     * @return the set of accepter UUIDs that missed this phase
     */
    public static Set<UUID> sweepForPhaseCompletion(
            QuestInstance quest,
            UUID triggeringPlayer,
            double[] anchorXyz,
            Function<UUID, Optional<double[]>> positionResolver,
            Predicate<UUID> isOnline,
            Nat20PartyQuestStore store,
            OnlineBannerDispatcher online,
            OfflineBannerQueuer offline) {
        Set<UUID> missed = Nat20PartyProximityEvictor.sweep(
                new LinkedHashSet<>(quest.getAccepters()),
                triggeringPlayer,
                anchorXyz,
                positionResolver,
                com.chonbosmods.party.Nat20PartyTuning.NAT20_PARTY_PROXIMITY);
        if (missed.isEmpty()) return missed;

        String topicHeader = quest.getVariableBindings()
                .getOrDefault("quest_topic_header", "Quest");
        for (UUID uuid : missed) {
            // Option B: do NOT drop the accepter from the quest. They stay on
            // the quest for subsequent phases; just miss this phase's rewards.
            if (isOnline.test(uuid)) {
                PendingQuestMissedBanner pending = new PendingQuestMissedBanner(
                        quest.getQuestId(), topicHeader, System.currentTimeMillis());
                online.fire(uuid, pending);
            }
            // Offline missed: silent. No queue per 2026-04-22 pivot.
        }
        return missed;
    }
}
