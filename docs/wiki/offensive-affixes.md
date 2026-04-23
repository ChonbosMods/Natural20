# Offensive Weapon Affixes

## Overview

Offensive affixes roll on weapons (mostly melee, some ranged) and change how your attacks feel or how they hit. There are seven of them:

- **Crit Chance** : chance for each hit to critically strike.
- **Crit Damage** : how much bonus damage a critical strike does.
- **Attack Speed** : how fast you swing the weapon.
- **Deep Wounds** : applies a bleed DOT on hit.
- **Crushing Blow** : bonus damage based on the target's current HP.
- **Backstab** : bonus damage when the target isn't facing you.
- **Precision** : ignores part of the target's armor.

Some of these are passive (always active while wielding), some are conditional (only under specific circumstances), and some are chance-based (roll every hit). They stack with each other freely, and several of them double-dip with ability scores: DEX, STR, and to a lesser extent the native attack rhythm of the weapon all feed into their effective numbers.

---

## Detailed Explanation

### Crit Chance & Crit Damage

These two affixes are paired: one rolls your chance to crit, the other rolls how hard a crit hits. They're documented together because they're meaningless in isolation.

**When it triggers**: Every non-DOT melee hit rolls once for crit. A successful roll turns that hit into a critical strike: damage is multiplied and the damage-text color swaps to the crit-gold tag.

**Crit chance baseline (DEX)**: Even without any Crit Chance affix, every point of DEX modifier adds crit chance as a linear baseline. The DEX baseline is **1.5% per point of modifier**, and it does not go through the affix softcap:

```
dexBaseline = DEX_modifier × 0.015
```

So +10 DEX modifier gives a flat 15% baseline crit chance, on top of anything from affixes.

**Crit chance affix value**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 3%  | 5%  |
| Uncommon  | 5%  | 8%  |
| Rare      | 7%  | 12% |
| Epic      | 10% | 18% |
| Legendary | 15% | 25% |

The affix portion is softcapped with a knee at **30%** and then added to the DEX baseline:

```
critChance = min(1.0, dexBaseline + softcap(affixChance, 0.30))
```

The 100% cap is a hard ceiling, never a mechanical state that's feasible in normal play.

**Crit damage baseline (STR)**: The base crit multiplier is **1.5×** (i.e., a crit deals 150% of normal damage by default). STR modifier adds linearly to this multiplier:

```
strBaseline = STR_modifier × 0.15
```

So +4 STR modifier gives +0.6 to the multiplier all by itself.

**Crit damage affix value**:

| Rarity    | Min  | Max  |
|-----------|------|------|
| Common    | 10%  | 20%  |
| Uncommon  | 15%  | 30%  |
| Rare      | 25%  | 50%  |
| Epic      | 40%  | 75%  |
| Legendary | 60%  | 100% |

The affix portion softcaps with a knee at **200%** (very high; in practice the curve is near-linear across all reasonable rolls). Assembled multiplier:

```
critMultiplier = 1.5 + strBaseline + softcap(affixDamage, 2.0)
finalDamage = originalDamage × critMultiplier
```

**Example**: a player with +8 DEX modifier and +3 STR modifier wields a Rare sword rolled with `+10% Crit Chance` and `+40% Crit Damage`. They hit a target for 100 damage.

- Crit chance: `(8 × 0.015) + softcap(0.10, 0.30) ≈ 0.12 + 0.093 ≈ 21.3%` per hit.
- On the 21% of hits that crit: multiplier = `1.5 + (3 × 0.15) + softcap(0.40, 2.0) ≈ 1.5 + 0.45 + 0.40 = 2.35×`.
- Crit damage: `100 × 2.35 = 235`.
- Non-crit hits remain at 100.

---

### Attack Speed

**When it triggers**: Passive. While wielding a melee weapon with the Attack Speed affix, every server tick nudges the player's interaction timing to complete faster. This translates to faster swings, faster tool use, and tighter attack chains.

**Stat requirement**: **DEX 12** minimum. If the wielder's raw DEX is below 12, the affix silently does nothing. This is an absolute stat check, not a modifier check.

**Rarity roll**:

| Rarity    | Min | Max  |
|-----------|-----|------|
| Common    | 1%  | 2%   |
| Uncommon  | 2%  | 3%   |
| Rare      | 3%  | 5%   |
| Epic      | 4%  | 7%   |
| Legendary | 6%  | 10%  |

**DEX scaling**: Factor 0.10 per point of DEX modifier:

```
effectiveSpeed = softcap(baseValue × (1 + 0.10 × DEX_modifier), 0.35)
```

**Softcap**: Knee at 35%.

**Notes**:
- Only one Attack Speed affix is applied from the equipped weapon. Other weapons on the player don't contribute.
- Switching to a weapon without the affix removes the speed buff on the next tick.
- The bonus is implemented as a "time shift" on Hytale's interaction chains, so it lands cleanly at production values (the tail frames of the swing animation trim rather than the animation jittering).

---

### Deep Wounds

**When it triggers**: Every non-DOT melee hit rolls proc chance. On a successful proc, a **Bleed** DOT is applied to the target for a rolled duration.

Note: Deep Wounds uses the same bleed DOT machinery as the elemental DOT system, so the bleed ticks on the shared 2-second beat, the same-type-blocks-new-procs rule applies (a Bleed already ticking on the target blocks further Bleed applications until it ends), and total damage is preserved across shorter duration rolls.

**Proc chance**: Base proc chance is **60%** per hit, scaled by **STR** with a factor of 0.18 per point of modifier (the same factor the affix uses for per-tick damage). Capped at 100%:

```
procChance = min(1.0, 0.60 × (1 + 0.18 × STR_modifier))
```

- **0 STR modifier**: 60% per hit
- **+2 STR**: 81.6% per hit
- **+4 STR or higher**: 100% per hit (cap)

So a STR-invested melee build hits the ceiling at +4 modifier, while low-STR wielders lose about two in five procs.

**Rarity roll** (per-tick damage at max duration):

| Rarity    | Min  | Max   |
|-----------|------|-------|
| Common    | 1.0  | 2.0   |
| Uncommon  | 1.5  | 3.0   |
| Rare      | 2.0  | 4.5   |
| Epic      | 3.5  | 7.0   |
| Legendary | 5.0  | 10.0  |

**STR scaling on damage**: The same 0.18 factor that boosts proc chance also boosts per-tick damage:

```
effectivePerTick = rolledValue × (1 + 0.18 × STR_modifier)
```

STR pulls double duty for Deep Wounds: reliability (proc rate) and output (per-tick damage) both scale with it.

**Duration**: Rolled at item-craft time, uniformly between 5 and 15 seconds. Shorter rolls preserve total damage, so they hit harder per tick and are the better roll.

**Tick math**: Ticks every 2 seconds.
```
totalDamage   = effectivePerTick × (15 / 2)              // i.e., × 7.5
damagePerTick = totalDamage / (rollDuration / 2)
```

**Damage cause**: `Nat20Bleed` — its own DOT type, not overlapping with elemental DOTs. A target can have Bleed + Ignite + Cold + Infect + Corrupt all at once (five different DOT types, each on its own lifetime, all ticking together on the shared 2-second beat).

**Interactions**: Bleed ticks carry the `Nat20Bleed` tag and do not go through the elemental weakness or resistance systems. They tick through Hytale's native damage pipeline, so they are visible to the combat particle system (red bleed overlay) and to death attribution.

---

### Crushing Blow

**When it triggers**: Every non-DOT melee or ranged hit. No proc chance.

**What it does**: Directly subtracts a percentage of the target's **current HP** from their health. It doesn't go through the damage pipeline, so it is not reduced by resistances and does not trigger thorns or other on-damage-taken effects.

Because the drain scales with current HP, Crushing Blow is most effective early in a fight against high-HP targets (bosses, elites) and tapers off naturally as the target's HP drops.

**Rarity roll** (percent of current HP drained per hit):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 2%  | 4%  |
| Uncommon  | 3%  | 5%  |
| Rare      | 4%  | 7%  |
| Epic      | 6%  | 10% |
| Legendary | 8%  | 12% |

**STR scaling**: Factor 0.15 per point of STR modifier.

**Softcap**: Knee at 20% (tight). Even a high Legendary roll with high STR will struggle to push the percentage significantly past 20% per hit.

**Math**:

```
effectivePercent = softcap(baseValue × (1 + 0.15 × STR_modifier), 0.20)
drain            = target.currentHP × effectivePercent
```

**Example**: a player with +3 STR modifier wields a Rare weapon rolled with `5.5%` Crushing Blow. Target has 400 HP when hit.

- Effective percent: `0.055 × (1 + 0.45) = 0.055 × 1.45 ≈ 8.0%`. Well below softcap knee, so nearly linear.
- Drain: `400 × 0.08 = 32` HP, removed directly.
- If the same weapon deals 50 base damage, the target now effectively takes `50` (weapon hit, reduced by vanilla armor as normal) plus `32` (Crushing Blow drain, unaffected by armor) on this swing.

**Important**: Because the drain bypasses the damage pipeline, it doesn't interact with Weakness, Resistance, Precision, Backstab, crits, or any other multiplier. It's a flat percentage-of-current-HP chunk added alongside the normal hit.

---

### Backstab

**When it triggers**: A non-DOT melee or ranged hit while the attacker is in the **rear 120° arc** of the target's facing direction.

The check is angle-based, using the target's yaw (horizontal facing) and the XZ vector from target to attacker. "Behind" is defined as the dot product of those two vectors being less than −0.5, equivalent to the attacker being somewhere in a 120-degree cone directly behind the target.

**Rarity roll** (damage multiplier bonus):

| Rarity    | Min | Max  |
|-----------|-----|------|
| Common    | 15% | 25%  |
| Uncommon  | 20% | 35%  |
| Rare      | 30% | 45%  |
| Epic      | 40% | 60%  |
| Legendary | 50% | 75%  |

**DEX scaling**: Factor 0.15 per point of DEX modifier.

**Softcap**: Knee at 100% (very loose; near-linear across the whole reasonable range).

**Math**:

```
effectiveBonus = softcap(baseValue × (1 + 0.15 × DEX_modifier), 1.0)
finalDamage    = originalDamage × (1 + effectiveBonus)
```

**Example**: Attacker with +4 DEX modifier wields an Epic dagger rolled with `50%` Backstab. They hit a target from behind for a base 80 damage.

- Effective bonus: `0.50 × (1 + 0.60) = 0.80` (80%). Well below the 100% knee; nearly linear.
- Final damage: `80 × 1.80 = 144`.
- If the same attacker swings from the front, Backstab does not fire and the hit does its base 80.

**Notes**:
- The check is purely geometric: whether the target is "alerted to you" or "in combat with you" doesn't matter, only which way they're facing.
- Targets that don't expose a meaningful yaw (certain static entities) simply won't trigger Backstab.
- Ranged hits are eligible — a shot to the back of a fleeing target procs it.

---

### Precision

**When it triggers**: Every non-DOT melee hit against a target wearing armor. Weapon must carry the Precision affix.

**What it does**: Ignores a percentage of the target's effective armor mitigation for this hit. Implementation-wise: it reads the target's armor and simulates how much damage the armor would block for this hit's damage type, then adds a percentage of that blocked amount back into the damage.

So Precision only helps against armored targets, and it scales up naturally against heavier armor: the more the target was going to block, the more Precision gives back.

**Rarity roll** (fraction of armor's blocked damage to restore):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 5%  | 10% |
| Uncommon  | 8%  | 15% |
| Rare      | 12% | 20% |
| Epic      | 15% | 27% |
| Legendary | 20% | 35% |

**DEX scaling**: Factor 0.12 per point of DEX modifier.

**Softcap**: Knee at 40%.

**Math**:

```
effectivePen   = softcap(baseValue × (1 + 0.12 × DEX_modifier), 0.40)
armorBlocked   = howMuch = armor would reduce this hit (vanilla rules)
finalDamage    = originalDamage + armorBlocked × effectivePen
```

**Example**: Attacker with +3 DEX modifier wields a Rare bow rolled with `18%` Precision. They shoot a heavily armored target whose armor would block 30 damage out of the 80 incoming (i.e., reduce it to 50 damage).

- Effective penetration: `0.18 × (1 + 0.36) = 0.18 × 1.36 ≈ 24.5%`. Below the 40% knee.
- Armor blocked: 30.
- Restored damage: `30 × 0.245 ≈ 7.4`.
- Final damage: `50 + 7.4 ≈ 57.4` (or equivalently: 80 incoming, armor blocks 30, Precision gives back 7.4).

**Notes**:
- Does nothing against unarmored targets: armor-blocked = 0, penetration of 0 is 0.
- Doesn't interact with Natural 20's elemental resistance system, only with Hytale's native armor mitigation.
- Damage causes that bypass resistances in the first place (certain vanilla "true damage" types) skip Precision entirely.

---

### How they compose

Many of these affixes pile up into a single hit. For a typical melee swing with the whole bundle:

1. The base swing damage is calculated (weapon + STR contributions).
2. **Crit** rolls; if it fires, the damage is multiplied by the crit multiplier.
3. **Backstab** checks the angle; if in the rear arc, damage is multiplied by `1 + backstabBonus`.
4. **Precision** reads the target's armor and pre-compensates for what would have been blocked.
5. Vanilla armor reduction applies (weakened by Precision's compensation).
6. Elemental weakness/resistance apply if the damage type is elemental.
7. Damage lands, and the target takes it.
8. **Deep Wounds** rolls proc and applies a Bleed DOT on success.
9. **Crushing Blow** runs independently: reads target's current HP and drains a flat percentage directly, bypassing the entire pipeline.

Attack Speed is orthogonal to everything else: it changes how often all of the above happens, not how hard each hit lands.

A well-stacked melee build mixes categories: Crit (burst), Backstab (positional multiplier), Deep Wounds (sustain DPS), Crushing Blow (anti-boss), Attack Speed (rate), Precision (anti-armor). No two offensive affixes are strictly redundant with each other.
