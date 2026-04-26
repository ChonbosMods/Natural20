# Champion and Boss Mobs

## Overview

Natural 20 never spawns mobs one at a time. Every Nat20 mob encounter is a **group**: a pack of **champion** minions around exactly one **boss**. Each group rolls a single **difficulty tier** (Uncommon, Rare, Epic, or Legendary) that colours the whole pack, amplifies their HP and damage, stacks them with affixes, and decides how hard they drop gear.

A typical encounter is:

- **3 to 7 champions** (minion mobs, tinted, affix-carrying) surrounding
- **1 boss** (tinted, named, with more affixes and several times the HP of a champion),
- all sharing the **same rarity tier**, with one exception: a Legendary group's champions cap at Epic.

Groups appear as **POI quest objectives** (the kill-this-camp and kill-this-boss phases of quests) and as **ambient surface spawns** (random encounters that roll as you travel). Both paths share the same mechanics: same tints, same affixes, same drop tables.

Native Hytale mobs that Natural 20 didn't spawn are untouched. No tint, no affixes, no Nat20 drops. If a mob isn't glowing, it isn't one of ours.

---

## Detailed Explanation

### Group composition

Every Nat20 group contains one boss and a pack of champions. Pack size leans **smaller in starter zones** and **larger in endgame zones**, but the 3-to-7 range is always on the table. A bad day in a starter zone can still produce a full 7-champion warband; a late-game encounter can still be a tight three-mob skirmish.

Every champion in a group shares the **same affix set**. The boss rolls its own affixes independently. Champions spread out around the anchor while the boss plants roughly at the centre : picture lieutenants fanned out around their leader.

The entire group shares a single mob role: all goblins, or all skeletons, or all wolves. The role is biome-themed, with a small chance of an off-theme "outlier" showing up for variety.

---

### Difficulty Tier

When a group spawns, it rolls one **difficulty tier** that applies to every mob in the pack:

- **Uncommon** : the common case. Small stat boost, fewest affixes.
- **Rare** : meaningful stat boost and more affixes than Uncommon.
- **Epic** : noticeable power spike, still a reasonably common encounter.
- **Legendary** : rare and dangerous. Only bosses reach Legendary, and only as an upgrade from an Epic roll.

The tier drives four things:

- **MLvl bonus** stacked on top of the zone's base level.
- **Body tint** colour.
- **Affix count** from the mob affix pool.
- **Boss nameplate** (rarity-coloured generated name).

Legendary is special: only the boss of an Epic group has a chance to upgrade to Legendary, and its champions stay at Epic regardless. A Legendary-tinted boss is roughly a one-in-forty sighting in ordinary play.

---

### Tints

Each tier applies a full-body colour overlay for the mob's entire lifetime. This is the at-a-glance rarity read:

| Tier      | Colour |
| --------- | ------ |
| Uncommon  | Green  |
| Rare      | Blue   |
| Epic      | Purple |
| Legendary | Gold   |

A tinted mob is always one of ours. Untinted mobs are native Hytale spawns.

---

### Nameplates

**Only bosses get custom names.** Champions keep their regular role display name ("Goblin Brawler," etc.).

Boss names are Diablo-style compound names: a **prefix** word concatenated directly with a **suffix** word to form a single word. An optional **appellation** title can follow. The chance of an appellation increases with rarity:

| Rarity    | Appellation chance  |
| --------- | ------------------- |
| Uncommon  | never               |
| Rare      | about half the time |
| Epic      | always              |
| Legendary | always              |

Higher-rarity bosses also pull from a wider, rarer word pool, so Legendary names lean toward exotic, menacing combinations. Real examples:

- **Uncommon**: *Gloomtouch,* *Shadowgrin,* *Bonehack.*
- **Rare**: *Bloodwound,* or *Bloodwound the Hunter.*
- **Epic**: *Deathspell the Destroyer,* *Soulrend the Mauler.*
- **Legendary**: *Wyrmbite the Slayer,* *Doomgrip the Wraith.*

Names don't repeat within a short rolling window, so you won't see the same boss name twice in a short play session.

---

### Affixes

Champions and bosses roll affixes from a **20-affix subset** of the gear affix pool:

- **Crit Chance**, **Crit Damage**, **Crushing Blow**, **Deep Wounds**, **Life Leech** : offensive combat affixes.
- **Fire**, **Frost**, **Poison**, **Void**, **Cold** : flat elemental damage on hit.
- **Ignite**, **Infect**, **Corrupt** : elemental DOT applications.
- **Hex**, **Vicious Mockery** : setup debuffs on the target.
- **Fire Resistance**, **Frost Resistance**, **Poison Resistance**, **Void Resistance** : defensive cuts.
- **Thorns** : reflected damage to melee attackers.

Notably, **Evasion is not in the mob pool.** Even on a player's armor it's a powerful affix, and handing random melee immunity to bosses would make Thorns and Deep-Wounds builds feel awful. Mobs can hit you, but they can't dodge.

Every affix on this list works mechanically the same way as on a player's gear. Rarity ranges, scaling factors, and softcaps all match the affix wiki entries. A Boss with Hex curses you just like a player's Hex weapon would; a Champion with Thorns reflects damage back exactly like a Thorns armor piece.

#### Affix count per role × tier

| Role     | Uncommon | Rare | Epic | Legendary |
| -------- | --------:| ----:| ----:| ---------:|
| Champion | 1        | 2    | 3    | (never)   |
| Boss     | 2        | 3    | 4    | 5         |

A Legendary Boss carries five distinct affixes; an Epic Boss four, and so on. Champions in a Legendary group fall back to the Epic row (3 affixes), since Champion Legendary isn't a valid combination.

---

### HP and damage

The tier role scales the base mob by a multiplier:

- **Champions** have several times the HP of a regular mob and hit about as hard as a regular mob of the same level.
- **Bosses** have substantially more HP than champions and hit harder on top of that.
- **Dungeon Bosses**, reserved for future content, sit well above regular bosses in both HP and damage.

On top of that tier multiplier, both HP and damage grow with the mob's mlvl. A mlvl-20 Champion is far deadlier than a mlvl-10 Champion, and the difficulty-tier mlvl bonus feeds directly into this curve. A Legendary Boss in a starter zone is substantially tougher than a neighbouring Uncommon Champion at the same base level.

**Party scaling**: if a player triggers a group with their party nearby, each online party member within range adds one mlvl to the group's scaling, up to a cap. The bump is locked in at spawn, so the group keeps its scaling even if the party disperses afterward.

---

### Loot drops

When a Nat20 mob dies with kill credit (see below), it rolls drops from the Nat20 loot system. Native Hytale mobs drop nothing of ours: Nat20 loot is elite-only.

Champions drop **occasionally** : roughly one in six Champion kills produces a single piece. Bosses drop **reliably**: always at least one piece, plus a chance at additional bonus pieces, with higher tiers rolling more bonus chances. A Legendary Boss often hands out three or more pieces in one kill.

Each drop slot independently picks from the **global Nat20 gear pool** (95% of slots) or the **mob's native drop list** (5% of slots). The native bias tilts some drops toward thematic gear: iron weapons from goblins, bone tools from skeletons, and so on.

Within the global pool, item categories are weighted so weapons and armor land more often than tools or ranged sidearms. The final per-slot probabilities work out to:

| Source       | Category       | Per-slot chance |
| ------------ | -------------- | --------------- |
| Global pool  | Melee weapon   | 28.5%           |
| Global pool  | Armor          | 28.5%           |
| Global pool  | Ranged weapon  | 19.0%           |
| Global pool  | Tool           | 19.0%           |
| Native list  | (mob-specific) | 5.0%            |

Drop rarity is gated by the mob's mlvl. Uncommon mobs in starter zones can't drop Legendaries, no matter how lucky the roll. Legendary bosses in endgame zones can drop the full range, weighted toward the higher rarities.

---

### Kill credit

Only players who **dealt damage** to a mob in the **last 30 seconds** before its death get credited with the kill. Credit applies to loot, quest progress, and XP.

- Any damage source counts: melee, ranged, DOT ticks, reflected damage, environmental kills where you softened the mob up first.
- The killing blow doesn't have to come from a player. If you drop a boss to 10 HP and it falls into lava, you still get credit.
- One-shot kills also work : the killing blow itself records the contribution.

Party members are tracked independently: each contributing player gets their own credit.

---

### Where they spawn

**POI quest objectives.** Most groups a new player sees are attached to **kill-mobs** or **kill-boss** quest phases. The POI site spawns its group at a fixed anchor when you first visit, and the same group is still there if you leave and come back. Its difficulty, affixes, and boss name are locked in at first spawn. Environmental deaths (falls, lava, drowning) don't respawn the mob: a slot that dies to the environment stays dead for the life of the quest.

**Ambient surface spawns.** Outside of quests, groups also appear as random surface encounters as you explore the world. The per-area chance of a spawn is modest, with a short cooldown between spawns per player so you don't get buried. Ambient groups appear a comfortable distance from the player : far enough to notice on the horizon, close enough to be worth engaging. Groups that sit untouched for a long stretch despawn quietly to avoid clutter. Cave spawning is not yet covered : ambient groups currently spawn on the surface only.

Both paths share the same biome-themed role selection: a pack in a forest leans toward forest mobs, a pack in a snowy biome leans toward cold-climate mobs, and so on.

---

## Encounter notes

- **Champions are a package deal.** They all share affixes. If one Champion is bleeding you out with Deep Wounds, every Champion in that pack has Deep Wounds. The boss is separate: its affixes don't match the minions'.
- **Bosses outlive champions by a lot.** The tier multiplier plus their mlvl bonus means a Legendary Boss can take several times the effort of clearing the entire pack of champions. Thinning out the minions first to stop incoming damage is usually the correct play.
- **Tint colour is the best at-a-glance difficulty read.** Green champions drop less than blue, blue less than purple, purple less than gold. A gold boss is always the highest-value encounter in the area.
- **Nat20 loot is champion-only.** Native Hytale mobs give you nothing of ours. Affixed wolves, named bosses, and the occasional Legendary encounter are the drivers of gear progression.
