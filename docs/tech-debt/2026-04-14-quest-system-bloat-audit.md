# Quest System Dead Code & Bloat: Tech Debt Audit

**Date:** 2026-04-14
**Branch discovered on:** `fix/fetch-naming-and-continue-buttons`
**Status:** Findings logged for later triage. Not blocking any in-flight work.

## Context

During the fetch-item-naming + reward-schema refactor on the `fix/fetch-naming-and-continue-buttons` branch, repeated dead-code recon passes surfaced more bloat than could be cleaned in one branch without losing focus on the imperative deliverable. The items below are **known-or-suspected dead code** the codebase can afford to carry while the main task finishes.

Cleanup already completed on this branch (for reference, so we don't re-audit):

- `v1_archived/` pool directory (commit `3978970`)
- `PhaseType` / `PhaseInstance` / `QuestRewardManager` (whole class) / `QuestTemplateRegistry.getVariant` (commit `7ba56c8`)
- 22 unused `random*` methods in `QuestPoolRegistry` (commit `36bc5be`)
- v2 smalltalk-about-quests subsystem: `TopicGenerator` quest-binding block, `TopicGraphBuilder` quest-bearer else branch, `SubjectFocus` quest accessors, 23 dead pool fields, 15 load/parse helpers, 4 dead pool `random*` methods, `NarrativeEntry` record, 2 Category C bindings (`quest_template_id`, `gather_item_id`) (commit `c78cc9b`)
- Dead `rewardText` field in all 259 v2+mundane templates (commit `1464711`)

## Findings by certainty

### Bucket A: Verified dead, safe delete candidates

These have been observed with zero callers but not yet deleted.

#### A1. `TopicPoolRegistry.randomPerspectiveDetail`

- **Location:** `src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java`
- **Why dead:** Only caller was the quest-binding block in `TopicGenerator.buildBindings`, retired in `c78cc9b`.
- **Investigation needed:** Confirm no other caller with a grep pass.
- **Estimated effort:** 10 minutes. One grep, one commit.

#### A2. `QuestPoolRegistry.setTemplateRegistry`

- **Location:** `src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java`
- **Why dead:** The field `templateRegistry` was removed in `c78cc9b`. The setter was retained as a no-op "to avoid churning QuestSystem constructor and future authoring hooks." Its caller in `QuestSystem` still invokes it. Both ends can go.
- **Investigation needed:** Confirm the called-but-does-nothing pattern, delete both sides.
- **Estimated effort:** 15 minutes.

### Bucket B: Live readers found, but readers may themselves be vestigial

Earlier recon tagged these as dead. Verification found they have consumers. Those consumers haven't been traced deep enough to confirm whether the full chain is alive.

#### B1. `fetch_item_label` binding

- **Set by:** `QuestGenerator.java` (FETCH_ITEM and PEACEFUL_FETCH branches)
- **Read by:** `POIProximitySystem.java:129`
- **Open question:** Does `POIProximitySystem` use the label for something player-facing, or is it a reflexive read with no downstream effect?
- **Investigation needed:** Read `POIProximitySystem` around line 129, trace what it does with the value.
- **Estimated effort:** 30 minutes.

#### B2. `enemy_type_id` binding

- **Set by:** `QuestGenerator.java:200` (approximately)
- **Read by:** `DialogueActionRegistry.java:672, 734`
- **Open question:** What do those DialogueActionRegistry sites do with `enemy_type_id`? Is it surfaced to players, or internal plumbing?
- **Investigation needed:** Read the two DialogueActionRegistry call sites; determine downstream use.
- **Estimated effort:** 30 minutes.

#### B3. `npc_x` / `npc_z` / `npc_settlement_key` bindings

- **Set by:** `QuestGenerator.resolveWorldBindings` (around lines 169-171)
- **Read by:** `DialogueActionRegistry` and `QuestGenerator` itself (e.g. `QuestGenerator.java:396`)
- **Open question:** Is the `QuestGenerator.java:396` self-read using the binding map as a cross-method communication channel, or is this genuine dead-code-reading-live-code?
- **Investigation needed:** Trace all 4 readers; determine if the binding map is being used as a kv-store (smell) or legitimate.
- **Estimated effort:** 45 minutes.

#### B4. `settlement_type` binding

- **Set by:** `QuestGenerator.java:179`
- **Read by:** zero current v2 templates. Documented in `authoring/quest_v2/quest_variable_palette.md` and `authoring/quest_v2/quest_template_schema.json` as a public palette variable.
- **Open question:** Is the palette doc aspirational (future authors may want it) or vestigial (templates used to reference it)?
- **Investigation needed:** Ask authoring lead whether `settlement_type` is intended for future use. If not, drop from palette docs AND from `QuestGenerator`.
- **Estimated effort:** 15 minutes once the authoring question is answered.

### Bucket C: Single-field suspects

#### C1. `TopicGraphBuilder.TopicDefinition.questTopic` field

- **Location:** `src/main/java/com/chonbosmods/topic/TopicGraphBuilder.java` (field on `TopicDefinition` record)
- **State after `c78cc9b`:** Subagent report says "`TopicDefinition.questTopic` still written as literal `false`" because the smalltalk-about-quests path always produces non-quest topics.
- **Open question:** Does anything read this field and branch on it? If not, the field is dead.
- **Investigation needed:** Grep for `questTopic` reads. Remove the field + its constructor parameter + all writes if zero reads.
- **Estimated effort:** 30 minutes.

### Bucket D: Whole-file / whole-area audits not yet run

These haven't had a recon pass. Running one would likely turn up more items in buckets A–C.

#### D1. `DialogueManager`

- **Path:** `src/main/java/com/chonbosmods/dialogue/DialogueManager.java`
- **Why audit:** Heavily used in the active v2 quest flow, but also the oldest part of the dialogue subsystem. Likely has pre-v2 vestiges. The file appears large (hundreds of lines).
- **Suggested recon:** Full method-by-method caller scan, flag any methods with zero callers OR callers that are themselves dead.
- **Estimated effort:** 1-2 hours.

#### D2. `DialogueResolver`

- **Path:** `src/main/java/com/chonbosmods/quest/DialogueResolver.java`
- **Why audit:** Touched during task 6 and 7+8, but only the `HIGHLIGHTED_QUEST_VARS` set and `overlayObjective`. The rest of the file is unverified.
- **Suggested recon:** Full method-by-method pass.
- **Estimated effort:** 45 minutes.

#### D3. `DialogueActionRegistry`

- **Path:** `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java`
- **Why audit:** The branch's name includes `continue-buttons`, implying work is planned here. Also the registry pattern accumulates dead action handlers easily.
- **Suggested recon:** List all registered action names, grep each for dispatcher references in JSON templates.
- **Estimated effort:** 1 hour.

#### D4. Remaining `topic/` files

- **Paths:** `TopicConstants`, `PercentageDedup`, `DispositionBracket`, `TopicPoolRegistry`, `TopicTemplateRegistry`
- **Why audit:** Sibling files to the heavily-touched `TopicGenerator` / `TopicGraphBuilder` / `SubjectFocus`. Dead bits may have survived by association.
- **Estimated effort:** 1-2 hours.

#### D5. Remaining `quest/` files

- **Paths:** `QuestState`, `QuestInstance`, `ObjectiveType`, `FetchItemTrackingSystem`, `CollectResourceTrackingSystem`, `POIKillTrackingSystem`, `QuestTracker`, `QuestStateManager`, `QuestSystem`, `QuestTemplateRegistry`
- **Why audit:** Quest state machine churned significantly during v1→v2. Tracking systems per objective type may have unused paths.
- **Estimated effort:** 2-3 hours.

#### D6. Asset / resources directories outside `quests/`

- **Paths:** `assets/Server/`, `src/main/resources/Common/`, `src/main/resources/loot/`
- **Why audit:** The working tree's initial git-status showed many deleted loot affix files unrelated to this branch. Those likely went through `main` already. A sanity sweep would confirm nothing in Java still references removed asset ids.
- **Estimated effort:** 30 minutes.

### Bucket E: Git-history weight

#### E1. `v1_archived/` historical content

- **State:** Deleted from the working tree in commit `3978970`, but still in git's pack history at `3978970^`.
- **Open question:** Does the repo's pack size matter enough to justify a destructive `git filter-repo` operation to purge the history? Probably not for a small team; definitely not without explicit consent.
- **Action:** No action unless repo size becomes a real problem. Listed here so it's not forgotten.

## Recommended triage order

When picking this backlog up later, suggested order by impact vs. effort:

1. **A1 + A2** (verified-dead deletes) — 25 minutes, one commit each
2. **C1** (`questTopic` flag) — 30 minutes, likely a clean removal
3. **B1 → B4** (binding consumer investigations) — 2 hours total; may cascade into more deletes
4. **D2** (`DialogueResolver` audit) — 45 minutes; small surface
5. **D1** (`DialogueManager` audit) — 1–2 hours; biggest expected yield
6. **D3** (`DialogueActionRegistry`) — do alongside the continue-buttons design work on its own branch
7. **D4 + D5** (remaining files) — longer tail, pick opportunistically
8. **D6** (asset directories) — cheap sanity check, low expected yield
9. **E1** (history prune) — defer indefinitely

## Notes on recon quality

Earlier recon passes produced partial false positives on Category C bindings — `fetch_item_label`, `enemy_type_id`, `npc_x`, `npc_z`, `npc_settlement_key` were each tagged "dead" by a greppy scan but later found to have real readers. Lesson: for any binding-string candidate, check BOTH `{binding_name}` in template JSON AND `get("binding_name")` / `getOrDefault("binding_name"` in Java code. Greps limited to one form miss consumers on the other side.
