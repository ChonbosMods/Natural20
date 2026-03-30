# TALK_TO_NPC Quest Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire up the TALK_TO_NPC quest objective so the player gets a yellow "!" marker at the target settlement, a quest dialogue topic is injected on the target NPC, and completing that conversation marks the objective done and shows the green "?" return marker.

**Architecture:** The target NPC dialogue text is already pre-generated at quest creation time (`target_npc_dialogue` binding). The implementation injects that text as a dialogue topic when the player talks to the target NPC, marks the objective complete on topic selection, and refreshes markers. No new ECS systems needed: all changes are in DialogueManager (topic injection), QuestMarkerProvider (new marker type), and the marker update method (new icon rendering).

**Tech Stack:** DialogueManager topic injection (same pattern as injectTurnInTopics), QuestMarkerProvider (new TARGET_NPC MarkerType), existing quest state machine.

---

### Task 1: Add TARGET_NPC Marker Type

**Files:**
- Modify: `src/main/java/com/chonbosmods/waypoint/QuestMarkerProvider.java`

**Step 1: Add TARGET_NPC to MarkerType enum**

Line 52, change:
```java
public enum MarkerType { POI, RETURN }
```
to:
```java
public enum MarkerType { POI, RETURN, TARGET_NPC }
```

**Step 2: Add icon constant**

Near other icon constants (lines 46-48):
```java
private static final String TARGET_ICON = "QuestTarget.png";
```

**Step 3: Add TARGET_NPC marker creation in refreshMarkers()**

In `refreshMarkers()` (line 100-144), after the `else if (hasPoi)` block (line 126-139), add a new branch for TALK_TO_NPC objectives. This fires when objectives are NOT complete and there is NO POI but there IS a `target_npc_settlement`:

```java
} else {
    // TARGET_NPC marker for TALK_TO_NPC objectives: yellow "!" at target settlement
    String targetSettlement = b.get("target_npc_settlement");
    if (targetSettlement != null && b.containsKey("target_npc")) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements != null) {
            SettlementRecord target = settlements.getByCell(targetSettlement);
            if (target != null) {
                String targetLabel = "Speak with " + b.get("target_npc");
                entries.add(new MarkerEntry(quest.getQuestId(), targetLabel,
                    target.getPosX(), target.getPosZ(), MarkerType.TARGET_NPC));
            }
        }
    }
}
```

**Step 4: Render TARGET_NPC markers in update()**

In the `update()` method (line 146-200), inside the marker iteration loop, add a case for TARGET_NPC. It should render identically to RETURN (always visible, no ring) but with the TARGET_ICON:

```java
if (entry.type == MarkerType.RETURN) {
    // existing RETURN rendering...
} else if (entry.type == MarkerType.TARGET_NPC) {
    collector.addIgnoreViewDistance(
        new MapMarkerBuilder("nat20_target_" + entry.questId, TARGET_ICON,
            new Transform(new Vector3d(entry.x, playerPos.getY(), entry.z)))
            .withCustomName(entry.questName)
            .build());
} else {
    // existing POI rendering...
}
```

**Step 5: Create placeholder icon file**

Create: `src/main/resources/Common/UI/Custom/Pages/QuestTarget.png`

For now, copy `QuestReturn.png` as the placeholder. The yellow "!" texture will be created later.

**Step 6: Compile and commit**

```
feat(quests): add TARGET_NPC marker type for TALK_TO_NPC objectives
```

---

### Task 2: Inject Quest Topic on Target NPC

**Files:**
- Modify: `src/main/java/com/chonbosmods/dialogue/DialogueManager.java`

**Step 1: Add injectTalkToNpcTopics() method**

Add a new method after `injectTurnInTopics()` (line 288). This method checks if the NPC the player is talking to is a TALK_TO_NPC target for any of the player's active quests. If so, it injects a quest dialogue topic.

```java
/**
 * If this NPC is the target of a TALK_TO_NPC objective, inject a quest dialogue topic
 * that, when selected, marks the objective complete and sets phase_objectives_complete.
 */
private void injectTalkToNpcTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
    QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
    if (questSystem == null) return;

    Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
    for (QuestInstance quest : quests.values()) {
        Map<String, String> b = quest.getVariableBindings();

        // Skip if objectives already complete (awaiting turn-in at quest giver, not here)
        if ("true".equals(b.get("phase_objectives_complete"))) continue;

        // Check current phase for TALK_TO_NPC objective targeting this NPC
        PhaseInstance phase = quest.getCurrentPhase();
        if (phase == null) continue;

        for (ObjectiveInstance obj : phase.getObjectives()) {
            if (obj.getType() != ObjectiveType.TALK_TO_NPC) continue;
            if (obj.isComplete()) continue;

            // Match: targetId is the NPC's generated name
            String targetNpc = obj.getTargetId();
            if (!npcId.equals(targetNpc)) continue;

            // This NPC is the target. Inject a quest dialogue topic.
            String questId = quest.getQuestId();
            String targetDialogue = b.getOrDefault("target_npc_dialogue",
                "You're looking into the situation? I can tell you what I know.");
            String questTitle = b.getOrDefault("quest_title", quest.getSituationId());
            String questGiver = quest.getSourceNpcId();

            String topicId = "talknpc_" + questId;
            String entryNodeId = topicId + "_entry";
            String actionNodeId = topicId + "_action";
            String confirmNodeId = topicId + "_confirm";

            // Action: COMPLETE_TALK_TO_NPC (custom action, see Task 3)
            graph.nodes().put(actionNodeId, new DialogueNode.ActionNode(
                List.of(Map.of("type", "COMPLETE_TALK_TO_NPC", "questId", questId)),
                confirmNodeId, List.of(), true
            ));

            // Confirm: direct player back to quest giver
            String confirmText = "Tell " + questGiver + " what I've told you. They'll want to hear it.";
            graph.nodes().put(confirmNodeId, new DialogueNode.DialogueTextNode(
                confirmText, List.of(), List.of(), true, false
            ));

            // Entry: target NPC delivers their dialogue, player can acknowledge
            String topicLabel = "About " + questTitle;
            graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                targetDialogue,
                List.of(new ResponseOption(
                    topicId + "_resp", "I'll pass that along.", null, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                )),
                List.of(), false, false
            ));

            // Topic: priority (sortOrder 0), always visible, quest-flagged
            graph.topics().addFirst(new TopicDefinition(
                topicId, topicLabel, entryNodeId,
                TopicScope.LOCAL, null, true, null, 0, null, true
            ));

            LOGGER.atInfo().log("Injected TALK_TO_NPC topic for quest %s on NPC %s",
                questId, npcId);
        }
    }
}
```

**Step 2: Call injectTalkToNpcTopics in startSession()**

In `startSession()` (line 49), after the existing `injectTurnInTopics()` call (line 92), add:

```java
// Inject quest topics for any TALK_TO_NPC objectives targeting this NPC
injectTalkToNpcTopics(graph, npcId, playerData);
```

**Step 3: Add required imports**

Add imports for `ObjectiveType`, `ObjectiveInstance`, `PhaseInstance`, `ResponseMode`, `ResponseOption`, `TopicDefinition`, `TopicScope`, `DialogueNode` if not already present.

**Step 4: Compile and commit**

```
feat(quests): inject quest dialogue topic on TALK_TO_NPC target NPCs
```

---

### Task 3: Register COMPLETE_TALK_TO_NPC Action

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java`

**Step 1: Register the action**

After the existing `TURN_IN_PHASE` registration (line 267), add:

```java
register("COMPLETE_TALK_TO_NPC", (ctx, params) -> {
    String questId = params.get("questId");
    QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
    if (questSystem == null || questId == null) return;

    QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
    if (quest == null) return;

    // Mark the TALK_TO_NPC objective complete
    PhaseInstance phase = quest.getCurrentPhase();
    if (phase == null) return;

    for (ObjectiveInstance obj : phase.getObjectives()) {
        if (obj.getType() == ObjectiveType.TALK_TO_NPC && !obj.isComplete()) {
            obj.markComplete();
            break;
        }
    }

    // Check if all phase objectives are now complete
    if (phase.isComplete()) {
        quest.getVariableBindings().put("phase_objectives_complete", "true");
    }

    // Save quest state
    Map<String, QuestInstance> allQuests = questSystem.getStateManager().getActiveQuests(ctx.playerData());
    allQuests.put(quest.getQuestId(), quest);
    questSystem.getStateManager().saveActiveQuests(ctx.playerData(), allQuests);

    // Refresh markers: TARGET_NPC marker disappears, RETURN marker appears
    QuestMarkerProvider.refreshMarkers(
        ctx.player().getPlayerRef().getUuid(), ctx.playerData());

    LOGGER.atInfo().log("COMPLETE_TALK_TO_NPC: quest %s objective complete, return to %s",
        questId, quest.getSourceNpcId());
});
```

**Step 2: Add required imports**

`ObjectiveType`, `ObjectiveInstance`, `PhaseInstance`, `QuestMarkerProvider` if not already imported.

**Step 3: Compile and commit**

```
feat(quests): COMPLETE_TALK_TO_NPC action marks objective done and swaps marker
```

---

### Task 4: Verify Full Flow and Edge Cases

**Files:**
- Possibly modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (if adjustments needed)

**Step 1: Verify marker flow**

Trace the full marker lifecycle:
1. Quest accepted → `target_npc_settlement` binding set, `poi_available` is NOT "true" for TALK_TO_NPC → `refreshMarkers()` should create TARGET_NPC marker (from Task 1)
2. Player talks to target NPC → `COMPLETE_TALK_TO_NPC` fires → `phase_objectives_complete` = "true" → `refreshMarkers()` → TARGET_NPC marker removed (objectivesComplete is now true), RETURN marker appears
3. Player returns to quest giver → turn-in → RETURN marker removed

Verify: in `refreshMarkers()`, the TARGET_NPC branch only fires when `!objectivesComplete && !hasPoi && target_npc_settlement exists`. After COMPLETE_TALK_TO_NPC sets `phase_objectives_complete`, `objectivesComplete` becomes true so the RETURN branch fires instead.

**Step 2: Verify TALK_TO_NPC quests don't set poi_available**

In QuestGenerator, the TALK_TO_NPC case (line 495-506) should NOT set `poi_available: true`. Verify the bindings are clean: no `poi_x/y/z`, no `poi_mob_state`, no `poi_spawn_descriptor`. These would cause the POI marker to appear instead of TARGET_NPC.

**Step 3: Compile full build**

```
./gradlew compileJava
```

**Step 4: Commit (if any fixes needed)**

```
fix(quests): verify TALK_TO_NPC marker and objective flow
```

---

## Marker Flow Summary

```
TALK_TO_NPC quest accepted
  ↓
refreshMarkers: target_npc_settlement exists, !objectivesComplete, !hasPoi
  → TARGET_NPC marker (yellow "!") at target settlement
  ↓
Player talks to target NPC
  → injectTalkToNpcTopics: quest topic injected
  → Player selects topic, clicks "I'll pass that along."
  → COMPLETE_TALK_TO_NPC action fires
  → objective.markComplete(), phase_objectives_complete = true
  → refreshMarkers: objectivesComplete = true
  → RETURN marker (green "?") at quest giver's settlement
  ↓
Player returns to quest giver
  → injectTurnInTopics: turn-in topic injected
  → TURN_IN_PHASE action fires
  → phase advanced or quest completed
  → refreshMarkers: RETURN marker removed
```

## What Exists vs What This Plan Adds

| Component | Exists | This Plan Adds |
|-----------|--------|----------------|
| Target NPC selection | ✓ (QuestGenerator) | - |
| target_npc_dialogue binding | ✓ (QuestGenerator) | - |
| Response pools (100+ entries) | ✓ (QuestPoolRegistry) | - |
| Objective summary "speak with X" | ✓ (QuestGenerator) | - |
| TARGET_NPC marker type | ✗ | Task 1 |
| Quest topic injection on target | ✗ | Task 2 |
| COMPLETE_TALK_TO_NPC action | ✗ | Task 3 |
| Marker flow verification | ✗ | Task 4 |
