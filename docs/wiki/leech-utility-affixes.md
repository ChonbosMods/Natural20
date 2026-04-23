# Leech & Utility Affixes

## Overview

The leech and utility affixes don't add raw damage on their own. They feed your resources, debuff the target, punish attackers, or help you regenerate. Six of them:

- **Life Leech** : heal a percentage of damage dealt.
- **Mana Leech** : restore a percentage of damage dealt as mana.
- **Focused Mind** : boosted mana regeneration while standing still.
- **Vicious Mockery** : debuff on target, amplifies all incoming damage to them.
- **Hex** : one-shot curse on target, consumed by the next hit for bonus damage.
- **Gallant** : armor retaliation, debuffs the attacker's outgoing damage.

Four of these sit on weapons, one is shared between weapons and armor, and one is armor-only. Several of them stack across multiple pieces of gear. None of them deal any damage by themselves, but they can meaningfully change the math of a fight.

---

## Detailed Explanation

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
- Costs one affix slot (unlike Life Leech's two).
- Visible via the Nat20_ManaLeech particle on the attacker.
- Only reads the weapon in hand for the affix (armor pieces can't carry it).

---

### Focused Mind

**Where it rolls**: Melee weapons **and** armor. This is the only affix in this group that armor and weapons share.

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

**Softcap**: Knee at 300% (very loose). This is the highest softcap knee in the affix system: the intent is for stacked Focused Mind to be a dominant caster-idle strategy, not one diminishing-returns quickly.

**Stacking across pieces**: All equipped pieces with Focused Mind sum their effective values into one total bonus. A caster running Focused Mind on a staff plus three armor slots compounds meaningfully before softcap.

**Math**:

```
sumBonus       = Σ (pieceValue × (1 + 0.15 × WIS_modifier))
effectiveBonus = softcap(sumBonus, 3.0)
boostThisTick  = naturalRegenThisTick × effectiveBonus
```

**Notes**:
- Purely defensive utility: Focused Mind does nothing during combat if you're kiting or repositioning.
- Because it rides on native regen ticks, it feels smooth rather than bursty.
- "Idle" is a position check, not an action check: channeling a spell or attacking in place still counts as idle for the purposes of Focused Mind. Only physical movement breaks it.

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

### Gallant

**Where it rolls**: Armor only. Does not roll on weapons.

**When it triggers**: Whenever the wearer is struck by a damage event with an entity source (a mob, another player, an NPC). Environmental damage (fall, lava, drowning) doesn't trigger Gallant: it needs a thing to punish.

**What it does**: On a successful proc, applies a debuff **on the attacker**, not the wearer. The debuffed attacker deals reduced damage to everything for the duration of the debuff. A Gallant wearer is not invulnerable, but the mob that hit them now hits softer across the board until the debuff expires.

**Duration**: **7 seconds** from application. Re-procs refresh the debuff and its expiry.

**Rarity roll**:

| Rarity    | Min  | Max  |
|-----------|------|------|
| Common    | 8%   | 12%  |
| Uncommon  | 10%  | 16%  |
| Rare      | 12%  | 18%  |
| Epic      | 14%  | 20%  |
| Legendary | 16%  | 22%  |

**Proc chance**: Base proc chance is **60%** per qualifying hit. **WIS** scales it toward 100%:

```
procChance = min(1.0, 0.60 × (1 + 0.15 × WIS_modifier))
```

- **0 WIS modifier**: 60% per strike against you
- **+2 WIS**: 78% per strike
- **+4 WIS**: 96% per strike
- **+5 WIS or higher**: 100% per strike (cap)

WIS drives proc reliability so CHA doesn't double up — CHA is reserved for how hard the debuff hits, not how often it lands.

**CHA scaling**: Factor 0.12 per point of CHA modifier. Scales the reduction value only.

**Stacking across pieces**:
- **Proc chance** is *not* additive per-piece: the highest per-piece proc chance is taken as the base (all pieces roll 60%, so this is uniform), then WIS scaling is applied once on top.
- **Reduction value** is summed additively across all equipped Gallant pieces before softcap.

So a player running Gallant on four armor pieces multiplies their reduction output without making it fire more often. WIS investment pushes the proc rate itself toward 100%.

**Softcap**: Knee at 60% (on reduction total).

**Math**:

```
sumReduction       = Σ (pieceValue × (1 + 0.12 × CHA_modifier))
effectiveReduction = softcap(sumReduction, 0.60)
attackerOutgoing   = original × (1 − effectiveReduction)   // applied to all hits from the debuffed attacker
```

A dedicated Gallant tank cares about three stats: enough pieces to stack reduction, WIS to make the proc fire reliably, and CHA to strengthen what each proc does when it lands.

**Notes**:
- The debuff is keyed per attacker entity, not per hit. A mob that procs Gallant while hitting Player A and then turns to hit Player B is still debuffed against Player B's damage (and any other target they swing at) until the 7-second timer expires.
- Gallant armor is a heavy tank pick: the more Gallant pieces you wear, the fewer pieces you have for Resistance or other defensive affixes. Tradeoff is reduction breadth (all damage types) vs. depth (one element).

---

### How they compose

These affixes mostly don't interfere with each other mechanically; they stack in separate pools:

- **Life Leech** and **Mana Leech** both scan the same incoming damage number and apply independently. A weapon with both refills health and mana off every hit.
- **Focused Mind** only runs while idle, so it doesn't compete with any of the on-hit utility affixes.
- **Vicious Mockery** and **Hex** both debuff the target but fill different niches: Vicious Mockery is a sustained 8-second damage amp on everyone hitting the target; Hex is a one-shot multiplier consumed by the next hit. A target can simultaneously have both active, so a Hex-consuming hit against a Vicious Mockery'd target gets both multipliers.
- **Gallant** lives on armor and is orthogonal to everything else.

Build implications:
- A **party healer** build layers Life Leech (self-sustain) and Focused Mind (out-of-combat recovery) on their kit; Mana Leech keeps them topped up to rejoin fights faster.
- A **debuff support** build stacks Vicious Mockery and Hex on the same weapon; every swing amplifies teammates' output.
- A **tank** build wears Gallant on as many armor pieces as the slot budget allows, potentially skipping elemental resistances if they expect frontal melee damage rather than elemental.
