package com.chonbosmods.topic;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A shared subject distributed across multiple NPCs in a settlement.
 * Tracks per-NPC visibility and optional quest binding.
 */
public class SubjectFocus {

    private final String subjectId;
    private final String subjectValue;
    private final boolean plural;
    private final TopicCategory category;
    private final Map<String, Boolean> npcVisibility = new LinkedHashMap<>();
    private @Nullable String questBearingNpc;
    private @Nullable String questSituationId;

    public SubjectFocus(String subjectId, String subjectValue, boolean plural, TopicCategory category) {
        this.subjectId = subjectId;
        this.subjectValue = subjectValue;
        this.plural = plural;
        this.category = category;
    }

    public void assignNpc(String npcGeneratedName, boolean startVisible) {
        npcVisibility.put(npcGeneratedName, startVisible);
    }

    public void setQuestBearer(String npcGeneratedName, String situationId) {
        this.questBearingNpc = npcGeneratedName;
        this.questSituationId = situationId;
    }

    public String getSubjectId() { return subjectId; }
    public String getSubjectValue() { return subjectValue; }
    public boolean isPlural() { return plural; }
    public TopicCategory getCategory() { return category; }
    public Map<String, Boolean> getNpcVisibility() { return npcVisibility; }
    public @Nullable String getQuestBearingNpc() { return questBearingNpc; }
    public @Nullable String getQuestSituationId() { return questSituationId; }
    public boolean hasQuest() { return questBearingNpc != null; }

    public Set<String> getAssignedNpcs() { return npcVisibility.keySet(); }

    public boolean isVisibleFor(String npcGeneratedName) {
        return npcVisibility.getOrDefault(npcGeneratedName, false);
    }
}
