# Quest Reward + Encounter Scaling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Plug the gear-filter hole in `AffixRewardRoller` (Phase 1); replace hardcoded `mobIlvl`/`rewardIlvl` per difficulty with a runtime formula `clamp(playerLevel, areaLevel-5, areaLevel) + ilvlBonus` for rewards (per-player at turn-in) and `areaLevel + ilvlBonus` for encounters (Phase 2). Easy quest rarity range bumped to `Uncommon-Rare` to align with mob loot.

**Architecture:** Reward roll moves from quest generation to per-phase turn-in. `QuestInstance.PhaseReward` no longer pre-stores the rolled item; instead stores the inputs needed at dispense time (`tier` rolled with dampener at gen, `areaLevelAtSpawn` snapshot, `ilvlBonus`). `AffixRewardRoller` plugs through `Nat20MobLootPool.buildGlobalBuckets` + `CategoryWeightedPicker` so the new gear filter is honored. Difficulty config drops `rewardIlvl`/`mobIlvl`, adds `ilvlBonus`. KILL_MOBS populationSpec uses `areaLevel + ilvlBonus` at all 3 call sites.

**Tech Stack:** Java 25, JUnit 5, Gson. Worktree at `.worktrees/feat-quest-reward-encounter-scaling/`.

**Reference design doc:** `docs/plans/2026-04-25-quest-reward-encounter-scaling-design.md`.

---

## Conventions

- Branch: `feat/quest-reward-encounter-scaling` (already created from main).
- All commands assume cwd = worktree root.
- Each task ends with one commit. **No `Co-Authored-By` lines, no `--no-verify`, no push.** Use `:` instead of `—`. Unicode `×` is fine.
- Test pattern reference: `src/test/java/com/chonbosmods/dialogue/WeightedTierDrawTest.java` (seeded `Random(42L)`, JUnit 5, package-private classes).
- Devserver smoke testing happens AFTER merge.

---

## Task 1: `AffixRewardRoller` plugs through the gear filter (Phase 1, standalone)

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/AffixRewardRoller.java`

**Step 1: Inspect current implementation**

```bash
grep -n 'getAllItemIds\|buildGlobalBuckets\|getItemIds\|categoryKey' src/main/java/com/chonbosmods/quest/AffixRewardRoller.java
```

Read the file end-to-end. The current `roll(String tier, int ilvl, Random random)` pulls from `entryRegistry.getAllItemIds()` and picks uniformly. Replace with the bucketed picker + gear-filter path.

**Step 2: Replace pool source**

In `AffixRewardRoller.roll(...)`, replace:

```java
List<String> itemIds = new ArrayList<>(entryRegistry.getAllItemIds());
String itemId = itemIds.get(random.nextInt(itemIds.size()));
```

with:

```java
Map<String, List<String>> buckets = Nat20MobLootPool.buildGlobalBuckets(entryRegistry, ilvl);
String itemId = CategoryWeightedPicker.pick(buckets, random);
if (itemId == null) {
    LOGGER.atWarning().log("Empty gear-pool buckets for quest reward at ilvl=%d; nothing to generate", ilvl);
    return null; // or throw — match existing failure mode of roll()
}
```

(Match the existing return type / null-handling of `roll()` — read the method to see what it returns on failure.)

Add imports: `com.chonbosmods.loot.CategoryWeightedPicker`, `com.chonbosmods.loot.mob.Nat20MobLootPool`, `java.util.Map`. Drop the now-unused imports for the flat list path.

If `roll()` has a sibling `rollFor(String itemId, ...)` that takes an explicit itemId, leave that path alone — it's the override case where the caller already knows the item.

**Step 3: Compile + run full suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. Existing quest-reward integration tests (if any) keep passing. The reward generation code path now respects the gear filter (blocklist + tokens + overrides) and category weights.

**Step 4: Manual sanity check**

`grep -rn 'AffixRewardRoller.roll\b' src/main/java` — every caller should still compile (we didn't change the signature).

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/AffixRewardRoller.java
git commit -m "fix(quest): route AffixRewardRoller through gear filter (blocklist now honored on quest rewards)"
```

---

## Task 2: `QuestRewardIlvl` pure-function helper (TDD)

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestRewardIlvl.java`
- Create: `src/test/java/com/chonbosmods/quest/QuestRewardIlvlTest.java`

**Step 1: Write the failing tests**

```java
package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestRewardIlvlTest {

    // ---- Reward formula ----

    @Test
    void rewardLowLevelInHomeZone() {
        // player=3, area=5, bonus=5 (Hard) → clamp(3, 0, 5) + 5 = 8
        assertEquals(8, QuestRewardIlvl.reward(3, 5, 5));
    }

    @Test
    void rewardLowLevelInHardZone_clampedToFloor() {
        // player=5, area=15, bonus=5 → clamp(5, 10, 15) + 5 = 15
        assertEquals(15, QuestRewardIlvl.reward(5, 15, 5));
    }

    @Test
    void rewardPlayerMatchedToArea() {
        // player=15, area=15, bonus=5 → clamp(15, 10, 15) + 5 = 20
        assertEquals(20, QuestRewardIlvl.reward(15, 15, 5));
    }

    @Test
    void rewardHighLevelFarmingStarter_cappedAtAreaCeiling() {
        // player=30, area=5, bonus=5 → clamp(30, 0, 5) + 5 = 10
        assertEquals(10, QuestRewardIlvl.reward(30, 5, 5));
    }

    @Test
    void rewardEasyQuestNoBonus() {
        // player=15, area=15, bonus=0 (Easy) → clamp(15, 10, 15) + 0 = 15
        assertEquals(15, QuestRewardIlvl.reward(15, 15, 0));
    }

    @Test
    void rewardClampedToMaxIlvl45() {
        // player=44, area=44, bonus=5 → 44 + 5 = 49 → clamped to 45
        assertEquals(45, QuestRewardIlvl.reward(44, 44, 5));
    }

    @Test
    void rewardClampedToMinIlvl1() {
        // player=1, area=1, bonus=0 → 1 (already at floor)
        assertEquals(1, QuestRewardIlvl.reward(1, 1, 0));
    }

    @Test
    void rewardLowAreaWideClampWindow() {
        // area=2 means lower-bound = max(0, 2-5) = -3 → effective floor = 1 (clamp to 1)
        // player=1, area=2, bonus=0 → clamp(1, max(0, -3), 2) + 0 = 1
        assertEquals(1, QuestRewardIlvl.reward(1, 2, 0));
    }

    // ---- Encounter formula ----

    @Test
    void encounterMatchesAreaPlusBonus() {
        // area=15, bonus=2 → 17
        assertEquals(17, QuestRewardIlvl.encounter(15, 2));
    }

    @Test
    void encounterEasyQuestUnchanged() {
        // area=10, bonus=0 → 10
        assertEquals(10, QuestRewardIlvl.encounter(10, 0));
    }

    @Test
    void encounterClampedToMax() {
        // area=43, bonus=5 → 48 → clamped to 45
        assertEquals(45, QuestRewardIlvl.encounter(43, 5));
    }

    @Test
    void encounterClampedToMin() {
        // area=0 (defensive — shouldn't happen but)
        assertEquals(1, QuestRewardIlvl.encounter(0, 0));
    }
}
```

**Step 2: Run tests to verify failure**

```bash
./gradlew test --tests QuestRewardIlvlTest
```

Expected: compile error (class doesn't exist).

**Step 3: Implement the helper**

```java
package com.chonbosmods.quest;

/**
 * Pure-function helpers for quest reward + encounter ilvl computation.
 * See design doc: docs/plans/2026-04-25-quest-reward-encounter-scaling-design.md
 */
public final class QuestRewardIlvl {

    private static final int MIN_ILVL = 1;
    private static final int MAX_ILVL = 45;
    private static final int CLAMP_BANDWIDTH = 5;

    private QuestRewardIlvl() {}

    /**
     * Reward ilvl for a single player completing a quest phase.
     * Formula: clamp(playerLevel, areaLevel - 5, areaLevel) + ilvlBonus, clamped to [1, 45].
     *
     * @param playerLevel    accepter's current mlvl
     * @param areaLevel      areaLevel snapshot at quest generation
     * @param ilvlBonus      difficulty bonus (Easy=0, Medium=2, Hard=5)
     */
    public static int reward(int playerLevel, int areaLevel, int ilvlBonus) {
        int lo = Math.max(MIN_ILVL, areaLevel - CLAMP_BANDWIDTH);
        int hi = Math.max(lo, areaLevel);
        int clamped = Math.max(lo, Math.min(hi, playerLevel));
        return Math.max(MIN_ILVL, Math.min(MAX_ILVL, clamped + ilvlBonus));
    }

    /**
     * Encounter (mob) ilvl for a quest. World-consistent: depends only on area + difficulty.
     * Formula: areaLevel + ilvlBonus, clamped to [1, 45].
     */
    public static int encounter(int areaLevel, int ilvlBonus) {
        return Math.max(MIN_ILVL, Math.min(MAX_ILVL, areaLevel + ilvlBonus));
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests QuestRewardIlvlTest
```

Expected: 12/12 pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestRewardIlvl.java \
        src/test/java/com/chonbosmods/quest/QuestRewardIlvlTest.java
git commit -m "feat(quest): add QuestRewardIlvl helper (clamp(player, area-5, area) + bonus; encounter = area + bonus)"
```

---

## Task 3: `DifficultyConfig` schema change + parser/validator update

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/model/DifficultyConfig.java`
- Modify: `src/main/java/com/chonbosmods/quest/QuestDifficultyRegistry.java`
- Possibly: `src/test/java/com/chonbosmods/quest/QuestDifficultyRegistryTest.java` (if it exists)

**Step 1: Inspect current shape**

```bash
cat src/main/java/com/chonbosmods/quest/model/DifficultyConfig.java
grep -n 'rewardIlvl\|mobIlvl' src/main/java/com/chonbosmods/quest/QuestDifficultyRegistry.java
```

Confirm record fields and parser/validator references to the two soon-to-be-removed fields.

**Step 2: Update record**

In `DifficultyConfig.java`:
- **Remove** the `int rewardIlvl` component.
- **Remove** the `int mobIlvl` component.
- **Add** `int ilvlBonus` component (place it near where the removed fields were, for readability).

**Step 3: Update parser**

In `QuestDifficultyRegistry.java`:
- Remove the `rewardIlvl` and `mobIlvl` parse lines and any associated validation (e.g. `if (c.rewardIlvl() <= 0) ...` and `if (c.mobIlvl() <= 0) ...` at lines 87-92).
- Add parse + validation for `ilvlBonus`:

```java
int ilvlBonus = obj.has("ilvlBonus") ? obj.get("ilvlBonus").getAsInt() : 0;
if (ilvlBonus < 0) {
    fail(resource, c.id(), "ilvlBonus must be >= 0 (got " + ilvlBonus + ")");
}
```

(Match the existing `fail(...)` pattern used by the registry.) Reasonable upper bound: warn but allow values > 10 in case future difficulty tiers emerge.

**Step 4: Update record constructor call site to pass `ilvlBonus`**

The record constructor in the registry — adjust the positional arg list to drop `rewardIlvl`, `mobIlvl` and add `ilvlBonus`.

**Step 5: Compile**

```bash
./gradlew compileJava
```

Expected: **fails** at every call site of `difficulty.rewardIlvl()` and `difficulty.mobIlvl()` — that's the signal of work to do in Tasks 5–8. Capture the compile errors and proceed.

**Step 6: Don't commit yet — Tasks 4 + 5 follow before the build is green**

The schema change cascades through several files. To keep commits coherent, batch Tasks 3 + 4 + 5 into one commit (record + JSON + PhaseReward). OR commit Task 3 with `// TODO: update callers` comments inserted at every now-broken call site to keep the build green at every step.

Recommended: combine Tasks 3 + 4 in one commit; Task 5 is its own commit; Tasks 6/7/8 follow.

---

## Task 4: Update three difficulty JSON files

**Files:**
- Modify: `src/main/resources/quests/difficulty/easy.json`
- Modify: `src/main/resources/quests/difficulty/medium.json`
- Modify: `src/main/resources/quests/difficulty/hard.json`

**Step 1: Replace each file**

`easy.json`:
```json
{
  "id": "easy",
  "xpAmount": 50,
  "rewardTierMin": "Uncommon",
  "rewardTierMax": "Rare",
  "ilvlBonus": 0,
  "mobBoss": true,
  "bossIlvlOffset": 3,
  "mobCountMultiplier": 0.8,
  "gatherCountMultiplier": 0.8
}
```

`medium.json`:
```json
{
  "id": "medium",
  "xpAmount": 100,
  "rewardTierMin": "Uncommon",
  "rewardTierMax": "Rare",
  "ilvlBonus": 2,
  "mobBoss": true,
  "bossIlvlOffset": 3,
  "mobCountMultiplier": 1.0,
  "gatherCountMultiplier": 1.0
}
```

`hard.json`:
```json
{
  "id": "hard",
  "xpAmount": 200,
  "rewardTierMin": "Rare",
  "rewardTierMax": "Epic",
  "ilvlBonus": 5,
  "mobBoss": true,
  "bossIlvlOffset": 3,
  "mobCountMultiplier": 1.3,
  "gatherCountMultiplier": 1.3
}
```

Notes:
- `rewardIlvl` + `mobIlvl` removed.
- `ilvlBonus` added.
- Easy `rewardTierMin` shifted from `Common` → `Uncommon` (matches mob loot which doesn't drop Common).

**Step 2: Verify all parse**

```bash
python3 -c "import json; [json.load(open('src/main/resources/quests/difficulty/' + f)) for f in ['easy.json','medium.json','hard.json']]" && echo OK
```

**Step 3: Combined commit (Tasks 3 + 4)**

```bash
git add src/main/java/com/chonbosmods/quest/model/DifficultyConfig.java \
        src/main/java/com/chonbosmods/quest/QuestDifficultyRegistry.java \
        src/main/resources/quests/difficulty/easy.json \
        src/main/resources/quests/difficulty/medium.json \
        src/main/resources/quests/difficulty/hard.json
git commit -m "refactor(quest): replace difficulty rewardIlvl/mobIlvl with ilvlBonus; bump Easy to Uncommon-Rare"
```

(Build will be RED at this commit — Tasks 5–8 fix the call sites that referenced the removed fields. Document this in the commit body if you want to be tidy: "intentionally breaks compile until follow-up commits land call-site changes.")

OR alternatively, defer this commit until after Task 5 to keep main green at every commit. Pick one approach and be consistent.

---

## Task 5: `PhaseReward` record shape change

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestInstance.java` (around line 240+, the `PhaseReward` inner class)

**Step 1: Inspect current shape**

```bash
sed -n '240,290p' src/main/java/com/chonbosmods/quest/QuestInstance.java
```

Capture: field list, constructor signature, getters, the Gson `BuilderCodec` field declarations.

**Step 2: Replace `PhaseReward` shape**

Old fields to **remove** (per design doc):
- `rewardItemId`
- `rewardItemCount`
- `rewardItemDisplayName`
- `rewardItemDataJson`
- `rewardIlvl`

Fields to **keep**:
- `rewardTier` (rolled at quest gen with dampener; reused per accepter)

Fields to **add**:
- `int areaLevelAtSpawn` — snapshotted at quest generation
- `int ilvlBonus` — copy of difficulty's ilvlBonus

Also update:
- The Gson `BuilderCodec` `addField(...)` chain to drop removed fields and add the two new ones.
- Constructor signature: `(String rewardTier, int areaLevelAtSpawn, int ilvlBonus)`.
- Drop now-unused getters (e.g. `getRewardItemId`, `getRewardIlvl`). Add `getAreaLevelAtSpawn()` and `getIlvlBonus()`.

**Step 3: Update call sites**

Run: `grep -rn 'PhaseReward(\|getRewardItemId\|getRewardIlvl\|getRewardItemCount\|getRewardItemDisplayName\|getRewardItemDataJson' src/main/java`

For every hit:
- Constructors of `PhaseReward(...)` → update arg list.
- Getters → remove the call OR replace with the new getters where needed (will be done concretely in Tasks 6-7).

**Step 4: Compile**

```bash
./gradlew compileJava
```

Will fail in `QuestGenerator` (Task 6), `Nat20QuestRewardDispatcher` + `DialogueActionRegistry` (Task 7), and `TutorialQuestFactory` + `TutorialPhase3Setup` (Task 8). That's expected.

**Step 5: Commit (with the build still red, OR defer until Task 6)**

```bash
git add src/main/java/com/chonbosmods/quest/QuestInstance.java
git commit -m "refactor(quest): replace pre-rolled PhaseReward fields with rewardTier + areaLevelAtSpawn + ilvlBonus"
```

(Same green-vs-red trade-off as Task 3/4 commit. Pick the approach that's consistent across this batch.)

---

## Task 6: `QuestGenerator` — defer reward roll, update KILL_MOBS spec, capture areaLevel

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java`

**Step 1: Find the reward-roll loop**

```bash
sed -n '160,200p' src/main/java/com/chonbosmods/quest/QuestGenerator.java
```

Around line 180 the current code calls `AffixRewardRoller.roll(phaseTier, difficulty.rewardIlvl(), random)` and stores the resulting itemStack. Remove that roll.

**Step 2: Compute `areaLevelAtSpawn` for the quest**

The quest is anchored to an NPC. Use the NPC's position to derive area level via `MobScalingConfig.areaLevelForDistance(distance)`. The exact accessor depends on the QuestGenerator's existing surface — find where the NPC's position is available in scope (probably via `citizen.getNpcEntity()` or similar). If no clean accessor exists, add one.

If the NPC has a stored `areaLevel` already (some mob systems persist it on a `LevelComponent`), prefer reading that.

If neither is available, fall back to `MobScalingConfig.areaLevelForDistance(...)` computed from NPC position vs. spawn point.

**Step 3: Build the new `PhaseReward`**

Replace:

```java
ItemStack phaseStack = AffixRewardRoller.roll(phaseTier, difficulty.rewardIlvl(), random);
// ... old code that sets rewardItemId, rewardItemCount, etc.
```

with:

```java
PhaseReward phaseReward = new PhaseReward(phaseTier, areaLevelAtSpawn, difficulty.ilvlBonus());
phaseRewards.add(phaseReward);
```

**Step 4: Update KILL_MOBS populationSpec**

Around line 614-621 the current code builds `KILL_MOBS:<enemy>:<count>:<mobIlvl>:<mobBoss>:<bossIlvlOffset>` using `difficulty.mobIlvl()`. Replace with:

```java
int mobIlvl = QuestRewardIlvl.encounter(areaLevelAtSpawn, difficulty.ilvlBonus());
String spec = "KILL_MOBS:" + enemyId + ":" + spawnCount + ":" + mobIlvl
            + ":" + difficulty.mobBoss() + ":" + difficulty.bossIlvlOffset();
```

**Step 5: Compile + run full suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL once Tasks 5 + 6 are both done. If still red, hunt the remaining call sites (probably in dispatcher + tutorial — Tasks 7 + 8).

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestGenerator.java
git commit -m "feat(quest): QuestGenerator captures areaLevelAtSpawn + ilvlBonus on PhaseReward; KILL_MOBS uses area+bonus"
```

(If you batched Tasks 3-6 into one commit per the earlier note, stage the relevant files together.)

---

## Task 7: Per-player reward dispense at turn-in

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/Nat20QuestRewardDispatcher.java`
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java`

**Step 1: Find the dispatcher's per-player loop**

```bash
sed -n '85,160p' src/main/java/com/chonbosmods/quest/Nat20QuestRewardDispatcher.java
```

Lines 95-122 currently read `tier`/`ilvl` from the stored `PhaseReward` and reroll. Replace `ilvl` with the new computed value using `QuestRewardIlvl.reward(playerLevel, areaLevelAtSpawn, ilvlBonus)`.

**Step 2: Update `dispenseItemsToOtherAccepters`**

```java
PhaseReward reward = quest.getPhaseReward(phaseIndex);
String tier = reward.getRewardTier();
int areaLevel = reward.getAreaLevelAtSpawn();
int ilvlBonus = reward.getIlvlBonus();

for (UUID uuid : quest.getAccepters()) {
    if (uuid.equals(triggeringPlayer)) continue;
    if (missed.contains(uuid)) continue;

    Ref<EntityStore> ref = world.getEntityRef(uuid);
    if (ref == null) continue;

    Player peer = store.getComponent(ref, Player.getComponentType());
    if (peer == null) continue;

    Nat20PlayerData data = store.getComponent(ref, Nat20PlayerData.getComponentType());
    int playerLevel = data != null ? data.getLevel() : 1;
    int ilvl = QuestRewardIlvl.reward(playerLevel, areaLevel, ilvlBonus);

    ItemStack rerolled = AffixRewardRoller.roll(tier, ilvl, random);
    // ... existing giveItem + logging path stays
}
```

(Verify the exact accessor for `Nat20PlayerData` — it's a Component on the player entity; the registry pattern is `Nat20PlayerData.getComponentType()` per existing usage.)

**Step 3: Find TURN_IN_V2 in DialogueActionRegistry**

```bash
grep -n 'TURN_IN_V2\|giveItem.*phaseReward' src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
```

Locate where TURN_IN_V2 dispenses the item to the triggering player. It currently does `peer.giveItem(phaseReward.toItemStack())` or similar pre-rolled path. Replace with:

```java
int playerLevel = ... // read from triggering player's Nat20PlayerData
int ilvl = QuestRewardIlvl.reward(playerLevel, phaseReward.getAreaLevelAtSpawn(), phaseReward.getIlvlBonus());
ItemStack rolled = AffixRewardRoller.roll(phaseReward.getRewardTier(), ilvl, random);
peer.giveItem(rolled, ...);
```

The dialogue `{reward_item}` binding now happens AFTER the roll — bind the display name from `rolled` instead of from the (no-longer-existing) pre-rolled `rewardItemDisplayName`.

**Step 4: Compile + run full suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. If failures, audit for stragglers calling removed `PhaseReward` getters.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/Nat20QuestRewardDispatcher.java \
        src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(quest): per-player reward roll at phase turn-in (clamp(player, area-5, area) + bonus)"
```

---

## Task 8: Tutorial quest paths

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/TutorialPhase3Setup.java`
- Modify: `src/main/java/com/chonbosmods/quest/TutorialQuestFactory.java`
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` (lines 1113, 1167 — tutorial KILL_MOBS specs)

**Step 1: Audit tutorial reward path**

```bash
grep -n 'AffixRewardRoller\|rewardIlvl\|mobIlvl' src/main/java/com/chonbosmods/quest/TutorialQuestFactory.java
grep -n 'rewardIlvl\|mobIlvl' src/main/java/com/chonbosmods/quest/TutorialPhase3Setup.java
```

Find every reference. For each:

- If it reads `difficulty.rewardIlvl()` or `difficulty.mobIlvl()` — replace with `QuestRewardIlvl.encounter(areaLevel, difficulty.ilvlBonus())` for KILL_MOBS specs, or with a per-player-at-dispense computation for reward grants.
- If the tutorial quest hardcodes ilvls (e.g. `AffixRewardRoller.roll("Common", 5, random)`), align with the new model: pick a difficulty (probably Easy), use `QuestRewardIlvl.reward(...)` at dispense.

**Step 2: Tutorial KILL_MOBS in DialogueActionRegistry (lines 1113, 1167)**

Around lines 1113 and 1167:

```java
+ ":" + difficulty.mobIlvl()
```

Replace with:

```java
+ ":" + QuestRewardIlvl.encounter(areaLevel, difficulty.ilvlBonus())
```

Find/derive the appropriate `areaLevel` for the tutorial spawn location. If the tutorial spawns at the player's current location, use `MobScalingConfig.areaLevelForDistance(...)` from that point.

**Step 3: Compile + run full suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. The tutorial quest still works (no integration test exists for it, but the compile + manual smoke later will catch regressions).

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/TutorialPhase3Setup.java \
        src/main/java/com/chonbosmods/quest/TutorialQuestFactory.java \
        src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(quest): tutorial paths use area-derived ilvl + per-player reward (replace hardcoded mobIlvl/rewardIlvl)"
```

---

## Post-implementation: smoke test on devserver

Devserver smoke testing happens AFTER merge (devserver cannot run from worktrees). Follow the smoke checklist in `docs/plans/2026-04-25-quest-reward-encounter-scaling-design.md` "Smoke Test Checklist" section. Key verifications:

- High-level player in starter zone gets capped low-tier rewards (no farming).
- Low-level player venturing into a hard zone gets slightly-below-area gear.
- Two players at different levels in same party each get their own catered ilvl.
- Multi-phase quest with mid-quest level-up: later phases get higher reward ilvl.
- Tutorial quest still works.
- No blocklisted items appear as quest rewards.

If smoke passes, save a shipped memory entry similar to `gear-pool-filter-shipped.md`.

---

## Open questions for the user during implementation

1. **`areaLevel` source for tutorial**: tutorial quest spawns at known coordinates. Should we treat the tutorial as "area level 1" hardcoded, or compute from the actual spawn location? Probably hardcoded ilvl 1 — flag for confirmation.
2. **Build state during the schema change** (Tasks 3-7): preference between batching all schema-cascade changes into one commit (cleaner history, risk of large diff) vs. landing them sequentially with intermediate red commits (atomic, build-broken between commits).
3. **Per-player `rewardTier` reroll**: today the design re-rolls only ilvl per player. If you want each player to also re-roll their own tier within the difficulty's `[tierMin, tierMax]` band (so one player gets Uncommon and another gets Rare from the same Hard quest), it's a one-line change in Task 7. Out of scope of the current design but easy to add.
