# Fetch Item Naming & Reward Flavor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

## Status (updated 2026-04-14)

Tasks 1–8 landed on branch `fix/fetch-naming-and-continue-buttons`:

| Task | Commit | Notes |
|---|---|---|
| 1. Extend ItemEntry record | `c917609` | amended to also host `fullForm()` as instance method per code review |
| 2. Migrate keepsake_items | `ad0a598` | amended to fit the ≤4-word epithet cap |
| 3. Migrate evidence_items | `7a47ff4` | |
| 4. ObjectiveInstance.targetEpithet | `bf1a123` | |
| 5. QuestGenerator wiring | (folded into `c917609` amend) | |
| 6. DialogueResolver overlay | `4c16420` | |
| 7+8. Reward schema split | `ff069be` | `QuestTemplateRegistry` needed no edits — Gson reflection handled the rename |

Task 9 (v2 template JSON migration) was superseded by the multi-phase bulk plan at `docs/plans/2026-04-14-quest-template-bulk-migration-plan.md` once the real scope (259 templates, 1000+ edits) became clear.

Task 10 (full compile + handoff) is pending: blocks on the bulk migration finishing, since templates still render `{quest_reward}` literally and reward 0 gold until Phase 1 of the bulk plan runs.

## Cleanup work completed in parallel

While the reward schema work was in flight, a broader cleanup ran on this same branch to retire v1 residue discovered during review:

| Commit | What |
|---|---|
| `1464711` | Deleted dead `rewardText` field from all 259 v2+mundane templates (Gson was silently dropping it after task 7+8) |
| `3978970` | Deleted `src/main/resources/quests/pools/v1_archived/` (34 files, 4.5 MB) |
| `7ba56c8` | Removed v1 `PhaseType` / `PhaseInstance` classes, `QuestRewardManager` (whole class, all methods dead), `getVariant` method on `QuestTemplateRegistry`, stale imports |
| `36bc5be` | Removed 22 unused `random*` methods from `QuestPoolRegistry` |
| `c78cc9b` | Retired v2 smalltalk-about-quests residue (TopicGenerator quest-binding block, TopicGraphBuilder quest-bearer else branch, `SubjectFocus` quest accessors, 23 dead pool fields, 15 load/parse helpers, 4 dead pool `random*` methods, `NarrativeEntry` record, 2 Category C bindings in `QuestGenerator`) |

Branch delta so far: ~500 net lines removed, build green throughout.

## Known follow-ups surfaced

- `TopicPoolRegistry.randomPerspectiveDetail` is now unused (side effect of retiring the quest-binding block in `c78cc9b`).
- `settlement_type` binding is still set by `QuestGenerator` and documented as a public palette variable, but no current v2 template references it. Kept for now; delete candidate if the palette contracts.
- Reward dispensing is still a stub. `TURN_IN_V2` computes the multiplier but doesn't move XP/items to the player. Separate follow-up.
- `fetch_item_label` binding in `QuestGenerator` was initially tagged dead by recon, then found to have a live reader at `POIProximitySystem.java:129`. Not deleted. If `POIProximitySystem` usage turns out to be incidental, revisit.
- Full dead-code backlog logged at `docs/tech-debt/2026-04-14-quest-system-bloat-audit.md`.

---

## Addendum: Difficulty-driven reward system (Tasks D1–D5)

After Tasks 1–8 landed, scope expanded: the reward is now an **affix item of runtime-rolled rarity + XP**, controlled by a new `QuestDifficulty` config. Templates no longer author `rewardGold` / `rewardItem` / `rewardXP` — only `rewardFlavor` (silent emotional beat, ≤5 words, never mentions the reward).

### Schema (final)

`QuestTemplateV2` reward-related fields:
```java
@Nullable String rewardFlavor,     // ≤5 words, unchanged
// No difficulty, no reward values. Difficulty is runtime-assigned.
```

`QuestInstance` gains new fields:
- `difficultyId` (String) — which difficulty was rolled for this quest
- `rewardItem` (ItemStack) — pre-rolled affix item, dispensed at turn-in
- `rewardXp` (int) — XP to award at turn-in
- Plus the rolled item's display name written into `variableBindings` as `reward_item`

### DifficultyConfig shape

```json
// src/main/resources/quests/difficulty/<id>.json
{
  "id": "medium",
  "xpAmount": 100,
  "rewardTierMin": "Uncommon",
  "rewardTierMax": "Rare",
  "rewardIlvl": 15,
  "mobIlvl": 15,
  "mobBoss": true,
  "bossIlvlOffset": 3,
  "mobCountMultiplier": 1.0,
  "gatherCountMultiplier": 1.0
}
```

Three levels for MVP:

| id | xp | tier range | reward iLvl | mob iLvl | boss | boss offset | mob × | gather × |
|---|---|---|---|---|---|---|---|---|
| easy | 50 | Common → Uncommon | 5 | 5 | true | 3 | 0.8 | 0.8 |
| medium | 100 | Uncommon → Rare | 15 | 15 | true | 3 | 1.0 | 1.0 |
| hard | 200 | Rare → Epic | 25 | 25 | true | 3 | 1.3 | 1.3 |

Tier range is inclusive on both ends. Boss is forced-on at every level for MVP so the spawn path is exercised during testing.

### Runtime flow

At `QuestGenerator.generate()`:
1. Pick a difficulty uniformly at random from the registry (MVP policy — pluggable).
2. Store `difficultyId` on the `QuestInstance`.
3. Apply `mobCountMultiplier` when rolling KILL_MOBS counts, `gatherCountMultiplier` when rolling COLLECT_RESOURCES counts.
4. Extend `populationSpec` on KILL_MOBS objectives with `mobIlvl`, `mobBoss`, `bossIlvlOffset`.
5. Roll reward tier uniformly in `[rewardTierMin, rewardTierMax]`. Call `AffixRewardRoller.roll(tier, rewardIlvl)` — real integration with the Nat20 loot/affix system, no placeholder. Store `ItemStack` on `QuestInstance.rewardItem`.
6. Write `variableBindings.put("reward_item", rolledItem.getDisplayName())`.
7. Store `difficulty.xpAmount` on `QuestInstance.rewardXp`.

At `TURN_IN_V2` final:
- Dispense `quest.rewardItem` to inventory via the real loot/inventory API. On failure (inventory full, system error), log `SEVERE` and leave the item undelivered — no silent drop.
- Award `quest.rewardXp` via the real XP system. If the XP system doesn't yet exist, omit the XP dispense call entirely — do not ship a log-only stub that pretends XP was granted.

**No hardcoded fallbacks. No placeholder returns. No log-only dispense stubs.** If any dependency is missing (difficulty config, affix system entry point, XP system), the code path fails loudly at the boundary rather than continuing with fake data. Missing dependencies block the task; they do not get a stub that silently degrades behavior.

---

## Task D1: DifficultyConfig data model and registry

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/model/DifficultyConfig.java`
- Create: `src/main/java/com/chonbosmods/quest/QuestDifficultyRegistry.java`
- Create: `src/main/resources/quests/difficulty/easy.json`
- Create: `src/main/resources/quests/difficulty/medium.json`
- Create: `src/main/resources/quests/difficulty/hard.json`
- Modify: `src/main/java/com/chonbosmods/quest/QuestSystem.java` (register new registry)

**Step 1: `DifficultyConfig` record**

```java
package com.chonbosmods.quest.model;

/**
 * Runtime-assigned quest difficulty tier. Drives reward tier range, XP amount,
 * mob iLvl / boss behavior, and objective count multipliers. Loaded from
 * {@code src/main/resources/quests/difficulty/*.json} by {@link
 * com.chonbosmods.quest.QuestDifficultyRegistry}.
 *
 * <p>Templates never reference a difficulty directly. {@link
 * com.chonbosmods.quest.QuestGenerator} picks one (randomly for MVP) at
 * generation time and applies its values throughout.
 */
public record DifficultyConfig(
    String id,
    int xpAmount,
    String rewardTierMin,
    String rewardTierMax,
    int rewardIlvl,
    int mobIlvl,
    boolean mobBoss,
    int bossIlvlOffset,
    double mobCountMultiplier,
    double gatherCountMultiplier
) {}
```

Tier fields are `String` (Hytale vanilla quality id: `"Common"`, `"Uncommon"`, etc.) rather than an enum, matching the `item-quality-rendering` memory's guidance to use vanilla IDs verbatim.

**Step 2: `QuestDifficultyRegistry`**

Loads all `.json` files under `quests/difficulty/` from the classpath, parses each with Gson into `DifficultyConfig`, stores by id. Fails loud on any file that doesn't parse or has a missing id. Provides:

- `DifficultyConfig get(String id)` — returns null if missing
- `DifficultyConfig random(Random random)` — uniformly picks one; throws `IllegalStateException` if registry is empty (no silent fallback)
- `Collection<String> ids()` — for logging

Follow the same pattern as `QuestPoolRegistry.loadItemPoolFromClasspath` for classpath enumeration. If classpath enumeration of a directory is awkward, hardcode the list of expected ids (`"easy", "medium", "hard"`) and load each by name — acceptable for MVP since we don't expect user-contributed difficulty configs yet.

**Step 3: JSON definitions**

Write the three JSON files exactly matching the MVP values table above. All three get `"mobBoss": true`.

**Step 4: Register in `QuestSystem`**

Add a `QuestDifficultyRegistry` field, instantiate + load it in the constructor, pass it to `QuestGenerator`. Expose a getter if anything outside the system needs it.

**Step 5: Compile + commit.**

```
feat(quest): add QuestDifficulty config system

Introduces three difficulty levels (easy / medium / hard) loaded from
quests/difficulty/*.json. Each carries xp amount, reward tier range,
reward iLvl, mob iLvl, mob-boss flag, boss iLvl offset, and objective
count multipliers.

Registry is wired into QuestSystem. Next task integrates it into
QuestGenerator.
```

---

## Task D2: Integrate difficulty into QuestGenerator

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java`
- Modify: `src/main/java/com/chonbosmods/quest/QuestInstance.java` (new `difficultyId` field)

**Step 1: Add `difficultyId` field on `QuestInstance`**

Plain String field with getter/setter, matching the existing `situationId` / `sourceNpcId` field patterns. Serializable.

**Step 2: Pick difficulty at start of `QuestGenerator.generate()`**

Immediately after the template is selected, before objective creation:

```java
DifficultyConfig difficulty = difficultyRegistry.random(random);
bindings.put("difficulty_id", difficulty.id());  // for logging, not template use
```

Pass `difficulty` into `createObjective` so the objective builder can apply multipliers.

**Step 3: Apply multipliers in objective count rolling**

In `createObjective`:
- `COLLECT_RESOURCES`: after rolling `count` from the pool or config, apply `(int) Math.round(count * difficulty.gatherCountMultiplier())`. Clamp to a minimum of 1.
- `KILL_MOBS`: same treatment. `rollKillCount` already exists; multiplier wraps its return.

**Step 4: Store `difficultyId` on the created `QuestInstance`**

```java
quest.setDifficultyId(difficulty.id());
```

**Step 5: Compile + commit.**

```
feat(quest): wire QuestDifficulty into generation

Every generated quest now rolls a difficulty at creation and stores
the id on QuestInstance. Mob count / gather count multipliers apply
during objective creation. Downstream (mob iLvl, reward, XP) in next
tasks.
```

---

## Task D3: Extend populationSpec with mob iLvl and boss flag

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (spec format)
- Modify: `src/main/java/com/chonbosmods/quest/ObjectiveInstance.java` if the spec gets parsed into structured fields
- Modify: whatever consumes `populationSpec` downstream (likely the cave-void population step — grep to find)

**Step 1: Decide on spec format**

Current: `"KILL_MOBS:<enemyId>:<count>"` (a flat colon-delimited string).

Extended: `"KILL_MOBS:<enemyId>:<count>:<ilvl>:<boss>:<bossIlvlOffset>"` where `<boss>` is `"true"` / `"false"`.

Keep it a string for storage simplicity (same record as before); parse on the consumer side.

**Step 2: Write the extended spec in `createPOIObjective`**

Pull `difficulty.mobIlvl()`, `difficulty.mobBoss()`, `difficulty.bossIlvlOffset()` from the difficulty argument and interpolate them into the spec.

**Step 3: Update downstream consumer**

Grep `populationSpec` usages. If the consumer parses by `split(":")` and indexes by position, extend the parser to read the new 3 fields. If the consumer currently only reads the first three tokens and ignores the rest, the extension is additive and requires no change — but the spawn behavior doesn't use iLvl / boss yet either. In that case:

- File a note in the commit message that the spec carries the data but spawn doesn't yet read it.
- Downstream wiring becomes a separate task / follow-up.

**Step 4: Compile + commit.**

```
feat(quest): extend KILL_MOBS populationSpec with iLvl and boss fields

populationSpec now encodes the difficulty's mob iLvl, mob-boss flag,
and boss iLvl offset. <Include note here about whether the downstream
spawn path reads them yet or if that's deferred.>
```

---

## Task D4: AffixRewardRoller (real integration, no stub)

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/AffixRewardRoller.java`

**Principle:** no fallbacks, no placeholder return values. If the affix / loot system isn't ready to roll a real item at a given tier + ilvl, `roll(...)` throws `UnsupportedOperationException` and quest generation fails loudly. This forces the integration to happen before the system ships — it does not let silent placeholder data reach players.

**Step 1: Investigate the existing Nat20 loot/affix API**

Before writing the class, read:
- `src/main/java/com/chonbosmods/loot/Nat20LootSystem.java`
- `src/main/java/com/chonbosmods/loot/registry/Nat20AffixRegistry.java`
- any `ItemStack` construction you find in there

Identify the public entry point that produces an affix-rolled `ItemStack` for a given tier + ilvl (or the closest equivalent). If one exists, `AffixRewardRoller.roll(...)` is a thin adapter that calls into it. If one doesn't exist, this task stops and files a dependency: the loot system needs a `rollForQuestReward(tier, ilvl, random)` entry point before D4 can proceed.

**Step 2: Write the helper**

```java
package com.chonbosmods.quest;

import com.hytale.sdk.item.ItemStack;  // verify actual import from loot system files
import java.util.Random;

/**
 * Rolls a reward item at a given rarity tier and item level by delegating to
 * the Nat20 loot / affix system. Intentionally has no fallback path: if the
 * underlying loot system cannot produce a valid stack, this method throws and
 * quest generation fails loudly. No placeholder, no silent zero-value data.
 */
public final class AffixRewardRoller {

    private AffixRewardRoller() {}

    /**
     * @param tier vanilla Hytale quality id: "Common" | "Uncommon" | "Rare" | "Epic" | "Legendary"
     * @param ilvl item level
     * @return the rolled reward stack
     * @throws IllegalArgumentException if tier or ilvl are out of range
     * @throws IllegalStateException    if the loot system cannot produce a valid stack
     */
    public static ItemStack roll(String tier, int ilvl, Random random) {
        // Delegate to the real Nat20 loot/affix entry point found in Step 1.
        // If the delegation fails or returns null, throw IllegalStateException
        // with a message pointing at the tier/ilvl combo that failed.
    }
}
```

If Step 1 determines the loot system doesn't yet expose a suitable entry point, **do not proceed with a throwing stub**. Instead, halt D4 and surface the blocker — the correct next move is either to implement the entry point in the loot system first, or to scope the reward system to whatever the loot system can actually do today.

**Step 3: Compile + commit (only if Step 2 produced a real implementation)**

```
feat(quest): add AffixRewardRoller backed by Nat20 loot/affix system

Rolls a reward ItemStack at a given tier and ilvl by delegating to the
existing Nat20 loot/affix system. No fallback: if the delegation
can't produce a valid stack, the roller throws and quest generation
fails loudly.
```

If Step 1 blocked on missing loot-system API, commit nothing for D4. Report the blocker and adjust the plan.

---

## Task D5: Wire reward + XP storage and TURN_IN_V2 stubs

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestInstance.java` (new `rewardItem`, `rewardXp` fields)
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (roll reward, write binding)
- Modify: `src/main/java/com/chonbosmods/quest/DialogueResolver.java` (highlight `reward_item`)
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` (TURN_IN_V2 dispense stubs)

**Step 1: Add fields to `QuestInstance`**

- `ItemStack rewardItem` — the rolled reward
- `int rewardXp` — XP amount from difficulty

Plus getters/setters, matching the existing field pattern. Serialization: if `QuestInstance` uses Gson for persistence, the `ItemStack` field needs a serializer — otherwise fall back to storing the item's qualityId + display name + (later) affix roll seed so it can be reconstructed at dispense time. For MVP stub, storing display name is enough to render dialogue; the actual item dispense is stubbed.

**Step 2: Roll reward in `QuestGenerator.generate()`**

After the difficulty roll (Task D2) and before template text bindings:

```java
String rewardTier = rollTierInRange(difficulty.rewardTierMin(),
                                     difficulty.rewardTierMax(), random);
ItemStack rewardItem = AffixRewardRoller.roll(rewardTier, difficulty.rewardIlvl(), random);
quest.setRewardItem(rewardItem);
quest.setRewardXp(difficulty.xpAmount());
bindings.put("reward_item", rewardItem.getDisplayName());
```

`rollTierInRange` is a small helper that picks uniformly between the two tier names inclusive. Implement inline or as a private static.

**Step 3: Highlight `reward_item`**

In `DialogueResolver.HIGHLIGHTED_QUEST_VARS`, add `"reward_item"`.

**Step 4: TURN_IN_V2 dispense (real, not stub)**

Inside the existing `TURN_IN_V2` handler, at the final-turn-in branch (`!quest.hasMoreConflicts()`), dispense the reward and XP for real. No stub.

- **Item dispense:** call the existing Nat20 inventory / reward API to put `quest.getRewardItem()` into the player's inventory. If the player's inventory is full and the item can't be placed, the fallback behavior must be a **hard noisy failure**: log `SEVERE` with quest id, item name, player uuid, and leave the item undelivered. Do NOT silently drop the item. (If the project already has an overflow pattern — drop to feet, mail, etc. — use that; otherwise log SEVERE and fail the turn-in cleanly.)
- **XP award:** if no XP system exists yet, this is a dependency blocker. Halt D5's XP portion and surface it just like D4's potential blocker. Do NOT add a "would grant N XP" log-only stub — that's a silent fallback. Either wire real XP or defer D5's XP work entirely.

If the XP system genuinely doesn't exist yet, D5 lands as:
- Real item dispense via loot/inventory API
- XP storage on `QuestInstance.rewardXp` (field is ready for when XP system ships)
- NO XP dispense call — that line is omitted until XP is real

Leave the existing multiplier calc / `claimReward` call in place.

**Step 5: Compile + commit.**

```
feat(quest): wire reward item binding + XP storage and TURN_IN_V2 stubs

Every generated quest rolls a reward at difficulty-derived tier and
ilvl; display name goes into {reward_item} binding for dialogue.
QuestInstance carries the rolled ItemStack and XP amount.

TURN_IN_V2 logs would-dispense lines for both item and XP. Real
dispense lands when the affix and XP systems are ready.
```

**Goal:** Split fetch-item pool labels into bare noun + short epithet so objective UI, waypoints, and inventory stacks read clean, and split the reward text into structured slots so flavor never masquerades as an item.

**Architecture:** Two pool files (`keepsake_items.json`, `evidence_items.json`) gain a richer schema: `noun` + `nounPlural` + optional `epithet`. `ItemEntry` keeps back-compat `label` fields so unrelated pools (collect_resources, hostile_mobs) don't need to change this pass. Two new template variables resolve from one pool roll: `{quest_item}` (bare noun) and `{quest_item_full}` (noun + epithet). `QuestTemplateV2` replaces its single `rewardText` string with `rewardGold` (int), `rewardItem` (nullable String), and `rewardFlavor` (nullable String); templates expose these as `{reward_gold}`, `{reward_item}`, `{reward_flavor}` and compose dialogue beats around them.

**Tech Stack:** Java 25, Gson, Hytale SDK. No unit tests exist in this project: verification is `./gradlew compileJava` plus runtime smoke test (documented as a deferred manual step because `./gradlew devServer` does not run from a worktree).

**Scope (in):** `keepsake_items.json`, `evidence_items.json`, `QuestPoolRegistry.ItemEntry` record + parser, `QuestGenerator` keepsake/evidence paths, `ObjectiveInstance` epithet field, `DialogueResolver.overlayObjective`, `QuestTemplateV2` reward fields, `QuestGenerator` reward bindings, all v2 `index.json` templates' `resolutionText` and reward declarations.

**Scope (out):** `collect_resources.json`, `hostile_mobs.json` (labels already clean); reward dispensing (already a stub, not being completed here); audit for invented item names (deferred to follow-up per design doc); continue-button fix (independent branch work).

**Reference:** `docs/plans/2026-04-14-fetch-item-naming-design.md` is the approved design. Read it before starting.

---

## Task 1: Extend `ItemEntry` record with noun/nounPlural/epithet

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java:31-36` (record declaration) and `:250-263` (parser)

**Step 1: Update the record**

Replace the `ItemEntry` record with:

```java
/** Entry with id + label + optional plural label for items/mobs.
 *  New fields {@code noun}, {@code nounPlural}, {@code epithet} support the
 *  fetch-item authoring model where articles and narrative clauses are removed
 *  from pool data. For pools that don't author these (mobs, collect_resources),
 *  {@code noun}/{@code nounPlural} default to {@code label}/{@code labelPlural}
 *  with leading articles stripped. */
public record ItemEntry(String id, String label, String labelPlural,
                         String noun, String nounPlural, @Nullable String epithet,
                         String category, int countMin, int countMax,
                         @Nullable String fetchItemType) {
    public ItemEntry(String id, String label, String labelPlural) {
        this(id, label, labelPlural, stripArticle(label), stripArticle(labelPlural), null,
             null, 0, 0, null);
    }

    /** Strip a leading "a ", "an ", or "the " from a label to produce a bare noun. */
    public static String stripArticle(String s) {
        if (s == null) return null;
        String lower = s.toLowerCase();
        if (lower.startsWith("a ")) return s.substring(2);
        if (lower.startsWith("an ")) return s.substring(3);
        if (lower.startsWith("the ")) return s.substring(4);
        return s;
    }
}
```

**Step 2: Update `parseItemEntries`**

Replace lines 250-263 with:

```java
private void parseItemEntries(JsonObject root, String arrayKey, List<ItemEntry> target) {
    JsonArray arr = root.getAsJsonArray(arrayKey);
    for (JsonElement el : arr) {
        JsonObject obj = el.getAsJsonObject();
        String id = obj.get("id").getAsString();
        String label = obj.has("label") ? obj.get("label").getAsString() : null;
        String labelPlural = obj.has("labelPlural") ? obj.get("labelPlural").getAsString() : label;

        // New schema: noun/nounPlural/epithet. Fall back to stripped label for
        // pools that haven't been migrated yet.
        String noun = obj.has("noun") ? obj.get("noun").getAsString() : ItemEntry.stripArticle(label);
        String nounPlural = obj.has("nounPlural") ? obj.get("nounPlural").getAsString()
                           : ItemEntry.stripArticle(labelPlural);
        String epithet = obj.has("epithet") && !obj.get("epithet").isJsonNull()
                         ? obj.get("epithet").getAsString() : null;

        // Legacy `label` defaults to the noun if unspecified (so code that still
        // reads .label() sees at least a bare word).
        if (label == null) label = noun;
        if (labelPlural == null) labelPlural = nounPlural;

        String category = obj.has("category") ? obj.get("category").getAsString() : null;
        int countMin = obj.has("countMin") ? obj.get("countMin").getAsInt() : 0;
        int countMax = obj.has("countMax") ? obj.get("countMax").getAsInt() : 0;
        String fetchItemType = obj.has("fetchItemType") ? obj.get("fetchItemType").getAsString() : null;
        target.add(new ItemEntry(id, label, labelPlural, noun, nounPlural, epithet,
                                 category, countMin, countMax, fetchItemType));
    }
}
```

**Step 3: Update the two hardcoded-fallback `ItemEntry` literals at lines 336, 341, 346, 351**

Those use the 3-arg convenience constructor: the new constructor handles them. No change needed, but verify by rereading.

**Step 4: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL. All existing callers of `entry.label()` / `entry.labelPlural()` still work; new `.noun()` / `.nounPlural()` / `.epithet()` accessors are now available.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java
git commit -m "feat(quest-pool): add noun/nounPlural/epithet fields to ItemEntry

Pools that author the new fields expose a bare noun form for objective
UI and inventory, plus an optional short epithet for dialogue flavor.
Legacy label fields remain for pools not yet migrated (mobs, resources),
with a stripArticle fallback so noun is populated even without authoring."
```

---

## Task 2: Migrate `keepsake_items.json` to new schema

**Files:**
- Modify: `src/main/resources/quests/pools/keepsake_items.json`

**Step 1: Rewrite all 12 entries**

Replace the file contents with:

```json
{
  "values": [
    {"id": "keepsake_journal",   "noun": "leather journal",   "nounPlural": "leather journals",   "epithet": "worn smooth by years",          "fetchItemType": "quest_book"},
    {"id": "keepsake_doll",      "noun": "stuffed toy",       "nounPlural": "stuffed toys",       "epithet": "slept with every night",        "fetchItemType": "quest_keepsake"},
    {"id": "keepsake_ring",      "noun": "ring",              "nounPlural": "rings",              "epithet": "never off their hand",          "fetchItemType": "quest_treasure"},
    {"id": "keepsake_plush",     "noun": "hand-stitched plushie", "nounPlural": "hand-stitched plushies", "epithet": "carried everywhere",    "fetchItemType": "quest_keepsake"},
    {"id": "keepsake_pendant",   "noun": "carved pendant",    "nounPlural": "carved pendants",    "epithet": "on a leather cord",             "fetchItemType": "quest_treasure"},
    {"id": "keepsake_toy",       "noun": "child's toy",       "nounPlural": "old toys",           "epithet": "kept since childhood",          "fetchItemType": "quest_keepsake"},
    {"id": "keepsake_charm",     "noun": "lucky charm",       "nounPlural": "lucky charms",       "epithet": "always kept close",             "fetchItemType": "quest_keepsake"},
    {"id": "keepsake_portrait",  "noun": "small portrait",    "nounPlural": "small portraits",    "epithet": "wrapped in cloth",              "fetchItemType": "quest_document"},
    {"id": "keepsake_compass",   "noun": "battered compass",  "nounPlural": "battered compasses", "epithet": "that still points true",        "fetchItemType": "quest_treasure"},
    {"id": "keepsake_bones",     "noun": "remains",           "nounPlural": "remains",            "epithet": "of someone they lost",          "fetchItemType": "quest_remains"},
    {"id": "keepsake_locket",    "noun": "tin locket",        "nounPlural": "tin lockets",        "epithet": "with a lock of hair inside",    "fetchItemType": "quest_treasure"},
    {"id": "keepsake_tonic",     "noun": "tonic",             "nounPlural": "tonics",             "epithet": "brewed every morning",          "fetchItemType": "quest_vial"}
  ]
}
```

Rules applied:
- No leading article in `noun` or `nounPlural`.
- `epithet` is ≤ 4 words, no leading "that"/"which" unless grammar requires.
- `keepsake_bones` nounPlural intentionally matches noun (uncountable).
- `label` / `labelPlural` are omitted: parser defaults them from `noun` / `nounPlural`.

**Step 2: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL (JSON is data, not code, but compile catches nothing here: step is included for the habit).

**Step 3: Commit**

```bash
git add src/main/resources/quests/pools/keepsake_items.json
git commit -m "data(quest-pool): migrate keepsake_items to noun/epithet schema

Entries now carry a bare noun (no article, no clause) plus an optional
≤4-word epithet. Articles are composed per-sentence by the template,
not baked into pool data."
```

---

## Task 3: Migrate `evidence_items.json` to new schema

**Files:**
- Modify: `src/main/resources/quests/pools/evidence_items.json`

**Step 1: Rewrite all 15 entries**

Replace the file contents with:

```json
{
  "values": [
    {"id": "evidence_ledger",        "noun": "signed ledger",          "nounPlural": "signed ledgers",          "epithet": null,                        "fetchItemType": "quest_book"},
    {"id": "evidence_letter",        "noun": "sealed letter",          "nounPlural": "sealed letters",          "epithet": null,                        "fetchItemType": "quest_letter"},
    {"id": "evidence_manifest",      "noun": "trade manifest",         "nounPlural": "trade manifests",         "epithet": "the original",              "fetchItemType": "quest_scroll"},
    {"id": "evidence_witness",       "noun": "witness statement",      "nounPlural": "witness statements",      "epithet": "in their own hand",         "fetchItemType": "quest_document"},
    {"id": "evidence_contract",      "noun": "forged contract",        "nounPlural": "forged contracts",        "epithet": null,                        "fetchItemType": "quest_scroll"},
    {"id": "evidence_journal",       "noun": "personal journal",       "nounPlural": "personal journals",       "epithet": null,                        "fetchItemType": "quest_book"},
    {"id": "evidence_map",           "noun": "annotated map",          "nounPlural": "annotated maps",          "epithet": null,                        "fetchItemType": "quest_letter"},
    {"id": "evidence_receipt",       "noun": "stack of receipts",      "nounPlural": "stacks of receipts",      "epithet": null,                        "fetchItemType": "quest_document"},
    {"id": "evidence_orders",        "noun": "sealed orders",          "nounPlural": "sealed orders",           "epithet": null,                        "fetchItemType": "quest_scroll"},
    {"id": "evidence_token",         "noun": "marked token",           "nounPlural": "marked tokens",           "epithet": null,                        "fetchItemType": "quest_treasure"},
    {"id": "evidence_correspondence","noun": "encoded correspondence", "nounPlural": "encoded correspondence",  "epithet": null,                        "fetchItemType": "quest_scroll"},
    {"id": "evidence_deed",          "noun": "deed",                   "nounPlural": "deeds",                   "epithet": "the original",              "fetchItemType": "quest_scroll"},
    {"id": "evidence_blueprint",     "noun": "stolen plans",           "nounPlural": "stolen plans",            "epithet": null,                        "fetchItemType": "quest_letter"},
    {"id": "evidence_signet",        "noun": "signet ring",            "nounPlural": "signet rings",            "epithet": null,                        "fetchItemType": "quest_treasure"},
    {"id": "evidence_logbook",       "noun": "guard's logbook",        "nounPlural": "guard logbooks",          "epithet": null,                        "fetchItemType": "quest_book"}
  ]
}
```

Notes:
- Evidence items are more clinical than keepsakes, so most `epithet`s are `null`.
- `"the original"` epithet is deliberately a fragment so template can write `"the {quest_item_full}"` → `"the original trade manifest"`.
- `evidence_correspondence` nounPlural intentionally matches noun (mass noun).

**Step 2: Commit**

```bash
git add src/main/resources/quests/pools/evidence_items.json
git commit -m "data(quest-pool): migrate evidence_items to noun/epithet schema

Most evidence entries leave epithet null: evidence is clinical, keepsakes
hold the emotional flavor. A few keep a leading fragment ('the original')
so templates can pair it with a definite article."
```

---

## Task 4: Add `targetEpithet` to `ObjectiveInstance`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/ObjectiveInstance.java`

**Why:** `DialogueResolver.overlayObjective` needs access to the epithet after persistence round-trips, so the objective must carry it. An objective created from a keepsake with `epithet = "kept since childhood"` serializes that, and on reload the overlay can reconstruct `{quest_item_full}`.

**Step 1: Add the field + accessors**

Insert near the existing `targetLabelPlural` field (line 7) and its accessors (line 40-41):

```java
// field
private String targetEpithet;

// in accessors section
public String getTargetEpithet() { return targetEpithet; }
public void setTargetEpithet(String targetEpithet) { this.targetEpithet = targetEpithet; }
```

Leave the constructors alone: `targetEpithet` is set via setter during objective creation, which matches the existing pattern for `targetLabelPlural`.

**Step 2: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/ObjectiveInstance.java
git commit -m "feat(quest): add targetEpithet to ObjectiveInstance

Carries the optional epithet from the pool entry through serialization
so the dialogue overlay can rebuild {quest_item_full} after a quest is
loaded from persisted state."
```

---

## Task 5: Wire `{quest_item}` and `{quest_item_full}` in `QuestGenerator`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (three sites: `resolveWorldBindings`, `COLLECT_RESOURCES` branch, `FETCH_ITEM`/`PEACEFUL_FETCH` branches)

**Principle for this task:** `quest_item` = bare noun. `quest_item_full` = bare noun + " " + epithet if epithet present, else equal to `quest_item`. `ObjectiveInstance.targetLabel` = bare noun. `ObjectiveInstance.targetEpithet` = epithet (may be null).

**Step 1: Add a private helper**

Insert near the bottom of `QuestGenerator` (before the closing brace):

```java
/** Compose the full form of a quest item (noun + epithet if present).
 *  Used for {@code {quest_item_full}} template variables; dialogue prose
 *  that wants flavor reads this, objective UI reads bare {@code quest_item}. */
private static String fullForm(QuestPoolRegistry.ItemEntry entry) {
    String noun = entry.noun();
    String epithet = entry.epithet();
    return (epithet != null && !epithet.isEmpty()) ? noun + " " + epithet : noun;
}
```

**Step 2: Update the fallback in `resolveWorldBindings` (around line 190-191)**

Replace:

```java
QuestPoolRegistry.ItemEntry gatherItem = poolRegistry.randomCollectResource(random);
bindings.put("quest_item", gatherItem.label());
bindings.put("gather_item_id", gatherItem.id());
```

with:

```java
QuestPoolRegistry.ItemEntry gatherItem = poolRegistry.randomCollectResource(random);
bindings.put("quest_item", gatherItem.noun());
bindings.put("quest_item_full", fullForm(gatherItem));
bindings.put("gather_item_id", gatherItem.id());
```

(`collectResources` doesn't author epithet, so `fullForm` returns the bare noun: this is intentional and harmless.)

**Step 3: Update the `COLLECT_RESOURCES` branch (around line 279-287)**

Replace the block that sets `quest_item` binding and creates the objective with:

```java
bindings.put("quest_item", collectItem.noun());
bindings.put("quest_item_full", fullForm(collectItem));
bindings.put("gather_item_id", collectItem.id());
bindings.put("gather_count", String.valueOf(count));

ObjectiveInstance collectObj = new ObjectiveInstance(
    type, collectItem.id(), collectItem.noun(),
    count, null, null
);
collectObj.setTargetLabelPlural(collectItem.nounPlural());
collectObj.setTargetEpithet(collectItem.epithet());
yield collectObj;
```

**Step 4: Update the `FETCH_ITEM` branch (around line 304-339)**

Replace the entire `case FETCH_ITEM -> { ... }` body with:

```java
case FETCH_ITEM -> {
    // Draw a keepsake or evidence item. Template may pin a specific item type
    // (e.g. "quest_vial"); otherwise the drawn pool entry's fetchItemType wins.
    QuestPoolRegistry.ItemEntry fetchEntry = random.nextBoolean()
        ? poolRegistry.randomKeepsakeItem(random)
        : poolRegistry.randomEvidenceItem(random);

    String fetchItemType = config.fetchItem() != null
        ? QuestPoolRegistry.capitalize(config.fetchItem())
        : QuestPoolRegistry.getBaseItemType(fetchEntry);

    bindings.put("quest_item", fetchEntry.noun());
    bindings.put("quest_item_full", fullForm(fetchEntry));
    bindings.put("gather_item_id", fetchEntry.id());
    bindings.put("fetch_item_type", fetchItemType);
    bindings.put("fetch_item_label", fetchEntry.noun());

    ObjectiveInstance fetchObj = createPOIObjective(type, bindings, config, random);
    if (fetchObj != null) {
        fetchObj.setTargetEpithet(fetchEntry.epithet());
        yield fetchObj;
    }

    ObjectiveInstance fallback = new ObjectiveInstance(
        type, fetchEntry.id(), fetchEntry.noun(),
        1, null, null
    );
    fallback.setTargetEpithet(fetchEntry.epithet());
    yield fallback;
}
```

Note: `createPOIObjective` currently sets `targetLabel` from `bindings.get("quest_item")`, which is now the bare noun. Good: no change needed there. But we DO need to set `targetEpithet` on the returned objective, which is what the new line `fetchObj.setTargetEpithet(fetchEntry.epithet())` handles.

**Step 5: Update the `PEACEFUL_FETCH` branch (around line 340-356)**

Replace the block with:

```java
case PEACEFUL_FETCH -> {
    QuestPoolRegistry.ItemEntry fetchEntry = poolRegistry.randomKeepsakeItem(random);
    bindings.put("quest_item", fetchEntry.noun());
    bindings.put("quest_item_full", fullForm(fetchEntry));
    bindings.put("gather_item_id", fetchEntry.id());
    String fetchItemType = QuestPoolRegistry.getBaseItemType(fetchEntry);
    bindings.put("fetch_item_type", fetchItemType);
    bindings.put("fetch_item_label", fetchEntry.noun());
    bindings.put("fetch_variant", "peaceful");

    String targetSettlementKey = bindings.get("target_npc_settlement_key");

    ObjectiveInstance peacefulObj = new ObjectiveInstance(
        type, fetchEntry.id(), fetchEntry.noun(),
        1, null, targetSettlementKey
    );
    peacefulObj.setTargetEpithet(fetchEntry.epithet());
    yield peacefulObj;
}
```

**Step 6: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

**Step 7: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestGenerator.java
git commit -m "feat(quest): resolve {quest_item} to bare noun and add {quest_item_full}

Keepsake/evidence/collect objectives now set two template variables:
- {quest_item}: bare noun, for objective UI and tight grammar slots
- {quest_item_full}: noun + epithet when available, for dialogue prose

ObjectiveInstance carries the epithet so the per-objective overlay in
DialogueResolver can reconstruct {quest_item_full} after persistence."
```

---

## Task 6: Overlay `{quest_item_full}` in `DialogueResolver`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/DialogueResolver.java:40-57` (HIGHLIGHTED_QUEST_VARS) and `:103-131` (overlayObjective)

**Step 1: Add `quest_item_full` to `HIGHLIGHTED_QUEST_VARS`**

Insert `"quest_item_full"` right after `"quest_item"` in the set literal (line 42):

```java
private static final Set<String> HIGHLIGHTED_QUEST_VARS = Set.of(
    // Items / counts (per-objective)
    "quest_item",
    "quest_item_full",
    "gather_count",
    ...
);
```

**Step 2: Update `overlayObjective` to write `quest_item_full`**

Replace the three cases that touch `quest_item` with versions that also set `quest_item_full`. Helper:

```java
private static String composeFull(ObjectiveInstance obj) {
    String noun = obj.getEffectiveLabel();
    String epithet = obj.getTargetEpithet();
    return (epithet != null && !epithet.isEmpty()) ? noun + " " + epithet : noun;
}
```

Place it immediately below `overlayObjective`. Then inside `overlayObjective`:

```java
case COLLECT_RESOURCES -> {
    bindings.put("gather_count", String.valueOf(objective.getRequiredCount()));
    if (objective.getEffectiveLabel() != null) {
        bindings.put("quest_item", objective.getEffectiveLabel());
        bindings.put("quest_item_full", composeFull(objective));
    }
}
```

```java
case FETCH_ITEM, PEACEFUL_FETCH -> {
    if (objective.getEffectiveLabel() != null) {
        bindings.put("quest_item", objective.getEffectiveLabel());
        bindings.put("quest_item_full", composeFull(objective));
    }
}
```

Leave `KILL_MOBS` and `TALK_TO_NPC` unchanged: they don't use epithets.

**Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/DialogueResolver.java
git commit -m "feat(dialogue): overlay {quest_item_full} per-objective

Templates using {quest_item_full} get the bare noun plus the objective's
epithet (if authored), entity-highlighted like {quest_item}. Templates
using {quest_item} continue to get the bare noun."
```

---

## Task 7: Split `rewardText` into `rewardGold` / `rewardItem` / `rewardFlavor`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/model/QuestTemplateV2.java`

**Step 1: Replace `rewardText` in the record signature**

Replace the `String rewardText,` parameter at line 51 with:

```java
int rewardGold,
@Nullable String rewardItem,
@Nullable String rewardFlavor,
```

Update the class-level Javadoc paragraph (currently explains `rewardText`, around lines 17-21) to describe the new three-slot model:

```java
 * <p>Rewards use three structured slots instead of a single sentence.
 * {@code rewardGold} is always dispensed; {@code rewardItem} (if non-null) is
 * a pool item id that becomes a real item stack; {@code rewardFlavor} (if
 * non-null) is a short emotional note (≤ 5 words) that appears in dialogue
 * but never pretends to be an inventory item. The corresponding template
 * variables are {@code {reward_gold}}, {@code {reward_item}}, and
 * {@code {reward_flavor}}; authors compose the turn-in beats around them.
```

**Step 2: Compile**

This WILL fail: `QuestGenerator`, `QuestTemplateRegistry`, and `DialogueActionRegistry` reference `template.rewardText()`. That's expected: the next task updates them.

```bash
./gradlew compileJava
```

Expected: BUILD FAILED with "method rewardText() not found" at the call sites. Record those call sites.

**Step 3: Do not commit yet.**

Commit at end of Task 8 after the callers compile.

---

## Task 8: Wire reward bindings and registry parsing

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java:58-61` (reward binding setup)
- Modify: `src/main/java/com/chonbosmods/quest/QuestTemplateRegistry.java` (JSON → record mapping)
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java:276-279` (TURN_IN_V2 reward handling)

**Step 1: Update `QuestGenerator.generate()` reward binding**

Replace lines 58-61:

```java
// Per-template author-defined reward flavor (e.g. "a pouch of silver and a hot meal")
bindings.put("quest_reward",
    template.rewardText() != null && !template.rewardText().isEmpty()
        ? template.rewardText() : DEFAULT_REWARD_TEXT);
```

with:

```java
// Structured reward: gold is always set, item + flavor are optional strings.
// Templates compose turn-in beats around {reward_gold}, {reward_item},
// {reward_flavor}. A reward_item that resolves to a known pool entry is
// rendered as its bare noun (e.g. "tin locket").
bindings.put("reward_gold", String.valueOf(template.rewardGold()));
if (template.rewardItem() != null && !template.rewardItem().isEmpty()) {
    QuestPoolRegistry.ItemEntry rewardEntry = poolRegistry.findFetchItemById(template.rewardItem());
    bindings.put("reward_item", rewardEntry != null ? rewardEntry.noun() : template.rewardItem());
} else {
    bindings.put("reward_item", "");
}
bindings.put("reward_flavor",
    template.rewardFlavor() != null ? template.rewardFlavor() : "");
```

Also remove the now-unused constant `DEFAULT_REWARD_TEXT` (line 26).

**Step 2: Update `QuestTemplateRegistry` JSON mapping**

Open `QuestTemplateRegistry.java`, find where v2 templates are parsed (search for `rewardText`). Replace the Gson field extraction with reads of the new three fields. Typical shape:

```java
// Before
String rewardText = obj.has("rewardText") ? obj.get("rewardText").getAsString() : "";

// After
int rewardGold = obj.has("rewardGold") ? obj.get("rewardGold").getAsInt() : 0;
String rewardItem = obj.has("rewardItem") && !obj.get("rewardItem").isJsonNull()
                    ? obj.get("rewardItem").getAsString() : null;
String rewardFlavor = obj.has("rewardFlavor") && !obj.get("rewardFlavor").isJsonNull()
                      ? obj.get("rewardFlavor").getAsString() : null;
```

Then update the constructor call to pass the three new values in place of `rewardText`.

**Audit the file before editing:** search for `rewardText` and update all references.

**Step 3: Update `HIGHLIGHTED_QUEST_VARS` in `DialogueResolver`**

Since `quest_reward` is gone, remove it from the set (line 56-57). Add the three new names:

```java
// Reward
"reward_gold",
"reward_item",
"reward_flavor"
```

**Step 4: Update `TURN_IN_V2` in `DialogueActionRegistry`**

Around lines 276-279, the existing comment is:

```java
// Reward (stub: multiplier computed but not yet dispensed)
double multiplier = BASE_REWARD_MULTIPLIER + (quest.getConflictCount() * CONFLICT_REWARD_BONUS);
if (quest.isSkillcheckPassed()) multiplier += SKILLCHECK_PASS_REWARD_BONUS;
quest.claimReward(quest.getConflictCount());
```

Reward dispensing remains a stub in this task: we're only restructuring the data. Leave the `claimReward` call unchanged. Update the comment to reflect the new model:

```java
// Reward (stub: gold/item/flavor live in template fields, not yet dispensed).
// Gold dispersal and item deposit are a follow-up task.
double multiplier = BASE_REWARD_MULTIPLIER + (quest.getConflictCount() * CONFLICT_REWARD_BONUS);
if (quest.isSkillcheckPassed()) multiplier += SKILLCHECK_PASS_REWARD_BONUS;
quest.claimReward(quest.getConflictCount());
```

No behavioral change here: that is deliberate. The plan's scope is schema + bindings, not closing the dispersal stub.

**Step 5: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

**Step 6: Commit (combines Tasks 7 and 8)**

```bash
git add src/main/java/com/chonbosmods/quest/model/QuestTemplateV2.java \
        src/main/java/com/chonbosmods/quest/QuestGenerator.java \
        src/main/java/com/chonbosmods/quest/QuestTemplateRegistry.java \
        src/main/java/com/chonbosmods/quest/DialogueResolver.java \
        src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(quest): split reward into gold/item/flavor slots

Templates now declare rewardGold (int), rewardItem (nullable pool id),
and rewardFlavor (nullable short note) instead of a single rewardText
string. Dialogue reads {reward_gold}, {reward_item}, {reward_flavor}
as separate variables so turn-in lines compose as distinct beats.

Reward dispensing is still a stub: wiring gold/item deposit is deferred."
```

---

## Task 9: Migrate v2 templates in `index.json`

**Files:**
- Modify: `src/main/resources/quests/v2/index.json` (all templates)

**Step 1: Audit templates**

Open the file and search for every `"rewardText"` occurrence. For each one:

1. Read the `rewardText` string.
2. Pick a `rewardGold` value (default 25; bump to 50 for 3-objective templates, 75 for 4+-objective templates).
3. If the old text named a concrete item-like thing (a coin, a meal, a tool), decide whether it should become a `rewardItem` pool id (must exist in `keepsake_items.json` or `evidence_items.json`) or live purely as `rewardFlavor`. **Default: it's flavor.**
4. Condense the remaining emotional note into ≤ 5 words and put it in `rewardFlavor`.

Replace the field:

```jsonc
// Before
"rewardText": "what coin I've kept hidden and a meal whenever you pass through",

// After
"rewardGold": 25,
"rewardItem": null,
"rewardFlavor": "a meal whenever you pass through"
```

**Step 2: Audit template text for `{quest_reward}` references**

Grep `index.json` for `{quest_reward}`. For each hit, rewrite the sentence to use the new variables. Typical patterns:

```jsonc
// Before
"resolutionText": "Take {quest_reward}. It's not what your time was worth."

// After (one-beat form when no flavor)
"resolutionText": "Take this: {reward_gold} gold. It's not what your time was worth."

// After (two-beat form when flavor is set)
"resolutionText": "Take this: {reward_gold} gold. And this. {reward_flavor}."
```

Authors hold the article / possessive / punctuation. The resolver's grammar cleanup (double-article, a/an) is still a safety net, but the sentence is now structurally clean.

**Step 3: Audit template text for `{quest_item}` in objective-UI-adjacent positions**

This step is mostly a spot-check. The global `{quest_item}` change from verbose label to bare noun may make a handful of existing template sentences read worse (e.g. `"I need {gather_count} {quest_item}"` was fine before when quest_item said "wheat" for collect, but for fetch-adjacent text like exposition it may now read "I lost a child's toy" previously and become "I lost child's toy" now — missing the article).

For each template that uses `{quest_item}` in a FETCH_ITEM phase's expositionText / conflictNText / resolutionText:

- If the sentence needs an article, prepend `"a "` / `"the "` / `"my "` around `{quest_item}` (e.g. `"I lost a {quest_item}"`).
- If the sentence wants flavor, switch to `{quest_item_full}` (e.g. `"I lost a {quest_item_full}, something kept since childhood"` — but the epithet is already inside quest_item_full, so prefer just `"I lost a {quest_item_full}"`).

Do not attempt to touch COLLECT_RESOURCES sentences unless they're visibly broken: collect_resources pool labels were already clean (things like "wheat", "iron ore").

**Step 4: Compile (JSON parse happens at runtime, not compile: use quick sanity)**

```bash
./gradlew compileJava
```

Validate JSON with a quick `jq` pass:

```bash
jq . src/main/resources/quests/v2/index.json > /dev/null
```

Expected: exit 0, no JSON parse errors.

**Step 5: Commit**

```bash
git add src/main/resources/quests/v2/index.json
git commit -m "data(quest): migrate v2 templates to new reward and quest_item schemas

Every rewardText is replaced with rewardGold/rewardItem/rewardFlavor.
Resolution/conflict text references {reward_gold}, {reward_item},
{reward_flavor} explicitly; {quest_reward} is gone.

Exposition/conflict sentences that referenced {quest_item} for fetch
items are rewritten to carry their own article and optionally switch
to {quest_item_full} when narrative flavor is welcome."
```

---

## Task 10: Full compile + smoke-test handoff

**Files:** none.

**Step 1: Full clean compile**

```bash
./gradlew clean compileJava
```

Expected: BUILD SUCCESSFUL, zero warnings about the modified files.

**Step 2: Document the manual smoke test**

This project's verification story is a live dev-server trace, and `./gradlew devServer` cannot run from a worktree (per `devserver-worktree-limitation.md`). The plan stops here; merge-to-main + smoke test is the human step.

Write a brief handoff note to the branch description (or commit message body on the merge commit). The smoke-test checklist:

- Generate a quest with a FETCH_ITEM phase. Confirm objective card reads "Fetch: <bare noun>" (not "Fetch: a <noun> <clause>").
- Check the inventory: the quest item stack name is title-case bare noun ("Child's Toy", not "A Child's Toy They Could Never Throw Away").
- Open dialogue that uses `{quest_item_full}`. Confirm the full noun + epithet renders without double articles.
- Turn in a quest. Confirm `{reward_gold}` appears as a number and `{reward_flavor}` appears as a short note in a separate beat. No "quest_reward" literal should appear anywhere.

**Step 3: Update project memory**

After the human smoke test confirms the flow, update `memory/quest-v2-system-state.md` to reflect: (a) pool schema change, (b) reward schema change, (c) new template variables available. Note that reward dispensing is still a stub.

**Step 4: No commit here** — memory update happens post-merge.

---

## Out-of-scope follow-ups (tracked, not in this plan)

1. **Audit for invented item names.** Design Section 3 — grep every site that composes an item name from freeform input and ensure it traces back to a pool roll. Filed as a separate task.
2. **Reward dispensing.** Actually move `rewardGold` into the player's coin balance and `rewardItem` into inventory on turn-in. Currently the `TURN_IN_V2` handler multiplier is computed but not applied.
3. **`collect_resources.json` / `hostile_mobs.json` migration.** Not currently broken; no pressure to change until we see a specific grammar failure.
4. **Continue-button fix.** Shares this branch but has no design yet. Separate brainstorm + plan before implementation.
