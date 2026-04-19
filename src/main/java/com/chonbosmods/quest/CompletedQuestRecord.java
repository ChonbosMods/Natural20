package com.chonbosmods.quest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Persisted snapshot of a quest the player has completed. Replaces the legacy
 * comma-separated {@code completed_quest_ids} string formerly stored in
 * {@link com.chonbosmods.data.Nat20PlayerData#getQuestFlags()}.
 *
 * <p>Captured at quest turn-in by {@link QuestStateManager#markQuestCompleted}
 * so the Quest Log UI can render past quests with their final objective text
 * even after the live {@link QuestInstance} has been removed from the
 * active-quest map.
 */
public final class CompletedQuestRecord {

    public static final BuilderCodec<CompletedQuestRecord> CODEC = BuilderCodec
            .builder(CompletedQuestRecord.class, CompletedQuestRecord::new)
            .addField(new KeyedCodec<>("QuestId", Codec.STRING),
                    CompletedQuestRecord::setQuestId, CompletedQuestRecord::getQuestId)
            .addField(new KeyedCodec<>("QuestName", Codec.STRING),
                    CompletedQuestRecord::setQuestName, CompletedQuestRecord::getQuestName)
            .addField(new KeyedCodec<>("FinalObjectiveText", Codec.STRING),
                    CompletedQuestRecord::setFinalObjectiveText, CompletedQuestRecord::getFinalObjectiveText)
            .build();

    private String questId = "";
    private String questName = "";
    private String finalObjectiveText = "";

    public CompletedQuestRecord() {}

    public CompletedQuestRecord(String questId, String questName, String finalObjectiveText) {
        this.questId = questId;
        this.questName = questName;
        this.finalObjectiveText = finalObjectiveText;
    }

    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }

    public String getQuestName() { return questName; }
    public void setQuestName(String questName) { this.questName = questName; }

    public String getFinalObjectiveText() { return finalObjectiveText; }
    public void setFinalObjectiveText(String finalObjectiveText) { this.finalObjectiveText = finalObjectiveText; }
}
