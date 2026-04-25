# Ability Scores

## Overview

Six ability scores shape every piece of your character's capability: **Strength** (STR), **Dexterity** (DEX), **Constitution** (CON), **Intelligence** (INT), **Wisdom** (WIS), and **Charisma** (CHA). Each score is a whole number from 0 to 30, and from every score a single **modifier** is derived: `floor(score / 3)`. A score of 9 gives +3, 15 gives +5, 30 gives +10. Dumping a stat all the way to 0 gives +0, never a negative penalty: combat math never punishes a low stat, it just doesn't reward it.

That modifier is the universal input to almost every other system:

- **Gear affix scaling** (Crit, Backstab, Deep Wounds, Resistance, and so on)
- **Non-affix baselines** (extra HP, extra mana, faster regen, movement speed, flat melee and elemental damage)
- **Dialogue skill checks** (d20 + modifier + proficiency bonus)

Each stat's non-affix baselines on your underlying Hytale stats:

| Stat |   |   |   |
| ---- | - | - | - |
| STR  | Flat melee damage     | Crit damage baseline   | Stamina regeneration |
| DEX  | Crit chance baseline  | Movement speed         | Fall damage reduction |
| CON  | Max HP                | Max Stamina            | —                    |
| INT  | Max Mana              | Flat elemental damage  | DoT per-tick scaling |
| WIS  | Perception            | Mana regeneration      | —                    |
| CHA  | —                     | —                      | —                    |

Charisma has no non-affix baselines: every CHA effect routes through a party-support or enemy-debuff affix (Rally, Vicious Mockery, Gallant).

---

## Detailed Explanation

### Strength (STR)

STR is the muscle stat. It drives your raw melee output and rewards aggressive close-range builds.

**Non-affix baselines**:

| Effect               | Formula                                                       | Example (mod +5)              |
| -------------------- | ------------------------------------------------------------- | ----------------------------- |
| Flat melee damage    | `+10 × mod` per swing                                         | +50 damage per hit            |
| Crit damage baseline | `+0.15 × mod` added to base 1.5× multiplier (outside softcap) | base crit becomes 2.25×       |
| Stamina regeneration | regen tick `× (1 + 0.18 × mod)`                               | ~90% faster stamina recovery  |

**Affix scaling** (see Offensive Affixes and Defense Affixes wikis):

| Affix             | What scales                                  | Factor |
| ----------------- | -------------------------------------------- | ------ |
| Deep Wounds       | proc chance *and* per-tick damage both scale | 0.18   |
| Crushing Blow     | percent-of-current-HP drain                  | 0.15   |
| Block Proficiency | stamina-drain reduction on blocked hits      | 0.15   |
| Haste             | tool mining speed                            | 0.08   |

**Skill check coverage**: Athletics.

---

### Dexterity (DEX)

DEX is the speed stat. It governs how often you hit, how fast you move, and whether you crit in the first place.

**Non-affix baselines**:

| Effect                | Formula                                       | Example (mod +5)                    |
| --------------------- | --------------------------------------------- | ----------------------------------- |
| Crit chance baseline  | `+0.015 × mod` to crit chance (outside softcap) | +7.5% baseline crit               |
| Movement speed        | base speed `× (1 + 0.04 × mod)`               | +20% movement speed                 |
| Fall damage reduction | fall damage `-10 × mod` (floored at 0)        | first 50 damage of any fall negated |

**Affix scaling** (see Offensive, Defense, Ability, and Utility Affix wikis):

| Affix         | What scales                                          | Factor |
| ------------- | ---------------------------------------------------- | ------ |
| Backstab      | rear-arc damage bonus                                | 0.15   |
| Precision     | armor-penetration percentage                         | 0.12   |
| Life Leech    | percent-damage-to-HP                                 | 0.12   |
| Attack Speed  | swing-speed boost                                    | 0.10   |
| Evasion       | melee dodge chance (total dodge hard-capped at 50%)  | 0.10   |
| Lightweight   | sprint stamina-drain compensation                    | 0.12   |

**Skill check coverage**: Stealth, Sleight of Hand, Acrobatics.

---

### Constitution (CON)

CON is the survival stat. Every point makes your pools deeper and your defensive affixes stubborner. CON is the only stat with no skill check attached: you can't argue a door open with it, only endure what happens after.

**Non-affix baselines**:

| Effect      | Formula                  | Example (mod +5) |
| ----------- | ------------------------ | ---------------- |
| Max HP      | `+10 × mod` to Health    | +50 HP           |
| Max Stamina | `+5 × mod` to Stamina    | +25 stamina      |

**Affix scaling** (see Defense Affixes wiki):

| Affix      | What scales                                        | Factor |
| ---------- | -------------------------------------------------- | ------ |
| Thorns     | reflected damage (among the highest in the game)   | 0.18   |
| Resilience | rate at which debuffs tick down                    | 0.12   |

**Skill check coverage**: none. CON matters in combat and gear, not in conversation.

---

### Intelligence (INT)

INT is the mind stat. It drives mana, elemental damage output, and the widest slice of knowledge-based dialogue checks. Casters and elemental builds invest in it as their primary damage stat.

**Non-affix baselines**:

| Effect                | Formula                                                                | Example (mod +5)             |
| --------------------- | ---------------------------------------------------------------------- | ---------------------------- |
| Max Mana              | `+10 × mod` to Mana                                                    | +50 mana                     |
| Flat elemental damage | `+10 × mod` per elemental hit (direct hits only, not DoT ticks)        | +50 damage per elemental hit |
| DoT per-tick scaling  | tick value `× (1 + 0.15 × mod)`, captured once at apply time           | DoT ticks deal +75%          |

**Affix scaling** (see Elemental Resistances, Offensive Affixes wikis):

| Affix                                                          | What scales      | Factor |
| -------------------------------------------------------------- | ---------------- | ------ |
| Fire / Frost / Poison / Void Resistance (on armor and shields) | all four scale   | 0.12   |
| Mana Leech                                                     | percent-damage-to-mana | 0.12 |

**Skill check coverage**: Investigation, Arcana, Religion, History, Nature.

---

### Wisdom (WIS)

WIS is the awareness stat. It governs how reliably your effects land on enemies, how alert your character is to the world, and how fast your mana trickles back. Caster and support builds lean on WIS to make the rest of their kit actually happen.

**Non-affix baselines**:

| Effect            | Formula                          | Example (mod +5)              |
| ----------------- | -------------------------------- | ----------------------------- |
| Perception        | `+10 × mod` to Nat20 Perception  | +50 Perception                |
| Mana regeneration | regen tick `× (1 + 0.025 × mod)` | ~12.5% faster mana recovery   |

**Affix scaling, *value side*** (how hard the effect hits when it lands):

| Affix                                              | What scales                                                          | Factor |
| -------------------------------------------------- | -------------------------------------------------------------------- | ------ |
| Fire / Frost / Poison / Void Weakness (on weapons) | all four scale                                                       | 0.15   |
| Hex                                                | one-shot curse amplification (highest scaling factor of any weapon affix) | 0.18 |
| Absorption                                         | damage-to-mana conversion                                            | 0.15   |
| Focused Mind                                       | idle mana-regen boost                                                | 0.15   |
| Water Breathing                                    | max-oxygen bonus                                                     | 0.15   |

**Affix scaling, *proc side*** (how reliably the effect fires):

| Affix                            | What scales              | Factor |
| -------------------------------- | ------------------------ | ------ |
| Ignite / Cold / Infect / Corrupt | proc chance on each hit  | 0.15   |
| Vicious Mockery                  | proc chance              | 0.15   |
| Gallant                          | on-being-hit proc        | 0.15   |

This split is deliberate. WIS lands the effect; a partner stat does the damage. Elemental DoTs pair WIS (proc) with INT (damage). Vicious Mockery and Gallant pair WIS (proc) with CHA (value). A caster build naturally wants WIS plus whichever damage stat their playstyle prefers.

**Skill check coverage**: Insight, Perception.

---

### Charisma (CHA)

CHA is the social stat. The most heavily used stat in dialogue, it owns the most commonly rolled checks. Every CHA effect in combat routes through a party-support or enemy-debuff affix.

**Affix scaling** (see Offensive and Defense Affixes wikis):

| Affix           | What scales                                                          | Factor |
| --------------- | -------------------------------------------------------------------- | ------ |
| Rally           | on-kill party damage buff                                            | 0.15   |
| Vicious Mockery | damage-amplify value on the debuffed target (WIS handles its proc side) | 0.15 |
| Gallant         | attacker-damage-reduction value (WIS handles its proc side)          | 0.12   |

**Skill check coverage**: Persuasion, Deception, Intimidation, Performance. Four checks, found more in dialogue than any other stat.

**Notes**:

- CHA is the "force multiplier" stat. It doesn't make *you* stronger, it makes *others* stronger (Rally), or *enemies* weaker (Vicious Mockery, Gallant).
- In dialogue, high-CHA characters dominate the NPC-interaction space: four of the fifteen skills are CHA, and those four (persuade, deceive, intimidate, perform) are the most common choices.

---

## Score-to-Modifier Reference

One formula, used for both skill checks and combat math: `modifier = floor(score / 3)`.

| Score | Modifier |
| ----- | -------- |
| 0–2   | +0       |
| 3–5   | +1       |
| 6–8   | +2       |
| 9–11  | +3       |
| 12–14 | +4       |
| 15–17 | +5       |
| 18–20 | +6       |
| 21–23 | +7       |
| 24–26 | +8       |
| 27–29 | +9       |
| 30    | +10      |

Ability-score affixes on gear (`+1` on Rare and Epic, `+2` on Legendary; Common and Uncommon never roll one) are flat additions to the raw score, so their impact depends on whether they push you into a new bracket. A `+2 STR` piece takes you from STR 9 (mod +3) to STR 11 (still +3), but the same `+2` on a STR 13 character takes you from +4 to +5, which is a real step. Planning gear around the `/ 3` bracket thresholds (3, 6, 9, 12, 15, 18, 21, 24, 27) is how min-maxers squeeze extra mileage out of every affix roll.

---

## Proficiency Bonus

Skill checks add a proficiency bonus *on top of* the stat modifier, but only for skills your character is trained in. Proficiency scales with character level:

| Level | Proficiency |
| ----- | ----------- |
| 1–8   | +2          |
| 9–16  | +3          |
| 17–24 | +4          |
| 25–32 | +5          |
| 33–40 | +6          |

A level-12 character with CHA 17 rolling a proficient Persuasion check rolls `d20 + 5 (CHA mod) + 3 (proficiency) = d20 + 8`. The same roll untrained would be `d20 + 5`. Training a skill is worth roughly a `+3` stat swing at mid levels and `+6` at endgame, entirely independent of raw score.

---

## Build Archetypes

None of these are locked in: any stat investment feeds the same formulas. But certain pairings naturally compound:

- **Barbarian / heavy melee** : STR + CON. STR makes every swing bigger and makes crits hit harder; CON provides the HP and stamina to stay in the melee. Deep Wounds, Crushing Blow, and Thorns all reward this pair.
- **Ranger / rogue** : DEX + WIS. DEX handles crit chance, movement, and precision; WIS lands debuffs and drives Perception. Backstab and Precision shine here, and Vicious Mockery becomes a real pick when WIS is already invested.
- **Shield tank / paladin** : STR + CON + CHA. STR scales Block Proficiency; CON drives Thorns and Resilience; CHA powers Gallant and Rally to turn the whole party into a wall.
- **Elementalist / caster** : INT + WIS. INT makes every elemental hit hit harder; WIS makes every DoT, Weakness, and Hex actually land reliably. Mana Leech, Absorption, and all four elemental affix families care about this pair.
- **Bard / face** : CHA + WIS + DEX. All six social-facing and awareness skills live across these three stats (Persuasion, Deception, Intimidation, Performance, Insight, Perception). WIS adds proc reliability, DEX adds mobility and initiative, and Rally plus Vicious Mockery turn the character into a party amplifier.
- **Explorer / artisan** : DEX + INT. DEX scales Haste (mining speed) and Lightweight (stamina); INT powers Mana Leech and elemental resistances for dungeon delves. Together they turn exploration into a fully-specced utility profession.
- **Adventurer (generalist)** : spread points so no stat drops below 9 (modifier +3) and none exceed 15 (modifier +5). Every affix does *something*, every dialogue check has a base chance, no dead stats.

---

## How it all composes

Two things tie the stat system together:

1. **One modifier curve for everything.** Skill checks, gear scaling, flat bonuses, regen, all read the same `floor(score / 3)`. There's no separate "combat mod" and "dialogue mod" to juggle. Invest once, benefit everywhere that stat matters.
2. **Additive score affixes on gear.** Every `+STR` / `+DEX` / etc. roll is a flat addition to the raw score, which recomputes the modifier, which cascades into every baseline and every scaled affix simultaneously. Swapping in a Legendary `+2 STR` weapon visibly raises your flat melee damage, your crit multiplier, your stamina regen, your Deep Wounds output, your Crushing Blow scaling, and your Block Proficiency all at once.

A mature character has thoughtful stat investment, takes advantage of the linear modifier curve, uses ability-score affixes to cross the `/ 3` bracket thresholds, and picks up gear whose affixes scale with whichever stats they've committed to.
