# Mob Loot Tuning Design (2026-04-21)

Tuning pass on the Nat20 mob drop pipeline. Four player-visible behavior changes + one knob (native bias). Hytale-native `ItemDropList` drops stay untouched.

## Goals

1. Common-rarity Nat20 drops always carry ≥1 affix. No "raw" Common gear.
2. Common becomes rarer than Uncommon. Uncommon is the new "baseline" outcome.
3. Champion mobs drop less often. Bosses unchanged.
4. Higher-tier champions (RARE / EPIC difficulty) skew toward better rarities when they do drop.
5. Slightly raise thematic "native-list" sampling inside the Nat20 pool (compensates for no longer having Common-bonus rerolls).

Explicitly out of scope: intercepting or suppressing Hytale's own `ItemDropList`. Those drops continue to fire in parallel as they do today. Research showed no SDK cancellation hook, and the post-spawn interception path (freeze + re-throw) was judged not worth the flicker + registry complexity.

## Changes

### 1. Common always affixed

**File:** `src/main/resources/loot/rarities/Common.json`

Change the one LootRule from `Probability: 0.5` to `Probability: 1.0`. `MaxAffixes: 1` stays. Every Nat20-generated Common now has exactly one affix, regardless of source (mob drops, chest loot, quest rewards).

### 2. Rarity weight re-curve

**Files:** `Common.json`, `Uncommon.json`

| Rarity | Old `BaseWeight` | New `BaseWeight` | New probability |
|---|---|---|---|
| Common | 1000 | **300** | ~17% |
| Uncommon | 500 | **1000** | ~57% |
| Rare | 200 | 200 | ~11% |
| Epic | 50 | 50 | ~3% |
| Legendary | 10 | 10 | ~0.6% |

Rare / Epic / Legendary weights untouched. These become the default pool used by everything that doesn't apply difficulty-specific biases (chest loot, quest rewards, UNCOMMON-difficulty mob drops).

### 3. Remove "Common-as-bonus-drop" rerolls

**File:** `src/main/java/com/chonbosmods/loot/mob/Nat20MobLootListener.java`

`generateDrops()` currently has a branch that, for RARE+ mobs, drops a Common item "for free" (doesn't consume a slot) and re-rolls the slot until a non-Common lands. Remove it:

- Delete `REROLL_CAP_MULTIPLIER`, the `rerollCommons` / `bonusReroll` variables, the `iterations` safety loop, and the `"Bonus drop (Common from %s mob)"` log branch.
- Replace the `while` loop with a simple `for (int i = 0; i < dropCount; i++)` that generates exactly one drop per slot and keeps whatever rarity it rolled.
- Pool-empty and pipeline-null null-safety branches remain: bail on the current slot and continue.

### 4. Champion drop rate cut + difficulty-aware rarity bias

**File:** `src/main/java/com/chonbosmods/loot/mob/Nat20MobDropCount.java`

Flatten all champion Bernoulli chances to **0.18**:

| Constant | Old | New |
|---|---|---|
| `CHAMPION_CHANCE_UNCOMMON` | 0.35 | **0.18** |
| `CHAMPION_CHANCE_RARE` | 0.40 | **0.18** |
| `CHAMPION_CHANCE_EPIC` | 0.45 | **0.18** |

`rollBoss` unchanged. `LEGENDARY CHAMPION` warning branch unchanged.

**Difficulty-aware rarity distribution on the 18% hits:**

| Difficulty | Common | Uncommon | Rare | Epic | Legendary |
|---|---|---|---|---|---|
| UNCOMMON champion | 300 (~17%) | 1000 (~57%) | 200 (~11%) | 50 (~3%) | 10 (~0.6%) |
| RARE champion | 300 (~14%) | **1500** (~68%) | **300** (~14%) | **75** (~3.4%) | **15** (~0.7%) |
| EPIC champion | **0** (0%) | **1600** (~73%) | **450** (~21%) | **115** (~5%) | **25** (~1%) |

Algorithm (applied at rarity selection time, keyed off the mob's `DifficultyTier`):

1. Start from base weights `{C:300, U:1000, R:200, E:50, L:10}`.
2. If difficulty ≥ RARE: multiply Uncommon / Rare / Epic / Legendary weights by **1.5×**.
3. If difficulty ≥ EPIC: set Common weight to 0, then redistribute its 300 weight: `Uncommon +100`, `Rare +150`, `Epic +40`, `Legendary +10`.

Step 3's split is chosen to absorb most of Common's displaced mass into Uncommon (the new baseline) while still tilting a chunk toward Rare/Epic/Legendary to make EPIC champions feel meaningfully better than RARE champions.

**Plumbing:**

- New method on `Nat20RarityRegistry`: `selectRandomForDifficulty(Random, int minTier, int maxTier, DifficultyTier difficulty)`. Applies the multiplier + common-floor logic over the already-gated pool (the existing `rarityGateForIlvl(ilvl)` gate from `Nat20LootPipeline` still clamps min/max).
- `Nat20LootPipeline.generate(...)` signature extends to accept an optional `DifficultyTier` (null = use default unbiased selection, i.e. chest loot, quest rewards, UNCOMMON mobs).
- `Nat20MobLootListener.generateDrops(...)` passes the mob's `DifficultyTier` through to the pipeline.

### 5. Native-list bias 8% → 12%

**File:** `src/main/java/com/chonbosmods/loot/mob/Nat20MobLootPool.java`

Bump `NATIVE_LIST_BIAS` from `0.08f` to `0.12f`. When the Nat20 pool picks an item for a drop slot, it now has a 12% chance of pulling from the mob's own `ItemDropList` (filtered to gear and ilvl-band) instead of the global gear pool. Small flavor bump.

## Explicitly unchanged

- Hytale's native `ItemDropList` fires as it does today. No interception, no suppression.
- `Nat20MobDropCount.rollBoss` (bosses stay at guaranteed + 30% bonus rolls).
- All rarity configs other than `Common.json` and `Uncommon.json`.
- Affix-rolling rules for Uncommon / Rare / Epic / Legendary.
- `Nat20ItemTierResolver` material-tier bands.

## Testing

Smoke only — every change is a data/constant edit or a mechanical branch removal plus one narrow new registry method. No new unit tests required.

1. **Common always affixed:** Spawn an UNCOMMON regular mob repeatedly, force several drops via a champion, verify every Common in the tooltip shows 1 affix line. Check a chest loot roll and a quest reward for the same.
2. **Rarity curve:** Kill ≥100 UNCOMMON champions (`/nat20 spawntier`), count rarities observed. Expect ~17% Common, ~57% Uncommon, ~11% Rare, small tails for Epic / Legendary.
3. **Champion drop rate:** Kill ≥50 champions at each of UNCOMMON / RARE / EPIC. Drop rate should converge to ~18% across all three.
4. **EPIC champion rarity bias:** Kill ≥50 EPIC champions that actually dropped. Expect zero Commons, Uncommon majority, and a visibly elevated Rare-plus tail compared to UNCOMMON champions.
5. **Native bias:** Kill a mob whose native `ItemDropList` contains a distinctive gear piece (e.g. Trork battleaxe). Over many kills, confirm the Nat20-pipelined version of that item appears at roughly 12% of drop slots sourced from the pool (independent of Hytale's own drop of the raw version).
6. **Boss regression check:** Kill a boss at each difficulty. Drop count should match the existing guaranteed-base + 30%-bonus tables.

## Risk notes

- Difficulty-aware rarity selection is the only non-trivial code change. Keep it behind the new `selectRandomForDifficulty` method with a fallback to the unbiased `selectRandom` path (`difficulty == null`) so chest/reward loot behavior does not shift.
- Removing the Common-reroll loop narrows the expected drop count per mob (no more silent bonus Commons). If this feels too stingy in playtest, we can compensate by raising champion chances or adding a flat +1 bonus slot at EPIC/LEGENDARY — not in this pass.
- Native `ItemDropList` drops continue to stack on top of Nat20 drops. Champions will often drop 1 Nat20-pipelined item *plus* the raw Hytale list, which is the existing behavior. If that ends up feeling noisy, the interception path researched here becomes the follow-up design.
