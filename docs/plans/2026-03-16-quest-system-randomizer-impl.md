# Quest System Randomizer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement a modular randomizing quest system using Polti's 36 Dramatic Situations with three-act phase structure, 7 objective types, variable-bound dialogue, and organic reference escalation.

**Architecture:** Quest templates are authored as JSON per dramatic situation, loaded at startup via the same classpath+override pattern used by the loot system. QuestGenerator resolves templates into concrete QuestInstance objects stored in Nat20PlayerData. QuestTracker monitors objective progress via event listeners and advances phases. ReferenceManager handles injection, escalation, and cleanup. All quest entry points flow through the existing DialogueActionRegistry.

**Tech Stack:** Java 25, Hytale Server SDK, Gson for JSON parsing, BuilderCodec for persistence, existing DialogueActionRegistry/ConversationSession/SettlementRegistry infrastructure.

**Working directory:** `/home/keroppi/Development/Hytale/Natural20/.worktrees/feat-quest-system-randomizer/`

**Compile command:** `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`

**Base package:** `com.chonbosmods.quest`

**Base source path:** `src/main/java/com/chonbosmods/quest/`

---

### Task 1: Core Enums and Data Models

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/PhaseType.java`
- Create: `src/main/java/com/chonbosmods/quest/ObjectiveType.java`
- Create: `src/main/java/com/chonbosmods/quest/ReferenceTier.java`
- Create: `src/main/java/com/chonbosmods/quest/ObjectiveInstance.java`
- Create: `src/main/java/com/chonbosmods/quest/PhaseInstance.java`
- Create: `src/main/java/com/chonbosmods/quest/QuestInstance.java`
- Create: `src/main/java/com/chonbosmods/quest/ReferenceState.java`

**Step 1: Create PhaseType enum**

```java
package com.chonbosmods.quest;

public enum PhaseType {
    EXPOSITION, CONFLICT, RESOLUTION
}
```

**Step 2: Create ObjectiveType enum**

```java
package com.chonbosmods.quest;

public enum ObjectiveType {
    GATHER_ITEMS,
    KILL_MOBS,
    DELIVER_ITEM,
    EXPLORE_LOCATION,
    FETCH_ITEM,
    TALK_TO_NPC,
    KILL_NPC
}
```

**Step 3: Create ReferenceTier enum**

```java
package com.chonbosmods.quest;

public enum ReferenceTier {
    PASSIVE, TRIGGER, CATALYST
}
```

**Step 4: Create ObjectiveInstance**

```java
package com.chonbosmods.quest;

public class ObjectiveInstance {
    private ObjectiveType type;
    private String targetId;       // item ID, mob type, NPC ID, or location cell key
    private String targetLabel;    // display name for dialogue
    private int requiredCount;
    private int currentCount;
    private boolean complete;
    private String directionHint;  // "north-west, about 200 blocks"
    private String locationId;     // settlement/dungeon cell key where content spawns

    public ObjectiveInstance() {}

    public ObjectiveInstance(ObjectiveType type, String targetId, String targetLabel,
                             int requiredCount, String directionHint, String locationId) {
        this.type = type;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.requiredCount = requiredCount;
        this.currentCount = 0;
        this.complete = false;
        this.directionHint = directionHint;
        this.locationId = locationId;
    }

    public ObjectiveType getType() { return type; }
    public String getTargetId() { return targetId; }
    public String getTargetLabel() { return targetLabel; }
    public int getRequiredCount() { return requiredCount; }
    public int getCurrentCount() { return currentCount; }
    public boolean isComplete() { return complete; }
    public String getDirectionHint() { return directionHint; }
    public String getLocationId() { return locationId; }

    public void incrementProgress(int amount) {
        currentCount = Math.min(currentCount + amount, requiredCount);
        if (currentCount >= requiredCount) complete = true;
    }

    public void markComplete() {
        currentCount = requiredCount;
        complete = true;
    }
}
```

**Step 5: Create PhaseInstance**

```java
package com.chonbosmods.quest;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class PhaseInstance {
    private PhaseType type;
    private String variantId;
    private List<ObjectiveInstance> objectives = new ArrayList<>();
    private @Nullable String referenceId;

    public PhaseInstance() {}

    public PhaseInstance(PhaseType type, String variantId, List<ObjectiveInstance> objectives,
                         @Nullable String referenceId) {
        this.type = type;
        this.variantId = variantId;
        this.objectives = objectives;
        this.referenceId = referenceId;
    }

    public PhaseType getType() { return type; }
    public String getVariantId() { return variantId; }
    public List<ObjectiveInstance> getObjectives() { return objectives; }
    public @Nullable String getReferenceId() { return referenceId; }

    public boolean isComplete() {
        return objectives.stream().allMatch(ObjectiveInstance::isComplete);
    }
}
```

**Step 6: Create QuestInstance**

```java
package com.chonbosmods.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuestInstance {
    private String questId;
    private String situationId;
    private String sourceNpcId;
    private String sourceSettlementId;
    private List<PhaseInstance> phases = new ArrayList<>();
    private int currentPhaseIndex;
    private Map<String, String> variableBindings = new HashMap<>();
    private Set<Integer> rewardsClaimed = new HashSet<>();

    public QuestInstance() {}

    public QuestInstance(String questId, String situationId, String sourceNpcId,
                         String sourceSettlementId, List<PhaseInstance> phases,
                         Map<String, String> variableBindings) {
        this.questId = questId;
        this.situationId = situationId;
        this.sourceNpcId = sourceNpcId;
        this.sourceSettlementId = sourceSettlementId;
        this.phases = phases;
        this.currentPhaseIndex = 0;
        this.variableBindings = variableBindings;
    }

    public String getQuestId() { return questId; }
    public String getSituationId() { return situationId; }
    public String getSourceNpcId() { return sourceNpcId; }
    public String getSourceSettlementId() { return sourceSettlementId; }
    public List<PhaseInstance> getPhases() { return phases; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public Map<String, String> getVariableBindings() { return variableBindings; }
    public Set<Integer> getRewardsClaimed() { return rewardsClaimed; }

    public PhaseInstance getCurrentPhase() {
        if (currentPhaseIndex < phases.size()) return phases.get(currentPhaseIndex);
        return null;
    }

    public boolean advancePhase() {
        if (currentPhaseIndex < phases.size() - 1) {
            currentPhaseIndex++;
            return true;
        }
        return false;
    }

    public boolean isComplete() {
        return currentPhaseIndex >= phases.size() - 1
            && phases.get(phases.size() - 1).isComplete();
    }

    public void claimReward(int phaseIndex) {
        rewardsClaimed.add(phaseIndex);
    }

    public boolean hasClaimedReward(int phaseIndex) {
        return rewardsClaimed.contains(phaseIndex);
    }
}
```

**Step 7: Create ReferenceState**

```java
package com.chonbosmods.quest;

import java.util.List;
import javax.annotation.Nullable;

public class ReferenceState {
    private String referenceId;
    private String templateId;
    private ReferenceTier tier;
    private String boundNpcId;
    private String boundSettlementId;
    private List<String> boundSituations;
    private @Nullable String unlockedTopicId;

    public ReferenceState() {}

    public ReferenceState(String referenceId, String templateId, ReferenceTier tier,
                          String boundNpcId, String boundSettlementId,
                          List<String> boundSituations) {
        this.referenceId = referenceId;
        this.templateId = templateId;
        this.tier = tier;
        this.boundNpcId = boundNpcId;
        this.boundSettlementId = boundSettlementId;
        this.boundSituations = boundSituations;
    }

    public String getReferenceId() { return referenceId; }
    public String getTemplateId() { return templateId; }
    public ReferenceTier getTier() { return tier; }
    public String getBoundNpcId() { return boundNpcId; }
    public String getBoundSettlementId() { return boundSettlementId; }
    public List<String> getBoundSituations() { return boundSituations; }
    public @Nullable String getUnlockedTopicId() { return unlockedTopicId; }

    public void setTier(ReferenceTier tier) { this.tier = tier; }
    public void setUnlockedTopicId(String topicId) { this.unlockedTopicId = topicId; }
}
```

**Step 8: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/
git commit -m "feat(quest): add core enums and data models

PhaseType, ObjectiveType, ReferenceTier enums.
ObjectiveInstance, PhaseInstance, QuestInstance, ReferenceState classes."
```

---

### Task 2: Quest Template JSON Models and Registry

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/model/QuestSituation.java`
- Create: `src/main/java/com/chonbosmods/quest/model/QuestVariant.java`
- Create: `src/main/java/com/chonbosmods/quest/model/DialogueChunks.java`
- Create: `src/main/java/com/chonbosmods/quest/model/PlayerResponse.java`
- Create: `src/main/java/com/chonbosmods/quest/model/ObjectiveConfig.java`
- Create: `src/main/java/com/chonbosmods/quest/model/QuestReferenceTemplate.java`
- Create: `src/main/java/com/chonbosmods/quest/QuestTemplateRegistry.java`

**Step 1: Create DialogueChunks**

```java
package com.chonbosmods.quest.model;

public record DialogueChunks(
    String intro,
    String plotStep,
    String outro
) {}
```

**Step 2: Create PlayerResponse**

```java
package com.chonbosmods.quest.model;

import javax.annotation.Nullable;

public record PlayerResponse(
    String text,
    String action,                   // ACCEPT, DECLINE
    @Nullable Integer dispositionShift
) {}
```

**Step 3: Create ObjectiveConfig**

```java
package com.chonbosmods.quest.model;

import javax.annotation.Nullable;

public record ObjectiveConfig(
    @Nullable Integer countMin,
    @Nullable Integer countMax,
    @Nullable String locationPreference  // DUNGEON, SETTLEMENT, or null
) {
    public int rollCount(java.util.Random random) {
        int min = countMin != null ? countMin : 1;
        int max = countMax != null ? countMax : min;
        return min + random.nextInt(max - min + 1);
    }
}
```

**Step 4: Create QuestVariant**

```java
package com.chonbosmods.quest.model;

import com.chonbosmods.quest.ObjectiveType;

import java.util.List;
import java.util.Map;

public record QuestVariant(
    String id,
    DialogueChunks dialogueChunks,
    List<PlayerResponse> playerResponses,
    List<ObjectiveType> objectivePool,
    Map<ObjectiveType, ObjectiveConfig> objectiveConfig
) {}
```

**Step 5: Create QuestReferenceTemplate**

```java
package com.chonbosmods.quest.model;

import java.util.List;

public record QuestReferenceTemplate(
    String id,
    List<String> compatibleSituations,
    String passiveText,
    String triggerTopicLabel,
    String triggerDialogue,
    List<String> catalystSituations,
    List<String> targetNpcRoles
) {}
```

**Step 6: Create QuestSituation**

Container for all variants of a single dramatic situation.

```java
package com.chonbosmods.quest.model;

import java.util.List;
import java.util.Map;

public class QuestSituation {
    private String id;
    private List<QuestVariant> expositionVariants;
    private List<QuestVariant> conflictVariants;
    private List<QuestVariant> resolutionVariants;
    private List<QuestReferenceTemplate> references;
    private Map<String, Double> npcRoleWeights;  // role name -> weight

    public QuestSituation() {}

    public QuestSituation(String id, List<QuestVariant> expositionVariants,
                           List<QuestVariant> conflictVariants,
                           List<QuestVariant> resolutionVariants,
                           List<QuestReferenceTemplate> references,
                           Map<String, Double> npcRoleWeights) {
        this.id = id;
        this.expositionVariants = expositionVariants;
        this.conflictVariants = conflictVariants;
        this.resolutionVariants = resolutionVariants;
        this.references = references;
        this.npcRoleWeights = npcRoleWeights;
    }

    public String getId() { return id; }
    public List<QuestVariant> getExpositionVariants() { return expositionVariants; }
    public List<QuestVariant> getConflictVariants() { return conflictVariants; }
    public List<QuestVariant> getResolutionVariants() { return resolutionVariants; }
    public List<QuestReferenceTemplate> getReferences() { return references; }
    public Map<String, Double> getNpcRoleWeights() { return npcRoleWeights; }
}
```

**Step 7: Create QuestTemplateRegistry**

Follows the same classpath+override pattern as `Nat20RarityRegistry`. Loads situation directories from `quests/` classpath and plugin data dir.

```java
package com.chonbosmods.quest;

import com.chonbosmods.quest.model.*;
import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class QuestTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final Map<String, QuestSituation> situations = new LinkedHashMap<>();

    public void loadAll(@Nullable Path overrideDir) {
        // Load from override directory (plugin data dir)
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> dirs = Files.list(overrideDir)) {
                dirs.filter(Files::isDirectory).forEach(this::loadSituationDir);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to list quest directories in %s", overrideDir);
            }
        }
        LOGGER.atInfo().log("Loaded %d quest situation(s)", situations.size());
    }

    private void loadSituationDir(Path dir) {
        String situationId = dir.getFileName().toString();
        try {
            List<QuestVariant> exposition = loadVariantsFile(dir.resolve("exposition_variants.json"));
            List<QuestVariant> conflict = loadVariantsFile(dir.resolve("conflict_variants.json"));
            List<QuestVariant> resolution = loadVariantsFile(dir.resolve("resolution_variants.json"));
            List<QuestReferenceTemplate> references = loadReferencesFile(dir.resolve("references.json"));
            Map<String, Double> weights = loadWeightsFile(dir.resolve("npc_weights.json"));

            if (exposition.isEmpty() && conflict.isEmpty() && resolution.isEmpty()) {
                LOGGER.atWarning().log("Situation %s has no variants, skipping", situationId);
                return;
            }

            situations.put(situationId, new QuestSituation(
                situationId, exposition, conflict, resolution, references, weights
            ));
            LOGGER.atInfo().log("Loaded situation: %s (%d expo, %d conf, %d reso, %d refs)",
                situationId, exposition.size(), conflict.size(), resolution.size(), references.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation: %s", situationId);
        }
    }

    private List<QuestVariant> loadVariantsFile(Path file) {
        if (!Files.exists(file)) return List.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray variants = root.getAsJsonArray("variants");
            List<QuestVariant> result = new ArrayList<>();
            for (JsonElement el : variants) {
                result.add(parseVariant(el.getAsJsonObject()));
            }
            return result;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse variants file: %s", file);
            return List.of();
        }
    }

    private QuestVariant parseVariant(JsonObject obj) {
        String id = obj.get("id").getAsString();

        JsonObject chunks = obj.getAsJsonObject("dialogueChunks");
        DialogueChunks dialogueChunks = new DialogueChunks(
            chunks.has("intro") ? chunks.get("intro").getAsString() : "",
            chunks.has("plotStep") ? chunks.get("plotStep").getAsString() : "",
            chunks.has("outro") ? chunks.get("outro").getAsString() : ""
        );

        List<PlayerResponse> responses = new ArrayList<>();
        if (obj.has("playerResponses")) {
            for (JsonElement el : obj.getAsJsonArray("playerResponses")) {
                JsonObject r = el.getAsJsonObject();
                responses.add(new PlayerResponse(
                    r.get("text").getAsString(),
                    r.get("action").getAsString(),
                    r.has("dispositionShift") ? r.get("dispositionShift").getAsInt() : null
                ));
            }
        }

        List<ObjectiveType> pool = new ArrayList<>();
        if (obj.has("objectivePool")) {
            for (JsonElement el : obj.getAsJsonArray("objectivePool")) {
                pool.add(ObjectiveType.valueOf(el.getAsString()));
            }
        }

        Map<ObjectiveType, ObjectiveConfig> configs = new HashMap<>();
        if (obj.has("objectiveConfig")) {
            JsonObject cfgObj = obj.getAsJsonObject("objectiveConfig");
            for (var entry : cfgObj.entrySet()) {
                ObjectiveType type = ObjectiveType.valueOf(entry.getKey());
                JsonObject cfg = entry.getValue().getAsJsonObject();
                configs.put(type, new ObjectiveConfig(
                    cfg.has("countMin") ? cfg.get("countMin").getAsInt() : null,
                    cfg.has("countMax") ? cfg.get("countMax").getAsInt() : null,
                    cfg.has("locationPreference") ? cfg.get("locationPreference").getAsString() : null
                ));
            }
        }

        return new QuestVariant(id, dialogueChunks, responses, pool, configs);
    }

    private List<QuestReferenceTemplate> loadReferencesFile(Path file) {
        if (!Files.exists(file)) return List.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray refs = root.getAsJsonArray("references");
            List<QuestReferenceTemplate> result = new ArrayList<>();
            for (JsonElement el : refs) {
                JsonObject r = el.getAsJsonObject();
                result.add(new QuestReferenceTemplate(
                    r.get("id").getAsString(),
                    jsonArrayToStringList(r.getAsJsonArray("compatibleSituations")),
                    r.get("passiveText").getAsString(),
                    r.get("triggerTopicLabel").getAsString(),
                    r.get("triggerDialogue").getAsString(),
                    jsonArrayToStringList(r.getAsJsonArray("catalystSituations")),
                    jsonArrayToStringList(r.getAsJsonArray("targetNpcRoles"))
                ));
            }
            return result;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse references file: %s", file);
            return List.of();
        }
    }

    private Map<String, Double> loadWeightsFile(Path file) {
        if (!Files.exists(file)) return Map.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Double> weights = new HashMap<>();
            for (var entry : root.entrySet()) {
                weights.put(entry.getKey(), entry.getValue().getAsDouble());
            }
            return weights;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse weights file: %s", file);
            return Map.of();
        }
    }

    private List<String> jsonArrayToStringList(JsonArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (JsonElement el : arr) list.add(el.getAsString());
        }
        return list;
    }

    public QuestSituation get(String situationId) {
        return situations.get(situationId);
    }

    public Collection<QuestSituation> getAll() {
        return situations.values();
    }

    /**
     * Select a situation weighted by the given NPC role.
     * Primary roles get their defined weight, others get 0.1.
     */
    public QuestSituation selectForRole(String npcRole, Random random) {
        List<QuestSituation> pool = new ArrayList<>(situations.values());
        if (pool.isEmpty()) return null;

        double totalWeight = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            double w = pool.get(i).getNpcRoleWeights().getOrDefault(npcRole, 0.1);
            weights[i] = w;
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return pool.get(i);
        }
        return pool.getLast();
    }

    /**
     * Find all reference templates compatible with a given situation ID, across all situations.
     */
    public List<QuestReferenceTemplate> findCompatibleReferences(String situationId) {
        List<QuestReferenceTemplate> result = new ArrayList<>();
        for (QuestSituation sit : situations.values()) {
            for (QuestReferenceTemplate ref : sit.getReferences()) {
                if (ref.compatibleSituations().contains(situationId)) {
                    result.add(ref);
                }
            }
        }
        return result;
    }

    public int getLoadedCount() {
        return situations.size();
    }
}
```

**Step 8: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/model/ src/main/java/com/chonbosmods/quest/QuestTemplateRegistry.java
git commit -m "feat(quest): add template JSON models and registry

QuestSituation, QuestVariant, DialogueChunks, PlayerResponse,
ObjectiveConfig, QuestReferenceTemplate records.
QuestTemplateRegistry loads situation directories with Gson."
```

---

### Task 3: DirectionUtil

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/DirectionUtil.java`

**Step 1: Create DirectionUtil**

Computes cardinal/intercardinal direction and rounded distance between two positions.

```java
package com.chonbosmods.quest;

public class DirectionUtil {

    private static final String[] DIRECTIONS = {
        "north", "north-east", "east", "south-east",
        "south", "south-west", "west", "north-west"
    };

    /**
     * Compute a human-readable direction hint from source to target.
     * @return e.g., "north-west, about 200 blocks"
     */
    public static String computeHint(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int roundedDistance = ((int) (distance / 50)) * 50; // round to nearest 50
        if (roundedDistance < 50) roundedDistance = 50;

        // atan2 gives angle from positive X axis; Hytale: +X is east, +Z is south
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        // Convert to compass: 0=east in atan2, we want 0=north
        // North is -Z, so north = atan2(-dz, dx) rotated
        angle = (90 - angle + 360) % 360; // now 0=north, 90=east, 180=south, 270=west

        int index = (int) ((angle + 22.5) / 45) % 8;
        return DIRECTIONS[index] + ", about " + roundedDistance + " blocks";
    }

    /**
     * Compute just the direction string (no distance).
     */
    public static String computeDirection(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (90 - angle + 360) % 360;
        int index = (int) ((angle + 22.5) / 45) % 8;
        return DIRECTIONS[index];
    }
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/DirectionUtil.java
git commit -m "feat(quest): add DirectionUtil for cardinal direction hints"
```

---

### Task 4: Add Quest State to Nat20PlayerData

**Files:**
- Modify: `src/main/java/com/chonbosmods/data/Nat20PlayerData.java`

Quest state is serialized as JSON strings inside the existing `questFlags` map to avoid adding complex new codec fields. The `questFlags` map already exists and is persisted via `STRING_MAP_CODEC`.

Key layout in `questFlags`:
- `"active_quests"` → Gson-serialized `Map<String, QuestInstance>`
- `"completed_quest_ids"` → comma-separated quest IDs
- `"active_references"` → Gson-serialized `Map<String, ReferenceState>`

**Step 1: Add quest helper methods to Nat20PlayerData**

Add after the `setQuestFlags` method (line 98):

```java
// --- Quest System Helpers ---
// Quest data is stored as JSON strings inside questFlags to avoid adding
// new codec fields. Gson serialization is handled by the quest system.

public String getQuestData(String key) {
    return questFlags.get(key);
}

public void setQuestData(String key, String jsonValue) {
    questFlags.put(key, jsonValue);
}

public void removeQuestData(String key) {
    questFlags.remove(key);
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/data/Nat20PlayerData.java
git commit -m "feat(quest): add quest data helpers to Nat20PlayerData"
```

---

### Task 5: QuestStateManager (Persistence Layer)

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestStateManager.java`

Serializes/deserializes quest state to/from Nat20PlayerData using Gson. All quest state access goes through this class.

**Step 1: Create QuestStateManager**

```java
package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

public class QuestStateManager {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String KEY_ACTIVE_QUESTS = "active_quests";
    private static final String KEY_COMPLETED_IDS = "completed_quest_ids";
    private static final String KEY_ACTIVE_REFS = "active_references";

    private static final Type QUEST_MAP_TYPE = new TypeToken<Map<String, QuestInstance>>() {}.getType();
    private static final Type REF_MAP_TYPE = new TypeToken<Map<String, ReferenceState>>() {}.getType();

    public Map<String, QuestInstance> getActiveQuests(Nat20PlayerData data) {
        String json = data.getQuestData(KEY_ACTIVE_QUESTS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Map<String, QuestInstance> result = GSON.fromJson(json, QUEST_MAP_TYPE);
        return result != null ? result : new HashMap<>();
    }

    public void saveActiveQuests(Nat20PlayerData data, Map<String, QuestInstance> quests) {
        data.setQuestData(KEY_ACTIVE_QUESTS, GSON.toJson(quests, QUEST_MAP_TYPE));
    }

    public void addQuest(Nat20PlayerData data, QuestInstance quest) {
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.put(quest.getQuestId(), quest);
        saveActiveQuests(data, quests);
    }

    public void removeQuest(Nat20PlayerData data, String questId) {
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.remove(questId);
        saveActiveQuests(data, quests);
    }

    public QuestInstance getQuest(Nat20PlayerData data, String questId) {
        return getActiveQuests(data).get(questId);
    }

    public Set<String> getCompletedQuestIds(Nat20PlayerData data) {
        String raw = data.getQuestData(KEY_COMPLETED_IDS);
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    public void markQuestCompleted(Nat20PlayerData data, String questId) {
        Set<String> completed = getCompletedQuestIds(data);
        completed.add(questId);
        data.setQuestData(KEY_COMPLETED_IDS, String.join(",", completed));
        removeQuest(data, questId);
    }

    public Map<String, ReferenceState> getActiveReferences(Nat20PlayerData data) {
        String json = data.getQuestData(KEY_ACTIVE_REFS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Map<String, ReferenceState> result = GSON.fromJson(json, REF_MAP_TYPE);
        return result != null ? result : new HashMap<>();
    }

    public void saveActiveReferences(Nat20PlayerData data, Map<String, ReferenceState> refs) {
        data.setQuestData(KEY_ACTIVE_REFS, GSON.toJson(refs, REF_MAP_TYPE));
    }

    public void addReference(Nat20PlayerData data, ReferenceState ref) {
        Map<String, ReferenceState> refs = getActiveReferences(data);
        refs.put(ref.getReferenceId(), ref);
        saveActiveReferences(data, refs);
    }

    public void removeReference(Nat20PlayerData data, String referenceId) {
        Map<String, ReferenceState> refs = getActiveReferences(data);
        refs.remove(referenceId);
        saveActiveReferences(data, refs);
    }
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestStateManager.java
git commit -m "feat(quest): add QuestStateManager for persistence

Serializes quest state as JSON in Nat20PlayerData.questFlags.
Manages active quests, completed IDs, and active references."
```

---

### Task 6: QuestGenerator Pipeline

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestGenerator.java`

The core pipeline: selects situation, rolls phase sequence, picks variants, resolves variables, rolls references, produces a QuestInstance.

**Step 1: Create QuestGenerator**

```java
package com.chonbosmods.quest;

import com.chonbosmods.quest.model.*;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class QuestGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final int MAX_PHASES = 6;
    private static final double CONFLICT_EXTEND_CHANCE = 0.40;
    private static final double RESOLUTION_EXTEND_CHANCE = 0.25;
    private static final double REFERENCE_INJECT_CHANCE = 0.20;

    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final AtomicLong questCounter = new AtomicLong(System.currentTimeMillis());

    // Vanilla items suitable for gather objectives
    private static final String[] GATHER_ITEMS = {
        "Hytale:RawMeat", "Hytale:Leather", "Hytale:Bone",
        "Hytale:Feather", "Hytale:WoodLog", "Hytale:Stone",
        "Hytale:IronOre", "Hytale:CottonFiber", "Hytale:Wheat"
    };

    // Hostile mob types for kill objectives
    private static final String[] HOSTILE_MOBS = {
        "Hytale:Trork_Grunt", "Hytale:Trork_Brute", "Hytale:Skeleton",
        "Hytale:Zombie", "Hytale:Spider"
    };

    public QuestGenerator(QuestTemplateRegistry templateRegistry, SettlementRegistry settlementRegistry) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
    }

    /**
     * Generate a quest for a player from the given NPC.
     * @param npcRole the quest-giving NPC's role name
     * @param npcId the NPC's unique identifier
     * @param npcSettlementCellKey the cell key of the NPC's settlement
     * @param npcX the NPC's X position
     * @param npcZ the NPC's Z position
     * @param completedIds quests this player has already completed
     * @return a fully resolved QuestInstance, or null if no situation is available
     */
    public @Nullable QuestInstance generate(String npcRole, String npcId,
                                             String npcSettlementCellKey,
                                             double npcX, double npcZ,
                                             Set<String> completedIds) {
        Random random = new Random();

        // Step 1: Select dramatic situation
        QuestSituation situation = templateRegistry.selectForRole(npcRole, random);
        if (situation == null) {
            LOGGER.atWarning().log("No quest situations available for role: %s", npcRole);
            return null;
        }

        // Step 2: Roll phase sequence
        List<PhaseType> phaseSequence = rollPhaseSequence(random);

        // Step 3: Pick variants per phase
        List<QuestVariant> selectedVariants = new ArrayList<>();
        ObjectiveType lastObjectiveType = null;
        for (PhaseType phase : phaseSequence) {
            List<QuestVariant> pool = switch (phase) {
                case EXPOSITION -> situation.getExpositionVariants();
                case CONFLICT -> situation.getConflictVariants();
                case RESOLUTION -> situation.getResolutionVariants();
            };
            if (pool.isEmpty()) {
                LOGGER.atWarning().log("No variants for phase %s in situation %s", phase, situation.getId());
                return null;
            }
            selectedVariants.add(pool.get(random.nextInt(pool.size())));
        }

        // Step 4: Resolve variable bindings
        Map<String, String> bindings = resolveBindings(npcX, npcZ, npcSettlementCellKey, random);

        // Step 5: Build phase instances with objectives
        List<PhaseInstance> phases = new ArrayList<>();
        for (int i = 0; i < phaseSequence.size(); i++) {
            QuestVariant variant = selectedVariants.get(i);
            PhaseType phaseType = phaseSequence.get(i);

            // Roll 1-2 objectives
            int objectiveCount = 1 + (random.nextDouble() < 0.4 ? 1 : 0);
            List<ObjectiveInstance> objectives = new ArrayList<>();
            for (int j = 0; j < objectiveCount && !variant.objectivePool().isEmpty(); j++) {
                ObjectiveType objType = pickObjectiveType(variant.objectivePool(), lastObjectiveType, random);
                ObjectiveConfig config = variant.objectiveConfig().getOrDefault(objType, new ObjectiveConfig(null, null, null));
                ObjectiveInstance obj = createObjective(objType, config, bindings, npcX, npcZ, random);
                objectives.add(obj);
                lastObjectiveType = objType;
            }

            // Step 5b: Roll reference injection for Conflict/Resolution phases
            String referenceId = null;
            if ((phaseType == PhaseType.CONFLICT || phaseType == PhaseType.RESOLUTION)
                    && random.nextDouble() < REFERENCE_INJECT_CHANCE) {
                referenceId = "ref_" + questCounter.incrementAndGet();
            }

            phases.add(new PhaseInstance(phaseType, variant.id(), objectives, referenceId));
        }

        // Step 6: Create QuestInstance
        String questId = "quest_" + situation.getId().toLowerCase() + "_" + Long.toHexString(questCounter.incrementAndGet());

        QuestInstance quest = new QuestInstance(
            questId, situation.getId(), npcId, npcSettlementCellKey, phases, bindings
        );

        LOGGER.atInfo().log("Generated quest %s: situation=%s, phases=%d, for NPC %s",
            questId, situation.getId(), phases.size(), npcId);
        return quest;
    }

    private List<PhaseType> rollPhaseSequence(Random random) {
        List<PhaseType> sequence = new ArrayList<>();
        sequence.add(PhaseType.EXPOSITION);

        PhaseType current = PhaseType.EXPOSITION;
        while (sequence.size() < MAX_PHASES) {
            if (current == PhaseType.EXPOSITION || current == PhaseType.CONFLICT) {
                sequence.add(PhaseType.CONFLICT);
                current = PhaseType.CONFLICT;

                // After conflict: chance to add another conflict
                if (sequence.size() < MAX_PHASES && random.nextDouble() < CONFLICT_EXTEND_CHANCE) {
                    continue;
                }
                // Otherwise move to resolution
                sequence.add(PhaseType.RESOLUTION);
                current = PhaseType.RESOLUTION;
            } else {
                // After resolution: chance to loop back to conflict
                if (random.nextDouble() < RESOLUTION_EXTEND_CHANCE) {
                    current = PhaseType.RESOLUTION; // will add conflict on next loop
                    continue;
                }
                break;
            }

            if (current == PhaseType.RESOLUTION) {
                break; // natural end
            }
        }

        // Ensure we end with resolution
        if (sequence.getLast() != PhaseType.RESOLUTION) {
            if (sequence.size() >= MAX_PHASES) {
                sequence.set(sequence.size() - 1, PhaseType.RESOLUTION);
            } else {
                sequence.add(PhaseType.RESOLUTION);
            }
        }

        return sequence;
    }

    private Map<String, String> resolveBindings(double npcX, double npcZ, String npcCellKey, Random random) {
        Map<String, String> bindings = new HashMap<>();

        // Quest item: random vanilla item
        String gatherItem = GATHER_ITEMS[random.nextInt(GATHER_ITEMS.length)];
        bindings.put("quest_item", gatherItem.substring(gatherItem.indexOf(':') + 1));

        // Enemy type
        String enemyType = HOSTILE_MOBS[random.nextInt(HOSTILE_MOBS.length)];
        bindings.put("enemy_type", enemyType.substring(enemyType.indexOf(':') + 1).replace("_", " "));
        bindings.put("enemy_type_id", enemyType);

        // Target NPC and location: find a nearby settlement
        SettlementRecord nearestOther = findNearestOtherSettlement(npcX, npcZ, npcCellKey);
        if (nearestOther != null) {
            bindings.put("location", nearestOther.getCellKey());
            bindings.put("location_hint", DirectionUtil.computeHint(npcX, npcZ,
                nearestOther.getPosX(), nearestOther.getPosZ()));

            // Pick a random NPC from that settlement
            if (!nearestOther.getNpcs().isEmpty()) {
                NpcRecord targetNpc = nearestOther.getNpcs().get(random.nextInt(nearestOther.getNpcs().size()));
                bindings.put("target_npc", targetNpc.getGeneratedName());
                bindings.put("target_npc_role", targetNpc.getRole());
                bindings.put("target_npc_settlement", nearestOther.getCellKey());
            }
        } else {
            bindings.put("location", "a distant place");
            bindings.put("location_hint", "far away");
        }

        // Gather item details
        bindings.put("gather_item_id", gatherItem);

        return bindings;
    }

    private @Nullable SettlementRecord findNearestOtherSettlement(double x, double z, String excludeCellKey) {
        SettlementRecord nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (SettlementRecord record : settlementRegistry.getAll().values()) {
            if (record.getCellKey().equals(excludeCellKey)) continue;
            double dx = record.getPosX() - x;
            double dz = record.getPosZ() - z;
            double dist = dx * dx + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = record;
            }
        }
        return nearest;
    }

    private ObjectiveType pickObjectiveType(List<ObjectiveType> pool, @Nullable ObjectiveType lastType, Random random) {
        if (pool.size() == 1) return pool.getFirst();
        // Try to avoid repeating the same type
        List<ObjectiveType> filtered = new ArrayList<>(pool);
        if (lastType != null) filtered.remove(lastType);
        if (filtered.isEmpty()) filtered = pool;
        return filtered.get(random.nextInt(filtered.size()));
    }

    private ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                              Map<String, String> bindings,
                                              double npcX, double npcZ, Random random) {
        return switch (type) {
            case GATHER_ITEMS -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                config.rollCount(random), null, null
            );
            case KILL_MOBS -> new ObjectiveInstance(
                type, bindings.get("enemy_type_id"), bindings.get("enemy_type"),
                config.rollCount(random), null, null
            );
            case DELIVER_ITEM -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
            );
            case EXPLORE_LOCATION -> new ObjectiveInstance(
                type, bindings.get("location"), bindings.get("location"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
            case FETCH_ITEM -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
            case TALK_TO_NPC -> new ObjectiveInstance(
                type, bindings.getOrDefault("target_npc", "an NPC"),
                bindings.getOrDefault("target_npc", "an NPC"),
                1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
            );
            case KILL_NPC -> new ObjectiveInstance(
                type, "bandit_" + Long.toHexString(random.nextLong()),
                "a dangerous outlaw",
                1, bindings.get("location_hint"), bindings.get("location")
            );
        };
    }
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestGenerator.java
git commit -m "feat(quest): add QuestGenerator pipeline

Selects dramatic situation, rolls phase sequence, picks variants,
resolves variable bindings from settlement registry, creates
objective instances with directional hints."
```

---

### Task 7: QuestRewardManager

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestRewardManager.java`

**Step 1: Create QuestRewardManager**

```java
package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

public class QuestRewardManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /**
     * Award XP for completing a phase.
     */
    public void awardPhaseXP(Player player, Nat20PlayerData playerData, PhaseInstance phase, boolean isFinalPhase, int totalPhases) {
        int baseXP = 30 + (playerData.getLevel() * 5);
        double multiplier = switch (phase.getType()) {
            case EXPOSITION -> 1.0;
            case CONFLICT -> 1.5;
            case RESOLUTION -> 2.0;
        };
        int xp = (int) (baseXP * multiplier);

        // Quest completion bonus on final resolution
        if (isFinalPhase) {
            xp += totalPhases * 25;
        }

        // TODO: Apply XP to player leveling system when implemented
        // For now, log it
        LOGGER.atInfo().log("Awarded %d quest XP to %s (phase: %s, final: %s)",
            xp, player.getPlayerRef().getUuid(), phase.getType(), isFinalPhase);
    }

    /**
     * Award loot for a Resolution phase.
     */
    public void awardLootReward(Player player, Ref<EntityStore> playerRef, Store<EntityStore> store, Nat20PlayerData playerData) {
        // TODO: Generate loot via Nat20LootPipeline with +1 rarity tier weight
        // For now, log it
        LOGGER.atInfo().log("Quest loot reward stub for %s", player.getPlayerRef().getUuid());
    }

    /**
     * Check if a mid-chain reward should be given (chain > 4 phases, at a resolution).
     */
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
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestRewardManager.java
git commit -m "feat(quest): add QuestRewardManager for XP and loot

Phase-based XP scaling with level, completion bonus for long chains,
mid-chain loot rewards for quests exceeding 4 phases."
```

---

### Task 8: QuestTracker (Objective Progress and Phase Advancement)

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestTracker.java`

This is the central coordinator: monitors objective progress, advances phases, and distributes rewards.

**Step 1: Create QuestTracker**

```java
package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.chat.Message;

import java.util.Map;
import java.util.UUID;

public class QuestTracker {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final QuestStateManager stateManager;
    private final QuestRewardManager rewardManager;

    public QuestTracker(QuestStateManager stateManager, QuestRewardManager rewardManager) {
        this.stateManager = stateManager;
        this.rewardManager = rewardManager;
    }

    /**
     * Report progress on an objective type for a player.
     * Called by event listeners when relevant events fire.
     */
    public void reportProgress(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                ObjectiveType type, String targetId, int amount) {
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);
        boolean changed = false;

        for (QuestInstance quest : quests.values()) {
            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.isComplete()) continue;
                if (obj.getType() != type) continue;
                if (!matchesTarget(obj, targetId)) continue;

                obj.incrementProgress(amount);
                changed = true;

                LOGGER.atInfo().log("Quest %s: objective %s progress %d/%d",
                    quest.getQuestId(), obj.getType(), obj.getCurrentCount(), obj.getRequiredCount());

                if (obj.isComplete()) {
                    sendMessage(playerRef, "Objective complete: " + obj.getTargetLabel());
                }
            }

            // Check if all objectives in current phase are complete
            if (phase.isComplete()) {
                onPhaseComplete(playerRef, store, playerData, quest);
            }
        }

        if (changed) {
            stateManager.saveActiveQuests(playerData, quests);
        }
    }

    /**
     * Handle a one-shot objective completion (explore, talk, deliver, fetch).
     */
    public void reportCompletion(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                  ObjectiveType type, String targetId) {
        reportProgress(playerRef, store, type, targetId, 1);
    }

    private void onPhaseComplete(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                  Nat20PlayerData playerData, QuestInstance quest) {
        PhaseInstance completedPhase = quest.getCurrentPhase();
        Player player = (Player) store.getEntity(playerRef);
        boolean isFinalPhase = quest.getCurrentPhaseIndex() >= quest.getPhases().size() - 1;

        // Award XP
        rewardManager.awardPhaseXP(player, playerData, completedPhase, isFinalPhase, quest.getPhases().size());

        // Award loot on Resolution phases
        if (completedPhase.getType() == PhaseType.RESOLUTION) {
            if (isFinalPhase || rewardManager.shouldGiveMidChainReward(quest)) {
                rewardManager.awardLootReward(player, playerRef, store, playerData);
                quest.claimReward(quest.getCurrentPhaseIndex());
            }
        }

        if (isFinalPhase) {
            // Quest complete
            sendMessage(playerRef, "Quest complete: " + quest.getSituationId());
            stateManager.markQuestCompleted(playerData, quest.getQuestId());
            LOGGER.atInfo().log("Quest %s completed by %s", quest.getQuestId(), player.getPlayerRef().getUuid());
        } else {
            // Advance to next phase
            quest.advancePhase();
            PhaseInstance nextPhase = quest.getCurrentPhase();
            sendMessage(playerRef, "Quest updated: return to the quest giver or proceed to your next objective.");
            LOGGER.atInfo().log("Quest %s advanced to phase %d: %s",
                quest.getQuestId(), quest.getCurrentPhaseIndex(), nextPhase.getType());
        }
    }

    private boolean matchesTarget(ObjectiveInstance obj, String targetId) {
        if (obj.getTargetId() == null || targetId == null) return false;
        // Exact match or contains (for mob type matching)
        return obj.getTargetId().equals(targetId) || targetId.contains(obj.getTargetId());
    }

    private void sendMessage(Ref<EntityStore> playerRef, String text) {
        try {
            playerRef.sendMessage(Message.raw(text));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send quest message");
        }
    }
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestTracker.java
git commit -m "feat(quest): add QuestTracker for objective progress and phase advancement

Reports progress from event listeners, checks phase completion,
awards XP/loot via QuestRewardManager, advances phase sequence."
```

---

### Task 9: ReferenceManager

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/ReferenceManager.java`

**Step 1: Create ReferenceManager**

```java
package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.model.QuestReferenceTemplate;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ReferenceManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final int MAX_ACTIVE_REFERENCES = 3;
    private static final double PASSIVE_TO_TRIGGER_CHANCE = 0.30;
    private static final double TRIGGER_TO_CATALYST_CHANCE = 0.40;

    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final QuestStateManager stateManager;
    private final AtomicLong refCounter = new AtomicLong(System.currentTimeMillis());

    public ReferenceManager(QuestTemplateRegistry templateRegistry,
                            SettlementRegistry settlementRegistry,
                            QuestStateManager stateManager) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
        this.stateManager = stateManager;
    }

    /**
     * Inject a reference for a quest phase.
     * @return the reference ID, or null if injection was skipped
     */
    public @Nullable String injectReference(Nat20PlayerData playerData, String situationId,
                                             String referenceId, double npcX, double npcZ) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        if (refs.size() >= MAX_ACTIVE_REFERENCES) return null;

        // Find compatible reference templates
        List<QuestReferenceTemplate> templates = templateRegistry.findCompatibleReferences(situationId);
        if (templates.isEmpty()) return null;

        Random random = new Random();
        QuestReferenceTemplate template = templates.get(random.nextInt(templates.size()));

        // Find a target NPC at a nearby settlement
        SettlementRecord targetSettlement = findTargetSettlement(npcX, npcZ, template.targetNpcRoles(), random);
        if (targetSettlement == null) return null;

        NpcRecord targetNpc = findTargetNpc(targetSettlement, template.targetNpcRoles(), random);
        if (targetNpc == null) return null;

        // Roll starting tier: 60% Passive, 30% Trigger, 10% Catalyst
        ReferenceTier tier;
        double tierRoll = random.nextDouble();
        if (tierRoll < 0.60) tier = ReferenceTier.PASSIVE;
        else if (tierRoll < 0.90) tier = ReferenceTier.TRIGGER;
        else tier = ReferenceTier.CATALYST;

        ReferenceState ref = new ReferenceState(
            referenceId, template.id(), tier,
            targetNpc.getGeneratedName(),
            targetSettlement.getCellKey(),
            template.catalystSituations()
        );

        // If starting as TRIGGER or CATALYST, unlock topic immediately
        if (tier == ReferenceTier.TRIGGER || tier == ReferenceTier.CATALYST) {
            String topicId = "ref_topic_" + refCounter.incrementAndGet();
            ref.setUnlockedTopicId(topicId);
            // Topic unlocking is handled by the dialogue system integration
        }

        refs.put(referenceId, ref);
        stateManager.saveActiveReferences(playerData, refs);

        LOGGER.atInfo().log("Injected reference %s: tier=%s, template=%s, target=%s at %s",
            referenceId, tier, template.id(), targetNpc.getGeneratedName(), targetSettlement.getCellKey());
        return referenceId;
    }

    /**
     * Attempt to escalate a PASSIVE reference when player visits the target settlement.
     * Called when player enters a settlement area.
     */
    public void checkPassiveEscalation(Nat20PlayerData playerData, String settlementCellKey) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        boolean changed = false;
        Random random = new Random();

        for (ReferenceState ref : refs.values()) {
            if (ref.getTier() != ReferenceTier.PASSIVE) continue;
            if (!ref.getBoundSettlementId().equals(settlementCellKey)) continue;

            if (random.nextDouble() < PASSIVE_TO_TRIGGER_CHANCE) {
                ref.setTier(ReferenceTier.TRIGGER);
                String topicId = "ref_topic_" + refCounter.incrementAndGet();
                ref.setUnlockedTopicId(topicId);
                changed = true;
                LOGGER.atInfo().log("Reference %s escalated: PASSIVE -> TRIGGER", ref.getReferenceId());
            }
        }

        if (changed) {
            stateManager.saveActiveReferences(playerData, refs);
        }
    }

    /**
     * Attempt to escalate a TRIGGER reference when player engages with its topic.
     * Called when player selects a reference trigger topic in dialogue.
     * @return true if escalated to CATALYST
     */
    public boolean checkTriggerEscalation(Nat20PlayerData playerData, String referenceId) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        ReferenceState ref = refs.get(referenceId);
        if (ref == null || ref.getTier() != ReferenceTier.TRIGGER) return false;

        Random random = new Random();
        if (random.nextDouble() < TRIGGER_TO_CATALYST_CHANCE) {
            ref.setTier(ReferenceTier.CATALYST);
            stateManager.saveActiveReferences(playerData, refs);
            LOGGER.atInfo().log("Reference %s escalated: TRIGGER -> CATALYST", referenceId);
            return true;
        }
        return false;
    }

    /**
     * Clean up all references associated with a completed quest.
     */
    public void cleanupQuestReferences(Nat20PlayerData playerData, QuestInstance quest) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        boolean changed = false;
        for (PhaseInstance phase : quest.getPhases()) {
            if (phase.getReferenceId() != null && refs.remove(phase.getReferenceId()) != null) {
                changed = true;
            }
        }
        if (changed) {
            stateManager.saveActiveReferences(playerData, refs);
        }
    }

    private @Nullable SettlementRecord findTargetSettlement(double x, double z,
                                                             List<String> targetRoles, Random random) {
        List<SettlementRecord> candidates = new ArrayList<>();
        for (SettlementRecord record : settlementRegistry.getAll().values()) {
            for (NpcRecord npc : record.getNpcs()) {
                if (targetRoles.isEmpty() || targetRoles.contains(npc.getRole())) {
                    candidates.add(record);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return null;
        // Prefer closer settlements
        candidates.sort(Comparator.comparingDouble(r -> {
            double dx = r.getPosX() - x;
            double dz = r.getPosZ() - z;
            return dx * dx + dz * dz;
        }));
        // Pick from top 3 closest
        int poolSize = Math.min(3, candidates.size());
        return candidates.get(random.nextInt(poolSize));
    }

    private @Nullable NpcRecord findTargetNpc(SettlementRecord settlement, List<String> targetRoles, Random random) {
        List<NpcRecord> matches = new ArrayList<>();
        for (NpcRecord npc : settlement.getNpcs()) {
            if (targetRoles.isEmpty() || targetRoles.contains(npc.getRole())) {
                matches.add(npc);
            }
        }
        if (matches.isEmpty()) return null;
        return matches.get(random.nextInt(matches.size()));
    }
}
```

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/ReferenceManager.java
git commit -m "feat(quest): add ReferenceManager for reference injection and escalation

60/30/10 starting tier split, 30% Passive->Trigger on proximity,
40% Trigger->Catalyst on dialogue engagement. Max 3 active per player."
```

---

### Task 10: Wire GIVE_QUEST and COMPLETE_QUEST Actions

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java`

Replace the existing stubs with real quest system integration.

**Step 1: Replace GIVE_QUEST stub**

In `DialogueActionRegistry.java`, replace lines 50-53:

```java
register("GIVE_QUEST", (ctx, params) -> {
    var questSystem = Natural20.getInstance().getQuestSystem();
    if (questSystem == null) {
        LOGGER.atWarning().log("GIVE_QUEST: quest system not initialized");
        return;
    }
    String npcRole = ctx.npcData() != null ? ctx.npcData().getRoleName() : "Villager";
    String npcId = ctx.npcId();
    String settlementCellKey = ctx.npcData() != null ? ctx.npcData().getSettlementCellKey() : "";

    // Get NPC position from the entity
    double npcX = 0, npcZ = 0;
    try {
        var transform = ctx.store().getComponent(ctx.npcRef(),
            com.hypixel.hytale.server.core.universe.world.storage.components.TransformComponent.TYPE);
        if (transform != null) {
            var pos = transform.getPosition();
            npcX = pos.x();
            npcZ = pos.z();
        }
    } catch (Exception e) {
        LOGGER.atWarning().log("GIVE_QUEST: could not get NPC position");
    }

    Set<String> completed = questSystem.getStateManager().getCompletedQuestIds(ctx.playerData());
    var quest = questSystem.getGenerator().generate(npcRole, npcId, settlementCellKey, npcX, npcZ, completed);
    if (quest != null) {
        questSystem.getStateManager().addQuest(ctx.playerData(), quest);
        ctx.playerRef().sendMessage(com.hypixel.hytale.server.core.chat.Message.raw(
            "New quest accepted: " + quest.getSituationId()));

        // Inject references for any phases that rolled them
        for (var phase : quest.getPhases()) {
            if (phase.getReferenceId() != null) {
                questSystem.getReferenceManager().injectReference(
                    ctx.playerData(), quest.getSituationId(),
                    phase.getReferenceId(), npcX, npcZ);
            }
        }
    }
});
```

**Step 2: Replace COMPLETE_QUEST stub**

Replace lines 55-58:

```java
register("COMPLETE_QUEST", (ctx, params) -> {
    String questId = params.get("questId");
    var questSystem = Natural20.getInstance().getQuestSystem();
    if (questSystem == null || questId == null) return;

    var quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
    if (quest != null && quest.isComplete()) {
        questSystem.getStateManager().markQuestCompleted(ctx.playerData(), questId);
        questSystem.getReferenceManager().cleanupQuestReferences(ctx.playerData(), quest);
        ctx.playerRef().sendMessage(com.hypixel.hytale.server.core.chat.Message.raw(
            "Quest completed: " + quest.getSituationId()));
    }
});
```

**Step 3: Add import**

Add at top of DialogueActionRegistry.java:

```java
import com.chonbosmods.Natural20;
```

**NOTE:** This task requires `Natural20.getQuestSystem()` which is added in Task 11. This task should be compiled together with Task 11. Save changes but defer compilation.

**Step 4: Commit (after Task 11 compiles)**

```bash
git add src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
```

---

### Task 11: QuestSystem Facade and Registration in Natural20.java

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java`

**Step 1: Create QuestSystem facade**

```java
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
```

**Step 2: Add QuestSystem to Natural20.java**

Add field after `lootSystem` (line 44):

```java
private QuestSystem questSystem;
```

Add accessor after `getLootSystem()` (line 81):

```java
public QuestSystem getQuestSystem() {
    return questSystem;
}
```

Add initialization in `start()`, after loot system load (line 169):

```java
// Initialize quest system
questSystem = new QuestSystem(settlementRegistry);
questSystem.loadTemplates(getDataDirectory().resolve("quests"));
```

**Step 3: Compile (this compiles Task 10 + Task 11 together)**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestSystem.java \
       src/main/java/com/chonbosmods/Natural20.java \
       src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(quest): add QuestSystem facade and wire into Natural20

QuestSystem facade coordinates all quest subsystems.
GIVE_QUEST/COMPLETE_QUEST actions now generate and complete real quests.
Quest templates loaded from plugin data directory on startup."
```

---

### Task 12: Write Starter Quest Templates

**Files:**
- Create: `src/main/resources/quests/Supplication/exposition_variants.json`
- Create: `src/main/resources/quests/Supplication/conflict_variants.json`
- Create: `src/main/resources/quests/Supplication/resolution_variants.json`
- Create: `src/main/resources/quests/Supplication/references.json`
- Create: `src/main/resources/quests/Supplication/npc_weights.json`
- Create: `src/main/resources/quests/DaringEnterprise/exposition_variants.json`
- Create: `src/main/resources/quests/DaringEnterprise/conflict_variants.json`
- Create: `src/main/resources/quests/DaringEnterprise/resolution_variants.json`
- Create: `src/main/resources/quests/DaringEnterprise/references.json`
- Create: `src/main/resources/quests/DaringEnterprise/npc_weights.json`
- Create: `src/main/resources/quests/Pursuit/exposition_variants.json`
- Create: `src/main/resources/quests/Pursuit/conflict_variants.json`
- Create: `src/main/resources/quests/Pursuit/resolution_variants.json`
- Create: `src/main/resources/quests/Pursuit/references.json`
- Create: `src/main/resources/quests/Pursuit/npc_weights.json`

Write 3 starter situations with 3 variants each to prove the system. This task should be authored by hand with care for dialogue quality. Template variables use `{variable_name}` syntax.

**Step 1: Create Supplication templates**

Supplication: a desperate plea for help. Weighted toward Villager and ArtisanCook.

`exposition_variants.json`:
```json
{
  "variants": [
    {
      "id": "supplication_expo_01",
      "dialogueChunks": {
        "intro": "Please, you have to help me. The {enemy_type} raided our stores and took everything we had.",
        "plotStep": "I've heard there may be supplies at a settlement {location_hint}. Could you gather {quest_item} for us?",
        "outro": "Thank you. You have no idea what this means to us."
      },
      "playerResponses": [
        { "text": "I'll help you.", "action": "ACCEPT" },
        { "text": "What do I get out of this?", "action": "ACCEPT", "dispositionShift": -5 },
        { "text": "I'm busy right now.", "action": "DECLINE" }
      ],
      "objectivePool": ["GATHER_ITEMS", "FETCH_ITEM"],
      "objectiveConfig": {
        "GATHER_ITEMS": { "countMin": 5, "countMax": 12 },
        "FETCH_ITEM": { "locationPreference": "SETTLEMENT" }
      }
    },
    {
      "id": "supplication_expo_02",
      "dialogueChunks": {
        "intro": "I don't know who else to turn to. Our people are suffering, and no one seems to care.",
        "plotStep": "There's a place {location_hint} where we might find what we need. Would you go there for me?",
        "outro": "Bless you. I'll be waiting right here."
      },
      "playerResponses": [
        { "text": "Tell me more about what you need.", "action": "ACCEPT" },
        { "text": "I'll see what I can do.", "action": "ACCEPT" },
        { "text": "Not my problem.", "action": "DECLINE", "dispositionShift": -10 }
      ],
      "objectivePool": ["EXPLORE_LOCATION", "GATHER_ITEMS"],
      "objectiveConfig": {
        "GATHER_ITEMS": { "countMin": 8, "countMax": 15 }
      }
    },
    {
      "id": "supplication_expo_03",
      "dialogueChunks": {
        "intro": "Stranger, I beg of you. We've lost so much already, and winter is coming fast.",
        "plotStep": "If you could bring us some {quest_item}, it would save lives. I've marked the direction: {location_hint}.",
        "outro": "May fortune favor your journey."
      },
      "playerResponses": [
        { "text": "I won't let your people down.", "action": "ACCEPT", "dispositionShift": 5 },
        { "text": "How much are we talking?", "action": "ACCEPT" },
        { "text": "I can't make promises.", "action": "DECLINE" }
      ],
      "objectivePool": ["GATHER_ITEMS", "DELIVER_ITEM", "FETCH_ITEM"],
      "objectiveConfig": {
        "GATHER_ITEMS": { "countMin": 5, "countMax": 10 },
        "DELIVER_ITEM": {},
        "FETCH_ITEM": { "locationPreference": "DUNGEON" }
      }
    }
  ]
}
```

`conflict_variants.json`:
```json
{
  "variants": [
    {
      "id": "supplication_conf_01",
      "dialogueChunks": {
        "intro": "Things have gotten worse since you left. The {enemy_type} came back, bolder this time.",
        "plotStep": "We need someone to deal with them before they destroy what little we have left.",
        "outro": "Be careful out there."
      },
      "playerResponses": [
        { "text": "I'll handle them.", "action": "ACCEPT" },
        { "text": "How many are there?", "action": "ACCEPT" }
      ],
      "objectivePool": ["KILL_MOBS", "EXPLORE_LOCATION"],
      "objectiveConfig": {
        "KILL_MOBS": { "countMin": 3, "countMax": 8 }
      }
    },
    {
      "id": "supplication_conf_02",
      "dialogueChunks": {
        "intro": "I hate to ask more of you, but there's someone who might be able to help us.",
        "plotStep": "There's a person at a settlement {location_hint}. They might know what to do about our situation.",
        "outro": "Please, speak with them. Tell them I sent you."
      },
      "playerResponses": [
        { "text": "I'll find them.", "action": "ACCEPT" },
        { "text": "This is getting complicated.", "action": "ACCEPT", "dispositionShift": -3 }
      ],
      "objectivePool": ["TALK_TO_NPC", "EXPLORE_LOCATION"],
      "objectiveConfig": {}
    },
    {
      "id": "supplication_conf_03",
      "dialogueChunks": {
        "intro": "I've discovered something troubling. Someone has been sabotaging our recovery.",
        "plotStep": "I found tracks leading toward {location_hint}. Someone needs to investigate.",
        "outro": "Find out who is behind this."
      },
      "playerResponses": [
        { "text": "I'll get to the bottom of this.", "action": "ACCEPT" },
        { "text": "Sabotage? That changes things.", "action": "ACCEPT" }
      ],
      "objectivePool": ["EXPLORE_LOCATION", "KILL_NPC", "FETCH_ITEM"],
      "objectiveConfig": {
        "FETCH_ITEM": { "locationPreference": "DUNGEON" }
      }
    }
  ]
}
```

`resolution_variants.json`:
```json
{
  "variants": [
    {
      "id": "supplication_reso_01",
      "dialogueChunks": {
        "intro": "You've done more than anyone could have asked. Our people will survive because of you.",
        "plotStep": "Take this as a token of our gratitude. You've earned it.",
        "outro": "You will always be welcome here."
      },
      "playerResponses": [
        { "text": "I'm glad I could help.", "action": "ACCEPT", "dispositionShift": 10 },
        { "text": "Just doing what needed to be done.", "action": "ACCEPT", "dispositionShift": 5 }
      ],
      "objectivePool": [],
      "objectiveConfig": {}
    },
    {
      "id": "supplication_reso_02",
      "dialogueChunks": {
        "intro": "I can't believe it's over. We owe you everything.",
        "plotStep": "Please, accept this. It's all we can offer, but it comes with our deepest thanks.",
        "outro": "If you ever need anything, don't hesitate to ask."
      },
      "playerResponses": [
        { "text": "Thank you. Stay safe.", "action": "ACCEPT", "dispositionShift": 10 },
        { "text": "You owe me one.", "action": "ACCEPT", "dispositionShift": -5 }
      ],
      "objectivePool": [],
      "objectiveConfig": {}
    },
    {
      "id": "supplication_reso_03",
      "dialogueChunks": {
        "intro": "The village is safe again, thanks to you. The children can sleep without fear.",
        "plotStep": "Here, this was my grandmother's. I want you to have it.",
        "outro": "Walk with fortune, friend."
      },
      "playerResponses": [
        { "text": "I'll treasure it.", "action": "ACCEPT", "dispositionShift": 15 },
        { "text": "Stay strong.", "action": "ACCEPT", "dispositionShift": 5 }
      ],
      "objectivePool": [],
      "objectiveConfig": {}
    }
  ]
}
```

`references.json`:
```json
{
  "references": [
    {
      "id": "ref_desperate_merchant",
      "compatibleSituations": ["Supplication", "Obtaining", "Loss"],
      "passiveText": "I heard a merchant at a nearby settlement has been begging for help too. Times are hard everywhere.",
      "triggerTopicLabel": "Desperate Times",
      "triggerDialogue": "You've heard about our troubles? Yes, we're barely hanging on. I thought we were the only ones suffering, but it seems the whole region is affected.",
      "catalystSituations": ["Supplication", "Disaster"],
      "targetNpcRoles": ["ArtisanCook", "Villager", "TavernKeeper"]
    }
  ]
}
```

`npc_weights.json`:
```json
{
  "Villager": 3.0,
  "ArtisanCook": 3.0,
  "ArtisanAlchemist": 1.0,
  "TavernKeeper": 1.5,
  "ArtisanBlacksmith": 0.5,
  "Guard": 0.5,
  "Traveler": 0.5
}
```

**Step 2: Create DaringEnterprise and Pursuit templates**

Follow the same pattern with appropriate thematic content. DaringEnterprise: ambitious undertakings, weighted toward Traveler and ArtisanBlacksmith. Pursuit: hunting/tracking, weighted toward Guard and Traveler.

Create the same 5 files for each situation with 3 variants per phase file.

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/resources/quests/
git commit -m "feat(quest): add starter quest templates for 3 situations

Supplication, DaringEnterprise, Pursuit with 3 variants each
per phase. Includes reference templates and NPC role weights."
```

---

### Task 13: Smoke Test

**Step 1: Copy quest templates to dev server data directory**

```bash
mkdir -p devserver/data/nat20/quests
cp -r src/main/resources/quests/* devserver/data/nat20/quests/
```

**Step 2: Wipe world data for clean test**

```bash
rm -rf devserver/universe/worlds devserver/universe/players
```

**Step 3: Start dev server**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew devServer`

**Step 4: Verify quest template loading**

Check server console for: `Loaded N quest situation(s)`

**Step 5: Test quest generation**

1. Find a settlement NPC
2. Interact (F-key)
3. If NPC has a quest topic, select it
4. Verify quest generates and message appears
5. Check server console for quest generation log

**Step 6: Verify quest state persistence**

1. Disconnect and reconnect
2. Verify quest data persists in player data

---

## Implementation Notes

### Compile Command
Always use: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`

### JSON Loading Path
Quest templates load from `getDataDirectory().resolve("quests")` which maps to `devserver/data/nat20/quests/` in dev mode. For classpath loading from the JAR, templates go in `src/main/resources/quests/`.

### Thread Safety
`QuestStateManager` reads/writes to `Nat20PlayerData` which is an ECS component. All access should happen on the entity's world thread. Event listeners that call `QuestTracker.reportProgress()` already run on the correct thread.

### What's Deferred
- Objective event listeners (actual inventory/kill/position listeners): separate PR, requires wiring into Hytale events
- Loot reward generation (currently stubbed): integrate with Nat20LootPipeline
- XP application to leveling system: requires leveling system implementation
- Fetch Item chest spawning: requires chest placement infrastructure
- Kill NPC hostile spawning: requires hostile NPC spawn infrastructure
- Dialogue system integration for quest topics: requires adding quest-type topic support to ConversationSession
