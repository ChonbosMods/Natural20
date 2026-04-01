package com.chonbosmods.quest;

import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.topic.PromptGroupRegistry;
import com.chonbosmods.topic.TopicGenerator;
import com.chonbosmods.topic.TopicPoolRegistry;
import com.chonbosmods.topic.TopicTemplateRegistry;

import java.nio.file.Path;

public class QuestSystem {

    private final QuestTemplateRegistry templateRegistry;
    private final QuestPoolRegistry poolRegistry;
    private final QuestStateManager stateManager;
    private final QuestGenerator generator;
    private final QuestTracker tracker;
    private final QuestRewardManager rewardManager;
    private final ReferenceManager referenceManager;
    private final TopicPoolRegistry topicPoolRegistry;
    private final TopicTemplateRegistry topicTemplateRegistry;
    private final PromptGroupRegistry promptGroupRegistry;
    private final TopicGenerator topicGenerator;

    public QuestSystem(SettlementRegistry settlementRegistry) {
        this.templateRegistry = new QuestTemplateRegistry();
        this.poolRegistry = new QuestPoolRegistry();
        this.poolRegistry.setTemplateRegistry(templateRegistry);
        this.stateManager = new QuestStateManager();
        this.rewardManager = new QuestRewardManager();
        this.tracker = new QuestTracker(stateManager, rewardManager);
        this.generator = new QuestGenerator(templateRegistry, settlementRegistry, poolRegistry);
        this.referenceManager = new ReferenceManager(templateRegistry, settlementRegistry, stateManager);
        this.topicPoolRegistry = new TopicPoolRegistry();
        this.topicTemplateRegistry = new TopicTemplateRegistry();
        this.promptGroupRegistry = new PromptGroupRegistry();
        this.topicGenerator = new TopicGenerator(topicPoolRegistry, topicTemplateRegistry, poolRegistry, generator, promptGroupRegistry);
    }

    public void loadTemplates(Path questDataDir) {
        templateRegistry.loadAll(questDataDir);
        poolRegistry.loadAll(questDataDir.resolve("pools"));

        // Load topic pools and templates (topics/ is sibling to quests/)
        Path topicsDir = questDataDir.getParent().resolve("topics");
        topicPoolRegistry.loadAll(topicsDir.resolve("pools"));
        topicTemplateRegistry.loadAll(topicsDir);
        promptGroupRegistry.loadAll(topicsDir.resolve("pools"));
    }

    public QuestTemplateRegistry getTemplateRegistry() { return templateRegistry; }
    public QuestPoolRegistry getPoolRegistry() { return poolRegistry; }
    public QuestStateManager getStateManager() { return stateManager; }
    public QuestGenerator getGenerator() { return generator; }
    public QuestTracker getTracker() { return tracker; }
    public QuestRewardManager getRewardManager() { return rewardManager; }
    public ReferenceManager getReferenceManager() { return referenceManager; }
    public TopicGenerator getTopicGenerator() { return topicGenerator; }
    public TopicPoolRegistry getTopicPoolRegistry() { return topicPoolRegistry; }
}
