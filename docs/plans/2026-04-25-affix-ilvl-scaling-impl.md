# Affix ilvl Scaling + Stat-Score Tightening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Compress low-ilvl affix values to 30% of endgame and grow linearly to today's value at ilvl 45 (endgame untouched); cap stat-score affixes at +2 (Rare/Epic/Legendary only) and exempt them from ilvl scaling; remove the `rarityGateForIlvl` clamp so any ilvl can roll any rarity.

**Architecture:** Replace `Nat20XpMath.ilvlScale(ilvl, qv)` body with `endgameScale(qv) × spread(ilvl)` where `endgameScale` matches today's ilvl-45 value per rarity and `spread` is a new linear 0.30→1.0 curve. Add `IlvlScalable` boolean field to `Nat20AffixDef` (defaults `true`); the six `score_*.json` files set it `false` and reshape `ValuesPerRarity` to omit Common/Uncommon. `Nat20ModifierManager` branches at the interpolate call sites based on the new field. `rarityGateForIlvl` always returns `{1, 5}`.

**Tech Stack:** Java 25, JUnit 5, Gson (already wired). Worktree at `.worktrees/feat-affix-ilvl-scaling/`.

**Reference design doc:** `docs/plans/2026-04-25-affix-ilvl-scaling-and-stat-score-tightening-design.md`

---

## Conventions

- Branch: `feat/affix-ilvl-scaling` (already created from main).
- All commands assume cwd = worktree root (`.worktrees/feat-affix-ilvl-scaling/`).
- Each task ends with one commit. **No `Co-Authored-By` lines, no `--no-verify`, no push.** Use `:` instead of `—` (em dash) per `~/.claude/CLAUDE.md`.
- Test class style: JUnit 5, package-private `class FooTest`, seeded `Random(42L)` for distribution checks. Reference: `src/test/java/com/chonbosmods/dialogue/WeightedTierDrawTest.java`.
- Devserver smoke testing happens AFTER merge (devserver cannot run from a worktree per memory `devserver-worktree-limitation.md`).

---

## Task 1: Replace `Nat20XpMath.ilvlScale` formula (TDD)

**Files:**
- Modify: `src/main/java/com/chonbosmods/progression/Nat20XpMath.java:105-108`
- Create: `src/test/java/com/chonbosmods/progression/Nat20XpMathTest.java` (or modify if it already exists)

**Step 1: Inspect existing test file (if any)**

Run: `ls src/test/java/com/chonbosmods/progression/`
If `Nat20XpMathTest.java` exists, append; otherwise create.

**Step 2: Write the failing tests**

```java
package com.chonbosmods.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20XpMathTest {

    private static final double EPS = 1e-9;

    @Test
    void ilvlScaleFloorAtIlvl1IsThirtyPercentOfEndgameForCommon() {
        // qv=1 (Common), ilvl=1 → spread=0.30 × endgameScale=2.10 = 0.630
        assertEquals(0.630, Nat20XpMath.ilvlScale(1, 1), EPS);
    }

    @Test
    void ilvlScaleFloorAtIlvl1IsThirtyPercentOfEndgameForLegendary() {
        // qv=5 (Legendary), ilvl=1 → spread=0.30 × endgameScale=2.628 = 0.7884
        assertEquals(0.7884, Nat20XpMath.ilvlScale(1, 5), EPS);
    }

    @Test
    void ilvlScaleAtIlvl45MatchesTodaysValueForCommon() {
        // qv=1, ilvl=45 → spread=1.0 × endgameScale=2.10 = 2.10 (today's value preserved)
        assertEquals(2.100, Nat20XpMath.ilvlScale(45, 1), EPS);
    }

    @Test
    void ilvlScaleAtIlvl45MatchesTodaysValueForLegendary() {
        // qv=5, ilvl=45 → spread=1.0 × endgameScale=2.628 (today's value preserved)
        assertEquals(2.628, Nat20XpMath.ilvlScale(45, 5), EPS);
    }

    @Test
    void ilvlScaleMidpointIsLinearBetweenFloorAndCeiling() {
        // qv=1, ilvl=23 → spread = 0.30 + 0.70 × 22/44 = 0.65
        // scale = 0.65 × 2.10 = 1.365
        assertEquals(1.365, Nat20XpMath.ilvlScale(23, 1), EPS);
    }

    @Test
    void ilvlScaleHigherRarityScalesProportionallyHigher() {
        // At ilvl 1, Legendary should be exactly endgameScale_legendary / endgameScale_common
        // higher than Common: 0.7884 / 0.630 = 2.628 / 2.10 = 1.252
        double common = Nat20XpMath.ilvlScale(1, 1);
        double legendary = Nat20XpMath.ilvlScale(1, 5);
        assertEquals(2.628 / 2.100, legendary / common, EPS);
    }
}
```

**Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests Nat20XpMathTest`
Expected: most assertions fail with the current formula. (e.g., `ilvlScale(1, 1)` = 1.0 today, expected 0.63 → FAIL).

**Step 4: Replace `Nat20XpMath.ilvlScale` body**

Locate the current method at `src/main/java/com/chonbosmods/progression/Nat20XpMath.java:105-108`:

```java
public static double ilvlScale(int ilvl, int qualityValue) {
    double perIlvl = 0.025 + (qualityValue - 1) * 0.003;
    return 1.0 + (ilvl - 1) * perIlvl;
}
```

Replace with:

```java
/**
 * ilvl × rarity → affix-value scale multiplier.
 * Two-component curve: {@code endgameScale(qv) × spread(ilvl)}.
 * <ul>
 *   <li>{@code endgameScale(qv)}: constant per rarity, equals today's ilvl-45 value
 *       (Common=2.10 ... Legendary=2.628).</li>
 *   <li>{@code spread(ilvl)}: linear from 0.30 at ilvl 1 to 1.00 at ilvl 45.</li>
 * </ul>
 * Endgame ceiling preserved: {@code ilvlScale(45, qv)} returns the same value
 * as the prior linear-from-1.0 formula. Low-ilvl values are dampened to 30% of endgame.
 *
 * @param ilvl item level (1..45)
 * @param qualityValue rarity tier (Common=1 .. Legendary=5)
 */
public static double ilvlScale(int ilvl, int qualityValue) {
    return endgameScale(qualityValue) * spread(ilvl);
}

private static double endgameScale(int qualityValue) {
    return 1.0 + 44 * (0.025 + (qualityValue - 1) * 0.003);
}

private static double spread(int ilvl) {
    return 0.30 + 0.70 * (ilvl - 1) / 44.0;
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests Nat20XpMathTest`
Expected: all 6 tests pass.

**Step 6: Run full suite to confirm no regressions**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. (Existing affix / loot tests may now produce different numerical values but should not assert specific scale numbers — verify any failures are real signal vs. baseline drift.)

**Step 7: Commit**

```bash
git add src/main/java/com/chonbosmods/progression/Nat20XpMath.java \
        src/test/java/com/chonbosmods/progression/Nat20XpMathTest.java
git commit -m "feat(progression): replace ilvlScale with endgameScale × spread (low-ilvl floor 0.30, endgame unchanged)"
```

---

## Task 2: Add `IlvlScalable` field to `Nat20AffixDef` (TDD)

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/def/Nat20AffixDef.java`
- Modify: `src/main/java/com/chonbosmods/loot/registry/Nat20AffixRegistry.java`
- Create or modify: `src/test/java/com/chonbosmods/loot/registry/Nat20AffixRegistryTest.java`

**Step 1: Inspect current `Nat20AffixDef` shape**

Run: `cat src/main/java/com/chonbosmods/loot/def/Nat20AffixDef.java`
The record will need a new `boolean ilvlScalable` component. Note its current field order and which call sites construct it.

**Step 2: Write failing test**

In `src/test/java/com/chonbosmods/loot/registry/Nat20AffixRegistryTest.java`, add (or create file):

```java
package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.def.AffixType;
import com.chonbosmods.loot.def.Nat20AffixDef;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Nat20AffixRegistryTest {

    private static InputStream inputOf(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsedAffixIsIlvlScalableByDefault() {
        String json = """
            { "Id": "nat20:test_default", "Frequency": 1, "Type": "EFFECT",
              "DisplayName": "x", "NamePosition": "PREFIX", "Categories": ["melee_weapon"],
              "TargetStat": "Test", "ModifierType": "ADDITIVE",
              "ValuesPerRarity": { "Common": { "Min": 0.1, "Max": 0.2 } } }
            """;
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadFrom(inputOf(json));
        Nat20AffixDef def = reg.getById("nat20:test_default");
        assertNotNull(def);
        assertTrue(def.ilvlScalable(), "absent IlvlScalable field should default to true");
    }

    @Test
    void parsedAffixHonorsExplicitIlvlScalableFalse() {
        String json = """
            { "Id": "nat20:test_static", "Frequency": 1, "Type": "STAT",
              "DisplayName": "x", "NamePosition": "PREFIX", "Categories": ["melee_weapon"],
              "TargetStat": "Test", "ModifierType": "ADDITIVE",
              "IlvlScalable": false,
              "ValuesPerRarity": { "Rare": { "Min": 1.0, "Max": 1.0 } } }
            """;
        Nat20AffixRegistry reg = new Nat20AffixRegistry();
        reg.loadFrom(inputOf(json));
        Nat20AffixDef def = reg.getById("nat20:test_static");
        assertNotNull(def);
        assertFalse(def.ilvlScalable(), "explicit IlvlScalable: false must round-trip through parser");
    }
}
```

If `Nat20AffixRegistry` has no `loadFrom(InputStream)` or no `getById(String)`, you'll need to use the existing public surface. Inspect the registry first and adapt the test to its shape (e.g. it may load from a fixed resource path; in that case stage a temp resource and pass via the existing API).

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests Nat20AffixRegistryTest`
Expected: compile error (`def.ilvlScalable()` doesn't exist).

**Step 4: Add `ilvlScalable` to `Nat20AffixDef` record**

Open `src/main/java/com/chonbosmods/loot/def/Nat20AffixDef.java`. The existing record is approximately:

```java
public record Nat20AffixDef(
    String id, int frequency, AffixType type, String displayName,
    NamePosition namePosition, List<String> categories,
    StatScaling statScaling, String targetStat, ModifierType modifierType,
    Map<String, AffixValueRange> valuesPerRarity, /* existing fields */
    Set<String> exclusiveWith, boolean mobEligible /* etc */
) { ... }
```

(Read the actual record body before editing — fields may differ.) Add `boolean ilvlScalable` as a new component, placed adjacent to `valuesPerRarity` for readability:

```java
public record Nat20AffixDef(
    String id, int frequency, AffixType type, String displayName,
    NamePosition namePosition, List<String> categories,
    StatScaling statScaling, String targetStat, ModifierType modifierType,
    Map<String, AffixValueRange> valuesPerRarity,
    boolean ilvlScalable,
    /* existing trailing fields preserved */
    ...
) { ... }
```

**Step 5: Update parser in `Nat20AffixRegistry`**

Find the existing constructor call site (around line 152 per the prior grep). Locate where `valuesPerRarity` is built and the record is constructed:

```java
boolean ilvlScalable = obj.has("IlvlScalable") ? obj.get("IlvlScalable").getAsBoolean() : true;
```

Pass it into the `Nat20AffixDef(...)` constructor at the right position.

**Step 6: Update any other `new Nat20AffixDef(...)` call sites**

Run: `grep -rn 'new Nat20AffixDef(' src/`
For every call site (likely just the registry), add `true` as the new positional arg matching the new component position. (Default-true preserves existing behavior.)

**Step 7: Run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. The two new `Nat20AffixRegistryTest` cases pass; all other tests unchanged.

**Step 8: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/def/Nat20AffixDef.java \
        src/main/java/com/chonbosmods/loot/registry/Nat20AffixRegistry.java \
        src/test/java/com/chonbosmods/loot/registry/Nat20AffixRegistryTest.java
git commit -m "feat(loot): add IlvlScalable field to Nat20AffixDef (default true)"
```

---

## Task 3: Add branching `Nat20AffixScaling.interpolate` overload (TDD)

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/Nat20AffixScaling.java`
- Create: `src/test/java/com/chonbosmods/loot/Nat20AffixScalingTest.java`

**Step 1: Write the failing test**

```java
package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20AffixScalingTest {

    private static final double EPS = 1e-9;

    /**
     * Skinny fake: a Nat20RarityRegistry that always returns a Common rarity (qv=1).
     * Replace with the real construction pattern if Nat20RarityRegistry isn't easily fakeable.
     */
    private static Nat20RarityRegistry fakeCommonRegistry() {
        Nat20RarityRegistry reg = new Nat20RarityRegistry();
        // Pseudocode — actual setup depends on registry shape. If the registry has
        // a public mutator, use it; otherwise consider extracting a smaller test seam.
        return reg;
    }

    @Test
    void interpolateScalableTrueAppliesIlvlScale() {
        // range [1.0, 1.0], lootLevel 0.5, ilvl 1, qv 1
        // ilvlScalable=true → uses range.interpolate(0.5, 1, 1) = 1.0 × ilvlScale(1,1) = 0.630
        AffixValueRange range = new AffixValueRange(1.0, 1.0);
        Nat20LootData data = new Nat20LootData();
        data.setItemLevel(1);
        data.setRarity("Common");
        Nat20RarityRegistry reg = fakeCommonRegistry();

        double v = Nat20AffixScaling.interpolate(range, 0.5, data, reg, true);
        assertEquals(0.630, v, EPS);
    }

    @Test
    void interpolateScalableFalseSkipsIlvlScale() {
        AffixValueRange range = new AffixValueRange(1.0, 1.0);
        Nat20LootData data = new Nat20LootData();
        data.setItemLevel(1);
        data.setRarity("Common");
        Nat20RarityRegistry reg = fakeCommonRegistry();

        double v = Nat20AffixScaling.interpolate(range, 0.5, data, reg, false);
        // ilvlScalable=false → falls back to range.interpolate(0.5) = 1.0 (no scaling)
        assertEquals(1.0, v, EPS);
    }

    @Test
    void interpolateScalableFalseStaysIntegerStableAcrossIlvls() {
        AffixValueRange range = new AffixValueRange(2.0, 2.0); // Legendary stat score
        Nat20LootData ilvl1 = new Nat20LootData(); ilvl1.setItemLevel(1); ilvl1.setRarity("Legendary");
        Nat20LootData ilvl45 = new Nat20LootData(); ilvl45.setItemLevel(45); ilvl45.setRarity("Legendary");
        Nat20RarityRegistry reg = fakeCommonRegistry(); // qv lookup is bypassed when ilvlScalable=false

        double v1 = Nat20AffixScaling.interpolate(range, 0.5, ilvl1, reg, false);
        double v45 = Nat20AffixScaling.interpolate(range, 0.5, ilvl45, reg, false);
        assertEquals(2.0, v1, EPS);
        assertEquals(2.0, v45, EPS);
    }
}
```

**NOTE on test seams:** if `Nat20RarityRegistry` is awkward to fake, two alternatives:
- Add a package-private constructor or factory to inject a minimal rarity map.
- Move the routing logic into a smaller pure helper (e.g. `Nat20AffixScaling.applyScale(double base, boolean scalable, int ilvl, int qv)`) and unit-test that, with the public 5-arg `interpolate` calling the helper.

The 5-arg public method is the contract; pick whichever route is cheapest while preserving it.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests Nat20AffixScalingTest`
Expected: compile error or method-not-found on the 5-arg `interpolate`.

**Step 3: Add the 5-arg overload to `Nat20AffixScaling`**

Open `src/main/java/com/chonbosmods/loot/Nat20AffixScaling.java`. Add:

```java
/**
 * Branching variant: skip ilvl scaling when {@code ilvlScalable} is false.
 * Used by stat-score affixes where integer stability matters more than scaling.
 */
public static double interpolate(AffixValueRange range, double lootLevel,
                                  Nat20LootData lootData, Nat20RarityRegistry rarityRegistry,
                                  boolean ilvlScalable) {
    if (!ilvlScalable) return range.interpolate(lootLevel);
    int qv = qualityValueOf(lootData, rarityRegistry);
    return range.interpolate(lootLevel, lootData.getItemLevel(), qv);
}
```

The existing 4-arg `interpolate` stays for any callers that don't yet know about `ilvlScalable`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests Nat20AffixScalingTest`
Expected: all 3 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/Nat20AffixScaling.java \
        src/test/java/com/chonbosmods/loot/Nat20AffixScalingTest.java
git commit -m "feat(loot): add ilvl-scaling branch on Nat20AffixScaling.interpolate"
```

---

## Task 4: Wire `Nat20ModifierManager` to use the new branching `interpolate`

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/Nat20ModifierManager.java:147` and `:169`

**Step 1: Read the current call sites**

Run: `grep -n 'Nat20AffixScaling.interpolate' src/main/java/com/chonbosmods/loot/Nat20ModifierManager.java`
Expected: two hits at lines 147 and 169 (per the prior grep). Read 5–10 lines of context around each to identify the local variables in scope (`affixDef`, `range`, `rolledAffix`, `lootData`, `rarityRegistry`).

**Step 2: Replace each call**

For each of the two call sites, change:

```java
double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, rarityRegistry);
```

to:

```java
double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, rarityRegistry, affixDef.ilvlScalable());
```

Verify `affixDef` is the variable name in scope; if it's named differently (e.g. `def`), use that.

**Step 3: Compile + run full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. No new failures.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/Nat20ModifierManager.java
git commit -m "feat(loot): route ModifierManager interpolate calls through the ilvlScalable branch"
```

---

## Task 5: Reshape the six stat-score JSONs

**Files:**
- Modify: `src/main/resources/loot/affixes/stat/score_str.json`
- Modify: `src/main/resources/loot/affixes/stat/score_dex.json`
- Modify: `src/main/resources/loot/affixes/stat/score_con.json`
- Modify: `src/main/resources/loot/affixes/stat/score_int.json`
- Modify: `src/main/resources/loot/affixes/stat/score_wis.json`
- Modify: `src/main/resources/loot/affixes/stat/score_cha.json`

**Step 1: Edit each file identically**

For each `score_*.json`:

a. Replace the `ValuesPerRarity` block with:

```json
"ValuesPerRarity": {
  "Rare":      { "Min": 1.0, "Max": 1.0 },
  "Epic":      { "Min": 1.0, "Max": 1.0 },
  "Legendary": { "Min": 2.0, "Max": 2.0 }
}
```

b. Add the field `"IlvlScalable": false,` adjacent to `"ModifierType"`. Keep alphabetical-ish order or co-locate with ModifierType for readability. Final-shape sample:

```json
{
  "Id": "nat20:score_str",
  "Frequency": 3,
  "Type": "STAT",
  "DisplayName": "server.nat20.affix.score_str",
  "NamePosition": "SUFFIX",
  "Categories": ["melee_weapon", "ranged_weapon", "armor", "tool"],
  "StatScaling": null,
  "TargetStat": "Score_STR",
  "ModifierType": "ADDITIVE",
  "IlvlScalable": false,
  "ValuesPerRarity": {
    "Rare":      { "Min": 1.0, "Max": 1.0 },
    "Epic":      { "Min": 1.0, "Max": 1.0 },
    "Legendary": { "Min": 2.0, "Max": 2.0 }
  },
  "Description": "Increases Strength score."
}
```

**Step 2: Verify all six parse and load**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. The affix-load INFO line at boot reports the same affix count as before (53). The six stat-score affixes now have `valuesPerRarity` keyed only on Rare/Epic/Legendary.

**Step 3: Spot-check via a focused test**

Add to `Nat20AffixRegistryTest.java`:

```java
@Test
void scoreAffixesExcludedFromCommonAndUncommonPools() {
    // Loads the real bundled affix resources via the registry's normal init path.
    // Asserts the score_* affixes don't appear in Common/Uncommon pools.
    Nat20AffixRegistry reg = new Nat20AffixRegistry();
    reg.loadFromResources();  // adapt to whatever the real init API is

    for (String stat : new String[]{"str", "dex", "con", "int", "wis", "cha"}) {
        String id = "nat20:score_" + stat;
        for (String rarity : new String[]{"Common", "Uncommon"}) {
            for (String cat : new String[]{"melee_weapon", "ranged_weapon", "armor", "tool"}) {
                List<Nat20AffixDef> pool = reg.getPool(AffixType.STAT, cat, rarity);
                assertFalse(pool.stream().anyMatch(d -> d.id().equals(id)),
                    id + " should not appear in pool (rarity=" + rarity + ", cat=" + cat + ")");
            }
        }
    }
}
```

Adapt `loadFromResources()` to the registry's actual init pattern (the production code probably loads from `src/main/resources/loot/affixes/**`).

**Step 4: Run + verify**

Run: `./gradlew test --tests Nat20AffixRegistryTest`
Expected: all tests pass, including the new exclusion test.

**Step 5: Commit**

```bash
git add src/main/resources/loot/affixes/stat/score_str.json \
        src/main/resources/loot/affixes/stat/score_dex.json \
        src/main/resources/loot/affixes/stat/score_con.json \
        src/main/resources/loot/affixes/stat/score_int.json \
        src/main/resources/loot/affixes/stat/score_wis.json \
        src/main/resources/loot/affixes/stat/score_cha.json \
        src/test/java/com/chonbosmods/loot/registry/Nat20AffixRegistryTest.java
git commit -m "feat(loot): tighten stat-score affixes to +1/+1/+2 (Rare+/Legendary), opt out of ilvl scaling"
```

---

## Task 6: Widen `rarityGateForIlvl` to always return `{1, 5}` (TDD)

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/Nat20LootPipeline.java:358-363`
- Create or modify: `src/test/java/com/chonbosmods/loot/Nat20LootPipelineTest.java`

**Step 1: Write the failing test**

```java
package com.chonbosmods.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Nat20LootPipelineTest {

    @Test
    void rarityGateIsFullRangeAtAllIlvls() {
        // Pre-change: ilvl 1 → {1,3}, ilvl 9 → {1,4}, ilvl 16 → {2,5}, ilvl 26 → {1,5}.
        // Post-change: every ilvl returns {1,5}.
        for (int ilvl : new int[]{1, 5, 8, 9, 15, 16, 25, 26, 30, 45}) {
            assertArrayEquals(new int[]{1, 5}, Nat20LootPipeline.rarityGateForIlvl(ilvl),
                "ilvl=" + ilvl + " must return {1, 5} after gate removal");
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests Nat20LootPipelineTest`
Expected: failures at ilvl ≤ 25.

**Step 3: Replace `rarityGateForIlvl` body**

Open `src/main/java/com/chonbosmods/loot/Nat20LootPipeline.java`, locate `rarityGateForIlvl`, replace with:

```java
/**
 * Returns the rarity tier clamp {minQv, maxQv} for the given item level.
 *
 * Post-2026-04-25: always returns {@code {1, 5}}. The prior ilvl-banded rarity gate
 * (1-8 = Common-Rare, etc.) was retired after the {@code ilvlScale} value-curve change
 * lets every ilvl roll every rarity safely. ilvl-driven progression now lives in
 * {@link com.chonbosmods.progression.Nat20XpMath#ilvlScale(int, int)}.
 *
 * The {@code ilvl} parameter is preserved for caller compatibility but is unused.
 *
 * @return {@code int[2] = {1, 5}} unconditionally.
 */
public static int[] rarityGateForIlvl(int ilvl) {
    return new int[]{1, 5};
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests Nat20LootPipelineTest`
Expected: pass.

**Step 5: Run full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. Existing tests should still pass since callers' `int[] gate = ...` reads still work — they just always get `{1, 5}` now.

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/Nat20LootPipeline.java \
        src/test/java/com/chonbosmods/loot/Nat20LootPipelineTest.java
git commit -m "feat(loot): retire rarityGateForIlvl clamp; all ilvls roll all rarities"
```

---

## Task 7: Wiki author handoff doc (final task)

**Files:**
- Create: `docs/wiki/_drafts/2026-04-25-affix-rebalance-handoff.md`

**Step 1: Write the handoff doc**

The doc must contain, in order:

1. **Plain-prose formula explanation.**
2. **Behavior changes summary** (rarity gate gone, stat scores +1/+1/+2, opt-out).
3. **Per-affix old-vs-new tables** for one example per category at ilvl 1 / 22 / 45:
   - Common Rally (offensive % multiplier example)
   - Legendary Crit Damage (offensive % multiplier example)
   - Common Resistance (defense; pick any from `defense-affixes.md`)
   - Legendary HP% (utility; from `hp.json`, still scales)
   - Rare STR score (stat; static)
4. **Spread lookup table** at ilvl 1, 5, 10, 15, 20, 25, 30, 35, 40, 45 → `spread(ilvl)` value.
5. **Pages that need editing.** Concrete file list:
   - `docs/wiki/offensive-affixes.md`
   - `docs/wiki/defense-affixes.md`
   - `docs/wiki/stat-affixes.md`
   - `docs/wiki/utility-affixes.md`
   - `docs/wiki/ability-affixes.md`
   With one bullet per file noting "the rarity tables now represent the endgame ceiling; add a note explaining the spread."
6. **Patch-note blurb** suitable for a one-paragraph public announcement.

Sample skeleton (adapt content):

```markdown
# Affix Rebalance Handoff Doc (2026-04-25)

This is a wiki-author handoff. Use it to update the affix wiki pages with the new
ilvl-scaling and stat-score rules. Not a published page itself.

## What changed

Affix values now scale linearly from 30% of the listed value at ilvl 1 to 100% at
ilvl 45. The Min/Max tables in each affix wiki page are now the **endgame ceiling**,
not the rolled value at low levels. Endgame parity is preserved: an ilvl-45 drop
still rolls exactly today's values.

Stat-score affixes (STR / DEX / CON / INT / WIS / CHA) opt out of scaling and
appear only on Rare+ rarities, capped at +2.

The rarity gate at low ilvl is retired: any ilvl can roll any rarity, including
Legendary. Frequency tables are unchanged, so Legendary at ilvl 1 remains rare.

## Formula

```
scale(ilvl, qv) = endgameScale(qv) × spread(ilvl)
endgameScale(qv) = 1 + 44 × (0.025 + (qv-1) × 0.003)
spread(ilvl) = 0.30 + 0.70 × (ilvl - 1) / 44
```

## Spread lookup

| ilvl | 1 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 |
|---|---|---|---|---|---|---|---|---|---|---|
| spread | 0.300 | 0.364 | 0.443 | 0.523 | 0.602 | 0.682 | 0.762 | 0.841 | 0.921 | 1.000 |

## Worked examples

### Common Rally (declared 0.05–0.08)
| ilvl | Old | New |
|---|---|---|
| 1 | 5.0–8.0% | 3.2–5.0% |
| 22 | 7.6–12.2% | 6.7–10.6% |
| 45 | 10.5–16.8% | 10.5–16.8% |

### Legendary Crit Damage (declared 0.60–1.00)
| ilvl | Old | New |
|---|---|---|
| 1 | 60–100% | 47.3–78.8% |
| 22 | ~95–158% | 100–167% |
| 45 | ~158–263% | 158–263% |

(... add similar tables for Common Resistance, Legendary HP%, Rare STR score)

## Pages to edit

- `docs/wiki/offensive-affixes.md` — every per-rarity table; add formula note.
- `docs/wiki/defense-affixes.md` — same.
- `docs/wiki/stat-affixes.md` — replace stat-score tables entirely with the
  fixed-value scheme (Rare/Epic = +1, Legendary = +2). Note Common/Uncommon excluded.
- `docs/wiki/utility-affixes.md` — add formula note.
- `docs/wiki/ability-affixes.md` — add formula note.

## Patch-note draft

> Affix balance pass: low-ilvl drops now feel proportional to their level. A
> Common Rally on a Bronze sword no longer rolls the same percentages as one on
> a Mithril sword. Endgame values are unchanged; the climb is just more visible
> from the early levels up.
>
> Stat-score affixes (STR/DEX/etc.) are now Rare+ only, capped at +2. They no
> longer appear on Common or Uncommon drops.
>
> Any rarity can drop at any item level: an ilvl-1 mob can occasionally cough up
> a Legendary, and the values will scale appropriately.
```

**Step 2: Verify the doc renders**

Run: `head -40 docs/wiki/_drafts/2026-04-25-affix-rebalance-handoff.md`
Skim for typos and table integrity.

**Step 3: Commit**

```bash
mkdir -p docs/wiki/_drafts
git add docs/wiki/_drafts/2026-04-25-affix-rebalance-handoff.md
git commit -m "docs(loot): wiki author handoff for affix ilvl scaling + stat-score rebalance"
```

---

## Post-implementation: hand off for smoke test

Devserver smoke testing happens AFTER merge to main (devserver cannot run from a worktree per memory `devserver-worktree-limitation.md`). Follow the smoke-test checklist in `docs/plans/2026-04-25-affix-ilvl-scaling-and-stat-score-tightening-design.md` "Smoke Test Checklist" section.

If smoke test passes, save a shipped memory entry summarizing the change (similar pattern to `gear-pool-filter-shipped.md`).

---

## Open questions for the user during implementation

These may surface; capture answers in the design doc as locked decisions:

1. If `Nat20RarityRegistry` is hard to fake in unit tests (Task 3), is it OK to extract a smaller pure-function helper for the routing logic to unit-test?
2. If the existing affix integration tests assert specific scale values that will change, is it OK to update those test assertions to the new numbers (or are the assertions intentional baseline anchors)?
