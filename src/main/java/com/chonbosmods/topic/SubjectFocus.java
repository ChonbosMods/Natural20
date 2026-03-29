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
    private final boolean proper;
    private final boolean questEligible;
    private final boolean concrete;
    private final TopicCategory category;
    private final List<String> categories;
    private final Map<String, Boolean> npcVisibility = new LinkedHashMap<>();
    private @Nullable String questBearingNpc;
    private @Nullable String questSituationId;
    private @Nullable Map<String, String> questBindings;

    public SubjectFocus(String subjectId, String subjectValue, boolean plural, boolean proper,
                        boolean questEligible, boolean concrete, TopicCategory category, List<String> categories) {
        this.subjectId = subjectId;
        this.subjectValue = subjectValue;
        this.plural = plural;
        this.proper = proper;
        this.questEligible = questEligible;
        this.concrete = concrete;
        this.category = category;
        this.categories = categories;
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
    public boolean isProper() { return proper; }
    public boolean isQuestEligible() { return questEligible; }
    public boolean isConcrete() { return concrete; }
    public TopicCategory getCategory() { return category; }
    public List<String> getCategories() { return categories; }
    public Map<String, Boolean> getNpcVisibility() { return npcVisibility; }
    public @Nullable String getQuestBearingNpc() { return questBearingNpc; }
    public @Nullable String getQuestSituationId() { return questSituationId; }
    public @Nullable Map<String, String> getQuestBindings() { return questBindings; }
    public void setQuestBindings(Map<String, String> bindings) { this.questBindings = bindings; }
    public boolean hasQuest() { return questBearingNpc != null; }

    public Set<String> getAssignedNpcs() { return npcVisibility.keySet(); }

    public boolean isVisibleFor(String npcGeneratedName) {
        return npcVisibility.getOrDefault(npcGeneratedName, false);
    }
}
