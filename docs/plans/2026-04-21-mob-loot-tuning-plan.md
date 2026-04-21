# Mob Loot Tuning Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Apply the 6 mob-loot behavior changes locked in `docs/plans/2026-04-21-mob-loot-tuning-design.md` — always-affixed Commons, Common-below-Uncommon weight curve, no-more-Common-bonus-rerolls, flat-18% champion drops with difficulty-biased rarities, and native-bias bump from 8% to 12%.

**Architecture:** All changes live in the existing mob-loot pipeline — two rarity JSONs, three Java files under `com.chonbosmods.loot.mob`, one registry method addition, and one pipeline-signature extension. No new classes, no new tests (per design). Each task ends with `./gradlew compileJava` and an atomic commit so the history reads as a clean tuning-pass sequence.

**Tech Stack:** Java 25 / Hytale plugin SDK / Gradle `./gradlew compileJava` / existing Nat20 loot architecture (`Nat20LootPipeline`, `Nat20RarityRegistry`, `Nat20MobLootListener`, `Nat20MobDropCount`, `Nat20MobLootPool`).

**Working directory:** `/home/keroppi/Development/Hytale/Natural20/.worktrees/mob-loot-tuning` (branch `feat/mob-loot-tuning`, off `main`).

---

## Task 0: Commit the design doc

**Files:**
- Track: `docs/plans/2026-04-21-mob-loot-tuning-design.md` (already written, currently untracked in the worktree)

**Step 1: Verify the file is present**

Run: `ls docs/plans/2026-04-21-mob-loot-tuning-design.md`
Expected: file exists.

**Step 2: Stage and commit**

Run:
```bash
git add docs/plans/2026-04-21-mob-loot-tuning-design.md
git commit -m "docs(loot): add mob loot tuning design (2026-04-21)"
```
Expected: one-file commit, no hook failures.

---

## Task 1: Rarity JSON edits (Common always affixed + weight re-curve)

**Files:**
- Modify: `src/main/resources/loot/rarities/Common.json`
- Modify: `src/main/resources/loot/rarities/Uncommon.json`

**Step 1: Edit `Common.json`**

Change two fields:
- `BaseWeight: 1000` → `BaseWeight: 300`
- In the single entry of `LootRules`: `Probability: 0.5` → `Probability: 1.0`

Leave everything else (`QualityValue`, `Color`, `DisplayName`, `MaxAffixes`, `MaxSockets`, `StatRequirement`, `TooltipTexture`, `TooltipArrowTexture`, `SlotTexture`, the `Type` and `Count` inside the loot rule) untouched.

**Step 2: Edit `Uncommon.json`**

Change one field:
- `BaseWeight: 500` → `BaseWeight: 1000`

Leave everything else untouched. Do **not** change `Rare.json`, `Epic.json`, or `Legendary.json`.

**Step 3: Compile**

Run: `./gradlew compileJava --quiet`
Expected: success (no classes reference these JSONs at compile time; they're parsed at runtime).

**Step 4: Commit**

Run:
```bash
git add src/main/resources/loot/rarities/Common.json src/main/resources/loot/rarities/Uncommon.json
git commit -m "feat(loot): common always affixed, rarer than uncommon"
```

---

## Task 2: Native bias 8% → 12%

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/mob/Nat20MobLootPool.java:38`

**Step 1: Edit the constant**

Change:
```java
public static final float NATIVE_LIST_BIAS = 0.08f;
```
to:
```java
public static final float NATIVE_LIST_BIAS = 0.12f;
```

That is the only edit in this file for this task.

**Step 2: Compile**

Run: `./gradlew compileJava --quiet`
Expected: success.

**Step 3: Commit**

Run:
```bash
git add src/main/java/com/chonbosmods/loot/mob/Nat20MobLootPool.java
git commit -m "feat(loot): bump mob native-list bias 8% -> 12%"
```

---

## Task 3: Champion drop chances flat 0.18

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/mob/Nat20MobDropCount.java:25-27`

**Step 1: Edit the three constants**

Change the block:
```java
private static final float CHAMPION_CHANCE_UNCOMMON = 0.35f;
private static final float CHAMPION_CHANCE_RARE     = 0.40f;
private static final float CHAMPION_CHANCE_EPIC     = 0.45f;
```
to:
```java
private static final float CHAMPION_CHANCE_UNCOMMON = 0.18f;
private static final float CHAMPION_CHANCE_RARE     = 0.18f;
private static final float CHAMPION_CHANCE_EPIC     = 0.18f;
```

Do **not** touch `BOSS_BONUS_CHANCE`, `rollBoss`, the `LEGENDARY CHAMPION` warning branch, or the class Javadoc percentages — update the percentages in the Javadoc at the top of the file from `35/40/45%` to `18%` across the board so the comment matches.

**Step 2: Compile**

Run: `./gradlew compileJava --quiet`
Expected: success.

**Step 3: Commit**

Run:
```bash
git add src/main/java/com/chonbosmods/loot/mob/Nat20MobDropCount.java
git commit -m "feat(loot): flatten champion drop chance to 18% across difficulties"
```

---

## Task 4: Remove Common-bonus-reroll loop

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/mob/Nat20MobLootListener.java:86-152`

**Step 1: Rewrite `generateDrops`**

Replace the current method (including the method-level Javadoc, the `REROLL_CAP_MULTIPLIER` constant, the while-loop, the `rerollCommons`/`bonusReroll` logic, the "Bonus drop" log, and the "Hit reroll cap" warning) with this single cleaner body:

```java
/**
 * Generate exactly {@code dropCount} drops. Each slot samples {@link Nat20MobLootPool}
 * once; whatever rarity the pipeline rolls is the drop. Null/empty picks and pipeline
 * failures skip the slot (no re-roll).
 */
private List<GeneratedDrop> generateDrops(Nat20MobLootPool pool, int dropCount,
                                           @Nullable DifficultyTier difficulty,
                                           int ilvl, Random rng) {
    Nat20LootPipeline pipeline = lootSystem.getPipeline();
    List<GeneratedDrop> results = new ArrayList<>(dropCount);

    int[] gate = Nat20LootPipeline.rarityGateForIlvl(ilvl);
    int effectiveMin = gate[0];
    int effectiveMax = gate[1];

    for (int slot = 1; slot <= dropCount; slot++) {
        Nat20MobLootPool.PickResult pick = pool.pick(rng);
        if (pick == null) {
            LOGGER.atWarning().log("Pool pick returned null at slot %d/%d; skipping", slot, dropCount);
            continue;
        }
        String itemId = pick.itemId();
        String categoryKey = Nat20ItemTierResolver.inferCategory(itemId);
        if (categoryKey == null) continue;
        String baseName = resolveDisplayName(itemId);

        Nat20LootData data = pipeline.generate(
                itemId, baseName, categoryKey,
                effectiveMin, effectiveMax,
                difficulty,
                rng, ilvl);

        if (data == null) {
            LOGGER.atWarning().log("Pipeline returned null (itemId=%s ilvl=%d source=%s); skipping slot %d",
                    itemId, ilvl, pick.source(), slot);
            continue;
        }

        results.add(new GeneratedDrop(itemId, data));
        LOGGER.atInfo().log("Drop %d/%d: %s [%s] from %s (source=%s)",
                slot, dropCount, data.getGeneratedName(), data.getRarity(), itemId, pick.source());
    }

    return results;
}
```

Notes on what changed vs. the old version:
- Loop is a plain `for` over `dropCount` — no `iterations`/`maxIterations`/`satisfied` bookkeeping.
- `REROLL_CAP_MULTIPLIER` constant is removed entirely.
- `rerollCommons` and `bonusReroll` booleans are removed; Common is just a normal rarity outcome.
- Two log branches collapse into one `"Drop N/M: ..."` line.
- The `pipeline.generate(...)` call **adds a `difficulty` arg before `rng`**. That overload does not exist yet — Task 5 adds it. **This task will not compile until Task 5 is done**; commit Task 4 only after Task 5 compiles (see ordering note in Step 3).

**Step 2: Delete the stale `REROLL_CAP_MULTIPLIER` constant**

Immediately above `generateDrops`, delete this declaration (it will be unused after the rewrite):
```java
private static final int REROLL_CAP_MULTIPLIER = 5;
```

**Step 3: Ordering note — defer the compile/commit**

This task is written as a *text edit only*. Do **not** attempt `./gradlew compileJava` or commit yet — the new `pipeline.generate(..., difficulty, ...)` signature doesn't exist on `Nat20LootPipeline`. Move directly to Task 5 to add it, then compile + commit both tasks together in Task 5's Step 6.

---

## Task 5: Difficulty-aware rarity selection

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/registry/Nat20RarityRegistry.java` (add new method)
- Modify: `src/main/java/com/chonbosmods/loot/Nat20LootPipeline.java` (add new overload of `generate` that threads `DifficultyTier`)

**Step 1: Add `selectRandomForDifficulty` to `Nat20RarityRegistry`**

Insert this new method immediately after the existing `selectRandom(Random, int, int)` method (after line 156). Keep existing methods unchanged — chest/reward loot still calls the unbiased variant.

```java
/**
 * Difficulty-biased rarity selection for Nat20 mob drops.
 *
 * <p>Starts from the default per-rarity base weights (filtered to the
 * [minTier, maxTier] window), then applies two layered biases keyed off
 * {@link DifficultyTier}:
 * <ul>
 *   <li>difficulty ≥ RARE: multiply Uncommon / Rare / Epic / Legendary
 *       weights by 1.5×.</li>
 *   <li>difficulty ≥ EPIC: zero Common's weight and redistribute its
 *       original 300-point share into Uncommon (+100), Rare (+150),
 *       Epic (+40), Legendary (+10).</li>
 * </ul>
 *
 * <p>Unknown/null difficulty (chest loot, quest rewards, UNCOMMON mobs)
 * behaves identically to {@link #selectRandom(Random, int, int)}.
 *
 * <p>See {@code docs/plans/2026-04-21-mob-loot-tuning-design.md} §4 for
 * the probability tables this reproduces.
 */
public Nat20RarityDef selectRandomForDifficulty(Random random, int minTier, int maxTier,
                                                 @Nullable DifficultyTier difficulty) {
    if (difficulty == null || difficulty == DifficultyTier.UNCOMMON) {
        return selectRandom(random, minTier, maxTier);
    }

    boolean multiplyHighTiers = true; // both RARE and EPIC apply the 1.5× bias
    boolean zeroCommon = difficulty == DifficultyTier.EPIC
            || difficulty == DifficultyTier.LEGENDARY;

    List<Nat20RarityDef> pool = new ArrayList<>();
    List<Double> weights = new ArrayList<>();
    double totalWeight = 0.0;

    for (var def : raritiesById.values()) {
        if (def.qualityValue() < minTier || def.qualityValue() > maxTier) continue;
        double w = def.baseWeight();
        boolean isCommon = def.qualityValue() == 1;
        if (isCommon && zeroCommon) {
            w = 0.0;
        } else if (!isCommon && multiplyHighTiers) {
            w *= 1.5;
        }
        // EPIC/LEGENDARY: redistribute Common's original 300 weight upward.
        if (zeroCommon) {
            switch (def.qualityValue()) {
                case 2 -> w += 100.0; // Uncommon
                case 3 -> w += 150.0; // Rare
                case 4 -> w += 40.0;  // Epic
                case 5 -> w += 10.0;  // Legendary
                default -> {}
            }
        }
        if (w <= 0.0) continue;
        pool.add(def);
        weights.add(w);
        totalWeight += w;
    }

    if (pool.isEmpty() || totalWeight <= 0.0) {
        LOGGER.atWarning().log("selectRandomForDifficulty: empty pool for tierRange=[%d,%d] difficulty=%s, falling back",
                minTier, maxTier, difficulty);
        return selectRandom(random, minTier, maxTier);
    }

    double roll = random.nextDouble() * totalWeight;
    double cumulative = 0.0;
    for (int i = 0; i < pool.size(); i++) {
        cumulative += weights.get(i);
        if (roll < cumulative) return pool.get(i);
    }
    return pool.getLast();
}
```

Add the required imports at the top of the file:
```java
import com.chonbosmods.progression.DifficultyTier;
```
(`java.util.List` and `java.util.ArrayList` are already imported via the star-import `java.util.*`. `javax.annotation.Nullable` is already imported.)

**Step 2: Add the new `generate` overload to `Nat20LootPipeline`**

Insert this overload immediately after the existing `generate(String, String, String, int, int, Random, int)` method (after line 214). It is a near-copy of the existing tier-clamped overload, with the rarity pick routed through `selectRandomForDifficulty` and the existing overload kept untouched.

```java
/**
 * Generate loot data with tier clamping AND difficulty-biased rarity selection.
 * Used by {@link com.chonbosmods.loot.mob.Nat20MobLootListener} so RARE/EPIC
 * champion drops skew toward higher rarities. Passing {@code difficulty == null}
 * is equivalent to the non-difficulty overload.
 *
 * <p>See {@code docs/plans/2026-04-21-mob-loot-tuning-design.md} §4.
 */
public Nat20LootData generate(String itemId, String baseName, String categoryKey,
                               int minRarityTier, int maxRarityTier,
                               @Nullable DifficultyTier difficulty,
                               Random random, int ilvl) {
    Nat20RarityDef rarity = rarityRegistry.selectRandomForDifficulty(
            random, minRarityTier, maxRarityTier, difficulty);
    if (rarity == null) {
        LOGGER.atWarning().log("No rarity definitions loaded, cannot generate loot for %s", itemId);
        return null;
    }

    double lootLevel = random.nextDouble();
    List<RolledAffix> rolledAffixes = rollAffixes(rarity, categoryKey, random);
    int sockets = rollSockets(rarity, random);

    String prefixSource = null;
    String suffixSource = null;
    StringBuilder nameBuilder = new StringBuilder();

    for (var affix : rolledAffixes) {
        Nat20AffixDef def = affixRegistry.get(affix.id());
        if (def != null && def.namePosition() == NamePosition.PREFIX && prefixSource == null) {
            prefixSource = affix.id();
            nameBuilder.append(getDisplayName(def, rarity.id(), random)).append(" ");
            break;
        }
    }

    nameBuilder.append(baseName);

    for (var affix : rolledAffixes) {
        Nat20AffixDef def = affixRegistry.get(affix.id());
        if (def != null && def.namePosition() == NamePosition.SUFFIX && suffixSource == null) {
            suffixSource = affix.id();
            nameBuilder.append(" of ").append(getDisplayName(def, rarity.id(), random));
            break;
        }
    }

    String generatedName = nameBuilder.toString();
    String variantItemId = resolveVariantId(itemId, rarity.id());
    String description = buildDescription(rolledAffixes, rarity.id(), sockets, rarity);

    Nat20LootData data = new Nat20LootData();
    data.setVersion(Nat20LootData.CURRENT_VERSION);
    data.setRarity(rarity.id());
    data.setLootLevel(lootLevel);
    data.setItemLevel(ilvl);
    data.setAffixes(rolledAffixes);
    data.setSockets(sockets);
    data.setGems(new ArrayList<>());
    data.setGeneratedName(generatedName);
    data.setNamePrefixSource(prefixSource);
    data.setNameSuffixSource(suffixSource);
    data.setDescription(description);
    data.setVariantItemId(variantItemId);

    String qualityId = rarity.id();
    if (itemRegistry != null) {
        String uniqueId = itemRegistry.registerItem(itemId, qualityId, data);
        data.setUniqueItemId(uniqueId);
    }

    LOGGER.atFine().log("Generated loot: %s [%s] variant=%s with %d affixes, %d sockets (lootLevel=%.2f, ilvl=%d, tierRange=[%d,%d], difficulty=%s)",
        generatedName, rarity.id(), variantItemId, rolledAffixes.size(), sockets, lootLevel, ilvl,
        minRarityTier, maxRarityTier, difficulty);

    return data;
}
```

Add the required imports at the top of the file:
```java
import com.chonbosmods.progression.DifficultyTier;
import javax.annotation.Nullable;
```

**Step 3: Verify `Nat20MobLootListener` imports resolve**

The call site in `Nat20MobLootListener.generateDrops` (rewritten in Task 4) uses `DifficultyTier` — verify the existing import at the top of the file is `import com.chonbosmods.progression.DifficultyTier;` (it already imports this — it's used elsewhere in the file). No new import needed.

**Step 4: Keep the legacy overload call untouched**

The old `generate(String, String, String, int, int, Random, int)` overload (the one without `DifficultyTier`) must remain in place. It's the path for chest loot + quest rewards. Do not modify it, do not delete it. Its callers (outside the mob path) continue to get the unbiased rarity pick.

**Step 5: Compile (now that all signatures exist, including the rewrite from Task 4)**

Run: `./gradlew compileJava --quiet`
Expected: success, no unresolved symbols. If a symbol-not-found error mentions `selectRandomForDifficulty`, re-check Step 1's insertion point. If it mentions `generate` with the wrong arity, re-check that the overload in Step 2 is in the file verbatim.

**Step 6: Commit Tasks 4 + 5 together**

Run:
```bash
git add \
  src/main/java/com/chonbosmods/loot/mob/Nat20MobLootListener.java \
  src/main/java/com/chonbosmods/loot/registry/Nat20RarityRegistry.java \
  src/main/java/com/chonbosmods/loot/Nat20LootPipeline.java
git commit -m "feat(loot): difficulty-biased rarity + drop one item per slot"
```

The combined commit is intentional: Task 4's rewrite and Task 5's overload are interdependent at the type level. Splitting them would produce one non-compiling intermediate commit.

---

## Task 6: Smoke test (manual, outside the worktree)

**Context:** `./gradlew devServer` cannot run from inside a git worktree (per `CLAUDE.md`). After Tasks 0–5 land and compile clean, merge `feat/mob-loot-tuning` into `main` (or fast-forward it), then run the server from the main working tree.

**Step 1: Merge & run devServer**

From the main working tree (`/home/keroppi/Development/Hytale/Natural20`):
```bash
git checkout main
git merge --ff-only feat/mob-loot-tuning
./gradlew devServer
```

**Step 2: Common always affixed**

- `/nat20 spawntier Champion Uncommon` — kill a handful of UNCOMMON champions.
- Pick up several Common-rarity drops.
- Open each tooltip: every Common must show exactly 1 affix line. Zero unaffixed Commons.

**Step 3: Rarity curve**

- Kill ≥100 UNCOMMON champions (or any Nat20 mobs whose drops route through the default pool).
- Over the sample, Common share should land near ~17%, Uncommon near ~57%. Rare/Epic/Legendary tails are the existing numbers.

**Step 4: Champion drop rate**

- Kill ≥50 champions at UNCOMMON, RARE, EPIC each.
- Drop rate (champions that dropped *anything*) should converge near 18% at each difficulty. Observable via the existing `[Nat20|MobLootListener]` log lines: count "Drop 1/1" emissions vs. total kills.

**Step 5: EPIC champion rarity bias**

- Kill ≥50 EPIC champions that actually dropped loot.
- Expect zero Commons, a visible majority of Uncommons, a visibly fatter Rare tail than the UNCOMMON-champion sample, and a handful of Epics/Legendaries.

**Step 6: Native bias**

- Kill a mob whose native `ItemDropList` contains a distinctive gear piece (Trork / Trog are easy picks).
- Look at the `source=native|global` field in the `[Nat20|MobLootListener] Drop N/M: ...` log lines.
- Over ≥50 drops, `native` should appear ~12% of the time.

**Step 7: Boss regression check**

- Kill a boss at each of UNCOMMON / RARE / EPIC / LEGENDARY.
- Drop counts should match the existing `rollBoss` tables (`1+1`, `1+2`, `1+3`, `2+3` guaranteed+bonus, each bonus roll still at 30%). No regressions.

**If anything fails:** do *not* patch on main; return to the worktree on `feat/mob-loot-tuning`, fix, commit, then re-merge.

---

## Out of scope (reminder)

- Hytale-native `ItemDropList` interception — explicitly dropped during design.
- Unit tests — design says smoke only.
- Chest loot / quest reward path — the unbiased `generate(..., Random, int)` overload stays untouched; only the mob path routes through the new difficulty-biased variant.

---

## Verification checklist before requesting review

- [ ] Task 0–5 all committed on `feat/mob-loot-tuning`.
- [ ] `./gradlew compileJava --quiet` passes with no errors (warnings about `getPlayerRef()` deprecation are pre-existing and unrelated).
- [ ] `git log --oneline main..feat/mob-loot-tuning` shows 5 commits (design + 4 feature commits): design → rarity JSON → native bias → champion rate → difficulty-biased rarity + one-drop-per-slot.
- [ ] No edits outside the design-listed files.
- [ ] Legacy `generate(String, String, String, int, int, Random, int)` overload still exists and is still unmodified.
