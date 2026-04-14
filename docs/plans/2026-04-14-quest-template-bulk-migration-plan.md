# Quest Template Bulk Migration: Multi-Phase Plan

> **For Claude:** Execute phase-by-phase. Phase 1 is script-assisted and lands one commit. Phases 2–3 dispatch multiple subagents in ordered batches, one commit per batch.

**Revision history:**
- 2026-04-14 (initial): plan written assuming `rewardText` still on disk as source material for Phase 2.
- 2026-04-14 (revised 1): commit `1464711` deleted `rewardText` from all 259 templates as part of the v1 cleanup. Phase 1 now pulls source material from git history via `git show 1464711^:<file>` instead of reading the live file. Phase 2's "delete rewardText" step is removed (it's already gone).
- 2026-04-14 (revised 2): reward schema moved to a runtime difficulty-driven model (see `2026-04-14-fetch-item-naming-impl.md` Addendum + Tasks D1–D5). Templates no longer carry `rewardGold`, `rewardItem`, or `rewardXP` — only `rewardFlavor`. Phase 1 script is simplified: seed `rewardFlavor` from legacy prose and rewrite `{quest_reward}` to a one-beat `"Take this {reward_item}."` for every template. Phase 2 upgrades the subset with real flavor to the two-beat form.

**Goal:** Migrate all 259 v2 quest templates to seed a `rewardFlavor` field from legacy prose and rewrite every `{quest_reward}` token into a `{reward_item}` reference, so the difficulty-driven reward system (Tasks D1–D5 in the impl plan) renders correctly at runtime.

**Why this plan exists:** Task 9 of `2026-04-14-fetch-item-naming-impl.md` was scoped under the assumption of a handful of templates. Actual scope:
- `src/main/resources/quests/v2/index.json`: 238 templates, 238 `{quest_reward}` tokens, 302 `{quest_item}` tokens, 183 have FETCH_ITEM objectives.
- `src/main/resources/quests/mundane/index.json`: 21 templates, 21 `{quest_reward}`, 30 `{quest_item}`, all use PEACEFUL_FETCH (not FETCH_ITEM).
- Original `rewardText` prose lives at `git show 1464711^:<path>` and is the authoritative source for Phase 2 flavor authoring.

About 500 authoring edits once you strip out the fields that are no longer template-owned. Not safe to do in one pass. This plan decomposes into phases where each phase has a clean acceptance test and commits independently.

**Runtime state as of this revision:** templates render literal `{quest_reward}` tokens in quest-giver dialogue (no binding exists for that key post-reward-schema-split). After impl-plan Tasks D1–D5 land, every quest generation will pre-roll a difficulty-tier reward item and bind its display name as `{reward_item}`. Phase 1 of this plan is the minimum intervention needed for templates to actually reference it.

**Architecture:**
- **Phase 1** is a mechanical script-assisted pass that seeds `rewardFlavor` from the pre-deletion `rewardText` prose (read from git history) and rewrites every `{quest_reward}` token into one-beat `"Take this {reward_item}."`. Runtime after Phase 1: no literal `{quest_reward}` tokens leak to players, and every quest's reward item shows up in its resolution text.
- **Phase 2** is a sequence of small authoring batches (~25 templates each) handled by dedicated subagents. Each batch reviews Phase 1's seeded output, polishes `rewardFlavor` into a ≤5-word form (or sets it null for purely transactional templates), and upgrades the one-beat resolution text to a two-beat form where flavor exists. Ordered sequentially (shared file, no race).
- **Phase 3** is the same pattern for the `{quest_item}` article audit, scoped to FETCH_ITEM-carrying templates only.
- **Phase 4** is a final grep + compile sweep.

**Tech Stack:** Python (stdlib only) for the Phase 1 script. Java 25 + Gson for compile verification. No unit tests exist.

**Files touched across all phases:**
- `src/main/resources/quests/v2/index.json`
- `src/main/resources/quests/mundane/index.json`
- Temporary: a one-off script at `tools/quest_template_migration.py` (deleted in Phase 4)
- Read-only reference (via `git show`): the pre-`1464711` versions of both index files

---

## Phase 1: Script-assisted baseline migration

**Goal:** Every template carries a seeded `rewardFlavor` (verbatim legacy prose from git history); every `{quest_reward}` in text fields becomes the one-beat `"Take this {reward_item}."` form. Phase 2 polishes the seeded flavor and upgrades the subset with real flavor to two-beat.

**Source material:** the legacy `rewardText` prose lives at `git show 1464711^:<path>` for both index files. The script reads that as a side-channel and injects each template's legacy prose into the new `rewardFlavor` slot (unconverted — preserving original length) so Phase 2 authors can see what the template used to say before condensing.

**Prerequisites:** impl-plan Tasks D1–D5 must already be on `fix/fetch-naming-and-continue-buttons` before this Phase runs. Without them, the `{reward_item}` token references the script writes have no binding at runtime.

### Task 1.1: Write the migration script

**Files:**
- Create: `tools/quest_template_migration.py`

**Behavior:**

The script reads TWO JSONs per file: the current on-disk file (has no `rewardText`) and the pre-deletion file from git (has `rewardText`). It matches templates by `id`, copies `rewardText` values from the git version into the new `rewardFlavor` field, rewrites `{quest_reward}` tokens, and writes the result back to disk.

CLI signature:
```
python3 tools/quest_template_migration.py --phase 1 \
    --file src/main/resources/quests/v2/index.json \
    --legacy-rev 1464711^
```

Per-template logic:

1. Look up this template's `id` in the legacy JSON. Extract `legacyRewardText` (may be null if a new template was added after `1464711`).
2. Add the `rewardFlavor` field on the current template:
   - `rewardFlavor`: `legacyRewardText` verbatim if present, else `null`. **Note:** Phase 2 will condense this to ≤5 words; the verbatim form is intentional scaffolding, not a final value. A `# TODO-P2` trailing comment in the JSON is not possible (JSON has no comments) but that's fine — Phase 2 identifies candidates by "rewardFlavor has >5 words".
3. For every string field in the template (the schema is flat: iterate known text fields `expositionText`, `acceptText`, `declineText`, `expositionTurnInText`, `conflict1Text`, `conflict1TurnInText`, `conflict2Text`, `conflict2TurnInText`, `conflict3Text`, `conflict3TurnInText`, `conflict4Text`, `conflict4TurnInText`, `resolutionText`, `targetNpcOpener`, `targetNpcCloser`, `targetNpcOpener2`, `targetNpcCloser2`, `skillCheck.passText`, `skillCheck.failText`), rewrite:
   - `{quest_reward}` → `Take this {reward_item}.`  — literal replacement of the token with a complete one-beat sentence. In cases where the surrounding text already provides a verb like "Take" and was relying on `{quest_reward}` being a noun phrase, a naive token swap will produce awkward prose ("Take Take this {reward_item}."). Phase 2 fixes these during the authoring review.
   - DO NOT touch `{quest_item}` in this phase.
4. Preserve JSON formatting: 2-space indent, ASCII-escaped strings OFF (keep UTF-8), key order preserved. Use `json.dump(obj, fp, indent=2, ensure_ascii=False)` with `sort_keys=False`.

The git-history read is a one-liner: `subprocess.check_output(["git", "show", f"{legacy_rev}:{path}"])`. Parse as JSON, build an id→rewardText lookup map, use it during the per-template pass.

**Fields explicitly NOT touched in Phase 1:**
- `rewardGold` / `rewardXP` / `rewardItem` / `difficulty` — these are not template fields in the final schema. Difficulty is runtime-assigned; XP and item come from the difficulty config. Templates only author `rewardFlavor`.

**CLI:**
```
python3 tools/quest_template_migration.py --phase 1 --file src/main/resources/quests/v2/index.json
```

Processes in-place. Prints a summary: `N templates migrated, M {quest_reward} tokens rewritten`.

**Step 1: Write the script.** Keep it under 100 lines.

**Step 2: Dry-run against v2/index.json.** Add a `--dry-run` flag that emits diffs to stdout without writing. Inspect the first template's output manually. Confirm it looks right.

**Step 3: Run for real on both target files.**
```bash
python3 tools/quest_template_migration.py --phase 1 --file src/main/resources/quests/v2/index.json
python3 tools/quest_template_migration.py --phase 1 --file src/main/resources/quests/mundane/index.json
```

**Step 4: Validate.**
```bash
jq . src/main/resources/quests/v2/index.json > /dev/null && jq . src/main/resources/quests/mundane/index.json > /dev/null
grep -c '{quest_reward}' src/main/resources/quests/v2/index.json src/main/resources/quests/mundane/index.json  # both should be 0
grep -c '"rewardFlavor":' src/main/resources/quests/v2/index.json  # should be 238
grep -c '"rewardFlavor":' src/main/resources/quests/mundane/index.json  # should be 21
```

**Step 5: Compile.**
```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

**Step 6: Commit.** Stage the script + both JSON files.

Commit message:
```
data(quest): Phase 1 - script-assisted baseline template migration

Seeds rewardFlavor on all 259 v2+mundane templates verbatim from the
pre-deletion rewardText prose read out of git history at 1464711^.
Rewrites every {quest_reward} token in template text fields to the
one-beat 'Take this {reward_item}.' form so runtime reward binding
(from impl-plan tasks D1-D5) renders the rolled affix item's name.

rewardFlavor values are intentionally verbatim at this phase - most
exceed the 5-word cap and will be condensed in Phase 2 along with
upgrading the one-beat text to two-beat where flavor warrants it.
```

No Co-Authored-By.

---

## Phase 2: Batched reward flavor authoring

**Goal:** Condense Phase 1's verbatim-seeded `rewardFlavor` values (typically the full legacy `rewardText` prose, often 8-20 words) into ≤5-word emotional notes where warranted, or set them to `null` when the original was purely transactional. Polish resolution text from the one-beat form into two-beat where flavor is added.

### Task 2.0: Enumerate situation buckets

**Files:**
- None. This is a recon pass.

**Step 1:** Dispatch a recon subagent. Ask it to extract the unique values of the `situation` field across both index.json files, with counts, and produce a batching plan. Target batch size: 20-30 templates. If any single situation bucket exceeds 30 templates, split it into `<situation>_a`, `<situation>_b`, etc.

**Output:** A markdown table written to `docs/plans/2026-04-14-phase2-batches.md` with columns: batch id, situation(s), template count, template ids.

**Step 2:** Commit the batching plan.
```
docs(quest): Phase 2 batching plan for template flavor authoring
```

### Task 2.1 ... 2.N: Per-batch flavor authoring

**Template per batch (repeat for each):**

**Files:**
- Modify: `src/main/resources/quests/v2/index.json` (or `mundane/index.json` depending on batch)

**Subagent prompt (paste into dispatch):**

> Condense `rewardFlavor` and polish resolution text for the N templates listed below. Each template's current `rewardFlavor` field holds the verbatim pre-deletion `rewardText` prose, seeded by Phase 1. Typical values are 8-20 words and need editing. For each template:
>
> 1. Read the current `rewardFlavor` — this is your source material.
> 2. Decide: is there an emotional/flavor element worth preserving, or is the whole string transactional?
>    - Transactional only (e.g. "a small pouch of silver coins") → set `rewardFlavor: null`, keep the one-beat form in resolutionText (no further edits).
>    - Has flavor (e.g. "what coin I've kept hidden and a meal whenever you pass through") → condense the emotional piece into ≤ 5 words (e.g. `"a meal whenever you visit"`) and overwrite `rewardFlavor`.
> 3. If you set a non-null `rewardFlavor`, rewrite the resolution text (and any conflict turn-in text that carries the reward moment) into two-beat form:
>    - One-beat (no flavor): `"Take this {reward_item}. ..."` (keep from Phase 1)
>    - Two-beat (with flavor): `"Take this {reward_item}. And this: {reward_flavor}. ..."`
>    - Authors own the article / punctuation around the flavor insertion. Never put `{reward_flavor}` mid-clause if it would break grammar. NPCs never mention XP, tier, or gold — the reward item's name shows itself, the flavor is pure emotional closure.
> 4. Never invent item names. If flavor refers to a concrete object that exists in keepsake_items or evidence_items, set `rewardItem` to the matching pool id and reference it via `{reward_item}`. Otherwise leave `rewardItem: null`.
>
> Stay strictly within the listed template ids. Do not touch other templates in the file.
>
> After editing:
> - `jq . <file>` must pass
> - For every template in your batch, `rewardFlavor` is either `null` or has ≤ 5 words (a Phase 4 grep will verify globally)
> - Report which templates you set a non-null `rewardFlavor` on and the ≤5-word value used, plus which you set to `null`.

Commit per batch:
```
data(quest): Phase 2 batch <batch-id> - reward flavor authoring

<batch-description: which situations / count>
```

### Dispatch order

Batches run **sequentially**, not in parallel, because they share `v2/index.json` / `mundane/index.json`. Each batch's subagent commits before the next is dispatched. The batching plan from Task 2.0 determines the order (suggest processing smallest situations first for early confidence).

### Phase 2 acceptance criteria

After all batches:
- Every template has `rewardFlavor` (either null or ≤5-word string).
- Every non-null `rewardFlavor` is ≤ 5 words (Phase 4 enforces globally via the word-count audit in Task 4.1 Step 2).
- No template has a `rewardFlavor` longer than 5 words except intentionally-deferred ones (flagged in the batch's report).
- Every template with a non-null `rewardFlavor` has its resolution text upgraded from one-beat to two-beat form, with `{reward_flavor}` referenced exactly once in a grammatically clean position.
- `./gradlew compileJava` passes.

---

## Phase 3: Batched FETCH_ITEM article audit

**Goal:** Find every `{quest_item}` reference inside a FETCH_ITEM-phase sentence and either prepend an article or switch to `{quest_item_full}`. Out of scope: COLLECT_RESOURCES / TALK_TO_NPC / KILL_MOBS sentences, unless visibly broken.

### Task 3.0: Enumerate FETCH batches

**Files:**
- None. Recon.

**Step 1:** Dispatch a recon subagent to list every template with a `FETCH_ITEM` objective and count `{quest_item}` occurrences in its `expositionText` / `conflictNText` / `conflictNTurnInText` / `resolutionText` / `targetNpcOpener*` / `targetNpcCloser*`. Group into batches of 20-30 templates. Write to `docs/plans/2026-04-14-phase3-batches.md`.

**Step 2:** Commit the batching plan.

### Task 3.1 ... 3.N: Per-batch article audit

**Subagent prompt (paste into dispatch):**

> Audit `{quest_item}` references in FETCH_ITEM-phase sentences for the N templates listed below. Per template, per occurrence:
>
> 1. Read the sentence.
> 2. Decide whether the sentence wants:
>    - **Bare reference** (short, grammar-tight): prepend `"a "` / `"the "` / `"my "` / possessive as grammar demands → `"I lost a {quest_item}"`.
>    - **Flavored reference** (prose, dialogue beat): switch to `{quest_item_full}` AND still prepend an article: `"I lost a {quest_item_full}"`. Remember: epithet is a clause, not an NP, so the article still precedes.
> 3. Never leave a bare `{quest_item}` mid-sentence without an article or possessive (unless it's the direct object after a verb like `"Find {quest_item}"` in an imperative — even then, prefer `"Find the {quest_item}"` for readability).
>
> Constraints:
> - Do NOT touch COLLECT_RESOURCES sentences in these templates.
> - Do NOT touch TALK_TO_NPC, KILL_MOBS text.
> - Do NOT touch sentences that don't reference `{quest_item}`.
> - Authors own article / possessive choice per-sentence. When in doubt, prefer the definite article (`"the"`) for resolution/follow-up text and the indefinite article (`"a"`/`"an"`) for first introduction.
>
> Stay strictly within the listed template ids.
>
> After editing:
> - `jq . <file>` must pass.
> - Report a count of `{quest_item_full}` switches vs. article-only fixes per template.

Commit per batch:
```
data(quest): Phase 3 batch <batch-id> - fetch item article audit

<batch-description>
```

### Dispatch order

Sequential, same-file rationale as Phase 2.

### Phase 3 acceptance criteria

- For every FETCH_ITEM-carrying template, every `{quest_item}` in fetch-phase text sits after an article/possessive or inside a clause where no article is grammatically needed.
- Spot-check: pick 5 random templates across batches and read the fetch exposition aloud. Every sentence should parse without a/an/the missing at a position where grammar demands it.

---

## Phase 4: Final sweep and cleanup

**Files:**
- Delete: `tools/quest_template_migration.py` (one-shot script, no further use)
- Verify: both index.json files
- Possibly modify: both index.json files, if any audit failures surface

### Task 4.1: Final grep + compile + cleanup

**Step 1: Grep for stragglers.**
```bash
grep -rn 'rewardText\|{quest_reward}' src/main/resources/quests/ src/main/java/
```
Expected: zero hits.

**Step 2: Flavor length audit.**

Run a one-liner that extracts every `rewardFlavor` value and flags any with > 5 words. If any found, dispatch a targeted fix subagent with the list.

```bash
python3 -c "
import json, sys
for path in ['src/main/resources/quests/v2/index.json', 'src/main/resources/quests/mundane/index.json']:
    with open(path) as f: data = json.load(f)
    for t in data.get('templates', []):
        f = t.get('rewardFlavor')
        if f and len(f.split()) > 5:
            print(f\"{path}:{t['id']}: {len(f.split())} words: {f}\")
"
```

**Step 3: Compile.**
```bash
./gradlew clean compileJava
```
Expected: BUILD SUCCESSFUL.

**Step 4: Delete the script.**
```bash
git rm tools/quest_template_migration.py
```

**Step 5: Commit.**
```
chore(quest): Phase 4 cleanup - remove migration script, final verify

Template migration complete. Deletes one-shot script. Full compile
passes, zero rewardText / {quest_reward} stragglers, all rewardFlavor
values within ≤5-word cap.
```

---

## Out-of-scope follow-ups

These stay on the follow-up list documented in the original fetch-item naming plan:
1. **Reward dispensing.** Actually move `rewardGold` into the player's coin balance and `rewardItem` into inventory on turn-in. Currently TURN_IN_V2 computes the multiplier but doesn't apply it.
2. **Audit for invented item names** (Design §3).
3. **`collect_resources.json` / `hostile_mobs.json` schema migration.** Not broken; no pressure.
4. **Continue-button fix** (separate branch work).
5. **`TopicPoolRegistry.randomPerspectiveDetail`** — unused since the v2 smalltalk-about-quests retirement (`c78cc9b`). Delete candidate.
6. **`settlement_type` binding** — set by `QuestGenerator`, documented in the palette, but no v2 template references it. Keep if future templates may want it; delete if the palette contracts.
7. **`fetch_item_label` binding** — initially tagged dead by recon, then found to have a live reader at `POIProximitySystem.java:129`. Revisit whether `POIProximitySystem`'s usage is incidental; if it is, delete both ends.

## Failure modes to watch for

- **Gson silently ignores typos.** If a subagent writes `"rewardFIavor"` (capital-I vs lowercase-l) the field is dropped and the template gets a null flavor. Mitigate: each Phase 2/3 batch subagent must verify its edits by re-parsing and checking presence of the field it set.
- **Merge conflicts on shared file.** Sequential dispatch avoids this. If a batch is dispatched while another is mid-run, commits will race. Strictly serialize.
- **`rewardFlavor` > 5 words.** Phase 4 catches these; fix via targeted subagent.
- **Fetch text over-edited.** A batch subagent might touch COLLECT_RESOURCES sentences out of zeal. Each Phase 3 batch's diff should be spot-checked for unrelated edits.
- **Awkward token swaps from Phase 1.** The literal `{quest_reward}` → `Take this {reward_item}.` substitution is mechanical and will produce sentences like "Here, take Take this {reward_item}." where the old token followed a verb. Phase 2 reviewers must fix these in the templates they touch. Phase 4's spot-check (Task 4.1 Step 2 extended) should read a handful of random resolution texts aloud to catch residual awkwardness.
- **`{reward_flavor}` referenced when `rewardFlavor: null`.** Renders as empty string. Safe if the sentence still parses. Phase 2 subagents must only inject `{reward_flavor}` into resolution text when they've set a non-null flavor on the same template.
