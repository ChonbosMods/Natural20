# Offensive Weapon Affixes

## Overview

Offensive affixes roll on weapons (mostly melee, some ranged) and change how your attacks feel or how they hit. There are twelve of them:

- **Crit Chance** : chance for each hit to critically strike.
- **Crit Damage** : how much bonus damage a critical strike does.
- **Attack Speed** : how fast you swing the weapon.
- **Deep Wounds** : applies a bleed DOT on hit.
- **Crushing Blow** : bonus damage based on the target's current HP.
- **Backstab** : bonus damage when the target isn't facing you.
- **Precision** : ignores part of the target's armor.
- **Life Leech** : heal a percentage of damage dealt.
- **Mana Leech** : restore a percentage of damage dealt as mana.
- **Vicious Mockery** : debuff on target, amplifies all incoming damage to them.
- **Hex** : one-shot curse on target, consumed by the next hit for bonus damage.
- **Rally** : on kill, nearby allies receive a temporary damage bonus.

Some of these are passive (always active while wielding), some are conditional (only under specific circumstances), some are chance-based (roll every hit), and some are consumable (applied on hit, consumed by follow-up damage). They stack with each other freely, and many of them scale with ability scores: DEX, STR, WIS, INT, and CHA all feed into the effective numbers depending on the affix. A few (Rally, Vicious Mockery, Hex) are party-facing — they amplify damage for allies or mark targets for the whole group — so they slot in as support offense for group play.

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

### Life Leech

**Where it rolls**: Melee weapons and ranged weapons.

**When it triggers**: Every non-DOT hit. No proc chance: it fires on every swing.

**What it does**: Converts a percentage of the final damage number into health for the attacker. The percentage is calculated against the post-modification damage (so crits, weakness, and other multipliers feed larger leech numbers).

**Rarity roll**:

| Rarity    | Min | Max  |
|-----------|-----|------|
| Common    | 2%  | 4%   |
| Uncommon  | 3%  | 6%   |
| Rare      | 5%  | 8%   |
| Epic      | 7%  | 10%  |
| Legendary | 8%  | 12%  |

**DEX scaling**: Factor 0.12 per point of DEX modifier.

**Softcap**: Knee at 20% (tight). Even a Legendary max-roll wielder with high DEX won't push meaningfully past a quarter of damage as heal.

**Math**:

```
effectivePercent = softcap(baseValue × (1 + 0.12 × DEX_modifier), 0.20)
healAmount       = finalDamage × effectivePercent
```

**Notes**:
- The heal is applied directly to the attacker's Health stat, not through the damage pipeline, so it can't overkill or over-heal beyond max. The stat system clamps it.
- Visible via the Nat20_LifeLeech particle at the attacker's torso when the heal lands.
- Leech runs off the damage value *after* other systems have modified it (weakness, crit, backstab, etc. all boost the leech number).

---

### Mana Leech

**Where it rolls**: Melee weapons and ranged weapons.

**When it triggers**: Every non-DOT hit. No proc chance.

**What it does**: Identical to Life Leech, but restores mana instead of health.

**Rarity roll** (identical to Life Leech):

| Rarity    | Min | Max  |
|-----------|-----|------|
| Common    | 2%  | 4%   |
| Uncommon  | 3%  | 6%   |
| Rare      | 5%  | 8%   |
| Epic      | 7%  | 10%  |
| Legendary | 8%  | 12%  |

**INT scaling**: Factor 0.12 per point of INT modifier.

**Softcap**: Knee at 20%.

**Math**:

```
effectivePercent = softcap(baseValue × (1 + 0.12 × INT_modifier), 0.20)
manaRestore      = finalDamage × effectivePercent
```

**Notes**:
- Visible via the Nat20_ManaLeech particle on the attacker.
- Only reads the weapon in hand for the affix (armor pieces can't carry it).

---

### Vicious Mockery

**Where it rolls**: Melee weapons and ranged weapons.

**When it triggers**: Every non-DOT hit rolls proc chance. On a successful proc, the target is marked with a debuff.

**What it does**: While the debuff is active, **all incoming damage** to the target from any source is amplified, regardless of damage type. Functionally similar to Weakness, but universal: it doesn't care if the damage is fire, physical, bleed, or void.

**Duration**: **8 seconds** from the last qualifying proc. A successful re-proc refreshes the timer. When multiple procs roll different values, **last proc wins** (the most recent value replaces whatever was previously stored).

**Proc chance**: Base proc chance is **60%** per hit, scaled by **WIS** with a factor of 0.15:

```
procChance = min(1.0, 0.60 × (1 + 0.15 × WIS_modifier))
```

- **0 WIS modifier**: 60% per hit
- **+2 WIS**: 78% per hit
- **+4 WIS**: 96% per hit
- **+5 WIS or higher**: 100% per hit (cap)

WIS (not CHA) drives proc reliability so CHA stays focused on the strength of the debuff itself.

**Rarity roll** (amplify percentage):

| Rarity    | Min  | Max  |
|-----------|------|------|
| Common    | 8%   | 12%  |
| Uncommon  | 10%  | 16%  |
| Rare      | 14%  | 22%  |
| Epic      | 20%  | 30%  |
| Legendary | 25%  | 35%  |

**CHA scaling**: Factor 0.15 per point of CHA modifier. Scales the amplify value only.

**Softcap**: Knee at 50%.

**Math**:

```
effectiveValue   = softcap(baseValue × (1 + 0.15 × CHA_modifier), 0.50)
finalDamage      = originalDamage × (1 + effectiveValue)   // while debuff active
```

(Amplification applies as a damage filter to any incoming hit while the debuff is active. A caster-leaning support build invests WIS for landing the debuff reliably, CHA for making it hurt.)

**Notes**:
- Applies to party members' damage too. Marking a target with Vicious Mockery helps the whole party, not just the wielder.
- The debuff is visual: a Nat20ViciousMockeryEffect particle floats on the target while active.
- Particle is gated against re-firing on refresh (the "applied once" pattern) to prevent stacking artifacts.

---

### Hex

**Where it rolls**: Melee weapons and ranged weapons.

**When it triggers**: Every non-DOT hit applies a **curse** to the target. The curse sits until it's consumed: the next incoming damage to the target (from any source) is amplified by the curse's bonus, and the curse is removed. Any subsequent damage to the same target is no longer amplified until a fresh Hex lands.

This is a **one-shot** debuff: one-hit amplification only, not a duration-based multiplier.

**Duration (as grace period)**: 15 seconds. If the cursed target isn't hit again within that window, the curse expires unused.

**Rarity roll**:

| Rarity    | Min  | Max  |
|-----------|------|------|
| Common    | 15%  | 25%  |
| Uncommon  | 20%  | 35%  |
| Rare      | 30%  | 50%  |
| Epic      | 45%  | 65%  |
| Legendary | 60%  | 80%  |

**WIS scaling**: Factor 0.18 per point of WIS modifier (highest scaling factor on any weapon affix).

**Softcap**: Knee at 100%.

**Math**:

```
effectiveBonus   = softcap(baseValue × (1 + 0.18 × WIS_modifier), 1.0)
nextIncomingHit  = originalDamage × (1 + effectiveBonus)   // consumed after this hit
```

**Known issue — self-consume (deferred rework)**: Today, because Hex is applied when your swing resolves and consumed when damage lands, a Hex-wielding player's own subsequent swings consume their own curse *and* benefit from the bonus. The intended design is that Hex should only be consumed by, and only amplify, damage from sources **other than** the item that applied it (teammates, DOT ticks, off-hand, environmental), rewarding setup play rather than becoming a flat self-buff. This will be addressed in a future combat polish pass.

The current (not-final) swing pattern:

1. First hit: applies Hex. No bonus on this hit (there was no existing Hex to consume).
2. Second hit: consumes the Hex from hit #1 (bonus damage applies to this swing), then applies a fresh Hex.
3. Third hit: consumes the Hex from hit #2 (bonus damage), then applies a fresh Hex.
4. …and so on.

A player who shares a target with a teammate also feeds them Hex bonuses: any damage the target takes consumes the curse, even when the Hex applier isn't the one landing the follow-up.

**Notes**:
- Visual: Nat20HexEffect particle on the cursed target. On consumption, the visual is forcibly shortened to 0.1 s so the particle fades quickly; the particle stream is then not re-emitted until a new Hex is applied.
- The Hex bonus applies to raw incoming damage before resistance/weakness filters; the math commutes so order doesn't matter for the final number.

---

### Rally

**Where it rolls**: Melee weapons and ranged weapons.

**When it triggers**: On a **killing blow** from a weapon carrying the Rally affix. Specifically, when the swing's damage is greater than or equal to the target's current HP. DOT tick kills do not trigger Rally; only direct weapon hits do.

**What it does**: On kill, every **other** player within a **20-block** radius of the killer receives a short-lived damage-amplification buff. The killer themselves is explicitly excluded: Rally is strictly a party-support affix. Solo players get nothing from it.

**Duration**: **12 seconds** from the kill. Subsequent kills refresh the timer and replace the buff value (last kill wins, not strongest).

**Rarity roll** (damage amplification percentage):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 5%  | 8%  |
| Uncommon  | 7%  | 12% |
| Rare      | 10% | 18% |
| Epic      | 13% | 22% |
| Legendary | 15% | 25% |

**CHA scaling**: Factor 0.15 per point of CHA modifier.

**Softcap**: Knee at 40%.

**Math**:

```
effectiveValue = softcap(baseValue × (1 + 0.15 × CHA_modifier), 0.40)
buffedAllyHit  = original × (1 + effectiveValue)   // applied to outgoing damage for 12s
```

The amplification is applied as a damage filter on all outgoing damage from buffed allies while the 12-second buff is active. Elemental, physical, DOT ticks from the buffed player — all get amplified equally.

**Example**: Attacker with +3 CHA wields a Rare axe rolled with `15%` Rally. Two party members are standing within 20 blocks when the attacker lands a killing blow on a mob.

- Effective rally: `0.15 × (1 + 0.15 × 3) = 0.15 × 1.45 ≈ 21.8%`. Below the 40% knee; near-linear.
- Both party members gain a `+21.8%` damage buff for 12 seconds.
- The attacker themselves does *not* gain the buff.
- If the attacker scores another kill in that window, the two party members' buffs refresh to the new roll's value and timer.

**Notes**:
- Kill detection is pre-damage-application: it checks `currentHP <= incomingDamage`. Multiple rally weapons landing the same killing blow only fire once (the loop returns after the first matched affix).
- The spatial query scans all entities within 20 blocks, then filters to only Player entities. NPCs and mobs are not eligible allies.
- Rally and Vicious Mockery can both be up on the same battlefield: Rally amps buffed allies' outgoing damage, Mockery amps everyone's outgoing damage against a specific target. They multiply when both apply.

---

### How they compose

Many of these affixes pile up into a single hit. For a typical melee swing with the whole bundle:

1. The base swing damage is calculated (weapon + STR contributions).
2. **Hex** (if the target was previously cursed): consume the curse and multiply the hit by `1 + hexBonus`.
3. **Crit** rolls; if it fires, the damage is multiplied by the crit multiplier.
4. **Backstab** checks the angle; if in the rear arc, damage is multiplied by `1 + backstabBonus`.
5. **Vicious Mockery** (if the target has the debuff active): multiply damage by `1 + mockeryBonus`.
6. **Precision** reads the target's armor and pre-compensates for what would have been blocked.
7. Vanilla armor reduction applies (weakened by Precision's compensation).
8. Elemental weakness/resistance apply if the damage type is elemental.
9. Damage lands, and the target takes it.
10. **Life Leech** and **Mana Leech** read the final damage number and convert percentages into health/mana for the attacker.
11. **Deep Wounds** rolls proc and applies a Bleed DOT on success.
12. **Vicious Mockery** rolls proc and applies the debuff to the target.
13. **Hex** applies a fresh curse to the target (whether or not one was consumed this hit).
14. **Crushing Blow** runs independently: reads target's current HP and drains a flat percentage directly, bypassing the entire pipeline.
15. If the hit killed the target and the weapon carries **Rally**, the on-kill buff fires for every other player within 20 blocks.

Attack Speed is orthogonal to everything else: it changes how often all of the above happens, not how hard each hit lands.

**Self-facing vs. party-facing**: Crit, Backstab, Deep Wounds, Crushing Blow, Precision, Attack Speed, Life Leech, and Mana Leech all affect the wielder's own damage / sustain directly. Vicious Mockery, Hex, and Rally are party-facing: they amplify damage dealt by *anyone* hitting the debuffed target (Mockery, Hex) or by *other* players near a kill (Rally). A support-leaning wielder can effectively multiply a whole party's DPS without needing to top damage charts personally.

A well-stacked melee build mixes categories: Crit (burst), Backstab (positional multiplier), Deep Wounds (sustain DPS), Crushing Blow (anti-boss), Attack Speed (rate), Precision (anti-armor), Life Leech (self-sustain), Mana Leech (resource), Vicious Mockery + Hex (setup debuffs), Rally (party support on kill). No two offensive affixes are strictly redundant with each other.
