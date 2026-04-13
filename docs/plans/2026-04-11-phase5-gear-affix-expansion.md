# Phase 5: Gear Affix Expansion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement 34 new combat affixes organized into 8 sequential batches. Each batch ends with a compilation check and smoke test before proceeding to the next.

**Status:** CODE COMPLETE (2026-04-12). All 8 batches compile. **Smoke testing in progress:** Batch 1 PASSED (see Phase 5b at the bottom of this document).

**Architecture:** All systems use proven patterns from Phase 2/4. New systems register in `Natural20.setup()` via `getEntityStoreRegistry().registerSystem()`. New affix JSONs are added to `Nat20AffixRegistry.BUILTIN_FILES`. EntityEffect JSONs go in `src/main/resources/Server/Entity/Effects/`. Particle assets go in `src/main/resources/Server/Particles/Combat/`.

**Tech Stack:** Hytale SDK 2026.02.18, Java 25, ScaffoldIt 0.2.x

**Testing model:** No JUnit. Each batch uses `./gradlew compileJava` for compilation, then `./gradlew devServer` + in-game smoke testing via `/nat20 combattest`, `/nat20 testweapon <affix>`, `/nat20 setstats`, `/nat20 debug on`.

**Key constraint:** EntityEffects do not stack. Reapplying refreshes duration. All systems must account for this.

---

## Reference: Registration Patterns

**System registration** (`Natural20.setup()`):
```java
mySystem = new Nat20MySystem(lootSystem);
getEntityStoreRegistry().registerSystem(mySystem);
```

**Disconnect cleanup** (in the `PlayerDisconnectEvent` handler):
```java
if (mySystem != null) mySystem.removePlayer(uuid);
```

**Affix registry** (`Nat20AffixRegistry.BUILTIN_FILES`):
Add new filenames to the `"effect"` array in the `BUILTIN_FILES` map.

**EntityEffect resolution** (lazy, at first use):
```java
private EntityEffect myEffect;
private boolean effectResolved;

private boolean resolveEffect() {
    if (effectResolved) return myEffect != null;
    myEffect = EntityEffect.getAssetMap().getAsset("Nat20MyEffect");
    effectResolved = true;
    return myEffect != null;
}
```

**Affix scan on weapon** (proven pattern):
```java
ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
for (RolledAffix rolled : lootData.getAffixes()) {
    if (AFFIX_ID.equals(rolled.id())) { /* found */ }
}
```

**Affix scan on armor** (proven pattern):
```java
var combined = InventoryComponent.getCombined(store, playerRef, armorComponentTypes);
// iterate combined for loot data on each armor piece
```

**Secondary damage event** (for elemental flat damage / thorns):
```java
commandBuffer.invoke(targetRef, new Damage(damageCauseIdx, amount, Damage.EntitySource.of(attackerRef)));
```

**EntityEffect application** (proven by Deep Wounds):
```java
EffectControllerComponent effectCtrl = store.getComponent(targetRef, EffectControllerComponent.getComponentType());
effectCtrl.addEffect(targetRef, resolvedEffect, commandBuffer);
```

**Particle spawn** (proven by CombatParticleSystem):
```java
ParticleUtil.spawnParticleEffect("Nat20_MyParticle", new Vector3d(x, y, z), store);
```

---

## Batch 1: Flat Elemental Damage (4 affixes, 1 system)

**Pattern:** Inspect Group. On player melee hit, scan weapon for flat elemental affix, fire a secondary `Damage` event with the matching elemental `DamageCause`. No stat scaling on the affix: INT amplifies downstream via existing `Nat20ScoreDamageSystem`.

### Files to Create

**Affix JSONs** (4):
- `src/main/resources/loot/affixes/effect/fire.json`
- `src/main/resources/loot/affixes/effect/frost.json`
- `src/main/resources/loot/affixes/effect/poison.json`
- `src/main/resources/loot/affixes/effect/void_damage.json`

Each follows this structure (example: `fire.json`):
```json
{
  "Id": "nat20:fire",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.fire",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": null,
  "TargetStat": "FireDamage",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 1.0, "Max": 2.0 },
    "Uncommon":  { "Min": 2.0, "Max": 4.0 },
    "Rare":      { "Min": 4.0, "Max": 7.0 },
    "Epic":      { "Min": 7.0, "Max": 11.0 },
    "Legendary": { "Min": 11.0, "Max": 16.0 }
  },
  "Description": "Adds flat fire damage to every hit."
}
```

Frost, Poison, Void use identical structure with appropriate Id/DisplayName/TargetStat.

**Particle assets** (4):
- `src/main/resources/Server/Particles/Combat/Nat20_FireHit.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_FrostHit.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_PoisonHit.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_VoidHit.particlesystem`

Author as small burst particles at the target. Fire: orange sparks. Frost: ice crystal shards. Poison: green toxic splash. Void: purple/dark wisp burst. Use `Nat20_CritMega.particlesystem` as structural reference, shorter LifeSpan (0.3s), smaller BoundingRadius (50).

**System class:**
- `src/main/java/com/chonbosmods/combat/Nat20ElementalDamageSystem.java`

Extends `DamageEventSystem`, group: `DamageModule.get().getInspectDamageGroup()`. On hit:
1. Resolve attacker as player (same guard as DeepWoundsSystem)
2. Get weapon from `InventoryComponent.getItemInHand()`
3. Get `Nat20LootData` from weapon metadata
4. For each rolled affix, check if it matches any of the four elemental affix IDs
5. Look up the `Nat20AffixDef`, compute base value from rarity + loot level via `AffixValueRange.interpolate()`
6. No stat scaling multiplication (StatScaling is null)
7. Fire secondary `Damage` with the matching DamageCause index and computed amount
8. Spawn matching particle at target position

Lazy-resolve all four `DamageCause` indices on first call:
```java
private int fireCauseIdx, iceCauseIdx, poisonCauseIdx, voidCauseIdx;
```
Using `DamageCause.getAssetMap().getIndex("Nat20Fire")` etc. (these DamageCause JSONs already exist from Phase 4).

### Files to Modify

- `src/main/java/com/chonbosmods/loot/registry/Nat20AffixRegistry.java`: add `"fire.json", "frost.json", "poison.json", "void_damage.json"` to the `"effect"` array in `BUILTIN_FILES`.
- `src/main/java/com/chonbosmods/Natural20.java`: instantiate and register `Nat20ElementalDamageSystem` in `setup()`.
- `src/main/java/com/chonbosmods/combat/Nat20CombatParticleSystem.java`: add cause-match for `Nat20Fire`, `Nat20Ice`, `Nat20Poison`, `Nat20Void` → spawn matching `Nat20_FireHit`/`Nat20_FrostHit`/`Nat20_PoisonHit`/`Nat20_VoidHit` particles. (Alternatively, the `Nat20ElementalDamageSystem` can spawn particles directly instead of routing through `Nat20CombatParticleSystem`. Choose whichever is cleaner: if spawning in the system directly, skip this modification.)

### Test Criteria — Batch 1 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → server starts without errors
3. `/nat20 combattest` → spawns dummies
4. `/nat20 debug on`
5. `/nat20 testweapon fire` → equip, hit passive dummy:
   - Debug log shows secondary `Nat20Fire` damage event with correct flat amount
   - Fire impact particle plays at target position
   - If player has INT bonus (`/nat20 setstats INT 20`), `Nat20ScoreDamageSystem` adds INT bonus to the elemental damage
6. Repeat for `/nat20 testweapon frost`, `/nat20 testweapon poison`, `/nat20 testweapon void_damage`
7. Verify no interference with existing crit/bleed systems

**Adjustment window:** fix any issues before proceeding to Batch 2.

---

## Batch 2: Elemental Proc DOTs (4 affixes, 1 system)

**Pattern:** Inspect Group. On player melee hit, scan weapon for DOT affix, roll WIS-scaled proc chance, apply EntityEffect on target via `EffectControllerComponent.addEffect()`. Reapplication refreshes duration (no stacking). Same pattern as `Nat20DeepWoundsSystem`.

### Files to Create

**Affix JSONs** (4):
- `src/main/resources/loot/affixes/effect/ignite.json`
- `src/main/resources/loot/affixes/effect/cold.json`
- `src/main/resources/loot/affixes/effect/infect.json`
- `src/main/resources/loot/affixes/effect/corrupt.json`

Each follows this structure (example: `ignite.json`):
```json
{
  "Id": "nat20:ignite",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.ignite",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "WIS", "Factor": 0.15 },
  "TargetStat": "IgniteDamage",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 1.0, "Max": 2.0 },
    "Uncommon":  { "Min": 1.5, "Max": 3.0 },
    "Rare":      { "Min": 2.0, "Max": 4.5 },
    "Epic":      { "Min": 3.5, "Max": 7.0 },
    "Legendary": { "Min": 5.0, "Max": 10.0 }
  },
  "Description": "On melee hit, chance to ignite the target, dealing fire damage over time.",
  "ProcChance": "20%"
}
```

Cold, Infect, Corrupt use same structure with appropriate Ids, DamageCauses, descriptions.

**EntityEffect JSONs** (4):
- `src/main/resources/Server/Entity/Effects/Nat20IgniteEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20ColdEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20InfectEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20CorruptEffect.json`

Each follows the `Nat20BleedEffect.json` structure (example: `Nat20IgniteEffect.json`):
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#4a0000",
    "EntityTopTint": "#ff4400",
    "EntityAnimationId": "Hurt",
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "LocalSoundEventId": "SFX_Effect_Burn_World",
    "Particles": [
      { "SystemId": "Nat20_IgniteDot" }
    ]
  },
  "DamageCalculator": {
    "BaseDamage": {
      "Nat20Fire": 0.5
    }
  },
  "DamageCalculatorCooldown": 2.0,
  "DamageEffects": {
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "PlayerSoundEventId": "SFX_Effect_Burn_World"
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 16,
  "DeathMessageKey": "server.general.deathCause.ignite"
}
```

Tint colors per element:
- Ignite: `#4a0000` / `#ff4400` (orange-red)
- Cold: `#001a4a` / `#66ccff` (blue-cyan)
- Infect: `#0a2a00` / `#44cc44` (green)
- Corrupt: `#1a0033` / `#aa44ff` (purple)

DamageCause per element: `Nat20Fire`, `Nat20Ice`, `Nat20Poison`, `Nat20Void` (all already exist).

**Particle assets** (4 DOT particles, continuous on entity while effect active):
- `src/main/resources/Server/Particles/Combat/Nat20_IgniteDot.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_ColdDot.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_InfectDot.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_CorruptDot.particlesystem`

Continuous particles attached to entity for effect duration. Fire: flickering flames. Cold: frost crystals. Infect: green toxic cloud. Corrupt: purple void wisps. Use `Dagger_Signature_Status` as reference for continuous entity-attached particles.

**System class:**
- `src/main/java/com/chonbosmods/combat/Nat20ElementalDotSystem.java`

Extends `DamageEventSystem`, group: `DamageModule.get().getInspectDamageGroup()`. Clone of `Nat20DeepWoundsSystem` pattern but handles four DOT affixes in one system:

1. Guard: cancelled, not EntitySource, not player, no weapon, no loot data → return
2. For each rolled affix, check against four DOT affix IDs (`nat20:ignite`, `nat20:cold`, `nat20:infect`, `nat20:corrupt`)
3. On match: resolve affix def, parse proc chance, roll `ThreadLocalRandom`
4. On proc: resolve the matching EntityEffect (lazy, one per element), apply via `EffectControllerComponent.addEffect()`
5. Log to debug system

No per-player state needed (EntityEffect handles lifecycle). No `removePlayer()` method required.

### Files to Modify

- `Nat20AffixRegistry.java`: add `"ignite.json", "cold.json", "infect.json", "corrupt.json"` to `"effect"` array.
- `Natural20.java`: register `Nat20ElementalDotSystem` in `setup()`.
- `Nat20CombatParticleSystem.java`: add cause-match for `Nat20Fire`/`Nat20Ice`/`Nat20Poison`/`Nat20Void` from DOT tick damage → spawn per-tick burst particle (same `Nat20_FireHit` etc. from Batch 1). This makes each DOT tick produce a visible splat like bleed does. (Skip if the EntityEffect's own `DamageEffects` particles are sufficient.)

### Test Criteria — Batch 2 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. `/nat20 testweapon ignite` with cranked-up test values (100% proc chance):
   - Hit dummy → orange entity tint appears, continuous fire particle on entity
   - DOT ticks visible in debug log: `Nat20Fire` damage every 2s for 16s
   - Hit again while DOT active → duration refreshes, does NOT stack
4. Repeat for `cold`, `infect`, `corrupt` with correct tint colors and DamageCauses
5. Verify flat elemental (Batch 1) + DOT on same weapon: both fire independently
6. Verify DOT doesn't interfere with existing bleed

**Adjustment window:** fix any issues before proceeding to Batch 3.

---

## Batch 3: Leech Pair (2 affixes, 2 small systems)

**Pattern:** Inspect Group. After damage resolves, read `damage.getAmount()`, compute percentage, `addStatValue()` on the attacker's health (Life Leech) or mana (Mana Leech). Fires every hit, no proc chance.

### Files to Create

**Affix JSONs** (2):
- `src/main/resources/loot/affixes/effect/life_leech.json`
- `src/main/resources/loot/affixes/effect/mana_leech.json`

```json
{
  "Id": "nat20:life_leech",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.life_leech",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "DEX", "Factor": 0.12 },
  "TargetStat": "LifeLeech",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.02, "Max": 0.04 },
    "Uncommon":  { "Min": 0.03, "Max": 0.06 },
    "Rare":      { "Min": 0.05, "Max": 0.08 },
    "Epic":      { "Min": 0.07, "Max": 0.10 },
    "Legendary": { "Min": 0.08, "Max": 0.12 }
  },
  "Description": "Steal a percentage of damage dealt as health."
}
```

Mana Leech: same structure, `"Primary": "INT"`, Id `nat20:mana_leech`.

**Particle assets** (2):
- `src/main/resources/Server/Particles/Combat/Nat20_LifeLeech.particlesystem` (red blood drop rising from target toward attacker, or red drop2 burst on attacker)
- `src/main/resources/Server/Particles/Combat/Nat20_ManaLeech.particlesystem` (blue water splash / blue drop burst on attacker)

Short burst, LifeSpan 0.3s. Play on the ATTACKER position, not the target.

**System classes** (2):
- `src/main/java/com/chonbosmods/combat/Nat20LifeLeechSystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20ManaLeechSystem.java`

Both extend `DamageEventSystem`, group: `DamageModule.get().getInspectDamageGroup()`.

Logic:
1. Guard: cancelled, zero damage, not EntitySource, not player → return
2. Get weapon, get loot data, find leech affix
3. Compute effective percentage: `AffixValueRange.interpolate(lootLevel)` × stat scaling
4. `healAmount = damage.getAmount() * effectivePercentage`
5. Resolve stat index: `EntityStatType.getAssetMap().getIndex("Health")` (Life) or `"Mana"` (Mana)
6. `EntityStatMap statMap = EntityStatsSystems.getStatMap(store, attackerRef)`
7. `statMap.addStatValue(statIdx, (float) healAmount)`
8. Spawn leech particle at attacker position
9. Debug log

No per-player state map. Stateless system.

### Files to Modify

- `Nat20AffixRegistry.java`: add `"life_leech.json", "mana_leech.json"` to `"effect"` array.
- `Natural20.java`: register both systems in `setup()`.

### Test Criteria — Batch 3 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. `/nat20 testweapon life_leech` with cranked test values (e.g. 50% leech):
   - `/nat20 setstats DEX 20` for stat scaling
   - Take some damage first (hit by attacker dummy or fall)
   - Hit passive dummy → debug log shows heal amount = damage × leech%
   - Health increases on attacker
   - Red blood particle on attacker position
4. `/nat20 testweapon mana_leech`:
   - `/nat20 setmana 50` (start with partial mana)
   - `/nat20 setstats INT 20`
   - Hit dummy → mana increases, blue particle on attacker
5. Life Leech + Crit: leech percentage applies to the FULL crit-amplified damage amount
6. Leech doesn't overheal past max

**Adjustment window:** fix any issues before proceeding to Batch 4.

---

## Batch 4: Debuff/Curse Weapon Effects (4 affixes)

Four affixes that apply EntityEffects with StatModifiers on targets. Hex adds single-hit consumption logic.

### Files to Create

**Affix JSONs** (4):
- `src/main/resources/loot/affixes/effect/vicious_mockery.json`
- `src/main/resources/loot/affixes/effect/fear.json`
- `src/main/resources/loot/affixes/effect/hex.json`
- `src/main/resources/loot/affixes/effect/gallant.json`

**Vicious Mockery** (CHA, weapon): target takes increased damage from all sources.
```json
{
  "Id": "nat20:vicious_mockery",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.vicious_mockery",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "CHA", "Factor": 0.15 },
  "TargetStat": "ViciousMockery",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.08, "Max": 0.12 },
    "Uncommon":  { "Min": 0.10, "Max": 0.16 },
    "Rare":      { "Min": 0.14, "Max": 0.22 },
    "Epic":      { "Min": 0.20, "Max": 0.30 },
    "Legendary": { "Min": 0.25, "Max": 0.35 }
  },
  "Description": "On hit, target takes increased damage from all sources for a short time."
}
```

**Fear** (CHA, weapon): reduces target damage output.
```json
{
  "Id": "nat20:fear",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.fear",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "CHA", "Factor": 0.15 },
  "TargetStat": "Fear",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.08, "Max": 0.12 },
    "Uncommon":  { "Min": 0.10, "Max": 0.16 },
    "Rare":      { "Min": 0.14, "Max": 0.22 },
    "Epic":      { "Min": 0.20, "Max": 0.30 },
    "Legendary": { "Min": 0.25, "Max": 0.35 }
  },
  "Description": "On hit, target deals reduced damage for a short time."
}
```

**Hex** (WIS, weapon): bonus damage on next hit, then consumed.
```json
{
  "Id": "nat20:hex",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.hex",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "WIS", "Factor": 0.18 },
  "TargetStat": "HexDamage",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.15, "Max": 0.25 },
    "Uncommon":  { "Min": 0.20, "Max": 0.35 },
    "Rare":      { "Min": 0.30, "Max": 0.50 },
    "Epic":      { "Min": 0.45, "Max": 0.65 },
    "Legendary": { "Min": 0.60, "Max": 0.80 }
  },
  "Description": "On hit, curse the target. Your next hit against them deals bonus damage, consuming the curse."
}
```

**Gallant** (CHA, armor): chance when struck to debuff attacker's damage.
```json
{
  "Id": "nat20:gallant",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.gallant",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "CHA", "Factor": 0.12 },
  "TargetStat": "Gallant",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.08, "Max": 0.12 },
    "Uncommon":  { "Min": 0.10, "Max": 0.16 },
    "Rare":      { "Min": 0.12, "Max": 0.18 },
    "Epic":      { "Min": 0.14, "Max": 0.20 },
    "Legendary": { "Min": 0.16, "Max": 0.22 }
  },
  "Description": "When struck, chance to reduce attacker's damage output.",
  "ProcChance": "15%"
}
```

**EntityEffect JSONs** (4):
- `src/main/resources/Server/Entity/Effects/Nat20ViciousMockeryEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20FearEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20HexEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20GallantEffect.json`

**Nat20ViciousMockeryEffect.json**: debuff that increases incoming damage. Uses negative damage resistance.
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#330000",
    "EntityTopTint": "#ff0000",
    "Particles": [
      { "SystemId": "Nat20_ViciousMockery" }
    ]
  },
  "StatModifiers": {
    "DamageResistance": [
      { "Target": "BASE", "Type": "ADDITIVE", "Value": -0.15 }
    ]
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 8
}
```

Note: the `StatModifiers.DamageResistance` approach needs SDK investigation. If `DamageResistance` isn't a valid stat for StatModifiers, fall back to a Filter Group amplification system that checks for the effect on the target (same pattern as Weakness Amplify in Batch 5). Document the outcome.

**Nat20FearEffect.json**: debuff that reduces target's damage output.
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#1a0033",
    "EntityTopTint": "#553388",
    "Particles": [
      { "SystemId": "Nat20_Fear" }
    ]
  },
  "StatModifiers": {
    "AttackDamage": [
      { "Target": "BASE", "Type": "MULTIPLICATIVE", "Value": -0.20 }
    ]
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 8
}
```

Same caveat: `AttackDamage` StatModifier needs verification. Fallback: Filter Group system that checks for fear effect on attacker and reduces outgoing damage.

**Nat20HexEffect.json**: visual-only curse marker. No StatModifiers (damage bonus is applied by the system on consumption).
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#1a0033",
    "EntityTopTint": "#9933ff",
    "Particles": [
      { "SystemId": "Nat20_Hex" }
    ]
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 15
}
```

**Nat20GallantEffect.json**: debuff on attacker that reduces their damage.
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#0a001a",
    "EntityTopTint": "#6633aa",
    "Particles": [
      { "SystemId": "Nat20_Gallant" }
    ]
  },
  "StatModifiers": {
    "AttackDamage": [
      { "Target": "BASE", "Type": "MULTIPLICATIVE", "Value": -0.15 }
    ]
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 7
}
```

**Particle assets** (4):
- `src/main/resources/Server/Particles/Combat/Nat20_ViciousMockery.particlesystem` — red `!` icon/shape above entity head, continuous while effect active
- `src/main/resources/Server/Particles/Combat/Nat20_Fear.particlesystem` — dark tint particle on entity, continuous
- `src/main/resources/Server/Particles/Combat/Nat20_Hex.particlesystem` — purple skull/wisp above entity head, continuous until consumed
- `src/main/resources/Server/Particles/Combat/Nat20_Gallant.particlesystem` — purple `?` icon above entity head, continuous

**System classes** (4):
- `src/main/java/com/chonbosmods/combat/Nat20ViciousMockerySystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20FearSystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20HexSystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20GallantSystem.java`

**Nat20ViciousMockerySystem**: Inspect Group. On player melee hit → scan weapon for affix → apply `Nat20ViciousMockeryEffect` on target. Stateless if StatModifiers work in the EntityEffect. If StatModifiers don't work, add a Filter Group companion system that amplifies incoming damage when target has the effect active (requires checking `EffectControllerComponent` for active effect presence).

**Nat20FearSystem**: Inspect Group. On player melee hit → scan weapon → apply `Nat20FearEffect` on target. Same StatModifier caveat as Vicious Mockery.

**Nat20HexSystem**: two-phase system.
- Phase 1 (Inspect Group): on player melee hit → scan weapon for hex affix → if target does NOT already have hex from this attacker → apply `Nat20HexEffect` on target, store `HexState(attackerUuid, bonusDamageMultiplier)` in `ConcurrentHashMap<Ref<EntityStore>, HexState>`.
- Phase 2 (Filter Group): on player melee hit → check if target has hex from this attacker → if yes → multiply damage by `1.0 + hexBonusMultiplier`, remove hex state from map, attempt `EffectControllerComponent.removeEffect()` to strip the visual. If `removeEffect()` doesn't exist, the EntityEffect expires naturally after 15s timeout.
- Important: Hex should NOT be applied and consumed on the same hit. The Inspect Group runs AFTER Filter Group, so application happens after consumption check naturally. Verify this ordering.
- `removePlayer(uuid)` clears any hex states where attacker matches.

**Nat20GallantSystem**: Inspect Group. On incoming melee damage to player → scan ARMOR for gallant affix → roll proc chance → on proc → apply `Nat20GallantEffect` on the ATTACKER. This is a "when struck" trigger, opposite of the others. The system reads the defender's armor, not the attacker's weapon.

### Files to Modify

- `Nat20AffixRegistry.java`: add 4 new filenames to `"effect"` array.
- `Natural20.java`: register 4 systems in `setup()`, add `hexSystem.removePlayer(uuid)` to disconnect handler.

### Test Criteria — Batch 4 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. `/nat20 testweapon vicious_mockery` (cranked test: 100% application):
   - Hit passive dummy → red `!` particle above dummy, entity tint
   - Hit dummy again while debuff active → debug shows amplified damage
   - Wait for duration → visual clears
   - Hit again → debuff reapplies (refreshes, no stack)
4. `/nat20 testweapon fear` (cranked test):
   - Hit attacker dummy → dummy's subsequent hits on player deal less damage
   - Verify via debug log: damage from dummy reduced while fear active
5. `/nat20 testweapon hex` (cranked test):
   - Hit dummy → purple particle above dummy head
   - Hit dummy again → bonus damage on second hit, purple particle disappears
   - Verify hex is NOT applied and consumed on the same hit
   - Verify timeout: apply hex, wait 15s → hex expires naturally
6. Equip gallant armor, let attacker dummy hit player:
   - Debug log shows proc roll
   - On proc → purple `?` above attacker dummy, dummy damage output reduced
7. **SDK investigation results documented**: do EntityEffect `StatModifiers` work for `DamageResistance`/`AttackDamage`? If not, document fallback approach used.

**Adjustment window:** fix any issues. If StatModifiers don't work in EntityEffects, implement Filter Group companion systems before proceeding.

---

## Batch 5: Elemental Weakness + Resistance (9 affixes, 3 systems)

### Files to Create

**Weakness affix JSONs** (4, weapon-only):
- `src/main/resources/loot/affixes/effect/fire_weakness.json`
- `src/main/resources/loot/affixes/effect/frost_weakness.json`
- `src/main/resources/loot/affixes/effect/void_weakness.json`
- `src/main/resources/loot/affixes/effect/poison_weakness.json`

Each with WIS scaling, melee_weapon + ranged_weapon categories.
```json
{
  "Id": "nat20:fire_weakness",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.fire_weakness",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "WIS", "Factor": 0.15 },
  "TargetStat": "FireWeakness",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.10, "Max": 0.15 },
    "Uncommon":  { "Min": 0.13, "Max": 0.20 },
    "Rare":      { "Min": 0.18, "Max": 0.28 },
    "Epic":      { "Min": 0.25, "Max": 0.38 },
    "Legendary": { "Min": 0.30, "Max": 0.45 }
  },
  "Description": "On hit, target takes increased fire damage from all sources for a short time."
}
```

**Weakness EntityEffect JSONs** (4):
- `src/main/resources/Server/Entity/Effects/Nat20FireWeaknessEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20FrostWeaknessEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20VoidWeaknessEffect.json`
- `src/main/resources/Server/Entity/Effects/Nat20PoisonWeaknessEffect.json`

Visual-only EntityEffects (element-colored tint, no StatModifiers). The damage amplification is handled by the system, not the effect.
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#4a2200",
    "EntityTopTint": "#ff6600"
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": true,
  "Duration": 10
}
```

Tint colors: Fire=#ff6600, Frost=#66ccff, Void=#aa44ff, Poison=#44cc44.

**Resistance affix JSONs** (5, armor-only):
- `src/main/resources/loot/affixes/effect/fire_resistance.json`
- `src/main/resources/loot/affixes/effect/frost_resistance.json`
- `src/main/resources/loot/affixes/effect/void_resistance.json`
- `src/main/resources/loot/affixes/effect/poison_resistance.json`
- `src/main/resources/loot/affixes/effect/physical_resistance.json`

Elemental resistances: INT scaling. Physical resistance: STR scaling.
```json
{
  "Id": "nat20:fire_resistance",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.fire_resistance",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "INT", "Factor": 0.12 },
  "TargetStat": "FireResistance",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.05, "Max": 0.08 },
    "Uncommon":  { "Min": 0.07, "Max": 0.12 },
    "Rare":      { "Min": 0.10, "Max": 0.18 },
    "Epic":      { "Min": 0.15, "Max": 0.24 },
    "Legendary": { "Min": 0.20, "Max": 0.30 }
  },
  "Description": "Reduces incoming fire damage."
}
```

Physical Resistance: same structure, `"Primary": "STR"`, matches all Physical-inherited DamageCauses except custom Nat20 causes (to avoid reducing bleed/thorns/crit damage: only reduces vanilla physical).

**System classes** (3):

1. `src/main/java/com/chonbosmods/combat/Nat20WeaknessApplySystem.java`
   - Inspect Group. On player melee hit → scan weapon for weakness affix → apply matching EntityEffect on target → store weakness state in `ConcurrentHashMap<Ref<EntityStore>, WeaknessState>` with element type and multiplier value.
   - `WeaknessState`: record of `(element, multiplier, expiryTimestamp)`.
   - Handles all four elements in one system.
   - `removePlayer(uuid)`: clean up any states where attacker matches.

2. `src/main/java/com/chonbosmods/combat/Nat20WeaknessAmplifySystem.java`
   - Filter Group. On incoming elemental damage → check if target has matching weakness in state map → if yes, multiply `damage.setAmount(damage.getAmount() * (1.0 + multiplier))`.
   - Must run AFTER `Nat20ScoreDamageSystem` so it amplifies the INT-boosted damage.
   - Check expiry timestamp; remove stale entries.

3. `src/main/java/com/chonbosmods/combat/Nat20ResistanceSystem.java`
   - Filter Group. On incoming damage → resolve target as player → scan armor for resistance affixes → compute total resistance percentage for matching element → reduce `damage.setAmount(damage.getAmount() * (1.0 - resistPercentage))`.
   - Handles all five resistance types in one system.
   - Lazy-resolve all DamageCause indices. For physical resistance, match the base `Physical` cause index (need to investigate if Physical-inherited causes also match, or if we need to match each one individually).

### Files to Modify

- `Nat20AffixRegistry.java`: add 9 new filenames to `"effect"` array.
- `Natural20.java`: register 3 systems in `setup()`, add weakness system cleanup to disconnect handler.

### Test Criteria — Batch 5 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. Fire Weakness test:
   - `/nat20 testweapon fire_weakness` (cranked: 100% weakness multiplier)
   - Hit passive dummy → orange tint on dummy
   - Switch to a fire weapon (`/nat20 testweapon fire`), hit same dummy → debug shows amplified fire damage
   - Wait for weakness to expire → fire damage returns to normal
4. Resistance test:
   - Equip fire resistance armor
   - Get hit by a fire source (or use a second player/system to deal fire damage) → debug shows reduced fire damage
   - `/nat20 setstats INT 20` → resistance percentage increases
5. Physical Resistance test:
   - Equip physical resistance armor, get hit by attacker dummy → reduced damage
6. **Team synergy test**: Player A applies Fire Weakness, Player B deals fire damage → B's fire damage is amplified by A's weakness debuff
7. Weakness + flat fire + ignite DOT: weakness amplifies both flat hit and DOT tick damage
8. Resistance stacking: two armor pieces with fire resistance → percentages combine (additive), verify softcap prevents 100%

**Adjustment window:** fix any issues before proceeding to Batch 6.

---

## Batch 6: Remaining Weapon Effects (3 affixes)

Three unique mechanic affixes, each with its own system.

### 6A: Crushing Blow (STR)

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/crushing_blow.json`

```json
{
  "Id": "nat20:crushing_blow",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.crushing_blow",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "STR", "Factor": 0.15 },
  "TargetStat": "CrushingBlow",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.02, "Max": 0.04 },
    "Uncommon":  { "Min": 0.03, "Max": 0.05 },
    "Rare":      { "Min": 0.04, "Max": 0.07 },
    "Epic":      { "Min": 0.06, "Max": 0.10 },
    "Legendary": { "Min": 0.08, "Max": 0.12 }
  },
  "Description": "On hit, deals a percentage of the target's current HP as bonus damage. More effective against high-health targets."
}
```

Test variant: 50% for verification.

**Particle:**
- `src/main/resources/Server/Particles/Combat/Nat20_CrushingBlow.particlesystem` — smoking puff at target, grey/brown smoke burst, LifeSpan 0.5s.

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20CrushingBlowSystem.java`

Inspect Group. On player melee hit:
1. Scan weapon for affix
2. Resolve target health: `EntityStatMap.get(healthIdx).getValue()` for current HP
3. Compute drain: `currentHP * effectivePercentage`
4. Apply: `statMap.subtractStatValue(healthIdx, (float) drain)` on target
5. Spawn smoke puff particle
6. Debug log

### 6B: Backstab (DEX)

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/backstab.json`

```json
{
  "Id": "nat20:backstab",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.backstab",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "DEX", "Factor": 0.15 },
  "TargetStat": "Backstab",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.15, "Max": 0.25 },
    "Uncommon":  { "Min": 0.20, "Max": 0.35 },
    "Rare":      { "Min": 0.30, "Max": 0.45 },
    "Epic":      { "Min": 0.40, "Max": 0.60 },
    "Legendary": { "Min": 0.50, "Max": 0.75 }
  },
  "Description": "Bonus damage when the target is not focused on you."
}
```

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20BackstabSystem.java`

Filter Group. On player melee hit → check if target's current locked target is NOT the attacker → if so, multiply damage.

**SDK investigation needed**: how to read NPC aggro target. Options:
1. `NPCEntity` may have a method to get current target/locked target
2. Check behavior tree blackboard for `LockedTargetClose` slot
3. Fallback: angle-based check (compare attacker position to target facing direction)

Document which approach works.

### 6C: Block Proficiency (STR)

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/block_proficiency.json`

```json
{
  "Id": "nat20:block_proficiency",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.block_proficiency",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon"],
  "StatScaling": { "Primary": "STR", "Factor": 0.15 },
  "TargetStat": "BlockProficiency",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.05, "Max": 0.10 },
    "Uncommon":  { "Min": 0.08, "Max": 0.15 },
    "Rare":      { "Min": 0.12, "Max": 0.22 },
    "Epic":      { "Min": 0.18, "Max": 0.30 },
    "Legendary": { "Min": 0.25, "Max": 0.40 }
  },
  "Description": "Increases damage reduction when blocking."
}
```

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20BlockProficiencySystem.java`

Filter Group. On incoming damage to player → check if player is blocking → if yes, scan weapon for affix → reduce damage by proficiency percentage.

**SDK investigation needed**: how to detect if a player is currently blocking. Check `InteractionManager` or movement state.

### Files to Modify

- `Nat20AffixRegistry.java`: add 3 filenames to `"effect"` array.
- `Natural20.java`: register 3 systems in `setup()`.

### Test Criteria — Batch 6 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. Crushing Blow test:
   - `/nat20 testweapon crushing_blow` (cranked: 50%)
   - `/nat20 setstats STR 20`
   - Hit 10k HP combat dummy → debug shows ~5000 HP drained first hit
   - Hit again → ~2500 drained (50% of remaining 5000)
   - Verify diminishing returns naturally: each hit drains less
   - Smoke puff particle at target
4. Backstab test:
   - `/nat20 testweapon backstab` (cranked: 200% bonus)
   - Hit passive dummy (no aggro) → massive bonus damage
   - Hit attacker dummy that IS targeting you → no bonus
   - Document which aggro detection method worked
5. Block Proficiency test:
   - `/nat20 testweapon block_proficiency` (cranked)
   - Block an attack from attacker dummy → reduced damage vs unblocked
   - Document blocking detection method
6. Crushing Blow + Hex combo: hex bonus should NOT apply to the crushing blow drain (crushing blow bypasses damage pipeline, uses subtractStatValue directly)

**Adjustment window:** fix any issues. Document SDK investigation results for backstab aggro detection and block detection.

---

## Batch 7: Defensive Armor Affixes (5 affixes)

### 7A: Thorns (CON) — proven pattern

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/thorns.json`

```json
{
  "Id": "nat20:thorns",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.thorns",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "CON", "Factor": 0.18 },
  "TargetStat": "ThornsDamage",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 1.0, "Max": 3.0 },
    "Uncommon":  { "Min": 2.0, "Max": 5.0 },
    "Rare":      { "Min": 4.0, "Max": 8.0 },
    "Epic":      { "Min": 6.0, "Max": 11.0 },
    "Legendary": { "Min": 8.0, "Max": 14.0 }
  },
  "Description": "Returns flat damage to melee attackers."
}
```

**DamageCause:**
- `src/main/resources/Server/Entity/Damage/Nat20Thorns.json`
```json
{
  "Inherits": "Physical",
  "DurabilityLoss": false,
  "StaminaLoss": false
}
```

**Particle:**
- `src/main/resources/Server/Particles/Combat/Nat20_Thorns.particlesystem` — metallic spark/clang on the attacker, short burst.

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20ThornsSystem.java`

Inspect Group. On incoming melee damage to player:
1. **Guard: ignore `Nat20Thorns` DamageCause** → prevents infinite ping-pong
2. Resolve attacker from `Damage.EntitySource`
3. Scan player's armor for thorns affix, compute effective flat damage
4. Fire reverse `Damage(thornsCauseIdx, flatDamage, EntitySource.of(playerRef))` at attacker via `commandBuffer.invoke(attackerRef, damage)`
5. Spawn metallic spark particle at attacker position

### 7B: Evasion (DEX) — proven pattern

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/evasion.json`

```json
{
  "Id": "nat20:evasion",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.evasion",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "DEX", "Factor": 0.10 },
  "TargetStat": "Evasion",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.02, "Max": 0.04 },
    "Uncommon":  { "Min": 0.03, "Max": 0.06 },
    "Rare":      { "Min": 0.05, "Max": 0.09 },
    "Epic":      { "Min": 0.07, "Max": 0.12 },
    "Legendary": { "Min": 0.10, "Max": 0.15 }
  },
  "Description": "Chance to completely dodge melee attacks."
}
```

Softcap K=0.20.

**Particle:**
- `src/main/resources/Server/Particles/Combat/Nat20_Evasion.particlesystem` — wind/blur whoosh at player position, swoosh effect.

**Sound:** Find a swoosh/dodge sound from available Hytale SFX. Try `SFX_Dagger_Dodge` or similar.

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20EvasionSystem.java`

Filter Group. On incoming melee damage to player:
1. Scan armor for evasion affix, sum effective dodge chance across all armor
2. Apply softcap: `Nat20Softcap.softcap(totalChance, 0.20)`
3. Roll `ThreadLocalRandom`
4. On dodge: `damage.setAmount(0)`, spawn whoosh particle + sound at player position
5. Debug log

### 7C: Flinch Resist (CON) — needs SDK investigation

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/flinch_resist.json`

```json
{
  "Id": "nat20:flinch_resist",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.flinch_resist",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "CON", "Factor": 0.15 },
  "TargetStat": "FlinchResist",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 2.0, "Max": 4.0 },
    "Uncommon":  { "Min": 3.0, "Max": 6.0 },
    "Rare":      { "Min": 5.0, "Max": 9.0 },
    "Epic":      { "Min": 7.0, "Max": 12.0 },
    "Legendary": { "Min": 8.0, "Max": 14.0 }
  },
  "Description": "Suppress hit animation when damage is below threshold."
}
```

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20FlinchResistSystem.java`

SDK investigation: determine how Hytale triggers flinch/hit animation. Options:
1. If there's a `HitAnimationComponent` or `HitReactionComponent`, suppress it when damage < threshold
2. If flinch is tied to damage amount and there's a flinch threshold property, modify it
3. If no direct control exists, this affix may need to be cut

### 7D: Guard Break Resist (CON) — needs SDK investigation

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/guard_break_resist.json`

```json
{
  "Id": "nat20:guard_break_resist",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.guard_break_resist",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "CON", "Factor": 0.15 },
  "TargetStat": "GuardBreakResist",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.10, "Max": 0.15 },
    "Uncommon":  { "Min": 0.13, "Max": 0.20 },
    "Rare":      { "Min": 0.18, "Max": 0.28 },
    "Epic":      { "Min": 0.25, "Max": 0.38 },
    "Legendary": { "Min": 0.30, "Max": 0.45 }
  },
  "Description": "Reduces stamina drain when blocking attacks."
}
```

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20GuardBreakResistSystem.java`

SDK investigation: how does blocking consume stamina? If there's a block stamina cost stat or component, modify it. Otherwise cut.

### 7E: Resilience (STR) — needs SDK investigation

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/resilience.json`

```json
{
  "Id": "nat20:resilience",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.resilience",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "STR", "Factor": 0.12 },
  "TargetStat": "Resilience",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.10, "Max": 0.15 },
    "Uncommon":  { "Min": 0.13, "Max": 0.20 },
    "Rare":      { "Min": 0.18, "Max": 0.28 },
    "Epic":      { "Min": 0.25, "Max": 0.38 },
    "Legendary": { "Min": 0.30, "Max": 0.45 }
  },
  "Description": "Reduces duration of all debuffs received."
}
```

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20ResilienceSystem.java`

SDK investigation: can we intercept or modify EntityEffect duration at application time? Options:
1. If `addEffect()` has a duration overload parameter → apply with `duration * (1.0 - resilience%)`
2. Per-tick system checks `EffectControllerComponent` for active debuffs, removes early
3. Track application times manually, remove after shortened duration

### Files to Modify

- `Nat20AffixRegistry.java`: add 5 filenames to `"effect"` array.
- `Natural20.java`: register all systems in `setup()`.
- `Nat20CombatParticleSystem.java`: add `Nat20Thorns` cause match → thorns spark particle.

### Test Criteria — Batch 7 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. Thorns test:
   - Equip thorns armor (cranked: 100 flat damage reflected)
   - Let attacker dummy hit player → attacker takes `Nat20Thorns` damage, metallic spark
   - Verify NO infinite loop: thorns damage doesn't trigger thorns
   - Thorns + Gallant: both proc independently on same hit
4. Evasion test:
   - Equip evasion armor (cranked: 80% dodge)
   - Let attacker dummy hit player → most hits dodged (damage = 0), whoosh particle + sound
   - Verify softcap: multiple evasion armor pieces, total chance approaches but never reaches 100%
   - Evasion dodge should NOT trigger Thorns (no damage event to inspect)
5. Flinch Resist: test if implemented, document SDK findings. Cut if no API.
6. Guard Break Resist: test if implemented, document SDK findings. Cut if no API.
7. Resilience: test if implemented. Apply a bleed DOT, verify it expires sooner with resilience armor.

**Adjustment window:** document all SDK investigation results. Cut any affixes where the API doesn't exist. Fix issues before proceeding.

---

## Batch 8: Utility Armor + On-Kill (3 affixes)

### 8A: Water Breathing (WIS)

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/water_breathing.json`

```json
{
  "Id": "nat20:water_breathing",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.water_breathing",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "WIS", "Factor": 0.15 },
  "TargetStat": "WaterBreathing",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.20, "Max": 0.40 },
    "Uncommon":  { "Min": 0.35, "Max": 0.60 },
    "Rare":      { "Min": 0.50, "Max": 0.90 },
    "Epic":      { "Min": 0.80, "Max": 1.30 },
    "Legendary": { "Min": 1.00, "Max": 1.80 }
  },
  "Description": "Greatly increases breath capacity underwater."
}
```

**Implementation**: `StaticModifier(MAX, ADDITIVE)` on the breath/oxygen stat. Same pattern as CON→MaxHP in `Nat20ScoreBonusSystem`. Could fold into that system or create a standalone `Nat20WaterBreathingSystem`.

SDK investigation: identify the breath stat name. Try `EntityStatType.getAssetMap().getIndex("Breath")`, `"Oxygen"`, `"Air"`.

### 8B: Light Foot (DEX) — CONDITIONAL

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/light_foot.json`

```json
{
  "Id": "nat20:light_foot",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.light_foot",
  "NamePosition": "SUFFIX",
  "Categories": ["armor"],
  "StatScaling": { "Primary": "DEX", "Factor": 0.12 },
  "TargetStat": "LightFoot",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.08, "Max": 0.12 },
    "Uncommon":  { "Min": 0.10, "Max": 0.18 },
    "Rare":      { "Min": 0.15, "Max": 0.25 },
    "Epic":      { "Min": 0.20, "Max": 0.30 },
    "Legendary": { "Min": 0.25, "Max": 0.35 }
  },
  "Description": "Reduces stamina cost while sprinting."
}
```

**Implementation**: try `StaticModifier` on sprint stamina cost stat. If no such stat exists: **CUT. No fallback.**

### 8C: Rally (CHA)

**Affix JSON:**
- `src/main/resources/loot/affixes/effect/rally.json`

```json
{
  "Id": "nat20:rally",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.rally",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "StatScaling": { "Primary": "CHA", "Factor": 0.15 },
  "TargetStat": "Rally",
  "ModifierType": "ADDITIVE",
  "ValuesPerRarity": {
    "Common":    { "Min": 0.05, "Max": 0.08 },
    "Uncommon":  { "Min": 0.07, "Max": 0.12 },
    "Rare":      { "Min": 0.10, "Max": 0.18 },
    "Epic":      { "Min": 0.13, "Max": 0.22 },
    "Legendary": { "Min": 0.15, "Max": 0.25 }
  },
  "Description": "On kill, nearby allies receive a temporary damage bonus."
}
```

**EntityEffect:**
- `src/main/resources/Server/Entity/Effects/Nat20RallyEffect.json`

```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#332200",
    "EntityTopTint": "#ffcc00",
    "Particles": [
      { "SystemId": "Nat20_Rally" }
    ]
  },
  "StatModifiers": {
    "AttackDamage": [
      { "Target": "BASE", "Type": "MULTIPLICATIVE", "Value": 0.15 }
    ]
  },
  "OverlapBehavior": "Overwrite",
  "Infinite": false,
  "Debuff": false,
  "Duration": 12
}
```

Note: same StatModifier caveat from Batch 4. If StatModifiers on EntityEffects don't work, use a Filter Group companion system.

**Particle:**
- `src/main/resources/Server/Particles/Combat/Nat20_Rally.particlesystem` — golden glow/buff particle, continuous while buff active.

**System:**
- `src/main/java/com/chonbosmods/combat/Nat20RallySystem.java`

Inspect Group. On damage → check if target dies (kill detection):
1. After damage, check target health ≤ 0 or check for `DeathComponent`
2. Resolve attacker player, scan weapon for rally affix
3. Spatial query: `TargetUtil.getAllEntitiesInSphere(attackerPos, radius, store)` for nearby players
4. For each nearby player (excluding the attacker): apply `Nat20RallyEffect` via `EffectControllerComponent.addEffect()`
5. Reapplication refreshes (no stacking)

**Kill detection approach**: check if `damage.getAmount() >= targetCurrentHealth` in Inspect Group (health hasn't been fully drained yet in Inspect, so compare against pre-damage health). Alternatively, register a listener on a death event if one exists. Document which approach works.

### Files to Modify

- `Nat20AffixRegistry.java`: add 2-3 filenames (water_breathing, rally, conditionally light_foot) to `"effect"` array.
- `Natural20.java`: register systems in `setup()`.

### Test Criteria — Batch 8 Checkpoint

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew devServer` → no errors
3. Water Breathing test:
   - Equip water breathing armor
   - Go underwater → breath lasts significantly longer
   - Document the breath stat name found
4. Light Foot test (if implemented):
   - Equip light foot armor
   - Sprint → stamina drains slower
   - If no API found: confirm cut, remove JSON
5. Rally test:
   - `/nat20 testweapon rally` (cranked: 100% bonus, large radius)
   - Have a second player nearby (or test with NPC ally if possible)
   - Kill a mob → golden glow appears on nearby allies
   - Debug log shows rally effect applied
   - Kill again while buff active → duration refreshes, doesn't stack
6. Rally + Vicious Mockery + Fire Weakness: verify all three work on the same weapon with independent triggers

**Adjustment window:** document all SDK findings. Remove Light Foot if API doesn't exist.

---

## Final Integration Test

After all 8 batches are complete and individually verified:

1. **Full affix count**: verify `Nat20AffixRegistry` loads all new affixes (34 new + existing = total count logged on startup)
2. **Loot roller test**: `/nat20 loot Weapon_Sword_Iron Legendary` multiple times → verify new affixes appear on generated weapons and armor
3. **Multi-affix weapon**: generate or `/nat20 testweapon` a weapon with multiple affixes (e.g. Fire + Ignite + Life Leech) → all three fire independently on hit
4. **Multi-affix armor**: generate armor with multiple affixes (e.g. Fire Resistance + Thorns + Evasion) → all three work when struck
5. **Cross-system combos**:
   - Fire Weakness (from weapon) + Fire Resistance (on target armor): weakness amplifies, resistance reduces, net effect correct
   - Hex consumed on Crushing Blow hit: verify interaction (crushing blow uses subtractStatValue, not damage pipeline: hex may not consume)
   - Life Leech on crit hit: leech percentage applies to crit-amplified damage
   - Evasion dodge: no damage → no thorns reflection, no gallant proc, no deep wounds proc
   - Resilience + Ignite DOT: DOT duration shortened (if resilience implemented)
6. **Performance**: with all systems registered, verify no noticeable tick lag or server performance issues
7. **Disconnect/reconnect**: all per-player state cleaned up, no orphaned entries

---

## Implementation Notes (2026-04-12)

### Compile Fixes Discovered During Implementation

| Issue | Fix |
|-------|-----|
| `Damage` constructor parameter order | `new Damage(source, causeIdx, amount)` not `(causeIdx, amount, source)` |
| `Damage.EntitySource` factory | `new Damage.EntitySource(ref)` not `Damage.EntitySource.of(ref)` |
| `EntityStatType` import path | `entitystats.asset.EntityStatType` not `entitystats.EntityStatType` |
| `CombinedItemContainer` import path | `inventory.container.CombinedItemContainer` not `inventory.CombinedItemContainer` |
| `TransformComponent` yaw access | `getRotation().getYaw()` not `getYaw()` |
| `EntityStatValue` current value | `.get()` not `.getValue()` |

### Design Decisions Made During Implementation

1. **Vicious Mockery + Fear + Gallant: Filter Group companions instead of EntityEffect StatModifiers.** The plan noted `DamageResistance` / `AttackDamage` stat names in EntityEffect `StatModifiers` blocks were unverified. Rather than gamble on an unknown API, all three use an Inspect Group apply system + a Filter Group companion system that reads per-target state from a `ConcurrentHashMap`. The EntityEffect JSONs provide only the visual (tint + particle): no `StatModifiers` block. This is the guaranteed-working pattern.

2. **Backstab uses angle-based detection, not NPC aggro API.** The plan listed three options. Angle-based (dot product of target facing vs direction to attacker, backstab = rear 120° arc) works for all entity types without SDK investigation. NPC aggro target API can be explored during smoke testing as an upgrade.

3. **Flinch Resist, Guard Break Resist, Resilience, Water Breathing, Light Foot: affix JSONs only, no system classes.** All five need SDK APIs that haven't been verified. The affix definitions are registered so the loot roller can generate them, but they have no gameplay effect until systems are added. Block Proficiency has a system class but the blocking detection is a TODO.

4. **Recursion guard in Nat20ElementalDamageSystem.** The system fires secondary damage events (e.g. `Nat20Fire`), which re-enter the Inspect Group and would trigger the system again. Added a check: if incoming damage cause is one of the four elemental causes, return immediately.

5. **Thorns ping-pong guard.** `Nat20ThornsSystem` skips processing when the incoming `DamageCause` is `Nat20Thorns`, preventing infinite reflection loops.

---

## Phase 5b: Systematic Smoke Testing

**Prereqs:** `./gradlew devServer`, connect client, `/nat20 debug on`.

**General procedure for each affix:**
1. `/nat20 testweapon <affix>` (or equip generated armor via `/nat20 loot`)
2. Hit combat dummies or get hit by attacker dummies
3. Check debug log for expected system output
4. Observe visual feedback (particle, entity tint, sound)
5. Verify duration/expiry/refresh behavior

---

### Batch 1: Flat Elemental Damage — PASSED (2026-04-12)

All four flat elemental affixes verified: correct secondary damage events, no recursion, proper particles.

**Fixes applied during testing:**
- Particle spawners replaced with real vanilla IDs (from `Assets.zip` inspection):
  - Fire: `Projectile_Fire_Static_Core` + `Projectile_Fire_Static_Sparks` (flame crystal staff fireball)
  - Frost: `Impact_Ice_Fragments` (LifeSpan 0.2s)
  - Poison: `Impact_Acid_Drops_Hit`
  - Void: `VoidSplash`
- Renamed `nat20:void_damage` → `nat20:void` for consistency with fire/frost/poison naming
  - File renamed: `void_damage.json` → `void.json`
  - Updated: `Nat20AffixRegistry`, `Nat20ElementalDamageSystem`

#### 1. Fire (`nat20:fire`) — PASSED
- `/nat20 testweapon fire` → secondary `Nat20Fire` damage (flat=6.4, final=6.0) every hit
- No recursion (elemental cause guard working)
- Fire crystal staff particle at target on each hit

#### 2. Frost (`nat20:frost`) — PASSED
- `/nat20 testweapon frost` → secondary `Nat20Ice` damage
- Ice fragment particle at target

#### 3. Poison (`nat20:poison`) — PASSED
- `/nat20 testweapon poison` → secondary `Nat20Poison` damage
- Acid drops particle at target

#### 4. Void (`nat20:void`) — PASSED
- `/nat20 testweapon void` → secondary `Nat20Void` damage
- Void splash particle at target

---

### Batch 2: Elemental Proc DOTs — PASSED (2026-04-12)

All four elemental DOT affixes verified: effects apply, entity tints display, DOT ticks deal correct elemental damage every 2s for 20s.

**Fixes applied during testing:**
- ProcChance bumped from 20% to 100% for all four DOTs (20% was too low for gameplay feel)
- Duration aligned to 20s (matching bleed), up from 16s
- EntityEffect particle references switched from broken custom `.particlesystem` files (which used particle system names as spawner IDs) to vanilla particle systems directly:
  - Ignite: `Effect_Fire` (vanilla burn status particle)
  - Cold: `Effect_Snow` + `Effect_Snow_Impact` (vanilla freeze status particle)
  - Infect: `Effect_Poison` (vanilla poison status particle)
  - Corrupt: kept `Nat20_CorruptDot` (uses valid `VoidSplash` + `VoidSmoke_Impact` spawner IDs)
- EntityEffect tints updated to match vanilla status effects:
  - Ignite: `#100600` / `#cf2302` (vanilla burn)
  - Cold: `#80ecff` / `#da72ff` (vanilla freeze)
  - Infect: `#000000` / `#008000` (vanilla poison)
  - Corrupt: `#1a0033` / `#aa44ff` (custom void purple)
- Resistance, Weakness, and INT bonus systems updated to also handle vanilla `Fire`, `Ice`, `Poison` DamageCauses (no vanilla Void exists). Fire resistance armor now resists lava/environmental burns.

#### 5. Ignite (`nat20:ignite`) — PASSED
- Effect applies on hit, vanilla burn tint + `Effect_Fire` particle on entity
- `Nat20Fire` DOT ticks every 2s for 20s

#### 6. Cold (`nat20:cold`) — PASSED
- Effect applies, vanilla freeze tint + `Effect_Snow` particle on entity
- `Nat20Ice` DOT ticks every 2s for 20s

#### 7. Infect (`nat20:infect`) — PASSED
- Effect applies, vanilla poison tint + `Effect_Poison` particle on entity
- `Nat20Poison` DOT ticks every 2s for 20s

#### 8. Corrupt (`nat20:corrupt`) — PASSED
- Effect applies, purple tint + `VoidSplash` particle on entity
- `Nat20Void` DOT ticks every 2s for 20s

---

### Batch 3: Leech Pair — PASSED (2026-04-13)

Both leech affixes verified: correct percentage calculation with stat scaling + softcap, stat restoration confirmed via debug log.

#### 9. Life Leech (`nat20:life_leech`) — PASSED
- Health restored on each hit, debug log confirms `[LifeLeech]` with correct percentages

#### 10. Mana Leech (`nat20:mana_leech`) — PASSED
- Mana restored on each hit (mp 64→65→66→67), debug log confirms `[ManaLeech] leech=7.4% dmg=10.0 restore=0.74`
- At Rare/0.8 + INT 20: 7.4% effective leech after softcap

---

### Batch 4: Debuff/Curse Weapon Effects — PASSED (2026-04-13)

All debuff systems verified: damage amplification/reduction confirmed via debug logs, particle visuals working with floating spread motion.

**Changes during testing:**
- Fear affix removed entirely (cut from mod)
- `/nat20 testarmor <affix>` command added (gives Iron Chestplate with affix, item ID `Armor_Iron_Chest`)
- All proc chances bumped to 100% (gallant was 15%, deep_wounds was 25%)
- Vicious Mockery: entity tint removed, red `!` particle (`Alerted_HiRes.png`) above entity head with floating spread motion
- Gallant: purple `?` particle (`Question.png`) above attacker head with floating spread motion
- Hex: purple poison skull particle (`Effect_Poison_Face1.png` recolored `#9933ff`) above entity head
- All three debuff systems use replace-not-stack logic (re-application refreshes state without stacking particles)
- Hex visual cleanup: EntityEffect Duration=1s with LifeSpan=1s on particle system. HexSystem re-applies effect on every hex-weapon hit (OVERWRITE). On consume, re-application stops and effect+particles expire within 1s naturally. Programmatic `removeEffect` does NOT clean up particles (confirmed: tint-only removal). Short-duration natural expiry is the only reliable cleanup method.
- Hex consume works from any damage source (not just original attacker)
- Discovered `addEffect(ref, effect, duration, OverlapBehavior, accessor)` 5-arg overload for explicit duration/behavior control

#### 11. Vicious Mockery (`nat20:vicious_mockery`) — PASSED
- `[ViciousMockery] applied` on first hit, `[ViciousMockery] refreshed` on subsequent hits
- `[ViciousMockery:Amplify] amplify=+14.5% damage=10.0->11.4` confirmed

#### 12. Fear — REMOVED
- Cut from mod. Functionally redundant with Gallant (both reduce damage output).

#### 13. Hex (`nat20:hex`) — PASSED
- `[Hex] applied bonus=31.5%` on first hit
- `[Hex:Consume] bonus=+31.5% damage=1.0->1.3` on next hit from any source
- Hex-weapon hits: Filter consumes old hex → Inspect applies new hex (correct ordering)
- Skull particle fades within 1s after consume

#### 14. Gallant (`nat20:gallant`) — PASSED
- `[Gallant] proc reduction=10.8%` on first incoming hit
- `[Gallant:Reduce] reduction=10.8% damage=8.9->7.9` on subsequent attacker hits
- `[Gallant] refreshed` on re-proc (no particle stacking)

---

### Batch 5: Elemental Weakness + Resistance — PASSED (2026-04-13)

All weakness and resistance systems verified: damage amplification/reduction confirmed via debug logs. Resistance handles both custom and vanilla elemental DamageCauses.

**Changes during testing:**
- Resistance/Weakness/INT bonus systems updated to handle vanilla `Fire`, `Ice`, `Poison` DamageCauses alongside custom `Nat20Fire`/`Nat20Ice`/`Nat20Poison`. Fire resistance armor now resists lava/environmental burns.
- Weakness particles: per-element floating icons tinted black above entity head (same motion as hex skulls). Fire=`Fire_Small.png`, Ice=`Snowflake.png`, Poison=`Effect_Poison_Face1.png` (animated skull), Void=`Spark_Wave_Purple.png`. Scales calibrated to match poison skull size.
- Weakness EntityEffects stripped of entity tint (particle-only visual).
- Weakness map changed from single-element-per-target to `Map<Ref, EnumMap<Element, WeaknessState>>`: multiple weakness types stack independently on the same entity.
- Replace-not-stack logic: visual only reapplied when previous debuff expired (fixes stale entry bug shared with VM/Gallant).
- All debuff systems (VM, Gallant, Weakness) share the same expiry-aware replace pattern: `isNew = previous == null || expired`.

#### 15-18. Elemental Weakness — PASSED
- `[Weakness] applied FIRE: multiplier=18.1%` on hit
- `[Weakness:Amplify] FIRE +18.1% damage=6.4->7.6` on elemental damage to weakened target
- Multiple weakness types stack on the same entity independently
- Particles visible and non-stacking per element

#### 19-22. Elemental Resistance — PASSED
- `[Resistance] nat20:fire_resistance: resist=12.3% damage=5.0->4.4` on vanilla `Fire` cause (environmental/lava)
- Resistance also works on custom `Nat20Fire` cause (affix elemental damage)

#### 23. Physical Resistance — PASSED
- `[Resistance] nat20:physical_resistance: resist=12.3% damage=9.5->8.3` on attacker dummy hits

---

### Batch 6: Remaining Weapon Effects

#### 24. Crushing Blow (`nat20:crushing_blow`)
- `/nat20 testweapon crushing_blow`
- `/nat20 setstats STR 20`
- Hit 10k HP combat dummy
- **Expect**: debug log `[CrushingBlow] targetHP=10000 drain=X (Y%)`. Smoke puff particle at target.
- Hit again → drain is smaller (percentage of reduced HP)
- Verify diminishing returns: each successive hit drains less

#### 25. Backstab (`nat20:backstab`)
- `/nat20 testweapon backstab`
- `/nat20 setstats DEX 20`
- Approach passive dummy from BEHIND, hit it
- **Expect**: debug log `[Backstab] bonus=+X%` with amplified damage
- Walk in front of dummy, face it, hit it
- **Expect**: no backstab bonus (attacker is in front arc)
- Hit attacker dummy that IS targeting/facing you → no bonus

#### 26. Block Proficiency (`nat20:block_proficiency`)
- `/nat20 testweapon block_proficiency`
- **SDK investigation needed during this test**: determine if player blocking state is detectable
- Block an attack from attacker dummy → check if debug log shows `[BlockProf]` reduction
- If no blocking detection API exists: document finding. System currently acts as passive weapon damage reduction (always on). Decide whether to keep, change, or cut.

---

### Batch 7: Defensive Armor Affixes

#### 27. Thorns (`nat20:thorns`)
- Generate thorns armor (or test via loot command)
- Let attacker dummy hit player
- **Expect**: debug log `[Thorns] reflected X damage to attacker`. Metallic spark particle on attacker.
- **Critical**: verify NO infinite loop. Thorns damage has `Nat20Thorns` cause: the system ignores this cause. If attacker also has thorns armor, only one reflection should occur.

#### 28. Evasion (`nat20:evasion`)
- Generate evasion armor with cranked values
- Let attacker dummy hit player
- **Expect**: some hits dodged (damage = 0), debug log `[Evasion] dodge=true`, whoosh particle at player
- Verify softcap: multiple evasion armor pieces, total chance approaches but never reaches 100%
- Evasion dodge should NOT trigger Thorns (damage is 0, thorns system guards on `damage.getAmount() <= 0`)

#### 29-31. Flinch Resist, Guard Break Resist, Resilience
- **SDK investigation phase.** No system classes exist yet. During this test session:
  1. **Flinch Resist**: investigate `HitAnimationComponent`, `HitReactionComponent`, or similar. Can hit animation be suppressed server-side?
  2. **Guard Break Resist**: investigate how blocking stamina drain is calculated. Is there a stat or component?
  3. **Resilience**: investigate `EffectControllerComponent` API. Does `addEffect()` have a duration overload? Can active effects be queried and removed early?
- Document findings. Implement system classes if API exists. Cut affix JSONs if no viable path.

---

### Batch 8: Utility Armor + On-Kill

#### 32. Water Breathing (`nat20:water_breathing`)
- **SDK investigation**: try `EntityStatType.getAssetMap().getIndex("Breath")`, `"Oxygen"`, `"Air"`, `"Drowning"` to find the breath stat
- If found: implement `StaticModifier(MAX, ADDITIVE)` on that stat (same pattern as CON→MaxHP)
- Test: equip water breathing armor, go underwater → breath lasts longer
- If no stat exists: document, cut affix

#### 33. Light Foot (`nat20:light_foot`)
- **SDK investigation**: look for sprint stamina drain stat. Try `"SprintStaminaCost"`, `"SprintDrain"`, or check `MovementSettings` for stamina drain fields
- If found: implement `StaticModifier` to reduce it
- If no API: cut affix, remove JSON

#### 34. Rally (`nat20:rally`)
- `/nat20 testweapon rally`
- `/nat20 setstats CHA 20`
- Need a killable target: spawn a low-HP NPC or use `/nat20 combattest` with the passive dummy and lower its HP first
- Kill the target
- **Expect**: debug log `[Rally] kill confirmed: buffed N nearby allies`. Golden glow + `Nat20_Rally` particle on nearby players.
- If testing solo: rally fires but buffed=0 (no allies nearby). Verify the kill detection works: debug log should still show the kill confirmation.
- With a second player nearby: verify they receive the `Nat20RallyEffect` EntityEffect (golden tint visible)
- Kill again while buff active → duration refreshes, doesn't stack

---

### Cross-System Combo Tests

After all individual affixes pass, verify these interactions:

| Combo | Expected Behavior |
|-------|-------------------|
| Fire + Ignite on same weapon | Both fire independently: flat fire every hit, ignite proc rolls separately |
| Fire Weakness + flat Fire | Weakness amplifies the flat fire damage |
| Fire Weakness + Ignite DOT tick | Weakness amplifies each DOT tick |
| Fire Resistance (armor) vs Fire Weakness (on self) | Both apply: weakness increases incoming, resistance reduces it |
| Hex + Crushing Blow | Hex consumed on the crushing blow hit IF crushing blow routes through Filter Group. BUT: crushing blow uses `subtractStatValue` directly, bypassing damage pipeline. Hex will NOT consume on crushing blow. Document this interaction. |
| Life Leech + Crit | Leech applies to the full crit-multiplied damage |
| Thorns + Gallant (both on armor) | Both proc independently when struck |
| Evasion dodge + Thorns | Dodged hit (damage=0) → thorns does NOT fire (guarded by `damage.getAmount() <= 0`) |
| Evasion dodge + Gallant | Dodged hit → gallant does NOT proc (guarded by `damage.getAmount() <= 0`) |
| Vicious Mockery + Fear on same weapon | Both apply to target: target takes more damage AND deals less |
| Rally buff on multiple kills | Reapplication refreshes, does not stack (EntityEffect `OverlapBehavior: Overwrite`) |
| Backstab + Crit | Both can apply to same hit: backstab multiplies in Filter Group, crit multiplies in Filter Group |
| Deep Wounds + Ignite on same target | Both DOT effects active simultaneously (different EntityEffects) |

---

### Performance + Cleanup Verification

1. **Server tick rate**: with all ~30 registered systems, verify no noticeable tick lag
2. **Disconnect/reconnect**: disconnect player, reconnect → hex state cleaned up, all per-player state reset
3. **Affix registry count**: check server startup log for total loaded affix count (should be ~54: 28 stat + 44 effect + 2 ability - overlap with pre-existing)
