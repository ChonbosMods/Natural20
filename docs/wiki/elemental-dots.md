# Elemental Damage Over Time (DOTs)

## Overview

Some weapons carry a **DOT** affix: on hit, they have a chance to apply a lingering elemental effect that ticks damage on the target for several seconds. There are four flavors: **Ignite** (fire), **Cold** (frost), **Infect** (poison), and **Corrupt** (void).

Base proc chance is 60% per hit, and Wisdom scales both the proc chance (up to a 100% cap) and the damage per tick. Each DOT's values are locked in at application time and tick at a fixed interval until the DOT runs out. Different DOT types stack on the same target, but while one element is ticking, further hits of that same element can't start a new DOT until the old one ends. Ticks are amplified by Elemental Weakness and reduced by Elemental Resistance.

---

## Detailed Explanation

### What it is

A DOT affix turns a portion of your damage into a sustained burn. On a successful hit, there's a chance to *proc*: apply a damage-over-time effect to the target. That effect then deals elemental damage at a steady interval until its duration runs out.

The per-tick number and the duration are decided at the moment the DOT is applied. After that, the DOT ticks mechanically until it ends. The wielder doesn't have to keep the weapon drawn or stay near the target: once applied, the DOT is the target's problem.

### The four flavors

All four share identical mechanics; they differ only in element:

- **Ignite** deals fire damage.
- **Cold** deals frost damage.
- **Infect** deals poison damage.
- **Corrupt** deals void damage.

### When it triggers

Every non-DOT hit the wielder lands rolls proc chance for each DOT affix on the weapon. DOT ticks themselves don't roll proc chance, so a DOT can't keep re-applying itself.

**Proc chance**: the base proc chance is **60%**. Wisdom scales it up with a factor of 0.15 per point of modifier, capped at 100%:

```
procChance = min(1.0, 0.60 × (1 + 0.15 × WIS_modifier))
```

- **0 WIS modifier**: 60% per hit
- **+2 WIS**: 78% per hit
- **+4 WIS**: 96% per hit
- **+5 WIS or higher**: 100% per hit (cap)

So a dedicated caster hits the ceiling with enough WIS investment, while low-WIS wielders lose about two in five procs. WIS also boosts per-tick damage (see below), so the stat pulls double duty.

### Duration

Each DOT roll carries its own duration, rolled at item-craft time, uniformly between:

- **Minimum**: 5 seconds
- **Maximum**: 15 seconds

The duration is a trait of the affix, so the same weapon always applies the same duration.

### Per-tick damage and total damage

A rolled DOT affix has a **base per-tick value**, determined by rarity:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 1.0 | 2.0 |
| Uncommon  | 1.5 | 3.0 |
| Rare      | 2.0 | 4.5 |
| Epic      | 3.5 | 7.0 |
| Legendary | 5.0 | 10.0 |

The rolled number is the per-tick damage **at max duration (15 s)**. DOTs tick every **2 seconds**, so a max-duration DOT fires 7 or 8 ticks.

**The key invariant**: total damage is preserved across duration rolls. A shorter roll delivers the same total damage in less time, which means more damage per tick and higher DPS. A shorter-duration roll of the same affix is therefore the *better* roll.

The formula:

```
totalDamage  = rolledPerTickValue × (15 / 2)       // i.e., × 7.5
damagePerTick = totalDamage / (rollDuration / 2)
```

So a Rare affix that rolls a base of `3.0` per tick delivers `3.0 × 7.5 = 22.5` total damage regardless of duration. With a 15 s duration: 3.0 per tick, 7-8 ticks. With a 5 s duration: 9.0 per tick, 2-3 ticks. Same total, very different feel.

A hard floor of **0.5 damage per tick** is enforced so low rolls always tick for something.

### WIS scaling

The DOT's base per-tick value is scaled by the wielder's **Wisdom (WIS)** modifier with a factor of **0.15**:

```
effectivePerTick = rolledPerTickValue × (1 + 0.15 × WIS_modifier)
```

This scaling is snapshotted **at application time**. If the wielder's WIS changes mid-DOT, the ticks already in flight don't change: only new applications see new WIS.

WIS scaling is applied *before* the total-damage-preservation formula, so stronger WIS increases total damage proportionally.

### Tick timing and sync

All DOTs on a single target share one synchronized tick phase. If a target has Ignite ticking and then gets hit with Infect, the Infect doesn't start its own independent timer: it joins the existing 2-second cadence. Every 2 seconds, all active DOTs on that target deal their damage simultaneously.

### Stacking and refresh

- **Different DOT types stack freely**: a target can have Ignite, Cold, Infect, and Corrupt all active at once. Each ticks its own damage on the shared 2-second beat.
- **Same DOT type blocks new procs until it ends**: while an Ignite is ticking on a target, further hits from *any* Ignite source cannot start a new Ignite on that same target. The active DOT rides out its original duration and damage values: it is not refreshed, not extended, and not replaced by a stronger roll. Once it expires, the next qualifying hit can proc a fresh Ignite. The same rule applies independently to each of the four elements.

### Lifetime

A DOT ends when either:

1. Its remaining duration hits zero, or
2. The target's visual effect (the matching particle/icon) expires independently. The visual is tied to the same duration but the system double-checks after the DOT has been running for 3 seconds, so if the visual is cleared early by some external interaction, the DOT stops with it.

### Interaction with other systems

DOT ticks are tagged with the element's damage type, so downstream systems see them the same way they see any other elemental hit:

- **Elemental Weakness** on the target: amplifies each tick by the weakness percentage.
- **Elemental Resistance** on the target: reduces each tick by the resistance percentage.
- **INT scaling from Nat20ScoreDamageSystem**: does **not** apply to DOT ticks. INT boosts the direct elemental weapon damage at application time, but DOT ticks use the WIS-scaled per-tick value that was baked in when the DOT was applied.
- **Applying new weakness/DOTs from DOT ticks**: DOT ticks never re-trigger weapon affixes. They do not apply weakness, they do not re-roll DOT procs, and they do not trigger elemental-damage secondary hits. Only direct hits do.

### End-to-end example

A player with +3 WIS wields a **Rare** sword rolled with `Ignite`, base per-tick `3.0`, duration `10 seconds`. The target has a **30% Fire Weakness** active.

1. Player hits. Proc chance: `0.60 × (1 + 0.15 × 3) = 0.60 × 1.45 ≈ 87%`. The roll passes; Ignite applies. (If it had failed, the hit would still land normally; just no DOT this swing.)
2. Per-tick with WIS: `3.0 × (1 + 0.15 × 3) = 3.0 × 1.45 ≈ 4.35` per tick at max duration.
3. Total damage preserved to 10 s: `totalDamage = 4.35 × 7.5 ≈ 32.6`, spread over `10 / 2 = 5` ticks → `32.6 / 5 ≈ 6.5` damage per tick.
4. Each tick fires with a fire damage tag. Fire Weakness on the target amplifies by 30%: `6.5 × 1.30 ≈ 8.5` damage per tick actually applied.
5. Over the DOT's 10-second life, the target takes roughly `8.5 × 5 = 42.5` fire damage, on top of the initial physical hit.
6. While this Ignite is ticking, any additional hits from any Ignite weapon on this target cannot start a second Ignite: the first proc has priority until it runs out.

If the same weapon also rolled an elemental fire-damage affix, the initial hit would also have dealt an INT-scaled direct fire damage event (also amplified by Fire Weakness). The DOT is a separate, persistent second layer.
