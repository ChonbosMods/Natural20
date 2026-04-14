package com.chonbosmods.topic;

import java.util.*;

/**
 * A subject focus assigned to a single NPC in a settlement. Quest topics in
 * v2 are injected at runtime by {@code DialogueManager.injectQuestAvailableTopics};
 * this class now only carries the topic-focus descriptor used by smalltalk
 * graph generation.
 */
public class SubjectFocus {

    private final String subjectId;
    private final String subjectValue;
    private final boolean plural;
    private final boolean proper;
    private final boolean questEligible;
    private final boolean concrete;
    private final List<String> categories;
    private final String poiType;
    private final List<String> questAffinities;

    public SubjectFocus(String subjectId, String subjectValue, boolean plural, boolean proper,
                        boolean questEligible, boolean concrete, List<String> categories,
                        String poiType, List<String> questAffinities) {
        this.subjectId = subjectId;
        this.subjectValue = subjectValue;
        this.plural = plural;
        this.proper = proper;
        this.questEligible = questEligible;
        this.concrete = concrete;
        this.categories = categories;
        this.poiType = poiType;
        this.questAffinities = questAffinities;
    }

    public String getSubjectId() { return subjectId; }
    public String getSubjectValue() { return subjectValue; }
    public boolean isPlural() { return plural; }
    public boolean isProper() { return proper; }
    public boolean isQuestEligible() { return questEligible; }
    public boolean isConcrete() { return concrete; }
    public List<String> getCategories() { return categories; }
    public String getPoiType() { return poiType; }
    public List<String> getQuestAffinities() { return questAffinities; }
}
