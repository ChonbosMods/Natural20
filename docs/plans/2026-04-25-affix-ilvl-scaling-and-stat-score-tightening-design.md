# Affix ilvl Scaling + Stat-Score Tightening

**Date:** 2026-04-25
**Branch:** TBD (likely `feat/affix-ilvl-scaling` off main after `feat/gear-pool-filter` merges)
**Status:** Design locked, implementation pending
**Amends:** `docs/plans/2026-04-15-xp-mlvl-ilvl-system-design.md` §3.2 — replaces the linear-from-1.0× `ilvlScale` with a two-component `endgameScale × spread` curve. Endgame ceiling preserved.

## Problem

Affixes feel overpowered at low ilvl. The existing `Nat20XpMath.ilvlScale(ilvl, qv) = 1 + (ilvl-1) × (0.025 + (qv-1) × 0.003)` linearly buffs values from 1.0× at ilvl 1 to 2.1×–2.628× at ilvl 45. The JSON `ValuesPerRarity` Min/Max acts as the *baseline* (ilvl 1 value), so a player picking up a Common Rally on an ilvl-3 weapon already gets the full 5–8% — same value as endgame. The progression curve is invisible at low levels.

Separately, stat-score affixes (STR / DEX / CON / INT / WIS / CHA) can roll up to +4 at Legendary — well outside the "feel" budget for an integer stat boost.

## Goals

1. Compress low-ilvl affix values so the climb to endgame is visible. Floor 0.30× at ilvl 1, 1.00× at ilvl 45 (linear).
2. **Preserve today's endgame ceiling exactly** — no high-ilvl nerf.
3. Cap stat-score affixes at +2 per affix and only roll on Rare+ rarities.
4. Remove the rarity gate — any ilvl can roll any rarity. Frequency unchanged.

## Locked Decisions

### New `ilvlScale` formula

Replace the body of `Nat20XpMath.ilvlScale(int ilvl, int qv)` with:

```java
public static double ilvlScale(int ilvl, int qv) {
    return endgameScale(qv) * spread(ilvl);
}

private static double endgameScale(int qv) {
    return 1.0 + 44 * (0.025 + (qv - 1) * 0.003);
}

private static double spread(int ilvl) {
    return 0.30 + 0.70 * (ilvl - 1) / 44.0;
}
```

`endgameScale(qv)` is constant per rarity — equals today's ilvl-45 value:

| qv | Rarity | endgameScale |
|---|---|---|
| 1 | Common | 2.100 |
| 2 | Uncommon | 2.232 |
| 3 | Rare | 2.364 |
| 4 | Epic | 2.496 |
| 5 | Legendary | 2.628 |

`spread(ilvl)` is linear: 0.30 at ilvl 1, ~0.65 at ilvl 22, 1.00 at ilvl 45.

The product `endgameScale × spread` matches today's value at ilvl 45 exactly (spread = 1.0) and is dampened to 30% of that at ilvl 1.

### Stat-score opt-out

Stat-score affixes (`score_str`, `score_dex`, `score_con`, `score_int`, `score_wis`, `score_cha`) bypass `ilvlScale` entirely. Scaling +2 down to +0.6 is meaningless for an integer stat boost.

**Mechanism**: new `IlvlScalable` boolean field on `Nat20AffixDef`, defaults `true`. The six stat-score JSONs explicitly set `"IlvlScalable": false`. `hp.json` (multiplicative HP affix) keeps the default `true` and continues to scale.

`Nat20ModifierManager` branches at the call site:

```java
double baseValue = affixDef.ilvlScalable()
    ? Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, rarityRegistry)
    : range.interpolate(rolledAffix.midLevel());   // existing 1-arg unscaled path
```

The 1-arg `AffixValueRange.interpolate(double lootLevel)` already exists for legacy callers — reuse it for stat scores. No new method.

### Stat-score value reshape

Each of the six `score_*.json` files replaces its `ValuesPerRarity` block with:

```json
"ValuesPerRarity": {
  "Rare":      { "Min": 1.0, "Max": 1.0 },
  "Epic":      { "Min": 1.0, "Max": 1.0 },
  "Legendary": { "Min": 2.0, "Max": 2.0 }
}
```

Common and Uncommon entries are **omitted**. The existing pool builder at `Nat20AffixRegistry.getPool:172` already filters affixes whose `valuesPerRarity` map lacks the relevant rarity, so omission = automatic gating. No code change to the pool builder.

Result:

- Common item: stat-score affixes excluded from pool entirely.
- Uncommon item: stat-score affixes excluded.
- Rare item: stat-score affixes can be picked (frequency-weighted). Rolled value = +1.
- Epic item: same. Rolled value = +1.
- Legendary item: rolled value = +2.

### Rarity gate removal

Replace `Nat20LootPipeline.rarityGateForIlvl(int ilvl)`:

```java
public static int[] rarityGateForIlvl(int ilvl) {
    return new int[]{1, 5};   // all rarities at all ilvls; ilvlScale handles low-ilvl dampening
}
```

`ilvl` parameter is preserved (now unused) so existing callers — `Nat20MobLootListener:97`, `Nat20ChestAffixInjectionSystem:127`, `Nat20ChestLootPicker:47, 63` — keep their signatures and don't have to change. Update Javadoc accordingly.

`Nat20ChestLootPicker.pickLoot(ilvl, minTier, maxTier, rng)` still respects caller-supplied clamps (the bonus-roll path that biases toward Common/Uncommon). Only the default gate widens.

## Worked Examples

### Common Rally (declared 0.05–0.08)

| ilvl | spread | scale | Today | New |
|---|---|---|---|---|
| 1 | 0.300 | 0.630 | 5.0–8.0% | 3.2–5.0% |
| 12 | 0.475 | 0.998 | 7.8–12.4% | 5.0–7.9% |
| 22 | 0.634 | 1.331 | 7.6–12.2% | 6.7–10.6% |
| 35 | 0.841 | 1.766 | 9.5–15.2% | 8.7–13.9% |
| 45 | 1.000 | 2.100 | 10.5–16.8% | **10.5–16.8%** |

### Legendary Rally (declared 0.15–0.25)

| ilvl | spread | scale | Today | New |
|---|---|---|---|---|
| 1 | 0.300 | 0.788 | 15–25% | 11.8–19.7% |
| 22 | 0.634 | 1.667 | 23.9–39.9% | 25.0–41.7% |
| 45 | 1.000 | 2.628 | 39.4–65.7% | **39.4–65.7%** |

### Crit Damage Common (declared 0.10–0.20)

| ilvl | spread | scale | range |
|---|---|---|---|
| 1 | 0.300 | 0.630 | 6.3–12.6% |
| 22 | 0.634 | 1.331 | 13.3–26.6% |
| 45 | 1.000 | 2.100 | **21.0–42.0%** |

### Stat-score (any score_*, all ilvls)

| Rarity | Value |
|---|---|
| Common | not rollable |
| Uncommon | not rollable |
| Rare | +1 |
| Epic | +1 |
| Legendary | +2 |

## File Structure

| Action | File | Purpose |
|---|---|---|
| Modify | `progression/Nat20XpMath.java` | Replace `ilvlScale` body; add private `endgameScale`/`spread` helpers |
| Modify | `loot/def/Nat20AffixDef.java` | Add `boolean ilvlScalable` to record |
| Modify | `loot/registry/Nat20AffixRegistry.java` | Parse `IlvlScalable` JSON field with default `true` |
| Modify | `loot/Nat20ModifierManager.java` | Branch on `affixDef.ilvlScalable()` at the two interpolate call sites (lines 147, 169) |
| Modify | `loot/Nat20LootPipeline.java` | `rarityGateForIlvl` always returns `{1, 5}` |
| Modify | `loot/affixes/stat/score_str.json` (×6 stat-score files) | Reshape `ValuesPerRarity`; add `"IlvlScalable": false` |
| No change | `loot/affixes/stat/hp.json` | HP affix keeps default ilvl scaling |
| No change | `loot/registry/Nat20AffixRegistry.getPool` | Existing rarity-key filter already excludes stat scores from Common/Uncommon pools |

## Edge Cases

1. **Stat-score on Common/Uncommon item via direct admin grant**: pool filter excludes them from the *roll* path. If an item somehow has a stat-score affix already (legacy data, admin), the unscaled path returns +1 or +2 as authored — no breakage.
2. **`hp.json` keeps `IlvlScalable: true`** explicitly via default. Multiplicative HP scaling per ilvl is a separate balance question, not in this design's scope.
3. **Existing rolled items in player inventory**: stored as `[lo, hi]` coefficients, recomputed at read-time. They auto-rebalance when the new scale function ships. No migration needed; intentional balance pass.
4. **Combat-time `StatScaling`** (e.g. CHA amplifies Rally per combat tick): unchanged. Operates on rolled value *after* `ilvlScale`, so a Rally on an ilvl-1 weapon still benefits from CHA, just from a lower base.
5. **`Nat20ChestLootPicker.pickLoot(ilvl, minTier, maxTier, rng)` clamps**: still honored. Only the default gate widens.
6. **Pre-existing `Nat20XpMath.ilvlScale` callers**: only `AffixValueRange.interpolate(lootLevel, ilvl, qv)` calls it. New behavior is invisible to other callers; signature unchanged.

## Smoke Test Checklist

Post-implementation, before merge:

- [ ] Boot devserver. No SEVERE on affix-load. INFO line confirms 53 affix definitions loaded.
- [ ] Spawn ilvl-1 mob. Kill 30. Inspect drops. Common Rally reads ~3–5% (today: 5–8%). Legendary Rally ~12–20% (today: 15–25%).
- [ ] Spawn ilvl-45 mob. Kill 10. Common Rally 10.5–16.8% (matches today). Legendary Rally 39–66%. **Endgame parity check.**
- [ ] Spawn ilvl-22 mob. Drops sit at ~63% of endgame.
- [ ] Inspect a Common drop. Zero stat-score affixes (excluded by absent JSON entry).
- [ ] Inspect a Rare drop with a stat-score. Value is exactly **+1** integer at ilvl 1 and ilvl 45 (no scaling).
- [ ] Inspect a Legendary drop with a stat-score. Value is exactly **+2**.
- [ ] At ilvl 1, kill mobs until Legendary drops. Confirm Legendary IS reachable post-gate-removal. Frequency should still feel rare per `Nat20RarityDef`.
- [ ] Existing player inventory item from before the change: open tooltip. Affix values shift (recomputed). Confirm it doesn't crash.

## Wiki Author Handoff (final implementation task)

After all code lands and smoke tests pass, generate `docs/wiki/_drafts/2026-04-25-affix-rebalance-handoff.md` containing:

1. **Formula in plain prose**: "Affix values scale linearly from 30% of the listed value at ilvl 1 to 100% at ilvl 45. The listed Min/Max in each affix table is now the *endgame ceiling*, not the rolled value at low levels."
2. **Behavior changes summary**: rarity gate removed (any ilvl can roll any rarity); stat scores capped at +2 and Rare+ only; Common/Uncommon items can't roll stat scores; HP affix unaffected (still scales).
3. **Per-affix value tables** showing OLD vs NEW at ilvl 1 / 22 / 45 for one example per category: Common Rally, Legendary Crit Damage, Common Resistance (any defense), Legendary HP%, Rare STR score. Author extrapolates from the formula.
4. **Spread lookup table** in 5-step increments (ilvl 1, 5, 10, 15, 20, 25, 30, 35, 40, 45 → spread multiplier).
5. **Pages that need editing**: explicit list — `offensive-affixes.md`, `defense-affixes.md`, `stat-affixes.md`, `utility-affixes.md`, `ability-affixes.md` — with the specific section in each that needs the rebalance note.
6. **One-paragraph "what to tell players"** blurb suitable for a patch note or wiki intro.

The handoff doc lives in `docs/wiki/_drafts/` (new directory) so it's clearly an artifact, not a published wiki page. Wiki author owns the rewrite from there.

## Open / Deferred

1. **Rarity frequency curve at low ilvl.** With the gate removed, per-rarity frequencies in `Nat20RarityDef` now drive what players see. Today those frequencies were tuned assuming the gate clamps Legendary out below ilvl 16. After this change, an ilvl-1 player has a non-zero chance of pulling Legendary on every drop. If smoke testing surfaces "too generous," retune `Nat20RarityDef` frequencies in a follow-up commit. **Not in this design's scope.**
2. **Wiki rewrite execution.** This design ships the handoff doc. The actual wiki edit is the wiki author's job and lands separately.
3. **Stat-score visibility at low ilvl.** Players who never reach Rare drops at low ilvl never see any STR/DEX/CON affixes. Acceptable trade-off; flag if smoke testing surfaces frustration.
4. **`hp.json` rebalance.** Still ilvl-scales today (multiplier × spread × endgame). If it feels off post-change, retune in a follow-up. Out of scope here.
