package com.chonbosmods.data;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.dialogue.model.ExhaustionState;
import com.chonbosmods.quest.CompletedQuestRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Nat20PlayerData implements Component<EntityStore> {

    private static final MapCodec<String, Map<String, String>> STRING_MAP_CODEC =
            new MapCodec<>(Codec.STRING, HashMap::new);

    private static final MapCodec<Integer, Map<String, Integer>> INT_MAP_CODEC =
            new MapCodec<>(Codec.INTEGER, HashMap::new);

    private static final SetCodec<String, Set<String>> STRING_SET_CODEC =
            new SetCodec<>(Codec.STRING, HashSet::new, false);

    private static final ArrayCodec<CompletedQuestRecord> COMPLETED_QUESTS_CODEC =
            ArrayCodec.ofBuilderCodec(CompletedQuestRecord.CODEC, CompletedQuestRecord[]::new);

    public static final BuilderCodec<Nat20PlayerData> CODEC = BuilderCodec.builder(Nat20PlayerData.class, Nat20PlayerData::new)
            .addField(new KeyedCodec<>("Stats", Codec.INT_ARRAY), Nat20PlayerData::setStats, Nat20PlayerData::getStats)
            .addField(new KeyedCodec<>("Level", Codec.INTEGER), Nat20PlayerData::setLevel, Nat20PlayerData::getLevel)
            .addField(new KeyedCodec<>("TotalXp", Codec.LONG), Nat20PlayerData::setTotalXp, Nat20PlayerData::getTotalXp)
            .addField(new KeyedCodec<>("PendingAbilityPoints", Codec.INTEGER),
                    Nat20PlayerData::setPendingAbilityPoints, Nat20PlayerData::getPendingAbilityPoints)
            .addField(new KeyedCodec<>("Proficiencies", STRING_SET_CODEC), Nat20PlayerData::setProficiencies, Nat20PlayerData::getProficiencies)
            .addField(new KeyedCodec<>("QuestFlags", STRING_MAP_CODEC), Nat20PlayerData::setQuestFlags, Nat20PlayerData::getQuestFlags)
            .addField(new KeyedCodec<>("Reputation", INT_MAP_CODEC), Nat20PlayerData::setReputation, Nat20PlayerData::getReputation)
            .addField(new KeyedCodec<>("GlobalFlags", STRING_MAP_CODEC), Nat20PlayerData::setGlobalFlags, Nat20PlayerData::getGlobalFlags)
            .addField(new KeyedCodec<>("NpcDispositions", INT_MAP_CODEC), Nat20PlayerData::setNpcDispositions, Nat20PlayerData::getNpcDispositions)
            .addField(new KeyedCodec<>("ExhaustedTopics", STRING_MAP_CODEC), Nat20PlayerData::setExhaustedTopicsRaw, Nat20PlayerData::getExhaustedTopicsRaw)
            .addField(new KeyedCodec<>("LearnedGlobalTopics", STRING_SET_CODEC), Nat20PlayerData::setLearnedGlobalTopics, Nat20PlayerData::getLearnedGlobalTopics)
            .addField(new KeyedCodec<>("SavedSessions", STRING_MAP_CODEC), Nat20PlayerData::setSavedSessions, Nat20PlayerData::getSavedSessions)
            .addField(new KeyedCodec<>("ConsumedDecisives", STRING_MAP_CODEC), Nat20PlayerData::setConsumedDecisivesRaw, Nat20PlayerData::getConsumedDecisivesRaw)
            .addField(new KeyedCodec<>("TopicEntryOverrides", STRING_MAP_CODEC), Nat20PlayerData::setTopicEntryOverridesRaw, Nat20PlayerData::getTopicEntryOverridesRaw)
            .addField(new KeyedCodec<>("TopicRecapNodes", STRING_MAP_CODEC), Nat20PlayerData::setTopicRecapNodesRaw, Nat20PlayerData::getTopicRecapNodesRaw)
            .addField(new KeyedCodec<>("NpcClosingValences", STRING_MAP_CODEC), Nat20PlayerData::setNpcClosingValencesRaw, Nat20PlayerData::getNpcClosingValencesRaw)
            .addField(new KeyedCodec<>("DiscoveredSettlements", STRING_SET_CODEC), Nat20PlayerData::setDiscoveredSettlements, Nat20PlayerData::getDiscoveredSettlements)
            .addField(new KeyedCodec<>("Perception", Codec.FLOAT), Nat20PlayerData::setPerception, Nat20PlayerData::getPerception)
            .addField(new KeyedCodec<>("CompletedQuests", COMPLETED_QUESTS_CODEC),
                    Nat20PlayerData::setCompletedQuestsRaw, Nat20PlayerData::getCompletedQuestsRaw)
            .addField(new KeyedCodec<>("FirstJoinSeen", Codec.BOOLEAN), Nat20PlayerData::setFirstJoinSeen, Nat20PlayerData::isFirstJoinSeen)
            .build();

    // Index order: STR=0, DEX=1, CON=2, INT=3, WIS=4, CHA=5
    private int[] stats = {0, 0, 0, 0, 0, 0};
    private int level = 1;
    private long totalXp = 0L;
    private int pendingAbilityPoints = 0;
    private Set<String> proficiencies = new HashSet<>();
    private Map<String, String> questFlags = new HashMap<>();
    private Map<String, Integer> reputation = new HashMap<>();
    private Map<String, String> globalFlags = new HashMap<>();

    // Dialogue persistence (durable across conversation resets)
    private Map<String, Integer> npcDispositions = new HashMap<>();                // NPC ID -> disposition
    private Map<String, Map<String, ExhaustionState>> exhaustedTopics = new HashMap<>(); // NPC ID -> topic ID -> state
    private Set<String> learnedGlobalTopics = new HashSet<>();                     // global topic IDs learned
    private Map<String, Map<String, Set<String>>> consumedDecisives = new HashMap<>();  // NPC ID -> topic ID -> response IDs
    private Map<String, Map<String, String>> topicEntryOverrides = new HashMap<>();     // NPC ID -> topic ID -> entry node ID
    private Map<String, Map<String, String>> topicRecapNodes = new HashMap<>();         // NPC ID -> topic ID -> last node ID

    // Dirty-exit session save (only when leaving mid-follow-up)
    private Map<String, String> savedSessions = new HashMap<>();          // NPC ID -> serialized JSON

    // Per-NPC closing valence for valence drift between conversations
    private Map<String, String> npcClosingValences = new HashMap<>();     // NPC ID -> valence name

    // Settlement discovery tracking (cellKeys of settlements this player has visited)
    private Set<String> discoveredSettlements = new HashSet<>();

    private float perception = 0.0f;

    // True once this player has completed the first-join flow (background selector
    // committed). Used by the Jiub tutorial NPC system to auto-trigger dialogue on
    // the player's very first join and skip it on subsequent logins.
    private boolean firstJoinSeen = false;

    // Persisted record of completed quests (for Quest Log UI). Replaces the legacy
    // comma-separated `completed_quest_ids` string flag.
    // Most-recent-first; QuestStateManager.markQuestCompleted prepends.
    private List<CompletedQuestRecord> completedQuests = new ArrayList<>();

    /** Runtime-only reference to the owning player's UUID. Populated by the
     *  plugin after the component is loaded from the entity store, so that
     *  code paths holding {@code Nat20PlayerData} can resolve the player UUID
     *  without plumbing it alongside. Not part of {@link #CODEC}; never
     *  persisted. */
    private transient UUID playerUuid;

    public Nat20PlayerData() {
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public int[] getStats() {
        return stats;
    }

    public void setStats(int[] stats) {
        this.stats = stats != null ? stats.clone() : new int[]{0, 0, 0, 0, 0, 0};
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getTotalXp() { return totalXp; }
    public void setTotalXp(long totalXp) { this.totalXp = totalXp; }

    public int getPendingAbilityPoints() { return pendingAbilityPoints; }
    public void setPendingAbilityPoints(int pendingAbilityPoints) {
        this.pendingAbilityPoints = pendingAbilityPoints;
    }

    /**
     * Add XP. Writes totalXp, recomputes+caches level, accrues pending ability points,
     * and returns the level delta so the caller can fire side effects (HP modifier,
     * level-up banner). Pure data mutation: side effects live in the caller.
     */
    public int addXp(int amount) {
        if (amount <= 0) return 0;
        int oldLevel = this.level;
        this.totalXp += amount;
        int newLevel = com.chonbosmods.progression.Nat20XpMath.levelForTotalXp(this.totalXp);
        if (newLevel != oldLevel) {
            this.level = newLevel;
            if (newLevel > oldLevel) {
                this.pendingAbilityPoints += (newLevel - oldLevel);
            }
        }
        return newLevel - oldLevel;
    }

    public Set<String> getProficiencies() {
        return proficiencies;
    }

    public void setProficiencies(Set<String> proficiencies) {
        this.proficiencies = proficiencies != null ? new HashSet<>(proficiencies) : new HashSet<>();
    }

    public Map<String, String> getQuestFlags() {
        return questFlags;
    }

    public void setQuestFlags(Map<String, String> questFlags) {
        this.questFlags = questFlags != null ? new HashMap<>(questFlags) : new HashMap<>();
    }

    // --- Quest System Helpers ---

    public String getQuestData(String key) {
        return questFlags.get(key);
    }

    public void setQuestData(String key, String jsonValue) {
        questFlags.put(key, jsonValue);
    }

    public void removeQuestData(String key) {
        questFlags.remove(key);
    }

    public Map<String, Integer> getReputation() {
        return reputation;
    }

    public void setReputation(Map<String, Integer> reputation) {
        this.reputation = reputation != null ? new HashMap<>(reputation) : new HashMap<>();
    }

    public Map<String, String> getGlobalFlags() {
        return globalFlags;
    }

    public void setGlobalFlags(Map<String, String> globalFlags) {
        this.globalFlags = globalFlags != null ? new HashMap<>(globalFlags) : new HashMap<>();
    }

    // --- NPC Dispositions ---

    public Map<String, Integer> getNpcDispositions() {
        return npcDispositions;
    }

    public void setNpcDispositions(Map<String, Integer> npcDispositions) {
        this.npcDispositions = npcDispositions != null ? new HashMap<>(npcDispositions) : new HashMap<>();
    }

    public int getDispositionFor(String npcId, int defaultValue) {
        return npcDispositions.getOrDefault(npcId, defaultValue);
    }

    public void setDispositionFor(String npcId, int value) {
        npcDispositions.put(npcId, DispositionBracket.clampDisposition(value));
    }

    // --- Exhausted Topics ---
    // Stored as Map<String, String> in codec (topicId:STATE,topicId:STATE), exposed as Map<String, Map<String, ExhaustionState>>

    public Map<String, Map<String, ExhaustionState>> getExhaustedTopics() {
        return exhaustedTopics;
    }

    public void setExhaustedTopics(Map<String, Map<String, ExhaustionState>> exhaustedTopics) {
        this.exhaustedTopics = exhaustedTopics != null ? new HashMap<>(exhaustedTopics) : new HashMap<>();
    }

    public Map<String, ExhaustionState> getExhaustedTopicsFor(String npcId) {
        return exhaustedTopics.computeIfAbsent(npcId, k -> new HashMap<>());
    }

    public ExhaustionState getTopicExhaustionState(String npcId, String topicId) {
        var npcTopics = exhaustedTopics.get(npcId);
        return npcTopics != null ? npcTopics.get(topicId) : null;
    }

    public void setTopicExhaustion(String npcId, String topicId, ExhaustionState state) {
        getExhaustedTopicsFor(npcId).put(topicId, state);
    }

    public void removeTopicExhaustion(String npcId, String topicId) {
        var npcTopics = exhaustedTopics.get(npcId);
        if (npcTopics != null) npcTopics.remove(topicId);
    }

    /**
     * Codec adapter: serialize exhaustedTopics as Map<String, String> with topicId:STATE pairs.
     */
    Map<String, String> getExhaustedTopicsRaw() {
        Map<String, String> raw = new HashMap<>();
        for (var npcEntry : exhaustedTopics.entrySet()) {
            if (!npcEntry.getValue().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var topicEntry : npcEntry.getValue().entrySet()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(topicEntry.getKey()).append(":").append(topicEntry.getValue().name());
                }
                raw.put(npcEntry.getKey(), sb.toString());
            }
        }
        return raw;
    }

    /**
     * Codec adapter: deserialize topicId:STATE pairs. Legacy data (bare topic ID) defaults to GRAYED.
     */
    void setExhaustedTopicsRaw(Map<String, String> raw) {
        exhaustedTopics = new HashMap<>();
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    Map<String, ExhaustionState> topics = new HashMap<>();
                    for (String pair : value.split(",")) {
                        String[] parts = pair.trim().split(":");
                        if (parts.length == 2) {
                            try {
                                topics.put(parts[0].trim(), ExhaustionState.valueOf(parts[1].trim()));
                            } catch (IllegalArgumentException e) {
                                topics.put(parts[0].trim(), ExhaustionState.GRAYED);
                            }
                        } else if (parts.length == 1 && !parts[0].trim().isEmpty()) {
                            topics.put(parts[0].trim(), ExhaustionState.GRAYED);
                        }
                    }
                    exhaustedTopics.put(entry.getKey(), topics);
                }
            }
        }
    }

    // --- Learned Global Topics ---

    public Set<String> getLearnedGlobalTopics() {
        return learnedGlobalTopics;
    }

    public void setLearnedGlobalTopics(Set<String> learnedGlobalTopics) {
        this.learnedGlobalTopics = learnedGlobalTopics != null ? new HashSet<>(learnedGlobalTopics) : new HashSet<>();
    }

    public boolean isGlobalTopicLearned(String topicId) {
        return learnedGlobalTopics.contains(topicId);
    }

    public void learnGlobalTopic(String topicId) {
        learnedGlobalTopics.add(topicId);
    }

    // --- Saved Sessions ---

    public Map<String, String> getSavedSessions() {
        return savedSessions;
    }

    public void setSavedSessions(Map<String, String> savedSessions) {
        this.savedSessions = savedSessions != null ? new HashMap<>(savedSessions) : new HashMap<>();
    }

    public String getSavedSession(String npcId) {
        return savedSessions.get(npcId);
    }

    public void setSavedSession(String npcId, String json) {
        savedSessions.put(npcId, json);
    }

    public void clearSavedSession(String npcId) {
        savedSessions.remove(npcId);
    }

    // --- NPC Closing Valences ---

    Map<String, String> getNpcClosingValencesRaw() {
        return npcClosingValences;
    }

    void setNpcClosingValencesRaw(Map<String, String> raw) {
        this.npcClosingValences = raw != null ? new HashMap<>(raw) : new HashMap<>();
    }

    public String getClosingValence(String npcId) {
        return npcClosingValences.get(npcId);
    }

    public void setClosingValence(String npcId, String valence) {
        npcClosingValences.put(npcId, valence);
    }

    // --- Consumed Decisives ---
    // Stored as Map<String, String> in codec (topicId=id1|id2,topicId=id1|id2), exposed as 3-level map

    public Map<String, Map<String, Set<String>>> getConsumedDecisives() { return consumedDecisives; }

    public void setConsumedDecisives(Map<String, Map<String, Set<String>>> consumedDecisives) {
        this.consumedDecisives = consumedDecisives != null ? new HashMap<>(consumedDecisives) : new HashMap<>();
    }

    public Set<String> getConsumedDecisivesFor(String npcId, String topicId) {
        return consumedDecisives.computeIfAbsent(npcId, k -> new HashMap<>()).computeIfAbsent(topicId, k -> new HashSet<>());
    }

    public void addConsumedDecisive(String npcId, String topicId, String responseId) {
        getConsumedDecisivesFor(npcId, topicId).add(responseId);
    }

    public void clearConsumedDecisivesForTopic(String npcId, String topicId) {
        var npcMap = consumedDecisives.get(npcId);
        if (npcMap != null) npcMap.remove(topicId);
    }

    Map<String, String> getConsumedDecisivesRaw() {
        Map<String, String> raw = new HashMap<>();
        for (var npcEntry : consumedDecisives.entrySet()) {
            if (!npcEntry.getValue().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var topicEntry : npcEntry.getValue().entrySet()) {
                    if (!topicEntry.getValue().isEmpty()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(topicEntry.getKey()).append("=").append(String.join("|", topicEntry.getValue()));
                    }
                }
                if (sb.length() > 0) raw.put(npcEntry.getKey(), sb.toString());
            }
        }
        return raw;
    }

    void setConsumedDecisivesRaw(Map<String, String> raw) {
        consumedDecisives = new HashMap<>();
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    Map<String, Set<String>> topics = new HashMap<>();
                    for (String segment : value.split(",")) {
                        String[] parts = segment.trim().split("=", 2);
                        if (parts.length == 2) {
                            Set<String> ids = new HashSet<>(Arrays.asList(parts[1].split("\\|")));
                            ids.removeIf(String::isEmpty);
                            if (!ids.isEmpty()) topics.put(parts[0].trim(), ids);
                        }
                    }
                    if (!topics.isEmpty()) consumedDecisives.put(entry.getKey(), topics);
                }
            }
        }
    }

    // --- Topic Entry Overrides ---
    // Stored as Map<String, String> in codec (topicId:nodeId,topicId:nodeId), exposed as 2-level map

    public Map<String, Map<String, String>> getTopicEntryOverrides() { return topicEntryOverrides; }

    public void setTopicEntryOverrides(Map<String, Map<String, String>> topicEntryOverrides) {
        this.topicEntryOverrides = topicEntryOverrides != null ? new HashMap<>(topicEntryOverrides) : new HashMap<>();
    }

    public String getTopicEntryOverride(String npcId, String topicId) {
        var npcMap = topicEntryOverrides.get(npcId);
        return npcMap != null ? npcMap.get(topicId) : null;
    }

    public void setTopicEntryOverride(String npcId, String topicId, String entryNodeId) {
        topicEntryOverrides.computeIfAbsent(npcId, k -> new HashMap<>()).put(topicId, entryNodeId);
    }

    // --- Topic Recap Nodes ---
    // Stored as Map<String, String> in codec (topicId:nodeId,topicId:nodeId), exposed as 2-level map

    public Map<String, Map<String, String>> getTopicRecapNodes() { return topicRecapNodes; }

    public void setTopicRecapNodes(Map<String, Map<String, String>> topicRecapNodes) {
        this.topicRecapNodes = topicRecapNodes != null ? new HashMap<>(topicRecapNodes) : new HashMap<>();
    }

    public String getTopicRecapNode(String npcId, String topicId) {
        var npcMap = topicRecapNodes.get(npcId);
        return npcMap != null ? npcMap.get(topicId) : null;
    }

    public void setTopicRecapNode(String npcId, String topicId, String nodeId) {
        topicRecapNodes.computeIfAbsent(npcId, k -> new HashMap<>()).put(topicId, nodeId);
    }

    Map<String, String> getTopicRecapNodesRaw() {
        Map<String, String> raw = new HashMap<>();
        for (var npcEntry : topicRecapNodes.entrySet()) {
            if (!npcEntry.getValue().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var topicEntry : npcEntry.getValue().entrySet()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(topicEntry.getKey()).append(":").append(topicEntry.getValue());
                }
                raw.put(npcEntry.getKey(), sb.toString());
            }
        }
        return raw;
    }

    void setTopicRecapNodesRaw(Map<String, String> raw) {
        topicRecapNodes = new HashMap<>();
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                Map<String, String> topics = new HashMap<>();
                for (String pair : entry.getValue().split(",")) {
                    String[] parts = pair.trim().split(":", 2);
                    if (parts.length == 2) {
                        topics.put(parts[0].trim(), parts[1].trim());
                    }
                }
                if (!topics.isEmpty()) topicRecapNodes.put(entry.getKey(), topics);
            }
        }
    }

    Map<String, String> getTopicEntryOverridesRaw() {
        Map<String, String> raw = new HashMap<>();
        for (var npcEntry : topicEntryOverrides.entrySet()) {
            if (!npcEntry.getValue().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var topicEntry : npcEntry.getValue().entrySet()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(topicEntry.getKey()).append(":").append(topicEntry.getValue());
                }
                raw.put(npcEntry.getKey(), sb.toString());
            }
        }
        return raw;
    }

    void setTopicEntryOverridesRaw(Map<String, String> raw) {
        topicEntryOverrides = new HashMap<>();
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                Map<String, String> topics = new HashMap<>();
                for (String pair : entry.getValue().split(",")) {
                    String[] parts = pair.trim().split(":", 2);
                    if (parts.length == 2) {
                        topics.put(parts[0].trim(), parts[1].trim());
                    }
                }
                if (!topics.isEmpty()) topicEntryOverrides.put(entry.getKey(), topics);
            }
        }
    }

    // --- Perception ---

    public float getPerception() {
        return perception;
    }

    public void setPerception(float perception) {
        this.perception = perception;
    }

    // --- First Join Seen ---

    public boolean isFirstJoinSeen() {
        return firstJoinSeen;
    }

    public void setFirstJoinSeen(boolean firstJoinSeen) {
        this.firstJoinSeen = firstJoinSeen;
    }

    // --- Discovered Settlements ---

    public Set<String> getDiscoveredSettlements() {
        return discoveredSettlements;
    }

    public void setDiscoveredSettlements(Set<String> discoveredSettlements) {
        this.discoveredSettlements = discoveredSettlements != null ? new HashSet<>(discoveredSettlements) : new HashSet<>();
    }

    public boolean hasDiscoveredSettlement(String cellKey) {
        return discoveredSettlements.contains(cellKey);
    }

    public void discoverSettlement(String cellKey) {
        discoveredSettlements.add(cellKey);
    }

    // --- Completed Quests ---

    public List<CompletedQuestRecord> getCompletedQuests() {
        return completedQuests;
    }

    public void setCompletedQuests(List<CompletedQuestRecord> completedQuests) {
        this.completedQuests = completedQuests != null ? new ArrayList<>(completedQuests) : new ArrayList<>();
    }

    /** Codec adapter: expose the list as an array for {@link ArrayCodec}. */
    CompletedQuestRecord[] getCompletedQuestsRaw() {
        return completedQuests.toArray(new CompletedQuestRecord[0]);
    }

    /** Codec adapter: rebuild the list from the decoded array. */
    void setCompletedQuestsRaw(CompletedQuestRecord[] raw) {
        completedQuests = raw != null ? new ArrayList<>(Arrays.asList(raw)) : new ArrayList<>();
    }

    @Override
    public Nat20PlayerData clone() {
        Nat20PlayerData copy = new Nat20PlayerData();
        copy.stats = this.stats.clone();
        copy.level = this.level;
        copy.totalXp = this.totalXp;
        copy.pendingAbilityPoints = this.pendingAbilityPoints;
        copy.proficiencies = new HashSet<>(this.proficiencies);
        copy.questFlags = new HashMap<>(this.questFlags);
        copy.reputation = new HashMap<>(this.reputation);
        copy.globalFlags = new HashMap<>(this.globalFlags);
        copy.npcDispositions = new HashMap<>(this.npcDispositions);
        copy.exhaustedTopics = new HashMap<>();
        for (var entry : this.exhaustedTopics.entrySet()) {
            copy.exhaustedTopics.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        copy.consumedDecisives = new HashMap<>();
        for (var entry : this.consumedDecisives.entrySet()) {
            Map<String, Set<String>> topicMap = new HashMap<>();
            for (var topicEntry : entry.getValue().entrySet()) {
                topicMap.put(topicEntry.getKey(), new HashSet<>(topicEntry.getValue()));
            }
            copy.consumedDecisives.put(entry.getKey(), topicMap);
        }
        copy.topicEntryOverrides = new HashMap<>();
        for (var entry : this.topicEntryOverrides.entrySet()) {
            copy.topicEntryOverrides.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        copy.topicRecapNodes = new HashMap<>();
        for (var entry : this.topicRecapNodes.entrySet()) {
            copy.topicRecapNodes.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        copy.learnedGlobalTopics = new HashSet<>(this.learnedGlobalTopics);
        copy.savedSessions = new HashMap<>(this.savedSessions);
        copy.npcClosingValences = new HashMap<>(this.npcClosingValences);
        copy.discoveredSettlements = new HashSet<>(this.discoveredSettlements);
        copy.perception = this.perception;
        copy.firstJoinSeen = this.firstJoinSeen;
        copy.completedQuests = new ArrayList<>(this.completedQuests);
        return copy;
    }
}
