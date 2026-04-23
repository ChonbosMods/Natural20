# Elemental Weakness

## Overview

Some weapons can apply an **Elemental Weakness** to the things you hit. While a target has a weakness active, all damage of that matching element hits harder, from any source.

There are four weaknesses, one per element: **Fire**, **Frost**, **Void**, and **Poison**. They're independent, stack across elements, and wear off on their own after a short time if you stop hitting the target.

---

## Detailed Explanation

### What it is

An Elemental Weakness is a short-lived debuff that a player puts on a target with a weapon affix. The debuff says "this target takes X% more damage from a specific element." Anything that deals that element's damage, whether the wielder's own attacks, a DOT tick from another source, an environmental hazard, or a different party member's spell, is amplified while the debuff is active.

### The four flavors

There is one weakness per element:

- **Fire Weakness** amplifies fire damage (including lava and ignite DOTs).
- **Frost Weakness** amplifies ice/cold damage (including cold DOTs).
- **Poison Weakness** amplifies poison damage (including infect DOTs).
- **Void Weakness** amplifies void damage (including corrupt DOTs).

They're tracked separately. One target can be suffering from all four at once, and each one only cares about its own element: a Fire Weakness will never amplify frost damage.

### Applying it

Weakness affixes appear on melee and ranged weapons. Every qualifying hit with an affixed weapon refreshes the debuff on whatever you hit. The effect lasts **5 seconds** from the most recent hit, so sustained combat keeps it up indefinitely and the particle effect only triggers when it's freshly applied.

If you hit the same target with multiple different elemental-weakness weapons, each element's entry is tracked independently. If you re-hit with the same element but a weaker roll, the new (weaker) value replaces the old one: **last hit wins**, not strongest.

DOT ticks coming off the target (like a fire DOT you already applied) do **not** refresh weakness. Only fresh incoming hits do. This prevents a single applied DOT from indefinitely keeping its own weakness alive.

### How much damage it adds

Every weakness affix rolls a **base multiplier** within a range determined by the item's rarity:

| Rarity    | Min Base | Max Base |
|-----------|----------|----------|
| Common    | 10%      | 15%      |
| Uncommon  | 13%      | 20%      |
| Rare      | 18%      | 28%      |
| Epic      | 25%      | 38%      |
| Legendary | 30%      | 45%      |

Within a rarity band, where the roll lands in the range depends on the item's level (higher item level trends toward the top of the range).

### Stat scaling

All four weaknesses scale with the wielder's **Wisdom (WIS)**. The scaling factor is **0.15 per point of WIS modifier**:

```
effectiveValue = baseValue × (1 + 0.15 × WIS_modifier)
```

So a WIS modifier of +3 boosts a rolled base value by 45% of itself. A Rare weakness rolling a 23% base becomes roughly `0.23 × 1.45 ≈ 33%` at +3 WIS.

### Softcap

To prevent stacking into absurd territory at high WIS, the effective value is passed through a softcap with a knee at **60%**. Below ~60%, values are nearly linear with the formula above. As the pre-cap value climbs past the knee, returns diminish smoothly along an asymptotic curve, so the multiplier never reaches 100% but gets closer and closer.

You can think of it as: "the formula gives you what you rolled up to about 60%, and after that you're fighting diminishing returns."

### How the damage math works

When an incoming hit is about to land on a target that has the matching weakness active:

```
finalDamage = originalDamage × (1 + multiplier)
```

So a multiplier of 0.30 increases the hit by 30%. A 40-damage fire DOT tick on a target with a 30% fire weakness becomes `40 × 1.30 = 52` damage.

Amplification only happens if the incoming damage's element matches the weakness. Physical, fall, bleed, and other non-elemental sources are unaffected even if an elemental weakness is active on the target.

### End-to-end example

A player holds a **Rare** sword rolled with `Fire Weakness` at ~23% base. Their WIS modifier is +2, so effective weakness is roughly `0.23 × 1.30 ≈ 30%` (softcap is a rounding error here because it's well below the 60% knee).

1. Player hits the target with the sword. Fire Weakness is applied for 5 seconds; the fire-weakness particle effect plays.
2. The same player's Ignite DOT (from another affix) ticks for 40 fire damage. Because the target has Fire Weakness, the tick becomes `40 × 1.30 = 52` damage.
3. A party member launches a frost spell for 50 damage. Target has no Frost Weakness, so the frost damage is unchanged: 50.
4. Player keeps meleeing; each qualifying hit refreshes Fire Weakness's 5-second timer.
5. Player stops attacking for 5+ seconds. Fire Weakness expires; subsequent fire damage is no longer amplified.
