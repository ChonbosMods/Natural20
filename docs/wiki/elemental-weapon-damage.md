# Elemental Weapon Damage

## Overview

Some weapons carry an **Elemental Damage** affix that adds flat elemental damage on top of every hit. There are four flavors: **Fire**, **Frost**, **Poison**, and **Void**. They stack with each other (a weapon rolled with both fire and frost deals both), they stack with regular weapon damage, and they can be amplified by the wielder's Intelligence.

Elemental hits are their own damage events, so they interact with other elemental systems: **Weakness** amplifies them, **Resistance** reduces them, and matching elemental DOTs like Ignite or Cold also fire off the same hit if those affixes are present.

---

## Detailed Explanation

### What it is

An Elemental Damage affix adds a flat chunk of elemental damage to every successful melee or ranged hit. Unlike percentage-based affixes, the value is a flat number: "+6 fire damage per hit," not "+6% damage."

Each elemental hit is delivered as a **separate damage event** tagged with the element's damage type. This matters because downstream systems (weakness, resistance, INT scaling) only act on damage events with the matching element tag.

### The four flavors

All four share identical mechanics and rolls; only the element tag differs:

- **Fire** damage
- **Frost** damage
- **Poison** damage
- **Void** damage

A single weapon can carry multiple elemental damage affixes (e.g., a sword with both fire and frost). Each one fires its own damage event on every hit, so they stack additively.

### When it triggers

Every non-DOT melee or ranged hit from the wielder. DOT ticks from other affixes (Ignite, Cold, Infect, Corrupt) do **not** re-trigger elemental damage, which prevents cascades. Elemental damage events also don't re-trigger themselves: a fire-damage secondary hit will not recursively spawn another fire-damage hit.

### How much damage it adds

Every elemental affix rolls a **flat damage value** within a range determined by the item's rarity:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 1   | 2   |
| Uncommon  | 2   | 4   |
| Rare      | 4   | 7   |
| Epic      | 7   | 11  |
| Legendary | 11  | 16  |

Within a rarity band, the roll trends toward the top of the range with higher item level and quality.

### INT scaling

The affix itself has **no stat scaling**: a Rare fire affix rolls the same 4-7 whether you have 8 INT or 20 INT. Instead, the scaling happens downstream: **any** elemental damage the player deals, including from affixes, DOTs, and environmental sources, gets boosted by the wielder's **Intelligence (INT)** modifier.

```
elementalBonus = max(0, INT_modifier) × 10
finalElementalDamage = rolledValue + elementalBonus
```

The bonus applies per elemental hit, is flat (not multiplicative), only counts positive INT modifiers, and is skipped on DOT ticks (the DOT's own source damage is boosted at application time, not every tick).

Example: A player with +4 INT hits with a Rare fire sword that rolled a 6 flat-damage value. The secondary fire hit is `6 + (4 × 10) = 46` fire damage before any amplification or resistance.

### STR vs INT

Physical damage uses STR, elemental damage uses INT, never both. The primary swing (physical) scales with STR; the elemental secondary hit scales with INT. This means a hybrid weapon with elemental affixes benefits from investing in both stats: STR grows the swing, INT grows the elemental chunk.

### Interaction with other systems

Because the elemental damage is a distinct damage event with an elemental tag, it interacts with everything else that cares about elements:

- **Elemental Weakness** on the target: amplifies the hit by whatever percentage the weakness is worth.
- **Elemental Resistance** on the target: reduces the hit by whatever percentage the resistance is worth.
- **Elemental DOTs** (Ignite, Cold, Infect, Corrupt): if the same weapon *also* has the matching DOT affix, both fire off the same swing. The flat elemental damage hits immediately; the DOT applies a stack that ticks over time.

Order of operations matters for the damage math: the rolled flat value and INT bonus are added first, then weakness/resistance scale the total.

### End-to-end example

A player with +3 INT wields a **Rare** sword that rolled `+5 fire damage`. The target has a **30% Fire Weakness** active and no Fire Resistance. The player lands a normal sword hit:

1. The primary physical swing resolves using STR as usual (unaffected by these affixes).
2. A secondary fire damage event spawns: base = 5 (rolled value).
3. INT scaling adds `3 × 10 = 30`: total becomes 35 fire damage.
4. Fire Weakness amplifies by 30%: `35 × 1.30 = 45.5` fire damage.
5. The target takes 45.5 fire damage on top of the physical swing.

If the sword also carried an **Ignite** affix, a fire DOT stack would also be applied on the same swing and would tick for its own (also weakness-amplified) damage over time.
