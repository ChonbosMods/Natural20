# Utility Affixes

## Overview

Utility affixes don't deal damage, debuff enemies, or directly help you win a fight. They change **how you move, how you breathe, or how you recover** between encounters. Three of them:

- **Focused Mind** : boosted mana regeneration while standing still.
- **Water Breathing** : greatly increased breath capacity underwater.
- **Lightweight** : reduced stamina cost while sprinting.

All three can roll on armor. Focused Mind can also roll on melee weapons. Each one stacks across multiple pieces of gear and is passive: no procs, no chance rolls, no cooldowns. They're quality-of-life affixes that make exploration and downtime smoother.

**Item-level scaling.** The Min and Max values shown below are the endgame ceiling at ilvl 45. Affix values scale linearly from 30% of the listed value at ilvl 1 to 100% at ilvl 45, so a roll on a low-ilvl drop will be smaller than what you see here.

---

## Detailed Explanation

### Focused Mind

**Where it rolls**: Melee weapons **and** armor.

**When it triggers**: Passive, checked every game tick. Two conditions must hold:

1. The player is **idle**: position delta since last tick is negligible (essentially, standing still).
2. Mana is **naturally regenerating** this tick (i.e., not at max, regen gating satisfied).

When both conditions line up, the natural mana regen tick is boosted. Any movement or mana-at-max stops the bonus immediately.

**What it does**: Multiplies the tick's natural regen delta by `1 + effectiveValue`. It doesn't regenerate mana on its own: it rides on top of the existing regen tick. If base regen wasn't firing, Focused Mind contributes nothing that tick.

**Rarity roll**:

| Rarity    | Min  | Max   |
|-----------|------|-------|
| Common    | 20%  | 40%   |
| Uncommon  | 35%  | 60%   |
| Rare      | 50%  | 80%   |
| Epic      | 70%  | 120%  |
| Legendary | 100% | 180%  |

**WIS scaling**: Factor 0.15 per point of WIS modifier.

**Softcap**: Knee at 300% (very loose). This is the highest softcap knee in the affix system: the intent is for stacked Focused Mind to be a dominant caster-idle strategy, not one that diminishes quickly.

**Stacking across pieces**: All equipped pieces with Focused Mind sum their effective values into one total bonus. A caster running Focused Mind on a staff plus three armor slots compounds meaningfully before softcap.

**Math**:

```
sumBonus       = Σ (pieceValue × (1 + 0.15 × WIS_modifier))
effectiveBonus = softcap(sumBonus, 3.0)
boostThisTick  = naturalRegenThisTick × effectiveBonus
```

**Notes**:
- Strictly an out-of-combat recovery tool: any movement during the tick breaks the buff, so you don't get mana back while kiting.
- Because it rides on native regen ticks, it feels smooth rather than bursty.
- "Idle" is a position check, not an action check: channeling a spell or attacking in place still counts as idle. Only physical movement breaks it.

---

### Water Breathing

**Where it rolls**: Armor only.

**When it triggers**: Passive. While equipped, the wearer's **max oxygen** is increased by the affix's percentage. The bonus is applied as a modifier on the Oxygen stat's MAX value.

**What it does**: Lets you stay underwater significantly longer before drowning. Doesn't prevent drowning, doesn't regenerate oxygen faster: it simply gives you a bigger oxygen pool to drain.

**Rarity roll**:

| Rarity    | Min  | Max   |
|-----------|------|-------|
| Common    | 20%  | 40%   |
| Uncommon  | 35%  | 60%   |
| Rare      | 50%  | 90%   |
| Epic      | 80%  | 130%  |
| Legendary | 100% | 180%  |

**WIS scaling**: Factor 0.15 per point of WIS modifier.

**Softcap**: Knee at 500% (extremely loose). Stacking Water Breathing on multiple pieces is genuinely rewarding: pre-cap total easily exceeds 200-300% with modest WIS.

**Stacking across pieces**: All equipped armor pieces with Water Breathing are summed into one bonus before softcap. Running it on four pieces with decent rolls can 3-5× your underwater breath budget.

**Math**:

```
sumBonus       = Σ (pieceValue × (1 + 0.15 × WIS_modifier))
effectiveBonus = softcap(sumBonus, 5.0)
maxOxygenBonus = 100 × effectiveBonus              // flat oxygen added to MAX
newMaxOxygen   = baseMaxOxygen + maxOxygenBonus
```

Changes are re-evaluated roughly once per second (20 ticks) so swapping armor in and out doesn't hammer the stat system. When the modifier updates, the current oxygen value is preserved (clamped to the new max).

**Notes**:
- Only the MAX is increased; oxygen still drains at the normal rate underwater.
- Has no interaction with any combat affix. Strictly underwater utility.
- Doesn't stack with vanilla water breathing effects in any special way; it's an additive modifier on MAX.

---

### Lightweight

**Where it rolls**: Armor only.

**When it triggers**: Passive, but **only while sprinting**. The system checks position delta per tick against a sprint threshold; when you're moving fast enough to be sprinting, and stamina is below max (confirming drain is actively happening), Lightweight compensates.

**What it does**: Reduces the effective stamina drain while sprinting. Base sprint drain is 1.0 stamina per second; Lightweight adds back a percentage of that drain every tick, so the wearer appears to lose stamina slower.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 8%  | 12% |
| Uncommon  | 10% | 18% |
| Rare      | 15% | 25% |
| Epic      | 20% | 30% |
| Legendary | 25% | 35% |

**DEX scaling**: Factor 0.12 per point of DEX modifier.

**Softcap**: Knee at 80%. Stacking to near-100% compensation (effectively infinite sprint) is prevented by the softcap, but high DEX + multiple Legendary pieces can get close to halving the drain.

**Stacking across pieces**: All equipped armor pieces with Lightweight sum into one reduction total before softcap.

**Math**:

```
sumReduction       = Σ (pieceValue × (1 + 0.12 × DEX_modifier))
effectiveReduction = softcap(sumReduction, 0.80)
perTickCompensation = dt × 1.0 × effectiveReduction   // stamina added back per tick
```

`dt` is the tick delta in seconds; at a 20 Hz tick rate, `dt ≈ 0.05`, so each tick restores `0.05 × effectiveReduction` stamina.

**Notes**:
- Only active while sprinting. Walking or standing still gets no benefit.
- Also gated on stamina being below max, so it doesn't overshoot when you're not actually draining.
- Doesn't affect stamina regen rate when not sprinting, and doesn't increase max stamina. Pure drain compensation.

---

### How they compose

These three don't interfere with each other and don't interact with combat affixes mechanically. Common pairings:

- **Focused Mind + Water Breathing** share WIS scaling and both roll on armor, so a WIS-heavy armor set naturally ends up with both: a "caster explorer" profile that recovers mana fast when idle and can dive for long underwater expeditions.
- **Lightweight** is the movement complement. It doesn't share a stat with the others (DEX, not WIS), so a stat-focused build tends to pick either caster utility (WIS) or movement utility (DEX) but not both at full efficiency.
- None of these compete with combat affixes for roll slots — they just compete with each other and with defensive rolls on armor, so a build that wants all three utility affixes sacrifices a good chunk of its defensive potential.
