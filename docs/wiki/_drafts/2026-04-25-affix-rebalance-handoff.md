# Affix Rebalance: Wiki Author Handoff

**Date:** 2026-04-25 (revised: tier-spread tightening pass)
**Status:** Code shipped. Wiki rewrite pending.
**Audience:** Wiki author. This is an artifact for the wiki author, not a published page.

This document is self-contained. You should not need the design doc to do your job.

## What changed, in plain prose

Two related changes:

1. **Affix values scale by ilvl.** A Common Rally on an ilvl-1 drop rolls about a third of the listed value, ramping up linearly to the listed value at ilvl 45. The Min/Max in each affix wiki table is the **endgame ceiling**, not the rolled value at low levels.

2. **Tier barely changes affix VALUES anymore.** A Legendary affix is now only ~5% stronger than a Common one of the same type at the same ilvl (was ~25%). Tier still differentiates items via slot count (more affixes on higher tiers) and stat scores (Rare/Epic +1, Legendary +2): tier controls breadth and stat scores; ilvl controls depth.

Concretely, a Common Rally on a starter weapon used to roll 5.0%–8.0% regardless of item level. After the rebalance, that same affix on an ilvl-1 drop rolls 3.2%–5.0%, climbs through the levels, and lands at 10.5%–16.8% at ilvl 45. Legendary Rally at ilvl 45 used to roll 13.1%–21.0% (25% above Common); it now rolls 11.0%–17.6% (5% above Common).

## Behavior changes summary

- **Rarity gate retired.** Any ilvl can roll any rarity from Common through Legendary. Per-rarity frequencies in `Nat20RarityDef` are unchanged, so a Legendary at ilvl 1 is still rare in practice; it is just no longer impossible.
- **Stat scores capped at +2 and Rare-or-better only.** STR / DEX / CON / INT / WIS / CHA affixes now roll exactly +1 on Rare and Epic items, +2 on Legendary items. They opt out of ilvl scaling entirely (no more fractional stat boosts at low ilvl). Common and Uncommon items cannot roll stat scores at all.
- **HP affix is unchanged in mechanic.** It still ilvl-scales. Its endgame multiplier shifts slightly because the per-tier endgameScale for Legendary changed (see Worked Examples below).
- **Tier endgame spread tightened from 25% to ~5%** (Legendary affix value vs Common at ilvl 45). Previously tier scaled affix values almost as much as one full level of ilvl difference; now it nudges them.

## The formula

```
scale(ilvl, qv) = endgameScale(qv) × spread(ilvl)
endgameScale(qv) = 1 + 44 × (0.025 + (qv - 1) × 0.0006)
spread(ilvl)     = 0.30 + 0.70 × (ilvl - 1) / 44
```

`endgameScale(qv)` is constant per rarity:

| qv | Rarity    | endgameScale |
|----|-----------|--------------|
| 1  | Common    | 2.1000       |
| 2  | Uncommon  | 2.1264       |
| 3  | Rare      | 2.1528       |
| 4  | Epic      | 2.1792       |
| 5  | Legendary | 2.2056       |

(Common is unchanged from the original formula. Legendary used to be 2.628; it is now 2.2056. The per-tier increment shrunk from 0.132 to 0.0264.)

`spread(ilvl)` is a linear ramp from 0.30 at ilvl 1 to 1.00 at ilvl 45. The product `endgameScale × spread` is what gets multiplied against the declared Min/Max range from the affix JSON.

## Spread lookup table

| ilvl   | 1     | 5     | 10    | 15    | 20    | 25    | 30    | 35    | 40    | 45    |
|--------|-------|-------|-------|-------|-------|-------|-------|-------|-------|-------|
| spread | 0.300 | 0.364 | 0.443 | 0.523 | 0.602 | 0.682 | 0.762 | 0.841 | 0.921 | 1.000 |

To compute any cell yourself: `spread = 0.30 + 0.70 × (ilvl - 1) / 44`.

## Worked old-vs-new examples

These use the actual declared values from the affix JSONs in `src/main/resources/loot/affixes/`. "Old" is the pre-change value at that ilvl. "New" is what players will see now.

### Common Rally (declared 0.05–0.08)

| ilvl | Old         | New         |
|------|-------------|-------------|
| 1    | 5.0%–8.0%   | 3.2%–5.0%   |
| 22   | 7.6%–12.2%  | 6.7%–10.6%  |
| 45   | 10.5%–16.8% | 10.5%–16.8% |

### Legendary Crit Damage (declared 0.60–1.00)

| ilvl | Pre-rebalance   | Now             |
|------|-----------------|-----------------|
| 1    | 60.0%–100.0%    | 39.7%–66.2%     |
| 22   | 95.5%–159.2%    | 83.9%–139.8%    |
| 45   | 157.7%–262.8%   | 132.3%–220.6%   |

(Endgame value moved down from 262.8% to 220.6% because Legendary endgameScale dropped from 2.628 to 2.2056 in the tier-tightening pass.)

### Common Fire Resistance (declared 0.05–0.08, from `fire_resistance.json`)

| ilvl | Old         | New         |
|------|-------------|-------------|
| 1    | 5.0%–8.0%   | 3.2%–5.0%   |
| 22   | 7.6%–12.2%  | 6.7%–10.6%  |
| 45   | 10.5%–16.8% | 10.5%–16.8% |

(Common Fire Resistance shares its declared Min/Max with Common Rally, so the percentages match. The ceilings differ at higher rarities; check the JSON for those rows.)

### Legendary HP% (declared 1.80×, from `hp.json`, STILL SCALES)

HP is multiplicative: the rolled value is a multiplier on max HP, not a coefficient. It still uses `IlvlScalable: true` because the scaling is meaningful at every level (1.19× HP at ilvl 1 still feels like an HP roll; +0.6 stat would not have felt like a stat roll).

| ilvl | Now value |
|------|-----------|
| 1    | 1.19×     |
| 22   | 2.52×     |
| 45   | 3.97×     |

(Math: 1.80 × endgameScale(5) × spread(ilvl). At ilvl 1, 1.80 × 2.2056 × 0.300 = 1.19. At ilvl 45, 1.80 × 2.2056 × 1.000 = 3.97.)

### Rare STR score (from `score_str.json`, STATIC)

Stat scores do not scale with ilvl. They roll the same fixed integer at every level.

| ilvl | Value |
|------|-------|
| 1    | +1    |
| 22   | +1    |
| 45   | +1    |

For Legendary STR, the value is +2 at every ilvl. For Common and Uncommon, the affix does not roll at all.

## Pages that need editing

All paths are relative to the repo root.

- **`docs/wiki/offensive-affixes.md`**: every per-rarity table needs two changes. (1) Add a short intro paragraph noting the spread (30% at ilvl 1, 100% at ilvl 45) so readers know the listed numbers are the endgame ceiling, not what they will see on early-game gear. (2) **Non-Common per-rarity rows need to be recomputed** with the new endgameScale values (Uncommon 2.1264, Rare 2.1528, Epic 2.1792, Legendary 2.2056). Common rows are unchanged because Common endgameScale stayed at 2.100. The easiest mechanical recipe per row: take the Common row's endgame Min/Max and multiply by `(non-common endgameScale / 2.100)` for the matching tier (~1.013x Uncommon, ~1.025x Rare, ~1.038x Epic, ~1.050x Legendary).
- **`docs/wiki/defense-affixes.md`**: same treatment as offensive-affixes. Intro note about spread; non-Common tables need recomputation per the same recipe.
- **`docs/wiki/stat-affixes.md`**: replace the existing stat-score tables with the fixed-value scheme. Rare = +1, Epic = +1, Legendary = +2. Note that Common and Uncommon items cannot roll stat scores. The HP affix on this page is unaffected by the +1/+1/+2 change but its non-Common endgame values DID move (it ilvl-scales like other affixes); recompute as above.
- **`docs/wiki/utility-affixes.md`**: add the same intro spread note. Non-Common tables need recomputation.
- **`docs/wiki/ability-affixes.md`**: add the same intro spread note. Non-Common tables need recomputation.
- `docs/wiki/ability-scores.md`: has stale "+4 STR" / "+2 DEX" examples (lines 170, 209, 211 reference old stat-score caps); update to the new +1/+1/+2 scheme.

A reusable intro paragraph you can paste into each affix page (tweak per page voice):

> The Min and Max values below are the endgame ceiling at ilvl 45. Affix values scale from 30% of the listed value at ilvl 1 to 100% at ilvl 45, so a roll on a low-ilvl drop will be smaller than what you see here. See the rebalance handoff for the full curve.

## Patch-note draft

> **Affix balance pass.** Low-ilvl drops now feel proportional to their level. A Common Rally on a Bronze sword no longer rolls the same percentages as one on a Mithril sword: it rolls about a third of those values at ilvl 1 and ramps up linearly to the full values at ilvl 45.
>
> **Tier now barely changes affix values.** A Legendary affix is only ~5% stronger than a Common one of the same kind at the same ilvl (was ~25%). What still differentiates Legendary items is the number of affix slots and the +2 stat-score affixes; the per-affix value depth comes from ilvl, not tier.
>
> **Stat-score affixes (STR / DEX / CON / INT / WIS / CHA) are now Rare-or-better only, and capped at +2.** They no longer appear on Common or Uncommon drops. Rare and Epic stat scores roll +1; Legendary rolls +2.
>
> **Any rarity can drop at any item level.** An ilvl-1 mob can occasionally cough up a Legendary, and the values will scale appropriately for the level. Frequency tables are unchanged, so Legendary at low ilvl is still a memorable event, not the new default.

## Footer

- Design doc: `docs/plans/2026-04-25-affix-ilvl-scaling-and-stat-score-tightening-design.md`
- Implementation branch: `feat/affix-ilvl-scaling`
- Key commits:
  - `78365177` feat(progression): replace ilvlScale with endgameScale × spread
  - `1e12fc23` feat(loot): add IlvlScalable field to Nat20AffixDef
  - `600da5ef` feat(loot): add ilvl-scaling branch on Nat20AffixScaling.interpolate
  - `c44ef78a` feat(loot): route ModifierManager interpolate calls through the ilvlScalable branch
  - `ab901f75` feat(loot): tighten stat-score affixes to +1/+1/+2 (Rare+/Legendary), opt out of ilvl scaling
  - `65ac7955` feat(loot): retire rarityGateForIlvl clamp; all ilvls roll all rarities
