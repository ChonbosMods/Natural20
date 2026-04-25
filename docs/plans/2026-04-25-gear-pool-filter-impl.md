# Gear-Pool Filter + Category Weights Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace `Nat20ItemTierResolver`'s in-Java material table with a JSON-driven gear filter (`gear_filter.json`) supporting blocklist + allowlist + tier-tokens + per-item overrides; bucket the global gear pool by category and pick via {melee:30, armor:30, ranged:20, tool:20} weights; tune `NATIVE_LIST_BIAS` from 0.12 to 0.05; close the 60-item ilvl-gating leak; apply the same picker to chest loot.

**Architecture:**
- `gear_filter.json` is the single source of truth for ilvl bands, blocklist, allowlist, and per-item overrides.
- `Nat20GearFilter` loads the JSON once at startup and exposes `resolveTier(itemId) → (ilvlBand, category)` with fail-closed semantics on parse error.
- `Nat20ItemTierResolver.MATERIAL_ILVL` is stripped; `allowsIlvl` and `inferCategory` now delegate to `Nat20GearFilter`.
- `CategoryWeightedPicker` is a pure helper that picks from `Map<Category, List<String>>` using `{30, 30, 20, 20}` with empty-bucket renormalization.
- `Nat20MobLootPool` precomputes four category buckets (filtered by ilvl) at build time; `pick()` rolls 5% native vs 95% category-weighted.
- `Nat20ChestLootPicker` uses the same bucketed pool with no native bias.

**Tech Stack:** Java 25, JUnit 5 (already wired), Gson (for JSON parsing — confirm in build.gradle.kts before Task 2), seeded `java.util.Random` for deterministic distribution tests.

**Reference design doc:** `docs/plans/2026-04-25-gear-pool-filter-and-category-weights-design.md`.

---

## Conventions

- Branch: `feat/gear-pool-filter` (already created from main).
- Each task ends with a single commit. **No `Co-Authored-By` lines**, **no `--no-verify`**, **no push to remote** (per `~/.claude/CLAUDE.md`).
- Write the failing test first; verify it fails; implement minimal code; verify it passes; commit.
- Test class style: JUnit 5, package-private `class FooTest`, seeded `Random(42L)` for distribution checks. Pattern reference: `src/test/java/com/chonbosmods/dialogue/WeightedTierDrawTest.java`.

---

## Task 1: Author the full `gear_filter.json` (migrated material table + leak triage)

**Files:**
- Create: `src/main/resources/loot/gear_filter.json`

**Step 1: Write the JSON resource**

Migrate every existing material token from `Nat20ItemTierResolver.MATERIAL_ILVL` (drop unused `silver`, `bark`, `flint`), add the 9 new thematic tokens, place the 19 per-item overrides for true one-offs, and seed the blocklist with `_NPC`-suffixed items plus `Wand_Root` and all 5 spellbooks.

```json
{
  "blocklist": [
    "Weapon_Longsword_Praetorian_NPC",
    "Weapon_Wand_Root",
    "Weapon_Spellbook_Demon",
    "Weapon_Spellbook_Fire",
    "Weapon_Spellbook_Grimoire_Brown",
    "Weapon_Spellbook_Grimoire_Purple",
    "Weapon_Spellbook_Rekindle_Embers"
  ],
  "allowlist": {},
  "tier_tokens": {
    "onyxium":      [30, 45],
    "adamantite":   [28, 45],
    "thorium":      [26, 45],
    "runic":        [22, 45],
    "nexus":        [22, 45],
    "doomed":       [22, 45],
    "prisma":       [22, 45],
    "incandescent": [22, 45],
    "frost":        [22, 45],
    "ancient":      [22, 45],
    "cindercloth":  [22, 40],
    "silversteel":  [22, 38],
    "mithril":      [22, 38],
    "cobalt":       [16, 32],
    "steel":        [16, 32],
    "iron":         [8, 26],
    "bronze":       [3, 20],
    "copper":       [3, 20],
    "leather":      [1, 16],
    "cloth":        [1, 14],
    "wool":         [1, 14],
    "wood":         [1, 14],
    "stone":        [1, 14],
    "bone":         [1, 14],
    "crude":        [1, 12],
    "scrap":        [1, 12],
    "rusty":        [1, 12],

    "tribal":     [3, 18],
    "zombie":     [3, 14],
    "kweebec":    [16, 28],
    "trork":      [16, 28],
    "scarab":     [22, 38],
    "trooper":    [22, 38],
    "praetorian": [22, 35],
    "crystal":    [22, 40],
    "void":       [28, 45]
  },
  "tier_item_overrides": {
    "Tool_Shears_Basic":                  [1, 14],
    "Weapon_Spear_Leaf":                  [3, 18],
    "Weapon_Staff_Bo_Bamboo":             [3, 18],
    "Weapon_Staff_Cane":                  [3, 18],
    "Weapon_Staff_Onion":                 [3, 18],
    "Weapon_Sword_Cutlass":               [8, 22],
    "Weapon_Gun_Blunderbuss":             [16, 32],
    "Weapon_Shortbow_Bomb":               [16, 32],
    "Weapon_Shortbow_Combat":             [16, 32],
    "Weapon_Shortbow_Pull":               [16, 32],
    "Weapon_Staff_Wizard":                [16, 32],
    "Weapon_Longsword_Flame":             [22, 38],
    "Weapon_Longsword_Katana":            [22, 38],
    "Weapon_Shield_Orbis_Knight":         [22, 38],
    "Weapon_Shortbow_Flame":              [22, 38],
    "Weapon_Shortbow_Ricochet":           [22, 38],
    "Weapon_Shortbow_Vampire":            [22, 38],
    "Weapon_Assault_Rifle":               [22, 45],
    "Weapon_Longsword_Spectral":          [28, 45]
  }
}
```

**Step 2: Verify JSON parses**

Run: `python3 -c "import json; json.load(open('src/main/resources/loot/gear_filter.json'))"`
Expected: no output (clean parse).

**Step 3: Verify all 60 leaks now resolve**

Save this script as `/tmp/verify_gear_filter.py` and run:

```python
import json

with open('src/main/resources/loot/entries/vanilla.json') as f:
    items = json.load(f)['Items']
with open('src/main/resources/loot/gear_filter.json') as f:
    gf = json.load(f)

def resolve(item_id):
    if item_id in gf['blocklist']: return ('blocked', None)
    if item_id in gf['tier_item_overrides']: return ('override', gf['tier_item_overrides'][item_id])
    if item_id in gf['allowlist']: return ('allow', gf['allowlist'][item_id]['ilvl'])
    lower = item_id.lower()
    matches = [(k, v) for k, v in gf['tier_tokens'].items() if k in lower]
    if matches:
        k, v = max(matches, key=lambda kv: len(kv[0]))
        return ('token:' + k, v)
    return ('UNRESOLVED', None)

unresolved = []
for k in items:
    if not (k.startswith('Armor_') or k.startswith('Tool_') or k.startswith('Weapon_')):
        continue
    src, _ = resolve(k)
    if src == 'UNRESOLVED':
        unresolved.append(k)

print(f"Unresolved gear items: {len(unresolved)}")
for k in unresolved: print(f"  {k}")
```

Run: `python3 /tmp/verify_gear_filter.py`
Expected: `Unresolved gear items: 0`.

**Step 4: Commit**

```bash
git add src/main/resources/loot/gear_filter.json docs/plans/2026-04-25-gear-pool-filter-impl.md docs/plans/2026-04-25-gear-pool-filter-and-category-weights-design.md docs/wiki/offensive-affixes.md
git commit -m "feat(loot): add gear_filter.json with full material table + leak triage"
```

---

## Task 2: Confirm Gson is on the classpath

**Step 1: Inspect build.gradle.kts**

Run: `grep -i 'gson' build.gradle.kts`
Expected: at least one `implementation("com.google.code.gson:gson:...")` line, OR transitive availability via Hytale SDK. If neither, the codebase likely uses Hytale's bundled JSON utility — check existing patterns:

Run: `grep -rn 'JsonParser\|Gson\|JsonObject' src/main/java/com/chonbosmods/loot/registry/ | head -5`

Expected: identifies which JSON library the existing loot registry uses (it loads `vanilla.json` already). Use the same library to load `gear_filter.json`.

**Step 2: Note the choice for Task 3**

If Gson is direct: `import com.google.gson.JsonObject;` etc.
If Hytale's loader: match the pattern from `Nat20LootEntryRegistry`.

No commit; this is a discovery step.

---

## Task 3: `Nat20GearFilter` — JSON loader + lookup, with full TDD

**Files:**
- Create: `src/main/java/com/chonbosmods/loot/filter/Nat20GearFilter.java`
- Create: `src/test/java/com/chonbosmods/loot/filter/Nat20GearFilterTest.java`
- Create: `src/test/resources/loot/gear_filter_test.json` (test fixture)

**Step 1: Create test fixture**

`src/test/resources/loot/gear_filter_test.json`:

```json
{
  "blocklist": ["Weapon_Banned_Item"],
  "allowlist": {
    "Mod:Custom_Plasma": { "ilvl": [22, 38], "category": "ranged_weapon" }
  },
  "tier_tokens": {
    "iron":        [8, 26],
    "silversteel": [22, 38],
    "silver":      [4, 20],
    "tribal":      [3, 18]
  },
  "tier_item_overrides": {
    "Weapon_Sword_Cutlass": [8, 22]
  }
}
```

(`silver` is included to assert the longest-match rule pulls `silversteel` over `silver`.)

**Step 2: Write the failing test**

`Nat20GearFilterTest.java`:

```java
package com.chonbosmods.loot.filter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class Nat20GearFilterTest {

    private static Nat20GearFilter filter;

    @BeforeAll
    static void load() {
        InputStream in = Nat20GearFilterTest.class.getResourceAsStream("/loot/gear_filter_test.json");
        filter = Nat20GearFilter.loadFrom(in);
    }

    @Test
    void blocklist_rejects() {
        assertTrue(filter.resolveTier("Weapon_Banned_Item").isEmpty());
    }

    @Test
    void per_item_override_wins_over_token() {
        // Cutlass has no material token, would otherwise be UNRESOLVED
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Cutlass").orElseThrow();
        assertArrayEquals(new int[]{8, 22}, r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void allowlist_supplies_explicit_category() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Mod:Custom_Plasma").orElseThrow();
        assertArrayEquals(new int[]{22, 38}, r.ilvlBand());
        assertEquals("ranged_weapon", r.category());
    }

    @Test
    void token_match_uses_prefix_inferred_category() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Sword_Iron").orElseThrow();
        assertArrayEquals(new int[]{8, 26}, r.ilvlBand());
        assertEquals("melee_weapon", r.category());
    }

    @Test
    void longest_token_wins() {
        // "silversteel" (11 chars) and "silver" (6 chars) both match; expect silversteel band
        Nat20GearFilter.TierResolution r = filter.resolveTier("Armor_Silversteel_Chest").orElseThrow();
        assertArrayEquals(new int[]{22, 38}, r.ilvlBand());
    }

    @Test
    void no_match_rejects() {
        assertTrue(filter.resolveTier("Weapon_Mystery_Item").isEmpty());
    }

    @Test
    void shield_categorised_as_armor_via_prefix_inference() {
        Nat20GearFilter.TierResolution r = filter.resolveTier("Weapon_Shield_Tribal").orElseThrow();
        assertEquals("armor", r.category());
    }

    @Test
    void allows_ilvl_in_band() {
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 8));
        assertTrue(filter.isAllowed("Weapon_Sword_Iron", 26));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 7));
        assertFalse(filter.isAllowed("Weapon_Sword_Iron", 27));
    }

    @Test
    void fail_closed_on_parse_error() {
        InputStream broken = new java.io.ByteArrayInputStream("{ not valid json".getBytes());
        Nat20GearFilter f = Nat20GearFilter.loadFrom(broken);
        assertFalse(f.isAllowed("Weapon_Sword_Iron", 10));
        assertTrue(f.resolveTier("Weapon_Sword_Iron").isEmpty());
    }
}
```

**Step 3: Run tests; verify they fail**

Run: `./gradlew test --tests Nat20GearFilterTest`
Expected: 9 failures, all "Cannot resolve symbol Nat20GearFilter" or compilation errors.

**Step 4: Implement `Nat20GearFilter`**

`Nat20GearFilter.java`:

```java
package com.chonbosmods.loot.filter;

import com.chonbosmods.loot.mob.Nat20ItemTierResolver;
import com.hypixel.hytale.logger.HytaleLogger;
// Use whichever JSON parser Task 2 identified (Gson vs. Hytale's). The skeleton below uses Gson; swap if needed.
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Nat20GearFilter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|GearFilter");

    public record TierResolution(int[] ilvlBand, String category) {}
    private record AllowEntry(int[] ilvlBand, String category) {}

    private final boolean failedClosed;
    private final Set<String> blocklist;
    private final Map<String, AllowEntry> allowlist;
    private final List<Map.Entry<String, int[]>> tokensByLengthDesc;
    private final Map<String, int[]> tierItemOverrides;

    private Nat20GearFilter(boolean failedClosed,
                            Set<String> blocklist,
                            Map<String, AllowEntry> allowlist,
                            List<Map.Entry<String, int[]>> tokensByLengthDesc,
                            Map<String, int[]> tierItemOverrides) {
        this.failedClosed = failedClosed;
        this.blocklist = blocklist;
        this.allowlist = allowlist;
        this.tokensByLengthDesc = tokensByLengthDesc;
        this.tierItemOverrides = tierItemOverrides;
    }

    public static Nat20GearFilter loadFrom(InputStream in) {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            Set<String> bl = new HashSet<>();
            for (JsonElement e : root.getAsJsonArray("blocklist")) bl.add(e.getAsString());

            Map<String, AllowEntry> al = new HashMap<>();
            for (var entry : root.getAsJsonObject("allowlist").entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                int[] band = readBand(o.getAsJsonArray("ilvl"));
                String cat = o.get("category").getAsString();
                al.put(entry.getKey(), new AllowEntry(band, cat));
            }

            Map<String, int[]> tt = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject("tier_tokens").entrySet()) {
                tt.put(entry.getKey().toLowerCase(Locale.ROOT), readBand(entry.getValue().getAsJsonArray()));
            }
            List<Map.Entry<String, int[]>> tokensSorted = new ArrayList<>(tt.entrySet());
            tokensSorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

            Map<String, int[]> tio = new HashMap<>();
            for (var entry : root.getAsJsonObject("tier_item_overrides").entrySet()) {
                tio.put(entry.getKey(), readBand(entry.getValue().getAsJsonArray()));
            }

            LOGGER.atInfo().log("gear_filter.json loaded: %d blocklist, %d allowlist, %d tokens, %d overrides",
                    bl.size(), al.size(), tt.size(), tio.size());
            return new Nat20GearFilter(false, bl, al, tokensSorted, tio);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse gear_filter.json — failing closed (zero Nat20 gear drops until fixed)");
            return new Nat20GearFilter(true, Set.of(), Map.of(), List.of(), Map.of());
        }
    }

    private static int[] readBand(com.google.gson.JsonArray arr) {
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    public Optional<TierResolution> resolveTier(String itemId) {
        if (failedClosed) return Optional.empty();
        if (itemId == null) return Optional.empty();
        if (blocklist.contains(itemId)) return Optional.empty();

        int[] override = tierItemOverrides.get(itemId);
        if (override != null) {
            String cat = Nat20ItemTierResolver.inferCategory(itemId);
            return cat == null ? Optional.empty() : Optional.of(new TierResolution(override, cat));
        }

        AllowEntry allow = allowlist.get(itemId);
        if (allow != null) return Optional.of(new TierResolution(allow.ilvlBand(), allow.category()));

        String lower = itemId.toLowerCase(Locale.ROOT);
        for (var entry : tokensByLengthDesc) {
            if (lower.contains(entry.getKey())) {
                String cat = Nat20ItemTierResolver.inferCategory(itemId);
                return cat == null ? Optional.empty() : Optional.of(new TierResolution(entry.getValue(), cat));
            }
        }
        return Optional.empty();
    }

    public boolean isAllowed(String itemId, int ilvl) {
        return resolveTier(itemId)
                .map(r -> ilvl >= r.ilvlBand()[0] && ilvl <= r.ilvlBand()[1])
                .orElse(false);
    }
}
```

**Step 5: Run tests; verify they pass**

Run: `./gradlew test --tests Nat20GearFilterTest`
Expected: all 9 tests pass.

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/filter/Nat20GearFilter.java src/test/java/com/chonbosmods/loot/filter/Nat20GearFilterTest.java src/test/resources/loot/gear_filter_test.json
git commit -m "feat(loot): add Nat20GearFilter (JSON-driven tier resolver with fail-closed parse)"
```

---

## Task 4: `CategoryWeightedPicker` — pure helper with empty-bucket renormalization

**Files:**
- Create: `src/main/java/com/chonbosmods/loot/CategoryWeightedPicker.java`
- Create: `src/test/java/com/chonbosmods/loot/CategoryWeightedPickerTest.java`

**Step 1: Write the failing test**

```java
package com.chonbosmods.loot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CategoryWeightedPickerTest {

    private static Map<String, List<String>> fullBuckets() {
        return Map.of(
            "melee_weapon",  List.of("mw_a", "mw_b"),
            "armor",         List.of("arm_a", "arm_b"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
    }

    @Test
    void deterministicWithSeededRng() {
        var b = fullBuckets();
        Random a = new Random(42L), b2 = new Random(42L);
        assertEquals(CategoryWeightedPicker.pick(b, a), CategoryWeightedPicker.pick(b, b2));
    }

    @Test
    void distributionRoughlyMatchesWeights() {
        var b = fullBuckets();
        Random rng = new Random(1L);
        Map<String, Integer> counts = new HashMap<>();
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            String pick = CategoryWeightedPicker.pick(b, rng);
            String cat = pick.startsWith("mw") ? "melee_weapon"
                       : pick.startsWith("arm") ? "armor"
                       : pick.startsWith("rw") ? "ranged_weapon" : "tool";
            counts.merge(cat, 1, Integer::sum);
        }
        // Allow ±2% slack at 100k samples
        assertEquals(0.30, counts.get("melee_weapon")  / (double) N, 0.02);
        assertEquals(0.30, counts.get("armor")         / (double) N, 0.02);
        assertEquals(0.20, counts.get("ranged_weapon") / (double) N, 0.02);
        assertEquals(0.20, counts.get("tool")          / (double) N, 0.02);
    }

    @Test
    void renormalizesWhenBucketEmpty() {
        Map<String, List<String>> b = Map.of(
            "melee_weapon",  List.of(),
            "armor",         List.of("arm_a"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
        Random rng = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            String pick = CategoryWeightedPicker.pick(b, rng);
            assertFalse(pick.startsWith("mw"), "must never return melee item");
        }
    }

    @Test
    void returnsNullWhenAllBucketsEmpty() {
        Map<String, List<String>> b = Map.of(
            "melee_weapon",  List.of(),
            "armor",         List.of(),
            "ranged_weapon", List.of(),
            "tool",          List.of());
        assertNull(CategoryWeightedPicker.pick(b, new Random(0)));
    }
}
```

**Step 2: Run tests; verify they fail**

Run: `./gradlew test --tests CategoryWeightedPickerTest`
Expected: 4 failures (class not found).

**Step 3: Implement `CategoryWeightedPicker`**

```java
package com.chonbosmods.loot;

import java.util.*;

public final class CategoryWeightedPicker {

    public static final Map<String, Integer> WEIGHTS = Map.of(
            "melee_weapon",  30,
            "armor",         30,
            "ranged_weapon", 20,
            "tool",          20);

    private CategoryWeightedPicker() {}

    public static String pick(Map<String, List<String>> buckets, Random rng) {
        Map<String, Integer> active = new HashMap<>();
        int total = 0;
        for (var e : WEIGHTS.entrySet()) {
            List<String> bucket = buckets.get(e.getKey());
            if (bucket != null && !bucket.isEmpty()) {
                active.put(e.getKey(), e.getValue());
                total += e.getValue();
            }
        }
        if (total == 0) return null;

        int roll = rng.nextInt(total);
        String chosen = null;
        for (var e : active.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) { chosen = e.getKey(); break; }
        }
        List<String> bucket = buckets.get(chosen);
        return bucket.get(rng.nextInt(bucket.size()));
    }
}
```

**Step 4: Run tests; verify they pass**

Run: `./gradlew test --tests CategoryWeightedPickerTest`
Expected: all 4 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/CategoryWeightedPicker.java src/test/java/com/chonbosmods/loot/CategoryWeightedPickerTest.java
git commit -m "feat(loot): add CategoryWeightedPicker {30/30/20/20} with empty-bucket renormalization"
```

---

## Task 5: Wire `Nat20GearFilter` into `Nat20ItemTierResolver` (strip `MATERIAL_ILVL`)

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/mob/Nat20ItemTierResolver.java`
- Modify: `src/main/java/com/chonbosmods/loot/Nat20LootSystem.java` (singleton init point — verify path)

**Step 1: Locate the loot system bootstrap**

Run: `grep -rn 'Nat20LootSystem\|new Nat20LootEntryRegistry' src/main/java/com/chonbosmods/ | head -10`
Expected: identifies where the loot subsystem is constructed at plugin setup.

**Step 2: Modify `Nat20LootSystem` to load `Nat20GearFilter` once**

Add a `Nat20GearFilter` instance field initialized at construction time:

```java
this.gearFilter = Nat20GearFilter.loadFrom(
    getClass().getResourceAsStream("/loot/gear_filter.json"));
```

Add public `getGearFilter()`. (Mirror the pattern used for `Nat20LootEntryRegistry`.)

**Step 3: Strip `MATERIAL_ILVL` from `Nat20ItemTierResolver`**

Replace the static `MATERIAL_ILVL` map and `allowsIlvl` body. `inferCategory` stays as-is (it's still the prefix-based fallback used by gear filter token / override paths).

```java
public final class Nat20ItemTierResolver {

    private Nat20ItemTierResolver() {}

    /** Set by Nat20LootSystem at startup. */
    private static volatile Nat20GearFilter filter;

    public static void setFilter(Nat20GearFilter f) {
        filter = f;
    }

    public static boolean allowsIlvl(String itemId, int ilvl) {
        return filter != null && filter.isAllowed(itemId, ilvl);
    }

    public static String inferCategory(String itemId) {
        if (itemId == null) return null;
        String local = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        if (local.startsWith("Armor_")) return "armor";
        if (local.startsWith("Tool_")) return "tool";
        if (local.startsWith("Weapon_")) {
            String lower = local.toLowerCase();
            if (lower.startsWith("weapon_shield")) return "armor";
            if (lower.contains("_bow") || lower.contains("_crossbow") || lower.contains("_shortbow")
                    || lower.contains("_longbow") || lower.contains("_gun") || lower.contains("_handgun")
                    || lower.contains("_blowgun") || lower.contains("_staff") || lower.contains("_wand")
                    || lower.contains("_spellbook")) {
                return "ranged_weapon";
            }
            return "melee_weapon";
        }
        return null;
    }

    public static boolean isGearItem(String itemId) {
        return inferCategory(itemId) != null;
    }
}
```

In `Nat20LootSystem` constructor: `Nat20ItemTierResolver.setFilter(this.gearFilter);` after loading.

**Step 4: Compile; run all loot tests**

Run: `./gradlew compileJava compileTestJava test --tests "com.chonbosmods.loot.*"`
Expected: clean compile, all loot tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/loot/mob/Nat20ItemTierResolver.java src/main/java/com/chonbosmods/loot/Nat20LootSystem.java
git commit -m "refactor(loot): strip MATERIAL_ILVL, delegate ilvl gating to Nat20GearFilter"
```

---

## Task 6: Refactor `Nat20MobLootPool` to bucket pool by category + use new picker + 5% native bias

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/mob/Nat20MobLootPool.java`
- Create: `src/test/java/com/chonbosmods/loot/mob/Nat20MobLootPoolTest.java`

**Step 1: Write the failing test**

The new test seeds the pool directly via a package-private constructor (or a static `forTesting` factory) so it can verify pick distribution without spinning up an `EntityStore`.

```java
package com.chonbosmods.loot.mob;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Nat20MobLootPoolTest {

    private static Map<String, List<String>> buckets() {
        return Map.of(
            "melee_weapon",  List.of("mw_a", "mw_b"),
            "armor",         List.of("arm_a", "arm_b"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
    }

    @Test
    void native_bias_is_5_percent() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of("native_only"));
        Random rng = new Random(11L);
        int nativeCount = 0;
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            Nat20MobLootPool.PickResult r = pool.pick(rng);
            if (r.source() == Nat20MobLootPool.Source.NATIVE) nativeCount++;
        }
        assertEquals(0.05, nativeCount / (double) N, 0.01);
    }

    @Test
    void empty_native_falls_through_to_global() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of());
        Random rng = new Random(3L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(Nat20MobLootPool.Source.GLOBAL, pool.pick(rng).source());
        }
    }

    @Test
    void global_distribution_is_30_30_20_20() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of());
        Random rng = new Random(2L);
        Map<String, Integer> counts = new HashMap<>();
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            String id = pool.pick(rng).itemId();
            String cat = id.startsWith("mw") ? "melee_weapon"
                       : id.startsWith("arm") ? "armor"
                       : id.startsWith("rw") ? "ranged_weapon" : "tool";
            counts.merge(cat, 1, Integer::sum);
        }
        assertEquals(0.30, counts.get("melee_weapon")  / (double) N, 0.02);
        assertEquals(0.30, counts.get("armor")         / (double) N, 0.02);
        assertEquals(0.20, counts.get("ranged_weapon") / (double) N, 0.02);
        assertEquals(0.20, counts.get("tool")          / (double) N, 0.02);
    }
}
```

**Step 2: Run tests; verify they fail**

Run: `./gradlew test --tests Nat20MobLootPoolTest`
Expected: failure (no `forTesting` factory yet, no bucketed pool, NATIVE_LIST_BIAS still 0.12).

**Step 3: Refactor `Nat20MobLootPool`**

Replace the existing two-pool design with bucketed-by-category. Key changes:

- `NATIVE_LIST_BIAS = 0.05f`.
- Replace `List<String> global` field with `Map<String, List<String>> buckets`.
- `build()` walks `getAllItemIds()`, calls `Nat20ItemTierResolver.allowsIlvl()` (which now goes through the gear filter), uses `Nat20ItemTierResolver.inferCategory()` to decide bucket placement.
- `pick(rng)` rolls native at 5% then delegates to `CategoryWeightedPicker.pick(buckets, rng)`.
- Add `static Nat20MobLootPool forTesting(Map<String, List<String>> buckets, List<String> native_)` for unit tests.

```java
public final class Nat20MobLootPool {

    public static final float NATIVE_LIST_BIAS = 0.05f;
    public enum Source { NATIVE, GLOBAL }
    public record PickResult(String itemId, Source source) {}

    private final Map<String, List<String>> buckets;
    private final List<String> native_;

    private Nat20MobLootPool(Map<String, List<String>> buckets, List<String> native_) {
        this.buckets = buckets;
        this.native_ = native_;
    }

    static Nat20MobLootPool forTesting(Map<String, List<String>> buckets, List<String> native_) {
        return new Nat20MobLootPool(buckets, native_);
    }

    public static Nat20MobLootPool build(Ref<EntityStore> mobRef,
                                          Store<EntityStore> store,
                                          int ilvl,
                                          Nat20LootEntryRegistry registry) {
        Map<String, List<String>> b = buildGlobalBuckets(registry, ilvl);
        List<String> native_ = buildNativePool(mobRef, store, ilvl);
        return new Nat20MobLootPool(b, native_);
    }

    public boolean isEmpty() {
        if (!native_.isEmpty()) return false;
        return buckets.values().stream().allMatch(List::isEmpty);
    }

    public PickResult pick(Random rng) {
        if (!native_.isEmpty() && rng.nextFloat() < NATIVE_LIST_BIAS) {
            return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
        }
        String pick = CategoryWeightedPicker.pick(buckets, rng);
        if (pick == null) {
            // All global buckets empty; fall back to native if non-empty
            if (!native_.isEmpty()) {
                return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
            }
            return null;
        }
        return new PickResult(pick, Source.GLOBAL);
    }

    public static Map<String, List<String>> buildGlobalBuckets(Nat20LootEntryRegistry registry, int ilvl) {
        Map<String, List<String>> b = new HashMap<>();
        for (String cat : CategoryWeightedPicker.WEIGHTS.keySet()) b.put(cat, new ArrayList<>());
        for (String itemId : registry.getAllItemIds()) {
            String cat = Nat20ItemTierResolver.inferCategory(itemId);
            if (cat == null) continue;
            if (!Nat20ItemTierResolver.allowsIlvl(itemId, ilvl)) continue;
            b.get(cat).add(itemId);
        }
        return b;
    }

    private static List<String> buildNativePool(Ref<EntityStore> mobRef,
                                                 Store<EntityStore> store, int ilvl) {
        // Same body as today, with isGearItem + allowsIlvl filters.
        // (Copy from current implementation.)
    }
}
```

The existing `static buildGlobalPool(...)` (used by `Nat20ChestLootPicker`) is removed; the chest picker switches to `buildGlobalBuckets()` in Task 7.

**Step 4: Run tests; verify they pass**

Run: `./gradlew test --tests Nat20MobLootPoolTest`
Expected: all 3 tests pass.

**Step 5: Compile downstream callers**

Run: `./gradlew compileJava`
Expected: compile fails on `Nat20ChestLootPicker` (Task 7 fixes that). If compile fails on `Nat20MobLootListener`, audit it for `pool.pick()` usage — should be unchanged since `PickResult` shape is preserved.

If `Nat20ChestLootPicker` is the only compile error, that's expected; proceed to Task 7. Don't commit Task 6 in a state that fails to compile — combine the commit with Task 7 below, OR temporarily restore the deleted `buildGlobalPool` to keep main compiling.

**Step 6: Commit (combined with Task 7 in next step)**

Defer the commit until Task 7 is green.

---

## Task 7: Refactor `Nat20ChestLootPicker` to use bucketed pool

**Files:**
- Modify: `src/main/java/com/chonbosmods/loot/chest/Nat20ChestLootPicker.java`

**Step 1: Update `pickLoot` body**

Replace the `List<String> pool = Nat20MobLootPool.buildGlobalPool(...)` flat-list path with the bucketed picker:

```java
Map<String, List<String>> buckets = Nat20MobLootPool.buildGlobalBuckets(
        lootSystem.getLootEntryRegistry(), ilvl);
boolean allEmpty = buckets.values().stream().allMatch(List::isEmpty);
if (allEmpty) {
    LOGGER.atWarning().log("Empty chest loot pool at ilvl=%d; nothing to generate", ilvl);
    return Optional.empty();
}

Nat20LootPipeline pipeline = lootSystem.getPipeline();

for (int attempt = 0; attempt < MAX_PICK_ATTEMPTS; attempt++) {
    String itemId = CategoryWeightedPicker.pick(buckets, rng);
    if (itemId == null) break;
    String categoryKey = Nat20ItemTierResolver.inferCategory(itemId);
    if (categoryKey == null) continue;
    String baseName = resolveDisplayName(itemId);

    Nat20LootData data = pipeline.generate(itemId, baseName, categoryKey,
            effectiveMin, effectiveMax, rng, ilvl);
    if (data == null) continue;
    LOGGER.atInfo().log("Chest pick: %s [%s] from %s (ilvl=%d tier=[%d,%d])",
            data.getGeneratedName(), data.getRarity(), itemId, ilvl, effectiveMin, effectiveMax);
    return Optional.of(data);
}

return Optional.empty();
```

Update the comment at the top of the file from "8% native bias does not apply" to "no native bias applies (chests have no role context)" and reference `Nat20MobLootPool.NATIVE_LIST_BIAS = 0.05f` as the mob-side value.

**Step 2: Compile + run all tests**

Run: `./gradlew compileJava test`
Expected: clean compile, all existing tests + Tasks 3/4/6 tests pass.

**Step 3: Commit (Tasks 6 + 7 together)**

```bash
git add src/main/java/com/chonbosmods/loot/mob/Nat20MobLootPool.java src/main/java/com/chonbosmods/loot/chest/Nat20ChestLootPicker.java src/test/java/com/chonbosmods/loot/mob/Nat20MobLootPoolTest.java
git commit -m "feat(loot): bucket gear pool by category, weight {30/30/20/20}, drop native bias 12→5%"
```

---

## Task 8: Manual smoke test on devserver

**Pre-flight:** `devserver` cannot run from worktrees (per memory `devserver-worktree-limitation.md`). If executing from a worktree, merge `feat/gear-pool-filter` into main first **without pushing**, run smoke tests on main, then either keep the merge or revert. Confirm with the user before merging.

**Step 1: Start devserver**

Run: `./gradlew devServer`
Expected: log line "gear_filter.json loaded: 8 blocklist, 0 allowlist, 36 tokens, 19 overrides" (or similar).

If you see "Failed to parse gear_filter.json — failing closed", stop and re-validate the JSON.

**Step 2: Distribution check via existing dev commands**

In-game:

- `/nat20 spawngroup epic 30` → kill the group → tally drops by category over 20 kills. Expect roughly 28% melee, 28% armor, 19% ranged, 19% tool (±5 percentage points at small N).
- `/nat20 spawntier legendary` × 5 boss kills → expect 2 guaranteed + bonus rolls each, varied categories.

Walk into a generated chest at ilvl 30, open ×10, tally categories. Expect 30/30/20/20 with no Iron-only repetition pattern.

**Step 3: Targeted leak checks**

- Spawn an ilvl-3 mob and kill 30. **No** drops of `Praetorian`, `Tribal`, `Void`, `Spectral`, `Mithril`, `Adamantite` should appear.
- Kill a Goblin (native = Iron drops) 30 times. Iron drops should be ~5% of slots, not ~12%. Check log lines tagged `source=NATIVE` vs `source=GLOBAL`.
- Spawn a Trork mob at ilvl 30 → no Trork armor drops (out of [16,28]).
- Spawn a Trork mob at ilvl 22 → Trork armor occasionally drops.

**Step 4: Sanity checks**

- `/give Weapon_Sword_Iron` works (gear_filter only affects loot generation, not commands).
- Boot log has no SEVERE about gear_filter.json.

**Step 5: Update memory + record results**

If smoke tests pass, save a memory entry summarising the change:

```markdown
- See [gear-pool-filter-shipped.md](gear-pool-filter-shipped.md) for the gear-pool filter + 30/30/20/20 category weights + 5% native bias (shipped 2026-04-25 on `feat/gear-pool-filter`). Closes 60-item ilvl-gating leak. Smoke test results in same memory entry.
```

If anything fails, do **not** commit — open a debugging session via the `superpowers:systematic-debugging` skill, find the root cause, fix, re-run.

**Step 6: Final commit (memory entry only)**

No source change in this step; just memory. After user reviews + confirms.

---

## Open Questions for the User After Implementation

1. Are there other `_NPC`-suffixed items beyond `Weapon_Longsword_Praetorian_NPC` that should be on the blocklist? Run `grep -E '_NPC[\"]' src/main/resources/loot/entries/vanilla.json` to enumerate.
2. After playtest: do the starting tier bands feel right, or should any token band shift? (Tune in a follow-up commit, not this one.)
3. Stack-quantity drops: schedule the follow-up design? (Wiki entry already added.)
