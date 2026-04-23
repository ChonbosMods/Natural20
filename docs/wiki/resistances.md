# Elemental Resistances

## Overview

**Resistance** affixes roll on armor and shields. While equipped, they reduce incoming damage of a matching element by a percentage. There are four flavors: **Fire**, **Frost**, **Poison**, and **Void**.

Multiple pieces of the same resistance stack together before a softcap trims the extremes, so a full set built around one element is significantly more durable against that element than a single piece would suggest. Each element's resistance only reduces damage of that element: a target with fire resistance still takes full frost damage.

> Physical damage is not covered by this system. Physical reduction is handled by Hytale's native armor mechanics, not by a Natural 20 affix.

---

## Detailed Explanation

### What it is

A Resistance affix is a passive damage reducer carried on a piece of armor or a shield. Whenever the wearer takes elemental damage of the matching type, the incoming number is scaled down by the resistance percentage. Resistance never cancels damage outright in practice, because the softcap prevents it from reaching 100%.

Resistance is purely defensive: it doesn't trigger, doesn't cost anything to use, and doesn't have a cooldown. It simply sits on the gear and shrinks incoming hits.

### The four flavors

All four resistances share identical roll ranges, identical softcap behavior, and identical damage-reduction math. They differ only in which element they cover and what actually counts as "that element" of damage:

- **Fire Resistance** reduces fire damage (including lava, Ignite DOTs, and fire weapon affixes).
- **Frost Resistance** reduces frost damage (including Cold DOTs and frost weapon affixes).
- **Poison Resistance** reduces poison damage (including Infect DOTs and poison weapon affixes).
- **Void Resistance** reduces void damage (including Corrupt DOTs and void weapon affixes).

Each resistance is independent. Having fire resistance does nothing to frost damage and vice versa.

All four scale with **Intelligence (INT)**.

### What it rolls on

Armor slots and shields. Shields use the same "armor" category as body armor for affix rolling, so shields can also carry resistance.

Resistance cannot roll on weapons.

### How much damage it reduces

Every resistance affix rolls a base percentage within a range determined by the item's rarity:

| Rarity    | Min   | Max   |
|-----------|-------|-------|
| Common    | 5%    | 8%    |
| Uncommon  | 7%    | 12%   |
| Rare      | 10%   | 18%   |
| Epic      | 15%   | 24%   |
| Legendary | 20%   | 30%   |

Within a rarity band, the roll trends toward the top of the range with higher item level and quality.

### Stat scaling

All elemental resistances scale with **Intelligence (INT)**. The scaling factor is **0.12 per point of modifier**:

```
effectiveValue = baseValue × (1 + 0.12 × INT_modifier)
```

So +3 INT boosts a rolled resistance value by 36% of itself (e.g., a 20% roll becomes 27.2%). Negative modifiers reduce the value.

### Stacking across pieces

Every equipped armor piece and shield is scanned for resistance affixes. All matching resistance values are summed **before** the softcap:

```
summedResistance = piece1 + piece2 + piece3 + ...
```

A full armor set with fire resistance on every piece adds up the contributions from each slot. This is why stacking matters: five pieces each giving 15% add up to a much higher pre-softcap total than any single piece alone.

### Softcap

The summed total is passed through a softcap with a knee at **50%**. Below ~50%, returns are near-linear. As the summed value climbs past the knee, returns diminish smoothly along an asymptotic curve, so resistance cannot reach 100% no matter how much is stacked.

Because the softcap knee is at 50% (compared to Weakness's 60% knee), stacking resistance past the midpoint pays diminishing returns faster than stacking Weakness. Full immunity is not possible.

### The damage reduction formula

When an incoming hit's element matches an active resistance, the final damage is:

```
finalDamage = originalDamage × (1 − resistance)
```

So a resistance of 30% reduces a 100-damage hit to 70 damage. The reduced value is floored at zero.

### Order of operations with Weakness

Weakness and Resistance both apply in the "filter" stage of damage processing. Weakness amplifies (multiplies by `1 + W`) and Resistance reduces (multiplies by `1 − R`). Because both are multiplicative, the order doesn't matter for the final number:

```
finalDamage = originalDamage × (1 + weakness) × (1 − resistance)
```

So a target with a 30% Fire Weakness and 20% Fire Resistance, hit for 100 fire damage, takes:

```
100 × 1.30 × 0.80 = 104
```

Weakness and resistance partially cancel but don't cancel cleanly: this is by design.

### What counts as "that element"

The resistance system matches against **all** damage events that carry the element's tag. That includes:

- Direct elemental weapon damage from fire/frost/poison/void affixes.
- DOT ticks from Ignite, Cold, Infect, and Corrupt.
- Environmental elemental damage where the game's base damage types line up with ours (e.g., lava-tagged fire damage, poison-tagged damage from in-world sources).

### End-to-end example

A player wears four armor pieces, each with a Rare `Fire Resistance` roll averaging `15%`. Their INT modifier is **+2**. A shield they're carrying has an Epic `Fire Resistance` roll of `22%`. The target takes a single fire hit for `100` damage while having **no** weakness active.

1. Each piece's effective value with INT scaling: armor piece = `0.15 × (1 + 0.12 × 2) = 0.15 × 1.24 ≈ 18.6%`. Shield piece = `0.22 × 1.24 ≈ 27.3%`.
2. Summed across 4 armor + 1 shield: `4 × 18.6% + 27.3% ≈ 101.7%`. Way over the 50% softcap knee.
3. Softcap with K=0.50 asymptotically compresses this; the final effective value lands somewhere in the 65-75% range (the curve flattens aggressively past 100% pre-cap).
4. Incoming 100 fire damage × `(1 − 0.70) = 30` damage taken.

Compare: a single Legendary piece at max roll (30% base, +2 INT → 37.2%) with no stacking would reduce the same hit to `100 × (1 − 0.372) = 62.8`. Stacking an elemental set pays real dividends against focused elemental damage; hybrid builds spread across multiple elements are much less efficient per piece but cover more incoming types.
