package com.chonbosmods.quest;

import com.chonbosmods.settlement.SettlementRegistry;

import java.nio.file.Path;

public class QuestSystem {

    private final QuestTemplateRegistry templateRegistry;
    private final QuestStateManager stateManager;
    private final QuestGenerator generator;
    private final QuestTracker tracker;
    private final QuestRewardManager rewardManager;
    private final ReferenceManager referenceManager;

    public QuestSystem(SettlementRegistry settlementRegistry) {
        this.templateRegistry = new QuestTemplateRegistry();
        this.stateManager = new QuestStateManager();
        this.rewardManager = new QuestRewardManager();
        this.tracker = new QuestTracker(stateManager, rewardManager);
        this.generator = new QuestGenerator(templateRegistry, settlementRegistry);
        this.referenceManager = new ReferenceManager(templateRegistry, settlementRegistry, stateManager);
    }

    public void loadTemplates(Path questDataDir) {
        templateRegistry.loadAll(questDataDir);
    }

    public QuestTemplateRegistry getTemplateRegistry() { return templateRegistry; }
    public QuestStateManager getStateManager() { return stateManager; }
    public QuestGenerator getGenerator() { return generator; }
    public QuestTracker getTracker() { return tracker; }
    public QuestRewardManager getRewardManager() { return rewardManager; }
    public ReferenceManager getReferenceManager() { return referenceManager; }
}
