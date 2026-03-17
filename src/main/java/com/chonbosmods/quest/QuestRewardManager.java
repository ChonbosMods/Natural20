package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class QuestRewardManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    public void awardPhaseXP(Nat20PlayerData playerData, PhaseInstance phase,
                              boolean isFinalPhase, int totalPhases) {
        int baseXP = 30 + (playerData.getLevel() * 5);
        double multiplier = switch (phase.getType()) {
            case EXPOSITION -> 1.0;
            case CONFLICT -> 1.5;
            case RESOLUTION -> 2.0;
        };
        int xp = (int) (baseXP * multiplier);

        if (isFinalPhase) {
            xp += totalPhases * 25;
        }

        // TODO: Apply XP to player leveling system when implemented
        LOGGER.atInfo().log("Awarded %d quest XP (phase: %s, final: %s)", xp, phase.getType(), isFinalPhase);
    }

    public void awardLootReward(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                 Nat20PlayerData playerData) {
        // TODO: Generate loot via Nat20LootPipeline with +1 rarity tier weight
        LOGGER.atInfo().log("Quest loot reward stub");
    }

    public boolean shouldGiveMidChainReward(QuestInstance quest) {
        if (quest.getPhases().size() <= 4) return false;
        int midpoint = quest.getPhases().size() / 2;
        int current = quest.getCurrentPhaseIndex();
        PhaseInstance phase = quest.getCurrentPhase();
        return current == midpoint
            && phase != null
            && phase.getType() == PhaseType.RESOLUTION
            && !quest.hasClaimedReward(current);
    }
}
