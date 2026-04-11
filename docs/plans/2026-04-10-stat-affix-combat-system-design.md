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

## Phase 1: Combat Test Harness — COMPLETE (2026-04-10)

**Goal**: a `/nat20 combattest` command that spawns a controlled test scenario, so every subsequent phase has instant feedback.

**Status**: Merged to main. All smoke test criteria passed.

### What Was Built

1. **Passive combat dummy** (`CombatDummy.json`): Variant of `Template_Settlement_Civilian`, 10k HP, no wander, ignores players and NPCs. Nametag "Combat Dummy".

2. **Aggressive combat dummy** (`AttackerDummy.json`): Variant of `Template_Settlement_Guard`, 10k HP, Iron Sword, hostile to players. Nametag "Attacker Dummy".

3. **`/nat20 combattest`**: Spawns both dummies near player, auto-enables debug logging. No arguments (see divergences below).

4. **`/nat20 testweapon <affix>`**: Separate command giving an Iron Sword with a specific affix baked into BSON metadata. Affix is functionally active (stat modifiers apply on equip) but tooltip does not display it (see divergences below).

5. **`/nat20 setstats STR 20 DEX 14 ...`**: Sets ability scores on `Nat20PlayerData`. Auto-creates the component if it doesn't exist yet.

6. **`/nat20 debug on|off`**: Toggles per-player `CombatDebugSystem` logging.

7. **`CombatDebugSystem`**: `DamageEventSystem` in Inspect Group. Logs cause, initial/final damage, HP/STA/MP, and D&D ability scores for attacker and target.

### Files Created

- `src/main/java/com/chonbosmods/combat/CombatDebugSystem.java`
- `src/main/java/com/chonbosmods/commands/CombatTestCommand.java`
- `src/main/java/com/chonbosmods/commands/SetStatsCommand.java`
- `src/main/java/com/chonbosmods/commands/DebugCommand.java`
- `src/main/java/com/chonbosmods/commands/TestWeaponCommand.java`
- `src/main/resources/Server/NPC/Roles/Nat20/CombatDummy.json`
- `src/main/resources/Server/NPC/Roles/Nat20/AttackerDummy.json`

### Divergences from Plan

1. **Test weapon is a separate command, not an arg on combattest.** Hytale's command parser doesn't support commands with only `OptionalArg`s and no `RequiredArg`. The original plan had `/nat20 combattest <affix> [rarity]` but this failed at runtime. Split into `/nat20 combattest` (no args) + `/nat20 testweapon <affix>` (required arg).

2. **Test weapon has no tooltip display for forced affixes.** `Nat20ItemRegistry.registerItem()` acquires asset locks that deadlock on the ECS command thread. The test weapon builds `Nat20LootData` manually with BSON metadata, which the equipment listener reads correctly (modifiers ARE active), but the tooltip shows a plain Iron Sword. Acceptable for dev testing: the debug logger confirms modifiers are applied.

3. **No `PlayerScoreChangeEvent` fired by setstats.** The plan called for a custom event. Not needed yet since no systems listen for it. Will add in Phase 3 when `AttributeDerivationSystem` needs it.

4. **`Nat20PlayerData` lazy creation discovered.** The component is only created on first NPC dialogue or `/nat20 setstats`, not on player connect. `SetStatsCommand` was fixed to auto-create it. Phase 3 must handle this (either null check in derivation system or create on `PlayerReadyEvent`).

5. **Hytale `OptionalArg` limitation discovered.** The existing `/nat20 loot` command's optional rarity arg also doesn't work (`/nat20 loot Weapon_Sword_Iron Rare` fails). This is a pre-existing SDK limitation, not new.

---

## Phase 2: Four Proof Affixes — COMPLETE (2026-04-10)

**Goal**: wire one affix per damage pipeline pattern, proving all four hooks work against the test harness.

**Status**: All four systems implemented and smoke-tested. Three fully proven, one architecturally proven but visually incomplete.

### 2a: Absorption (Filter Group) — PROVEN ✅

**What it proves**: intercepting and modifying incoming damage before health is affected.

**Proven method**: `DamageEventSystem` registered in `DamageModule.get().getFilterDamageGroup()`. System fires on all damage events, checks if target is a player, scans weapon + armor for `nat20:absorption` affix, computes effective absorption with WIS scaling and softcap, then:
- Drains mana via `EntityStatMap.subtractStatValue(manaIdx, absorbed)`
- Reduces damage via `damage.setAmount(incoming - absorbed)`
- Per-player cooldown tracked in `ConcurrentHashMap<UUID, Long>` (5 seconds)

**Key SDK patterns**:
- `EntityStatValue.set()` is **protected**: use `subtractStatValue()` / `addStatValue()` / `setStatValue()` on EntityStatMap
- `EntityStatType.getAssetMap().getIndex("Mana")` resolves mana stat index
- `InventoryComponent.getItemInHand(store, ref)` for weapon, `InventoryComponent.getCombined(store, ref, armorComponentTypes)` for armor scan

**Test result**: At Rare + loot 0.8 + WIS 18: raw=0.320 (32%), softcapped effective=0.195 (19.5%). Incoming 10.6 damage → absorbed 2.1, mana 100→98, damage reduced to 8.5. Cooldown correctly blocks absorption for 5s between activations.

**Files**: `Nat20AbsorptionSystem.java`, `absorption.json`

### 2b: Deep Wounds (Inspect Group proc) — PROVEN ✅

**What it proves**: post-damage observation, proc chance rolling, and applying ongoing DOT.

**Proven method**: `DamageEventSystem` registered in `DamageModule.get().getInspectDamageGroup()`. On melee hit, extracts attacker player from `Damage.EntitySource`, scans weapon for `nat20:deep_wounds` affix, rolls proc chance (25%), and on proc stores bleed state in `ConcurrentHashMap<Ref<EntityStore>, BleedState>`. A `ScheduledExecutorService` ticks every 500ms, dispatching to `world.execute()` to apply periodic health drain via `statMap.subtractStatValue(healthIdx, perTickDamage)`.

**Key SDK patterns**:
- `damage.getSource()` → cast to `Damage.EntitySource` → `entitySource.getRef()` for attacker entity
- Manual DOT via scheduled executor + `world.execute()` dispatch (EffectControllerComponent API unverified)
- Direct health drain bypasses damage pipeline: no visual damage numbers/particles (acceptable for Phase 2)

**Test result**: At Rare + loot 0.8 + STR 18: base=4.00, effective=6.88, softcapped total=4.37, perTick=0.44 over 10 ticks (5s). Bleed correctly drains target HP between hits. New proc replaces existing bleed.

**Files**: `Nat20DeepWoundsSystem.java`, `deep_wounds.json`

### 2c: Attack Speed (InteractionChain) — PARTIALLY PROVEN ⚠️

**What it proves**: ECS ticking system architecture, InteractionManager component access, and time shift API.

**Proven method**: `EntityTickingSystem<EntityStore>` with `SystemDependency(Order.AFTER, InteractionSystems.TickInteractionManagerSystem.class)`. Gets InteractionManager via `InteractionModule.get().getInteractionManagerComponent()` (must lazy-init: null during `setup()`). Applies `im.setGlobalTimeShift(InteractionType, bonus)` for all types + `chain.setTimeShift(bonus)` on active chains.

**Result**: Time shift value **sticks and reads back correctly** (`chain.getTimeShift()` returns applied value), but **vanilla Hytale does not use it for swing timing**. Full visual effect requires AttackAnimationsFramework's interaction pipeline (intercepts interaction entries, modifies timing, syncs client animations via `HytalePerPlayerItemAnimationSyncService`).

**Key SDK patterns**:
- `InteractionModule.get().getInteractionManagerComponent()` returns `ComponentType<EntityStore, InteractionManager>` — must be called at runtime, not during `setup()`
- `InteractionManager` is an ECS component at `com.hypixel.hytale.server.core.entity.InteractionManager`
- `InteractionChain` at `com.hypixel.hytale.server.core.entity.InteractionChain` with `setTimeShift(float)` / `getTimeShift()` / `getType()`
- `InteractionType` enum at `com.hypixel.hytale.protocol.InteractionType`
- `UUIDComponent.getComponentType()` needed in ECS query for player identification
- Query: `Query.and(Query.any(), UUIDComponent.getComponentType(), Player.getComponentType())`

**For Phase 4**: either add AAF as a dependency, or decompile `HytalePerPlayerItemAnimationSyncService` to replicate client animation sync.

**Files**: `Nat20AttackSpeedSystem.java`, `attack_speed.json`

### 2d: Focused Mind (Passive tick) — PROVEN ✅

**What it proves**: per-tick regen manipulation based on player movement state.

**Proven method**: `EntityTickingSystem<EntityStore>` that runs every game tick. Tracks per-player position and mana via `ConcurrentHashMap<UUID, PlayerState>`. Detects idle by comparing position between ticks (`distanceSq < 0.01`). When idle and mana increased since last tick (natural regen), multiplies the delta by the affix bonus and adds via `statMap.addStatValue(manaIdx, boost)`. Running at game tick rate produces smooth mana gain that matches native regen cadence (no visible jumps).

**Key SDK patterns**:
- Position from `TransformComponent.getPosition()` (getX/getY/getZ)
- `statMap.addStatValue(statIdx, amount)` to boost regen (public, unlike `set()`)
- `statMap.get(statIdx).getMax()` for clamping
- ECS ticking at game tick rate for smooth stat changes (ScheduledExecutorService at 1s causes visible "jumps")

**Test result**: At Rare + loot 0.8 + WIS 18: effective bonus=74.4%. Native mana regen 5.0/sec, boosted to ~8.7/sec. Smooth regen visible on client. Stops immediately on movement, resumes on idle.

**Files**: `Nat20FocusedMindSystem.java`, `focused_mind.json`

### Also Built in Phase 2

- `Nat20Softcap.softcap(value, k)` utility: `value / (1 + value/k)`
- `/nat20 setmana <amount>` dev command for testing mana-dependent affixes
- Fix: EFFECT/ABILITY affixes skip `StaticModifier` path in `Nat20ModifierManager` (suppresses fake-stat warnings)
- Fix: new affix JSONs must be added to `Nat20AffixRegistry.BUILTIN_FILES` hardcoded list
- Softcap k values: Absorption=0.50, Deep Wounds=12.0, Attack Speed=0.35, Focused Mind=2.0

### Phase 2 Divergences from Plan

1. **Deep Wounds uses manual DOT, not EffectControllerComponent**: `EffectControllerComponent.addEffect()` API unverified. Manual health drain via `subtractStatValue()` works but produces no visual particles/damage numbers. Migration to native effects deferred to Phase 4.
2. **Attack Speed requires AAF for visual effect**: `setTimeShift()` API confirmed working (values stick, read back correctly) but vanilla engine ignores it for swing timing. Architecture correct for AAF integration.
3. **Focused Mind uses position-based idle detection, not MovementStatesComponent**: `MovementStatesComponent` API unverified. Position delta comparison is reliable and proven.
4. **Both Focused Mind and Attack Speed are ECS EntityTickingSystems**: original plan called for ScheduledExecutorService. ECS systems are better: game-tick-rate execution avoids visible stat jumps, proper thread safety, automatic entity iteration.
5. **Softcap k values are hardcoded constants, not loaded from config JSON**: `withConfig()` approach deferred. Constants in each system class are sufficient for Phase 2.
6. **Players have 0 mana by default**: `/nat20 setmana` command added as workaround. Phase 3 will set base mana pool from INT score.

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
