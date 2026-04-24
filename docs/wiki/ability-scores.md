# Ability Scores

## Overview

Six ability scores shape every piece of your character's capability: **Strength** (STR), **Dexterity** (DEX), **Constitution** (CON), **Intelligence** (INT), **Wisdom** (WIS), and **Charisma** (CHA). Each score is a whole number from 0 to 30, and from every score a single **modifier** is derived: `floor(score / 3)`. A score of 9 gives +3, 15 gives +5, 30 gives +10. Dumping a stat all the way to 0 gives +0, never a negative penalty: combat math never punishes a low stat, it just doesn't reward it.

That modifier is the universal input to almost every other system:

- **Gear affix scaling** (Crit, Backstab, Deep Wounds, Resistance, and so on)
- **Non-affix baselines** (extra HP, extra mana, faster regen, movement speed, flat melee and elemental damage)
- **Dialogue skill checks** (d20 + modifier + proficiency bonus)

Each stat has its own identity: raw damage and crit ceiling (STR), speed and precision (DEX), raw survivability (CON), mana and magical output (INT), effect reliability and sensing (WIS), party support and social play (CHA). You don't need to specialize: the modifier curve is strictly linear, so every 3 points of any stat gives you roughly the same step of progression in whatever that stat does.

The rest of this page lists every effect each stat has. Affix math is not duplicated here: follow the cross-references to the affix wikis for rarity ranges, softcap knees, and full formulas.

---

## Detailed Explanation

### Strength (STR)

STR is the muscle stat. It drives your raw melee output and rewards aggressive close-range builds.

**Non-affix baselines**:

- **Flat melee damage**: every melee swing deals an extra `10 × STR_modifier` flat damage on top of the weapon's base damage and any affix contributions. A STR 15 character (modifier +5) adds `+50` to every hit.
- **Crit damage baseline**: the base crit multiplier is 1.5×. STR adds `0.15 × STR_modifier` directly to that multiplier, outside any softcap. STR 18 (modifier +6) pushes the base crit from 1.5× to 2.4× all by itself, before any `Crit Damage` affix is involved.
- **Stamina regeneration**: every tick of natural stamina regen is boosted by `STR_modifier × 0.18`. STR 15 recovers stamina roughly 90% faster than STR 0, making sustained sprinting and chained swings much cheaper.

**Affix scaling** (see Offensive Affixes and Defense Affixes wikis):

- **Deep Wounds** : proc chance *and* per-tick damage both scale with factor 0.18.
- **Crushing Blow** : percent-of-current-HP drain scales with factor 0.15.
- **Block Proficiency** : stamina-drain reduction on blocked hits scales with factor 0.15.
- **Haste** : tool mining speed scales with factor 0.08.

**Skill check coverage**: Athletics.

---

### Dexterity (DEX)

DEX is the speed stat. It governs how often you hit, how fast you move, and whether you crit in the first place.

**Non-affix baselines**:

- **Crit chance baseline**: every point of DEX modifier adds `0.015` (1.5%) to your crit chance on every melee hit. This baseline is linear and sits outside the crit-chance softcap. DEX 18 (modifier +6) gives a flat 9% baseline crit chance even on an unaffixed weapon.
- **Movement speed**: your base movement speed is multiplied by `1 + (0.04 × DEX_modifier)`. DEX 15 (modifier +5) is 20% faster than DEX 0. Only positive modifiers count, and the modifier is always non-negative anyway, so the effect only ever adds speed.
- **Fall damage reduction**: fall damage is reduced by a flat `10 × DEX_modifier` points, floored at zero. A DEX 18 character (modifier +6) shrugs off the first 60 damage of any fall before any other mitigation.

**Affix scaling** (see Offensive, Defense, Ability, and Utility Affix wikis):

- **Backstab** : rear-arc damage bonus scales with factor 0.15.
- **Precision** : armor-penetration percentage scales with factor 0.12.
- **Life Leech** : percent-damage-to-HP scales with factor 0.12.
- **Attack Speed** : swing-speed boost scales with factor 0.10.
- **Evasion** : melee dodge chance scales with factor 0.10 (total dodge is hard-capped at 50%).
- **Lightweight** : sprint stamina-drain compensation scales with factor 0.12.

**Skill check coverage**: Stealth, Sleight of Hand, Acrobatics.

---

### Constitution (CON)

CON is the survival stat. Every point makes your pools deeper and your defensive affixes stubborner. CON is the only stat with no skill check attached: you can't argue a door open with it, only endure what happens after.

**Non-affix baselines**:

- **Max HP**: your max health is increased by `10 × CON_modifier`, applied as an additive modifier to the Health stat. CON 15 (modifier +5) adds 50 to the HP pool.
- **Max Stamina**: your max stamina is increased by `5 × CON_modifier`, half the HP rate. CON 15 adds 25 to the stamina pool.

**Affix scaling** (see Defense Affixes wiki):

- **Thorns** : reflected damage scales with factor 0.18, among the highest in the game.
- **Resilience** : rate at which debuffs tick down scales with factor 0.12.

**Skill check coverage**: none. CON matters in combat and gear, not in conversation.

---

### Intelligence (INT)

INT is the mind stat. It drives mana, elemental damage output, and the widest slice of knowledge-based dialogue checks. Casters and elemental builds invest in it as their primary damage stat.

**Non-affix baselines**:

- **Max Mana**: your max mana is increased by `10 × INT_modifier`. INT 15 (modifier +5) adds 50 to the mana pool.
- **Flat elemental damage**: every elemental hit (Fire, Frost, Poison, Void) deals an extra `10 × INT_modifier` flat damage on top of the affix's rolled value. This applies to direct hits only, not to DoT ticks: the DoT gets its INT boost a different way (see next line).
- **DoT per-tick scaling**: Ignite, Cold, Infect, and Corrupt ticks are multiplied by `1 + (0.15 × INT_modifier)` at the moment the DoT is applied. The value is captured once, so changing INT mid-fight doesn't retroactively change ticks already in flight.

**Affix scaling** (see Elemental Resistances, Offensive Affixes wikis):

- **Fire / Frost / Poison / Void Resistance** (on armor and shields) : all four scale with factor 0.12.
- **Mana Leech** : percent-damage-to-mana scales with factor 0.12.

**Skill check coverage**: Investigation, Arcana, Religion, History, Nature. 

---

### Wisdom (WIS)

WIS is the awareness stat. It governs how reliably your effects land on enemies, how alert your character is to the world, and how fast your mana trickles back. Caster and support builds lean on WIS to make the rest of their kit actually happen.

**Non-affix baselines**:

- **Perception**: your Nat20 Perception value is increased by `10 × WIS_modifier`. Perception gates sense-based features (noticing hidden details, awareness range).
- **Mana regeneration**: every tick of natural mana regen is boosted by `WIS_modifier × 0.025`. This is a much gentler multiplier than STR's stamina boost: WIS isn't meant to be the primary mana economy (that's Focused Mind and Mana Leech). It's a trickle, not a firehose.

**Affix scaling, *value side*** (how hard the effect hits when it lands):

- **Fire / Frost / Poison / Void Weakness** (on weapons) : all four scale with factor 0.15.
- **Hex** : one-shot curse amplification scales with factor 0.18, the highest scaling factor of any weapon affix.
- **Absorption** : damage-to-mana conversion scales with factor 0.15.
- **Focused Mind** : idle mana-regen boost scales with factor 0.15.
- **Water Breathing** : max-oxygen bonus scales with factor 0.15.

**Affix scaling, *proc side*** (how reliably the effect fires):

- **Ignite / Cold / Infect / Corrupt** : proc chance on each hit scales with factor 0.15.
- **Vicious Mockery** : proc chance scales with factor 0.15.
- **Gallant** : the on-being-hit proc scales with factor 0.15.

This split is deliberate. WIS lands the effect; a partner stat does the damage. Elemental DoTs pair WIS (proc) with INT (damage). Vicious Mockery and Gallant pair WIS (proc) with CHA (value). A caster build naturally wants WIS plus whichever damage stat their playstyle prefers.

**Skill check coverage**: Insight, Perception.

---

### Charisma (CHA)

CHA is the social stat. The most heavily used stat in dialogue, it owns the most commonly rolled checks. Every CHA effect in combat routes through a party-support or enemy-debuff affix.

**Non-affix baselines**: none.

**Affix scaling** (see Offensive and Defense Affixes wikis):

- **Rally** : on-kill party damage buff scales with factor 0.15.
- **Vicious Mockery** : damage-amplify value on the debuffed target scales with factor 0.15 (WIS handles its proc side).
- **Gallant** : attacker-damage-reduction value scales with factor 0.12 (WIS handles its proc side).

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

Ability-score affixes on gear (`+1 STR`, `+2 DEX`, and so on) are flat additions to the raw score, so their impact depends on whether they push you into a new bracket. A `+2 STR` piece takes you from STR 9 (mod +3) to STR 11 (still +3), but the same `+2` on a STR 13 character takes you from +4 to +5, which is a real step. Planning gear around the `/ 3` bracket thresholds (3, 6, 9, 12, 15, 18, 21, 24, 27) is how min-maxers squeeze extra mileage out of every affix roll.

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
2. **Additive score affixes on gear.** Every `+STR` / `+DEX` / etc. roll is a flat addition to the raw score, which recomputes the modifier, which cascades into every baseline and every scaled affix simultaneously. Swapping in a Legendary `+4 STR` weapon visibly raises your flat melee damage, your crit multiplier, your stamina regen, your Deep Wounds output, your Crushing Blow scaling, and your Block Proficiency all at once.

A mature character has thoughtful stat investment, takes advantage of the linear modifier curve, uses ability-score affixes to cross the `/ 3` bracket thresholds, and picks up gear whose affixes scale with whichever stats they've committed to.
