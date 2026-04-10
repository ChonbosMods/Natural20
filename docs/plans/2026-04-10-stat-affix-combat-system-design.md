# Stat & Affix Combat System: Phased Implementation Design

> Phased plan for wiring the D&D ability score system and gear affixes into Hytale's combat pipeline. Companion to `NAT20_STAT_SYSTEM_GUIDE.md` and `NAT20_PASSIVES_AND_AFFIXES.md`.

## Context

The stat enum (`Stat.java`), player data (`Nat20PlayerData` with scores/modifiers), affix definitions (42 JSON files), modifier manager (`Nat20ModifierManager`), equipment listener (`Nat20EquipmentListener`), and damage event skeleton (`Nat20AffixEventListener`) all exist. What's missing: the actual combat mechanics that connect ability scores and gear affixes to Hytale's damage pipeline, entity effects, and stat systems.

Research from 11 community mod JARs (HeroCore, HySkills, RPGLeveling, StunningCombat, AttackAnimationsFramework, weapon-enhancer, SimpleEnchantments, hyRPGCore, HytaleDevLib, Mana_Stamina_Tweaks, RPGMobs) confirmed the SDK patterns documented in the design guides. Key corrections applied to the guides:

- **Regen scaling**: per-tick intercept (not StaticModifier on Regenerating), because Regenerating entries are global
- **Movement speed**: `MovementManager.getSettings().baseSpeed` fallback if `hytale:movement_speed` stat type doesn't exist
- **Attack speed**: `InteractionChain.setTimeShift()` or `InteractionManager.setGlobalTimeShift()`, not a simple cooldown read
- **Softcap**: `softcap(v, k) = v / (1 + v/k)` applied to all affix effective values to prevent linear stacking
- **Equip detection**: active hotbar item change is now a native SDK feature (no polling needed)

## Shared Utilities (Built as Needed)

### Softcap Function

```java
public static double softcap(double value, double k) {
    if (k <= 0 || value <= 0) return 0;
    return value / (1.0 + value / k);
}
```

Each affix type defines its own `k` in config JSON. Aggressive `k` for crit chance, attack speed, movement speed. Generous `k` for flat damage bonuses.

### Damage Pipeline Ordering Reference

| Slot | Registration | Our Use |
|------|-------------|---------|
| AFTER Gather, BEFORE Filter | `SystemGroupDependency(AFTER, getGatherDamageGroup())` + `SystemGroupDependency(BEFORE, getFilterDamageGroup())` | Raw weapon scaling (future) |
| IN Filter Group | `getGroup() → getFilterDamageGroup()` | Crit rolls, Absorption, Block Prof, Knockback Resist, Backstab, fall damage reduction |
| AFTER Filter, BEFORE ApplyDamage | `SystemGroupDependency(AFTER, getFilterDamageGroup())` + `SystemDependency(BEFORE, DamageSystems.ApplyDamage.class)` | STR/INT flat damage bonuses |
| IN Inspect Group | `getGroup() → getInspectDamageGroup()` | Deep Wounds, Fire, Demoralize, Rally, Arcane Edge, Health Regen on Kill, lifesteal |

### Modifier Key Convention

| Type | Pattern | Example |
|------|---------|---------|
| Persistent score bonus | `nat20:<score>_<effect>` | `nat20:con_max_health` |
| Gear affix | `nat20:affix_<id>` | `nat20:affix_attack_speed` |
| Aura buff on ally | `nat20:aura_<source>_<effect>` | `nat20:aura_presence_damage` |

---

## Phase 1: Combat Test Harness

**Goal**: a `/nat20 combattest` command that spawns a controlled test scenario, so every subsequent phase has instant feedback.

### Deliverables

1. **Passive combat dummy**: high-HP NPC spawned 5 blocks in front of the player. Doesn't fight back or flee. Visible nametag: "Combat Dummy". Uses a custom `nat20:combat_dummy` role with `"Attitude": "Passive"` and inflated health.

2. **Aggressive combat dummy**: spawned beside the passive dummy. Predictable fixed-damage melee attacks, no abilities. Visible nametag: "Attacker Dummy". For testing incoming damage, Absorption, flinch resist, knockback resist.

3. **Test weapon command**: `/nat20 combattest <affix_id> <level>` gives the player a weapon with the specified affix baked into BSON metadata at the given level.

4. **Stat override command**: `/nat20 setstats STR 20 DEX 14 ...` sets ability scores in `Nat20PlayerData` and fires a `PlayerScoreChangeEvent`.

5. **Debug damage logger**: a `DamageEventSystem` in Inspect Group that logs to console on every damage event: raw damage, modified damage, active `nat20:` modifiers on attacker and target, active entity effects on target, mana/health/stamina snapshot. Togglable per-player via `/nat20 debug combat on|off`.

### Test Criteria

- Run `/nat20 combattest`, see both dummies spawn
- Hit passive dummy, see debug log with damage numbers
- Get hit by attacker dummy, see debug log with incoming damage
- `/nat20 setstats CON 20`, observe no visible change yet (persistent bonuses not wired until Phase 3)

---

## Phase 2: Four Proof Affixes

**Goal**: wire one affix per damage pipeline pattern, proving all four hooks work against the test harness.

### 2a: Absorption (Filter Group)

**What it proves**: intercepting and modifying incoming damage before health is affected.

- `Nat20AbsorptionSystem` extending `DamageEventSystem`, registered in Filter Group
- On incoming damage to a player with Absorption affix equipped:
  - Check cooldown timer (transient component on player, fixed server-config value)
  - If cooldown expired: compute absorbed amount (capped per-hit percentage), subtract from mana via `statMap.subtractStatValue(manaIndex, absorbed)`, reduce `damage.setAmount()` by same amount, reset cooldown timer
  - If cooldown active: skip entirely, no partial absorption
- Effective absorption percentage = `softcap(base × (1 + WIS_mod × scale_factor), k)`
- **Test**: equip absorption weapon, `/nat20 setstats WIS 18`, let attacker dummy hit you. Debug log shows mana drop + reduced health damage. Take another hit within cooldown window: full damage, no mana change.

### 2b: Deep Wounds (Inspect Group proc)

**What it proves**: firing post-damage entity effects via EffectControllerComponent.

- Create `Entity/Effects/nat20_bleed.json`: periodic DOT, red drip particles, audio tick, `OverlapBehavior: Extend`
- Create `Entity/DamageCauses/nat20_bleed_tick.json`: physical DOT damage type
- In Inspect Group system: on melee hit with Deep Wounds affix, roll proc chance scaled by STR modifier, apply via `EffectControllerComponent.addEffect(targetRef, bleedEffect, duration, OverlapBehavior.EXTEND, store)`
- Both proc chance and per-tick damage scale with STR: `softcap(base × (1 + STR_mod × scale_factor), k)`
- **Test**: equip deep wounds weapon, `/nat20 setstats STR 18`, hit passive dummy. Debug log shows proc roll, bleed particles on dummy, periodic damage ticks.

### 2c: Attack Speed (Custom stat / InteractionChain)

**What it proves**: modifying native interaction timing from gear affix data.

- `Nat20AttackSpeedSystem` extending `EntityTickingSystem`, queries players with active weapon
- Read attack speed affix value from equipped weapon BSON, compute effective value with softcap
- Apply via `InteractionChain.setTimeShift(float)` on active chains, or `InteractionManager.setGlobalTimeShift(InteractionType, float)`
- Client animation sync via `UpdateItemPlayerAnimations` packet if visual desync is noticeable
- Reset to default time shift when weapon is unequipped
- **Test**: equip attack speed weapon, `/nat20 setstats DEX 18`, swing at passive dummy. Visibly faster swing rate vs. bare-handed baseline.

### 2d: Focused Mind (Passive tick)

**What it proves**: per-tick regen manipulation based on movement state.

- `Nat20FocusedMindSystem` extending `EntityTickingSystem`, queries players with Focused Mind affix equipped
- Read `MovementStates.idle` from `MovementStatesComponent`
- When idle: detect mana regen delta (current - previous tick's value), multiply by WIS-scaled bonus, write boosted value back via `statMap.setStatValue()`
- Track previous mana values per player in `ConcurrentHashMap<UUID, Float>`
- Ticks stop boosting immediately on movement
- Effective regen multiplier = `softcap(base × (1 + WIS_mod × scale_factor), k)`
- **Test**: `/nat20 setstats WIS 16`, equip focused mind weapon, stand still near dummies. Debug log shows boosted mana regen. Start moving: regen returns to normal rate.

### Also Built in Phase 2

- `Nat20Softcap.softcap(value, k)` utility function
- Softcap config JSON with per-affix `k` values, loaded via `withConfig()`

---

## Phase 3: Persistent Score Bonuses

**Goal**: wire the six D&D ability scores to their always-on stat effects, independent of gear.

### Core System

`Nat20AttributeDerivationSystem` extending `EntityTickingSystem`, implementing `EntityStatsSystems.StatModifyingSystem` (marker interface for stat recalculation ordering).

Per-tick logic (optimized with dirty flag):
1. Read six scores from `Nat20PlayerData`, compute D&D modifiers: `floor((score - 10) / 2)`
2. Compare against cached previous modifiers per player: skip recalc if unchanged
3. Remove all `nat20:<score>_<effect>` keyed modifiers from relevant stats
4. Write fresh `StaticModifiers` back to `EntityStatMap`

Recalculation triggers:
- `PlayerScoreChangeEvent` (fired by `/nat20 setstats` and future leveling)
- Player connect
- The dirty flag ensures the tick-based system only does real work when scores change

### Bonus Implementations

**StaticModifier bonuses** (straightforward):

| Score | Bonus | Implementation |
|-------|-------|----------------|
| CON | Max health | `StaticModifier(MAX, ADDITIVE)` on `getHealth()` |
| CON | Max stamina | `StaticModifier(MAX, ADDITIVE)` on `getStamina()` |
| INT | Max mana | `StaticModifier(MAX, ADDITIVE)` on `getMana()` |

**Filter Group damage bonuses** (new DamageEventSystem or extend existing):

| Score | Bonus | Implementation |
|-------|-------|----------------|
| STR | Melee damage | Flat additive `damage.setAmount(damage.getAmount() + STR_mod)` on melee hits |
| INT | Spell/magic damage | Flat additive on magical DamageCause hits |
| DEX | Fall damage reduction | Reduce `damage.setAmount()` on `FALL` DamageCause by DEX modifier |

**Per-tick interceptors** (same pattern proven by Focused Mind in Phase 2):

| Score | Bonus | Implementation |
|-------|-------|----------------|
| STR | Stamina regen | Detect regen delta, multiply by `1 + STR_mod × 0.18` |
| WIS | Mana regen | Detect regen delta, multiply by `1 + WIS_mod × 0.025` |

**Movement speed** (DEX):

- Try `StaticModifier` on `hytale:movement_speed` stat type
- If stat index resolves to `Integer.MIN_VALUE`: fall back to `MovementManager.getSettings().baseSpeed` manipulation with `mm.update(packetHandler)`
- Add reconciliation check to prevent drift (tolerance 0.02f)

**Custom float** (WIS perception): stored on `Nat20PlayerData`, read by quest/detection systems. No native stat hook.

**CHA**: no combat hook. Already handled by dialogue system.

### Gear Affix Recomputation

When `PlayerScoreChangeEvent` fires, all currently equipped items must recompute their effective affix values (since ability modifier changed). The derivation system notifies `Nat20ModifierManager` to remove and reapply all `nat20:affix_*` modifiers with updated effective values.

### Test Criteria

- `/nat20 setstats CON 20`: max health visibly increases, debug log shows modifier applied
- `/nat20 setstats DEX 18`: movement speed increases (walk around to feel it)
- `/nat20 setstats STR 16`: hit passive dummy, debug log shows higher damage than baseline
- `/nat20 setstats WIS 14`: stand still, observe faster mana regen in debug log
- Change stats while holding an affix weapon: affix effective values update in debug log

---

## Phase 4: Remaining 14 Affixes

**Goal**: batch-implement all remaining affixes. Each is a variation on a pattern proven in Phase 2.

### Implementation Order

**4a: Crit system** (Crit Chance + Crit Damage as a pair):
- Create `Entity/Stats/nat20_crit_chance.json` and `nat20_crit_damage.json` custom stat types
- Create `Entity/DamageCauses/nat20_critical.json` with yellow `damageTextColor`
- Crit Chance (DEX affix): writes `StaticModifier` to `nat20:crit_chance` stat
- Crit resolution system in Filter Group: read crit chance stat, roll, set crit flag
- Crit Damage (STR affix): reads crit flag, multiplies `damage.setAmount()`
- Swap DamageCause to `nat20:critical`, inject `IMPACT_PARTICLES` and `IMPACT_SOUND_EFFECT`

**4b: Remaining Filter Group** (proven by Absorption):
- Block Proficiency (STR): intercept blocked hits, scale damage reduction
- Backstab (DEX): check target aggro state, bonus damage if unaggro'd
- Knockback Resist (CON): add multiplier < 1.0 to `KnockbackComponent.modifiers`
- Flinch Resist (CON): conditionally cancel hit animation below threshold
- Guard Break Resist (CON): scale stamina drain threshold

**4c: Remaining Inspect Group procs** (proven by Deep Wounds):
- Fire (INT): roll chance, apply native `hytale:burning` via EffectControllerComponent, duration scales with INT
- Arcane Edge (INT): fire secondary `Damage` event with `nat20:magical` DamageCause
- Spell Attunement (INT): modifier read by mana drain system before subtracting cost
- Demoralize (CHA): apply `nat20:fear` Entity Effect with negative damage output modifier on target
- Health Regen on Kill (WIS): kill detection via `DeathComponent`, immediate `addStatValue` on killer's health
- Rally (CHA): kill detection, broadcast Entity Effect to allies within radius

**4d: Aura system** (newest pattern):
- Commanding Presence (CHA): `EntityTickingSystem` with spatial query via `SpatialResource.getSpatialStructure().collect(position, radius, refs)` for nearby players. Broadcast flat damage bonus as `StaticModifier`. Remove when source player moves out of range or unequips.

### New Assets

- `Entity/Effects/nat20_fear.json` (debuff: outgoing damage reduction)
- `Entity/DamageCauses/nat20_magical.json` (Arcane Edge secondary hit)
- `Entity/DamageCauses/nat20_critical.json` (yellow damage text)
- `Entity/Stats/nat20_crit_chance.json` and `nat20_crit_damage.json`

### Test Criteria

Each affix tested individually via `/nat20 combattest <affix_id> <level>` with appropriate stat overrides. Crit system tested end-to-end: equip both Crit Chance and Crit Damage weapons (or a weapon with both affixes), hit dummy repeatedly, observe yellow crit numbers in damage log.

---

## Phase 5: Polish & Softcap Tuning

**Goal**: tune all softcap `k` values, verify affix interactions under stacking, and harden edge cases.

### Softcap Tuning

- All `k` values in config JSON, hot-reloadable via `withConfig()`
- Test each affix at extreme values: score 20 (modifier +5), Legendary scale factor (0.12)
- Priority targets: Crit Chance, Attack Speed, Movement Speed, Absorption percentage
- Verify diminishing returns feel right: first few points of an affix should feel impactful, stacking past 3-4 items of the same affix should plateau

### Interaction Testing

| Combo | Verify |
|-------|--------|
| Crit Chance + Crit Damage + Deep Wounds | Crit can proc bleed |
| Absorption + Flinch Resist | Absorption reduces first, flinch checks reduced amount |
| Attack Speed + Backstab | Fast swings don't trivialize aggro window |
| Commanding Presence + Rally | Aura modifiers don't accumulate on allies |
| Multiple `nat20:` modifiers on same stat | Remove-before-rewrite prevents drift |

### Edge Cases

- Score change while item is equipped: gear affixes recompute with new modifier
- Item swap during Absorption cooldown: cooldown persists, doesn't reset
- Player death/respawn: persistent bonuses reapply, transient timers reset
- Chunk reload: verify StaticModifiers survive entity persistence
- Disconnect/reconnect: full recalculation on connect

### Debug Cleanup

- `/nat20 debug combat on|off` stays in codebase as a dev tool
- Modifier key audit: log any orphaned `nat20:` modifiers on player connect
- Combat dummies remain spawnable for ongoing development

---

## Phase Dependencies

```
Phase 1: Test Harness
    ↓
Phase 2: Four Proof Affixes ←── also builds softcap utility
    ↓
Phase 3: Persistent Bonuses ←── uses softcap, regen intercept pattern from Phase 2
    ↓
Phase 4: Remaining 14 Affixes ←── all patterns proven, batch work
    ↓
Phase 5: Polish & Tuning
```

Phases 2 and 3 are independent of each other (both depend only on Phase 1), but building Phase 2 first de-risks the damage pipeline and proves the regen intercept pattern that Phase 3 reuses.
