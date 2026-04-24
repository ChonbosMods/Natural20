# Ability Affixes

## Overview

Ability affixes change **what an item can do**, not just its numbers. They add new behaviors on top of an item's normal use: never breaking, mining in shapes, pulling drops to you, mining faster. Nine of them, grouped into three roles:

**Durability protection** (works on tools, weapons, and armor):
- **Indestructible** : item never loses durability.
- **Fortified** : percent chance to skip durability loss on use.

**Mining utility** (tool-only):
- **Haste** : mines faster.
- **Telekinesis** : nearby drops teleport to your inventory.

**Block shape** (tool-only, mutually exclusive):
- **Quake** : 3x3 flat area.
- **Delve** : 1x5 line into the block face.
- **Rend** : vertical strip.
- **Fissure** : horizontal strip perpendicular to aim.
- **Resonance** : vein miner for same-type blocks.

Most ability affixes require a minimum raw ability score (not modifier) to function. Below the threshold, the affix does nothing — you can wield the item but it won't activate until your stats catch up. This gates the more powerful mining abilities behind investment.

---

## Detailed Explanation

### Indestructible

**Where it rolls**: Tools, melee weapons, ranged weapons, and armor.

**When it triggers**: Always-on, 100% durability preservation. Any time the engine would normally decrease this item's durability, it simply doesn't.

**Trigger rate**: **100%** — this is a flag, not a proc. The `Value` per rarity is fixed at 1.0 (representing "always active"), and there is no `ProcChance` field.

**Rarity availability**:

| Rarity    | Available |
|-----------|-----------|
| Common    | —         |
| Uncommon  | —         |
| Rare      | —         |
| Epic      | yes       |
| Legendary | yes       |

Indestructible rolls only on Epic and Legendary items. Lower-rarity items cannot carry it.

**Stat requirement**: None. Any character class can benefit.

**Mutually exclusive with**: Fortified. An item can have one or the other, never both.

**Notes**:
- Implementation is via a bytecode patch that intercepts the engine's `LivingEntity.updateItemStackDurability` path. If the patch didn't load, Indestructible silently does nothing — no fallback, no warning in-world.
- Works in every context durability normally drains: mining blocks, taking armor hits, attacking mobs, and the shape-mining cascades (Quake/Delve/Rend/Fissure/Resonance) that would otherwise eat durability per block broken.

---

### Fortified

**Where it rolls**: Tools, melee weapons, ranged weapons, and armor — the same four categories as Indestructible.

**When it triggers**: Every time the engine tries to reduce the item's durability, Fortified rolls a chance to cancel that reduction. On success, the durability loss is skipped for that one use.

**Rarity roll** (chance to skip durability loss per use):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Uncommon  | 15% | 20% |
| Rare      | 20% | 30% |
| Epic      | 30% | 45% |
| Legendary | 45% | 60% |

**Stat requirement**: **CON 12+** (raw ability score, not modifier). Without CON 12, Fortified rolls but does nothing.

**No scaling, no softcap**: the rolled value IS the percentage. There's no stat multiplier pushing it past the Legendary 60% cap.

**Mutually exclusive with**: Indestructible. Choose between a chance-based save and a guaranteed save.

**Notes**:
- Fortified is weaker per-use but available on more rarity tiers (Uncommon+ vs. Indestructible's Epic+).
- A Fortified item at 60% with heavy use still takes damage about 2 of every 5 swings, so durability will deplete over time. It stretches the item's lifespan ~2.5x, not forever.
- Implementation shares the same bytecode-patched intercept as Indestructible.

---

### Haste

**Where it rolls**: Tools only.

**When it triggers**: Every block-damage tick while mining with a Haste tool. Not a proc: every tick gets the boost.

**What it does**: Multiplies the mining damage-per-tick by `1 + effectiveValue`. Blocks break faster.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 10% | 15% |
| Uncommon  | 15% | 20% |
| Rare      | 20% | 30% |
| Epic      | 30% | 40% |
| Legendary | 40% | 55% |

**Stat requirement**: **DEX 12+**.

**DEX scaling**: Factor 0.08 per point of DEX modifier. Applied to the rolled value before softcap.

**Softcap**: Knee at 40%.

**Math**:

```
effectiveHaste = softcap(baseValue × (1 + 0.08 × DEX_modifier), 0.40)
blockDamage   *= (1 + effectiveHaste)
```

**Notes**:
- Interacts cleanly with shape-mining affixes (Quake, Delve, etc.): the initial block is damaged faster, then the cascade fires once per shape.
- Does not affect attack speed for combat — it only modifies `DamageBlockEvent`, which is mining-only.

---

### Telekinesis

**Where it rolls**: Tools only.

**When it triggers**: Passive, ticks every game tick while the tool is held in hand. No procs, no cooldowns: any item drop that enters the pickup radius gets pulled.

**What it does**: All eligible item entities within **8 blocks** of the player are teleported into the inventory. Items with a custom Pickup interaction (traps, special containers) are skipped, matching vanilla pickup rules.

**Rarity roll**: Flag-style, value always 1.0 across Uncommon, Rare, Epic, and Legendary.

**Stat requirement**: **INT 12+**.

**No scaling, no softcap**: either you have it or you don't. The rarity doesn't change the radius or speed, only which rarity tiers the affix is available on.

**Notes**:
- Applies to drops from any source — blocks you mined, shape-mining cascades, mob loot, other players' drops, chest spills. Anything that's a free-floating item entity within 8 blocks.
- Inventory-full case is handled: if your bag is stuffed, only the partial stack that fits gets pulled, and the rest stays on the ground.
- Only active while the Telekinesis tool is in hand. Switching weapons drops the pull radius to zero.

---

### Block Shape Affixes

Five affixes that change **how a tool breaks blocks**. All five are mutually exclusive with each other — a tool can carry at most one shape. They're rolled onto tools only, and all except Haste and Telekinesis require stat investment.

All five fire on `BreakBlockEvent`: when you break a block with the tool, the cascade fires additional block breaks around the origin. Cascade drops fall on the ground and can be vacuumed by a Telekinesis tool if paired. Each cascade block also drains durability normally (subject to Indestructible/Fortified).

A global safety cap of **128 blocks** per cascade prevents runaway vein mines.

#### Quake

**Shape**: 3x3 flat area on the same Y plane as the origin block (5x5 at Legendary max roll).

**Rarity roll** (the "size" parameter, interpreted as the NxN square):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Rare      | 3   | 3   |
| Epic      | 3   | 3   |
| Legendary | 3   | 5   |

**Stat requirement**: **STR 14+**.

**Notes**: The origin block is part of the 3x3 (or 5x5); the cascade hits the 8 (or 24) surrounding blocks on the same Y level. Useful for clearing floors and quarries.

#### Delve

**Shape**: A line drilling straight into the block face you mined. At size 5, that's the origin block plus 4 more blocks extending into the wall.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Rare      | 5   | 5   |
| Epic      | 5   | 5   |
| Legendary | 5   | 7   |

**Stat requirement**: **DEX 14+**.

**Notes**: Direction is determined by which face you mined. Great for tunnelling.

#### Rend

**Shape**: Vertical strip — the origin block plus blocks above or below it based on your aim pitch.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Rare      | 3   | 3   |
| Epic      | 3   | 3   |
| Legendary | 3   | 5   |

**Stat requirement**: **STR 12+**.

**Notes**: Defaults to downward if your aim doesn't give a clear vertical direction. Great for mining downward shafts or cutting straight up.

#### Fissure

**Shape**: Horizontal strip perpendicular to your aim. If you mine facing north, Fissure widens east-west.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Rare      | 3   | 3   |
| Epic      | 3   | 3   |
| Legendary | 3   | 5   |

**Stat requirement**: **STR 12+**.

**Notes**: Complements Delve (which drills forward); Fissure widens a tunnel laterally. A rotation of axe swings can sculpt corridors with the right mix.

#### Resonance

**Shape**: Vein miner. Breaks all connected blocks of the **same type** as the origin, up to the roll's cap.

**Rarity roll** (max blocks to break per use):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Rare      | 16  | 16  |
| Epic      | 32  | 32  |
| Legendary | 48  | 64  |

**Stat requirement**: **INT 14+**.

**Notes**:
- "Same type" matches the block's canonical ID. Ore veins, forest groves (same-species logs), stone patches — anything with a consistent block ID flood-fills.
- Flood-fill uses a breadth-first search from the origin, so it spreads outward evenly rather than digging a tunnel.
- The cascade hits a global 128-block safety cap even if the roll says higher, protecting against pathological giant veins.

---

### How they compose

**Exclusivity rules:**
- **Block shape** affixes (Quake, Delve, Rend, Fissure, Resonance) are all mutually exclusive with each other via `ExclusiveWith`. A tool has at most one shape.
- **Durability** affixes (Indestructible, Fortified) are mutually exclusive with each other.
- Everything else composes freely. A tool can simultaneously carry Haste, Telekinesis, one shape affix, and one durability affix — that's four ability affixes on a single tool if the slot budget allows.

**Stat-requirement gating**: Ability affixes with stat requirements silently do nothing when the requirement isn't met. An INT-8 character swinging a Resonance pickaxe gets no vein mining at all — the affix appears in the tooltip but doesn't trigger. This is intentional design: ability affixes reward stat investment and can't be bypassed.

**Build archetypes**:

- **Miner / tool specialist**: dedicated tool rolls Haste + Telekinesis + one shape (Quake or Resonance, usually) + Fortified. Fast, wide-area mining with minimal durability anxiety.
- **Battle-ready armor**: keep Fortified on armor pieces as lightweight durability insurance while you stack defensive affixes for the combat side.
- **Endgame kit**: Indestructible on a Legendary weapon or Legendary tool is a permanent investment — no durability maintenance ever. Given its Epic+ gating, this is a true late-game affix.
- **Caster-friendly toolkit**: Resonance + Telekinesis + Haste on an INT-heavy caster's pickaxe. Vein-mine an ore, then let the drops pour into the inventory while the next swing already moves faster.
