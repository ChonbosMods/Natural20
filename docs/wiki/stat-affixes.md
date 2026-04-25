# Ability Score Affixes

## Overview

Six affixes that add flat points to your character's ability scores. One per stat:

- **+STR** : Strength
- **+DEX** : Dexterity
- **+CON** : Constitution
- **+INT** : Intelligence
- **+WIS** : Wisdom
- **+CHA** : Charisma

While the item is equipped or held, the bonus is added to your character's score. Unequip the item and the bonus goes away immediately. Stacking these across multiple pieces is how a build shapes its stat profile without having to invest level-up points into everything.

What the stats themselves actually *do* — skill checks, damage scaling, affix reliability, carry weight, etc. — is covered separately in the **Ability Scores** wiki page.

---

## Details

**Where they roll**: Melee weapons, ranged weapons, armor (shields included), and tools. Any equippable or wieldable item can carry one, but only on Rare or better gear. Common and Uncommon items never roll a stat-score affix.

**Rarity roll** (flat score points added; identical across all six ability scores):

| Rarity    | Value |
|-----------|-------|
| Common    | —     |
| Uncommon  | —     |
| Rare      | +1    |
| Epic      | +1    |
| Legendary | +2    |

Stat-score affixes do **not** scale with item level. A Rare +1 STR roll on an ilvl-1 drop carries the same +1 as a Rare +1 STR on an ilvl-45 drop. Item level is for the magnitude curves on damage, regen, resistance, and the like; stat scores are deliberately a flat integer so they always cleanly cross or miss the `/ 3` modifier brackets.

**Stacking**: Every equipped/held piece with a score affix contributes. A full set of Legendary gear with matching score affixes can push a single stat by 8-10+ points over your base character sheet. Multiple different score affixes on one set stack across stats (e.g. +STR on chest, +DEX on boots, +INT on weapon).

**Activation**: Purely equip-based. No proc, no cooldown. Bonus applies the moment the item is equipped, removes the moment it's unequipped. Switching loadouts changes your stat profile in real time.

**Notes**:
- Each affix adds a flat score value. The derived *modifier* (used in most affix formulas) is recalculated automatically from the new score.
- A Legendary weapon with +2 STR is a meaningful chunk of one stat's investment, and crossing one of the `/ 3` modifier thresholds (3, 6, 9, 12, 15, 18, 21, 24, 27) with that +2 is the difference between a real upgrade and a wasted roll. Plan equipment around the threshold you're trying to hit.
- There is no affix to increase HP directly on player gear. (The `hp` affix exists in the codebase but is mob-only: mobs use it for health-scaled difficulty tiers.)
