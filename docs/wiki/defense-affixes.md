# Defense Affixes

## Overview

Defense affixes keep you alive. Some reduce incoming damage, some dodge it entirely, some shorten debuffs, some punish attackers. Six of them:

- **Absorption** : redirect incoming damage into your mana pool.
- **Evasion** : chance to fully dodge melee attacks.
- **Resilience** : debuffs on you tick down faster.
- **Thorns** : reflect flat damage back to melee attackers.
- **Block Proficiency** : reduced stamina cost when blocking hits.
- **Gallant** : debuff the attacker's outgoing damage when they strike you.

All six are armor-only (shields included, since shields share the armor category). They never compete with weapon affixes for roll slots. Most of these stack across multiple pieces of gear, rewarding committed defensive builds.

---

## Detailed Explanation

### Absorption

**Where it rolls**: Armor only (shields included).

**When it triggers**: Any incoming damage to the wearer, subject to a **5-second cooldown** per player. During the cooldown, incoming damage is unreduced.

**What it does**: Converts a percentage of the incoming damage into mana cost. The damage is reduced by the absorbed amount, and your mana pool pays for it. If you don't have enough mana, the absorption is capped at whatever mana you have.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 5%  | 10% |
| Uncommon  | 8%  | 15% |
| Rare      | 12% | 22% |
| Epic      | 18% | 30% |
| Legendary | 25% | 40% |

**WIS scaling**: Factor 0.15 per point of WIS modifier.

**Softcap**: Knee at 70%. Stacking Absorption across a full set + weapon can reach substantial reduction, but the softcap prevents runaway trivialization.

**Stacking across pieces**: All equipped armor pieces with Absorption are summed before softcap.

**Math**:

```
sumAbsorb       = Σ (pieceValue × (1 + 0.15 × WIS_modifier))
effectiveAbsorb = softcap(sumAbsorb, 0.70)
absorbedDamage  = min(incomingDamage × effectiveAbsorb, currentMana)
finalDamage     = incomingDamage − absorbedDamage
mana           -= absorbedDamage
```

**Notes**:
- The cooldown is **per-player**, not per-piece. Stacking more pieces doesn't reset the cooldown: it only increases the magnitude of each fire.
- If the wearer is at 0 mana, Absorption does nothing even when off cooldown. The mana cost is a hard requirement.
- Environmental damage (fall, lava, drowning) is absorbed same as entity-source damage: Absorption doesn't check the source.

---

### Evasion

**Where it rolls**: Armor only.

**When it triggers**: Only **melee attacks** (damage from an entity source). Ranged and elemental damage ignore Evasion.

**What it does**: Rolls dodge chance against the attack. On success, the entire damage event is cancelled: zero damage, no knockback, no hit angle change, no flinch. Failed rolls let the hit through as normal.

**Rarity roll** (fixed dodge chance per piece):

| Rarity    | Value |
|-----------|-------|
| Common    | 2%    |
| Uncommon  | 3%    |
| Rare      | 4%    |
| Epic      | 6%    |
| Legendary | 8%    |

Note: unlike most affixes, Evasion's rolls are **fixed** per rarity (Min == Max). There's no variance within a rarity band.

**DEX scaling**: Factor 0.10 per point of DEX modifier.

**Hard cap**: Total dodge chance is capped at **50%** — this is a hard ceiling, not a softcap. A dedicated evasion build cannot exceed a coin flip regardless of stacking or DEX.

**AffixSlotCost**: 2. Evasion is a heavyweight affix that crowds out other rolls on the same piece.

**Stacking across pieces**: Dodge chances sum additively across armor pieces before applying the 50% cap.

**Math**:

```
sumDodge        = Σ (pieceValue × (1 + 0.10 × DEX_modifier))
effectiveDodge  = min(sumDodge, 0.50)
```

Every qualifying melee hit rolls a uniform random number; if it's under `effectiveDodge`, the hit is dodged.

**Notes**:
- Dodging cancels the damage event completely, so follow-up systems that read the event (leech, thorns, weakness application) don't fire either: dodge = nothing happened.
- Visual: a Nat20_Evasion particle and the Toad-Rhino-Tongue-Whoosh sound on a successful dodge.
- Ranged and elemental attacks are not affected. This is specifically "sidestep melee swings" flavor.

---

### Resilience

**Where it rolls**: Armor only.

**When it triggers**: Passive, every game tick. If any **debuff** EntityEffect is active on the wearer (buffs are ignored), Resilience drains extra duration from each one.

**What it does**: Every active debuff's remaining duration is reduced faster than normal. A 30% Resilience value means debuffs tick down **30% faster than their natural rate** — a 10-second debuff expires in roughly 7.7 seconds instead.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 10% | 15% |
| Uncommon  | 13% | 20% |
| Rare      | 18% | 28% |
| Epic      | 25% | 38% |
| Legendary | 30% | 45% |

**CON scaling**: Factor 0.12 per point of CON modifier.

**Softcap**: Knee at 80%. Stacking past ~80% pays diminishing returns; a full Legendary set can't make debuffs effectively instantaneous.

**Stacking across pieces**: All pieces with Resilience sum their effective values into one total rate multiplier.

**Math**:

```
sumResilience       = Σ (pieceValue × (1 + 0.12 × CON_modifier))
effectiveResilience = softcap(sumResilience, 0.80)
extraDrainPerTick   = dt × effectiveResilience
```

Each qualifying debuff has `extraDrainPerTick` seconds shaved off its remaining duration every tick, on top of the debuff's normal countdown.

**Notes**:
- Affects **debuffs only** — identified by `isDebuff()` on the active effect. Player-applied buffs (Rally, for example) tick down at normal speed.
- Infinite-duration effects are skipped: Resilience doesn't do anything against `isInfinite()` effects.
- Works against elemental weaknesses, Vicious Mockery, Hex, and any other Nat20 debuff marked as `isDebuff()`. Hytale-native debuffs also eligible if properly tagged.

---

### Thorns

**Where it rolls**: Armor only.

**When it triggers**: Any incoming damage (from an entity source), with a **50% proc chance** per hit. DOT ticks also roll thorns.

**What it does**: On a successful proc, returns a chunk of flat damage directly to the attacker. The damage is tagged with a special `Nat20Thorns` cause to prevent an infinite ping-pong loop (the system skips damage events with this cause).

**Rarity roll** (flat damage per stack):

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 1.0 | 3.0 |
| Uncommon  | 2.0 | 5.0 |
| Rare      | 4.0 | 8.0 |
| Epic      | 6.0 | 11.0 |
| Legendary | 8.0 | 14.0 |

**CON scaling**: Factor 0.18 per point of CON modifier (among the highest scaling factors on any affix).

**Softcap**: Knee at 5000%. In practice this means no effective cap: Thorns' damage output is purely roll × stack × CON. This design trusts the 50% proc chance and AffixSlotCost: 2 to balance it.

**Stacking across pieces**: All equipped Thorns values sum into one damage number before softcap.

**Math**:

```
sumThorns         = Σ (pieceValue × (1 + 0.18 × CON_modifier))
effectiveDamage   = softcap(sumThorns, 50.0)
if roll <= 0.50: attacker takes effectiveDamage as Nat20Thorns damage
```

**Notes**:
- Flat damage, not percentage — against high-level attackers with a lot of HP, Thorns feels chippy; against low-level mobs it can one-shot.
- Damage is reflected with an attacker/defender swap: the defender becomes the source of the thorns damage, the attacker becomes the target.
- Hit particle spawns on the attacker's torso when a proc lands.
- Since Thorns uses its own damage cause, it bypasses Natural 20's elemental resistance system and Weakness/Mockery amplification. Pure raw damage.

---

### Block Proficiency

**Where it rolls**: Armor only (shields included, since shields roll on the armor category).

**When it triggers**: Only on **blocked hits** — the incoming damage event must carry the `BLOCKED` meta flag, which Hytale sets when a shield or block stance successfully intercepts the hit.

**What it does**: Reduces the stamina drain the wearer pays for the block. A 30% Block Proficiency makes a blocked hit cost 70% of its usual stamina.

**Rarity roll**:

| Rarity    | Min | Max |
|-----------|-----|-----|
| Common    | 5%  | 10% |
| Uncommon  | 8%  | 15% |
| Rare      | 12% | 22% |
| Epic      | 18% | 30% |
| Legendary | 25% | 40% |

**STR scaling**: Factor 0.15 per point of STR modifier.

**Softcap**: Knee at 80%.

**Stacking across pieces**: Values sum across all equipped armor and shield pieces before softcap.

**Math**:

```
sumReduction       = Σ (pieceValue × (1 + 0.15 × STR_modifier))
effectiveReduction = softcap(sumReduction, 0.80)
blockedHit.staminaDrainMultiplier = base × (1 − effectiveReduction)
```

**Notes**:
- Does **not** reduce the damage the block already mitigates: Block Proficiency only affects the stamina cost, not the damage math. Hytale's native blocking rules handle the damage side.
- Only fires on hits flagged as blocked. Unblocked hits pay normal stamina (because they don't drain block stamina at all) so the reduction is moot.
- A heavy-shield build stacks Block Proficiency to extend how long they can block-tank before running out of stamina.

---

### Gallant

**Where it rolls**: Armor only.

**When it triggers**: Whenever the wearer is struck by a damage event with an entity source (a mob, another player, an NPC). Environmental damage (fall, lava, drowning) doesn't trigger Gallant: it needs a thing to punish.

**What it does**: On a successful proc, applies a debuff **on the attacker**, not the wearer. The debuffed attacker deals reduced damage to everything for the duration of the debuff. A Gallant wearer is not invulnerable, but the mob that hit them now hits softer across the board until the debuff expires.

**Duration**: **7 seconds** from application. Re-procs refresh the debuff and its expiry.

**Rarity roll** (reduction percentage per piece):

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

Defense affixes interact in a specific order when an incoming hit lands. For a blocked melee hit taken by a fully defensive build:

1. **Evasion** rolls first. If it dodges, the entire event is cancelled and nothing else on this list fires.
2. If the hit isn't dodged, **Absorption** converts a portion to mana (subject to cooldown and mana availability).
3. **Block Proficiency** reduces the stamina drain if this is a blocked hit.
4. Vanilla armor reduction and any elemental **Resistance** filters apply.
5. Final damage lands on the wearer.
6. **Thorns** rolls a 50% proc and may reflect flat damage back to the attacker.
7. **Gallant** rolls its proc and may debuff the attacker's outgoing damage for the next 7 seconds.
8. **Resilience** keeps ticking down the wearer's active debuffs in the background regardless of this particular hit.

**Build archetypes**:

- **Evasion tank**: stack Evasion across 2-3 armor pieces (keep in mind the 2-slot cost and 50% hard cap). Pair with DEX investment. Excellent against melee swarms, useless against ranged or elemental damage.
- **Magic tank**: stack Absorption across armor and shield slots and pair with WIS + mana pool investment. Every big hit becomes a partial mana payment instead of a health hit. Thrives when Mana Leech or Focused Mind is available to refill the pool.
- **Retaliation tank**: stack Thorns with high CON. The 50% proc chance plus the 0.18 scaling factor means every other hit bites back; against mobs that swarm and chain-attack, Thorns becomes a serious DPS layer on top of being defense.
- **Debuff-shrugger**: stack Resilience to compress hostile debuffs. Strong in fights with high-duration elemental weakness / Vicious Mockery / Hex setups coming at you.
- **Block tank**: stack Block Proficiency and carry a shield. STR investment rewards both this affix and the physical side of your offense. Pairs naturally with Gallant (another STR/CHA pairing) for attacker-debuff-on-block-then-retaliate rotations.
- **Gallant-focused tank**: stack Gallant across a full set and invest WIS + CHA. Every mob that hits you becomes less dangerous to the whole party for 7 seconds — effectively party-wide damage reduction against targeted mobs, shared from your armor.
