# Quest Template Bulk Migration: Multi-Phase Plan

> **For Claude:** Execute phase-by-phase. Phase 1 is script-assisted and lands one commit. Phases 2–3 dispatch multiple subagents in ordered batches, one commit per batch.

**Goal:** Migrate all 259 v2 quest templates to the new reward schema (`rewardGold`/`rewardItem`/`rewardFlavor`) and new template variables (`{reward_gold}`/`{reward_item}`/`{reward_flavor}`/`{quest_item_full}`) without attempting thousands of inline edits in a single subagent pass.

**Why this plan exists:** Task 9 of `2026-04-14-fetch-item-naming-impl.md` was scoped under the assumption of a handful of templates. Actual scope:
- `src/main/resources/quests/v2/index.json`: 238 templates, 238 `rewardText`, 238 `{quest_reward}` tokens, 302 `{quest_item}` tokens, 183 have FETCH_ITEM objectives.
- `src/main/resources/quests/mundane/index.json`: 21 templates, 21 `rewardText`, 21 `{quest_reward}`, 30 `{quest_item}`, all use PEACEFUL_FETCH (not FETCH_ITEM).

About 1000+ authoring edits. Not safe to do in one pass. This plan decomposes into phases where each phase has a clean acceptance test and commits independently.

**Architecture:**
- **Phase 1** is a mechanical script-assisted pass that makes the build functionally correct with placeholder authoring quality. Runtime after Phase 1: quests give gold, no crashes, no literal `{quest_reward}` tokens leak to players. Old `rewardText` stays in the JSON but Gson ignores it (already verified).
- **Phase 2** is a sequence of small authoring batches (~25 templates each) handled by dedicated subagents. Each batch reviews Phase 1's placeholder output, authors proper `rewardFlavor` values, polishes resolution text, and removes the now-dead `rewardText`. Ordered sequentially (shared file, no race).
- **Phase 3** is the same pattern for the `{quest_item}` article audit, scoped to FETCH_ITEM-carrying templates only.
- **Phase 4** is a final grep + compile sweep.

**Tech Stack:** Python (stdlib only) for the Phase 1 script. Java 25 + Gson for compile verification. No unit tests exist.

**Files touched across all phases:**
- `src/main/resources/quests/v2/index.json`
- `src/main/resources/quests/mundane/index.json`
- Temporary: a one-off script at `tools/quest_template_migration.py` (deleted in Phase 4)

---

## Phase 1: Script-assisted baseline migration

**Goal:** Every template carries the new reward fields; every `{quest_reward}` in text fields becomes a minimum-viable one-beat gold reference. Old `rewardText` stays in place as authoritative source for Phase 2.

### Task 1.1: Write the migration script

**Files:**
- Create: `tools/quest_template_migration.py`

**Behavior:**

Read a JSON file on stdin or by path. For each object that looks like a template (has `rewardText` field):

1. Count `objectives.length`. Compute `rewardGold`:
   - length ≤ 2 → 25
   - length == 3 → 50
   - length ≥ 4 → 75
2. Add/overwrite three fields:
   - `rewardGold`: integer from rule above
   - `rewardItem`: `null`
   - `rewardFlavor`: `null`
3. Leave `rewardText` field in place (don't delete).
4. For every string field in the template (recursively if nested, but the schema is flat: iterate known text fields `expositionText`, `acceptText`, `declineText`, `expositionTurnInText`, `conflict1Text`, `conflict1TurnInText`, `conflict2Text`, `conflict2TurnInText`, `conflict3Text`, `conflict3TurnInText`, `conflict4Text`, `conflict4TurnInText`, `resolutionText`, `targetNpcOpener`, `targetNpcCloser`, `targetNpcOpener2`, `targetNpcCloser2`, `skillCheck.passText`, `skillCheck.failText`), rewrite:
   - `{quest_reward}` → `{reward_gold} gold`
   - DO NOT touch `{quest_item}` in this phase.
5. Preserve JSON formatting: 2-space indent, ASCII-escaped strings OFF (keep UTF-8), key order preserved. Use `json.dump(obj, fp, indent=2, ensure_ascii=False)` with `sort_keys=False`.

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
grep -c '"rewardGold":' src/main/resources/quests/v2/index.json  # should be 238
grep -c '"rewardGold":' src/main/resources/quests/mundane/index.json  # should be 21
```

**Step 5: Compile.**
```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

**Step 6: Commit.** Stage the script + both JSON files.

Commit message:
```
data(quest): Phase 1 - script-assisted baseline reward migration

Adds rewardGold/rewardItem/rewardFlavor to all 259 v2+mundane templates
(gold scaled by objective count, item/flavor nullable). Rewrites every
{quest_reward} token in template text fields to a minimum-viable
'{reward_gold} gold' form.

Old rewardText field is left intact as authoritative source for Phase 2
flavor authoring. Gson ignores unknown fields, so runtime is correct.
```

No Co-Authored-By.

---

## Phase 2: Batched reward flavor authoring

**Goal:** Replace Phase 1's `rewardFlavor: null` placeholders with real ≤5-word emotional notes where warranted, polish resolution text from the one-beat form into two-beat where flavor is added, and delete the legacy `rewardText` field once migrated.

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

> Author `rewardFlavor` and polish resolution text for the N templates listed below. For each template:
>
> 1. Read the legacy `rewardText` field — this is your source material.
> 2. Decide: is there an emotional/flavor element worth preserving, or is the whole string transactional?
>    - Transactional only → `rewardFlavor: null`, keep the one-beat form in resolutionText (no further edits).
>    - Has flavor → condense the emotional piece into ≤ 5 words and set as `rewardFlavor`.
> 3. If you set a non-null `rewardFlavor`, rewrite the resolution text (and any conflict turn-in text that carries the reward moment) into two-beat form:
>    - One-beat (no flavor): `"Take this: {reward_gold} gold. ..."` (keep)
>    - Two-beat (with flavor): `"Take this: {reward_gold} gold. And this. {reward_flavor}. ..."`
>    - Authors own the article / punctuation around the flavor insertion. Never put `{reward_flavor}` mid-clause if it would break grammar.
> 4. Delete the `rewardText` field once you've authored `rewardFlavor`.
> 5. Never invent item names. If flavor refers to a concrete object that exists in keepsake_items or evidence_items, set `rewardItem` to the matching pool id and reference it via `{reward_item}`. Otherwise leave `rewardItem: null`.
>
> Stay strictly within the listed template ids. Do not touch other templates in the file.
>
> After editing:
> - `jq . <file>` must pass
> - `grep -c "rewardText" <file>` must be exactly `(original_count - N)` (where N is your batch size — confirm you deleted exactly your batch's rewardText fields)
> - Report which templates you set a non-null `rewardFlavor` on and the ≤5-word value used.

Commit per batch:
```
data(quest): Phase 2 batch <batch-id> - reward flavor authoring

<batch-description: which situations / count>
```

### Dispatch order

Batches run **sequentially**, not in parallel, because they share `v2/index.json` / `mundane/index.json`. Each batch's subagent commits before the next is dispatched. The batching plan from Task 2.0 determines the order (suggest processing smallest situations first for early confidence).

### Phase 2 acceptance criteria

After all batches:
- `grep -c '"rewardText"' src/main/resources/quests/v2/index.json src/main/resources/quests/mundane/index.json` returns 0.
- Every template has `rewardGold` + `rewardItem` + `rewardFlavor`.
- Every non-null `rewardFlavor` is ≤ 5 words (a Phase 4 grep will enforce).
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
3. **`fetch_item_label` dead binding.** Confirmed unused after Task 5 recon. Safe to delete in a follow-up cleanup.
4. **`collect_resources.json` / `hostile_mobs.json` schema migration.** Not broken; no pressure.
5. **Continue-button fix** (separate branch work).

## Failure modes to watch for

- **Gson silently ignores typos.** If a subagent writes `"rewardGoId"` instead of `"rewardGold"` (capital-I vs lowercase-l), Gson skips it and the template gets 0 gold. Mitigate: each Phase 2/3 batch subagent must verify its edits by re-parsing and checking presence of the field it set.
- **Merge conflicts on shared file.** Sequential dispatch avoids this. If a batch is dispatched while another is mid-run, commits will race. Strictly serialize.
- **`rewardFlavor` > 5 words.** Phase 4 catches these; fix via targeted subagent.
- **Fetch text over-edited.** A batch subagent might touch COLLECT_RESOURCES sentences out of zeal. Each Phase 3 batch's diff should be spot-checked for unrelated edits.
- **`{reward_item}` referenced when `rewardItem: null`.** Renders as empty string. Safe, but readers will see weird "Take this: 25 gold, and the ." Fix: only write `{reward_item}` into text fields when `rewardItem` is non-null on that template.
