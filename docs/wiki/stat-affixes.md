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

**Where they roll**: Melee weapons, ranged weapons, armor (shields included), and tools. Any equippable or wieldable item can carry one.

**Rarity roll** (flat score points added; identical across all six ability scores):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 1   | 1   |
| Uncommon  | 1   | 2   |
| Rare      | 2   | 2   |
| Epic      | 2   | 3   |
| Legendary | 3   | 4   |

**Stacking**: Every equipped/held piece with a score affix contributes. A full set of armor plus a weapon with matching score affixes can push a single stat by 15-20+ points over what your base character sheet provides. Multiple different score affixes on one set stack across stats (e.g. +STR on chest, +DEX on boots, +INT on weapon).

**Activation**: Purely equip-based. No proc, no cooldown. Bonus applies the moment the item is equipped, removes the moment it's unequipped. Switching loadouts changes your stat profile in real time.

**Notes**:
- Each affix adds a flat score value. The derived *modifier* (used in most affix formulas) is recalculated automatically from the new score.
- Items with a score affix essentially bake a mini stat build into themselves. A Legendary weapon with +4 STR is worth a full stat point-buy's worth of investment in STR alone.
- There is no affix to increase HP directly on player gear. (The `hp` affix exists in the codebase but is mob-only: mobs use it for health-scaled difficulty tiers.)
