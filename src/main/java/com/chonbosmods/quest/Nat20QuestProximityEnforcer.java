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

    @FunctionalInterface
    public interface OfflineBannerQueuer {
        void queue(UUID playerUuid, PendingQuestMissedBanner pending);
    }

    /**
     * Evict eligible accepters outside radius or offline. Ghost-safe:
     * offline evictees get a pending banner, online evictees get an immediate banner.
     * The triggering player is never evicted.
     */
    public static void sweepForPhaseCompletion(
            QuestInstance quest,
            UUID triggeringPlayer,
            double[] anchorXyz,
            Function<UUID, Optional<double[]>> positionResolver,
            Predicate<UUID> isOnline,
            Nat20PartyQuestStore store,
            OnlineBannerDispatcher online,
            OfflineBannerQueuer offline) {
        Set<UUID> toEvict = Nat20PartyProximityEvictor.sweep(
                quest.eligibleAccepters(),
                triggeringPlayer,
                anchorXyz,
                positionResolver,
                com.chonbosmods.party.Nat20PartyTuning.NAT20_PARTY_PROXIMITY);
        if (toEvict.isEmpty()) return;

        String topicHeader = quest.getVariableBindings()
                .getOrDefault("quest_topic_header", "Quest");
        for (UUID uuid : toEvict) {
            store.dropAccepter(quest.getQuestId(), uuid);
            PendingQuestMissedBanner pending = new PendingQuestMissedBanner(
                    quest.getQuestId(), topicHeader, System.currentTimeMillis());
            if (isOnline.test(uuid)) {
                online.fire(uuid, pending);
            } else {
                offline.queue(uuid, pending);
            }
        }
    }
}
