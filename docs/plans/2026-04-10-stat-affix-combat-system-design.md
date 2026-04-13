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

## Phase 3: Persistent Score Bonuses — COMPLETE (2026-04-10)

**Goal**: wire the six D&D ability scores to their always-on stat effects, independent of gear.

**Status**: Complete. Three systems implemented and smoke-tested. DEX movement speed deferred to Phase 4 (grouped with Attack Speed: both need MovementManager/AAF). See `docs/plans/2026-04-10-phase3-persistent-score-bonuses.md` for implementation details.

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

## Phase 4: Deferred Systems + Crit/Bleed VFX — COMPLETE (2026-04-11)

**Goal**: resolve deferred Phase 3 items and build the crit/bleed damage visibility systems.

**Status**: COMPLETE. All subsystems implemented, visual feedback resolved via particle overlays + native EntityEffect. Smoke-tested and confirmed.

### What Was Built

1. **Custom DamageCause assets** (6): `Nat20Critical`, `Nat20Bleed`, `Nat20Fire`, `Nat20Ice`, `Nat20Void`, `Nat20Poison`. All inherit from `Physical`. `DamageTextColor` stripped: the `CombatTextUpdate` packet carries no color field, so the floating damage number over enemies cannot be recolored server-side. `DamageTextColor` only flows into `DamageInfo` (packet 112) sent to the victim's own HUD when the target is a player.

2. **Custom EntityStatType assets** (2): `Nat20CritChance` (0.0–1.0), `Nat20CritDamage` (1.0–10.0, initial 1.5).

3. **Crit affix definitions** (2): `crit_chance` (EFFECT type, DEX-scaled) and `crit_damage` (EFFECT type, STR-scaled). Scanned directly from weapon loot data (stat-based approach failed: `StaticModifier(MAX)` only sets max, current stays 0 for non-regen stats).

4. **Nat20CritSystem**: `DamageEventSystem` in Filter Group. Scans weapon affixes for `crit_chance`, rolls `ThreadLocalRandom`, multiplies damage by `BASE_CRIT_MULTIPLIER (1.5) + crit_damage`, swaps `DamageCause` to `Nat20Critical`. Supports `nat20:force_crit` marker affix for dev testing (100% crit chance, bypasses softcap).

5. **Bleed: native EntityEffect + particle overlay** ✅. `Nat20DeepWoundsSystem` applies `Nat20BleedEffect` via `EffectControllerComponent.addEffect()` on proc. The EntityEffect ticks natively through Hytale's damage pipeline (hurt animation, flinch, death handling, debuff, entity tint). `Nat20CombatParticleSystem` spawns `Nat20_BleedSplat` (red sauce-splash stream + drops, LifeSpan=0.4s) on each `Nat20Bleed` cause damage event. Tick sound: `SFX_Effect_Burn_World`. Cooldown: 2.0s, Duration: 20s (10 ticks).

6. **Crit: particle + sound overlay** ✅. `Nat20CombatParticleSystem` spawns `Nat20_CritMega` (Explosion_Medium + gold dust + crit sparks, BoundingRadius=100, IsImportant=true) and plays `SFX_Golem_Earth_Slam_Impact` via `SoundUtil.playSoundEvent3d` on each `Nat20Critical` cause damage event.

7. **DEX movement speed**: `Nat20MovementSpeedSystem` (`EntityTickingSystem`). Bridges DEX modifier to `MovementManager.getSettings().baseSpeed` with 4% per DEX modifier point. Drift reconciliation every tick (tolerance 0.02f). Raises `maxSpeedMultiplier` to 3.0f. Syncs to client via `mm.update(playerRef.getPacketHandler())`.

8. **Attack speed client animation sync**: `Nat20AttackSpeedSystem` now sends `UpdateItemPlayerAnimations` packets with boosted `ItemAnimation.speed` on attack animations (swing, stab, slash, strike, attack, combo keywords). Tracks `AnimSyncState` per player, restores baseline on bonus removal.

9. **Gear affix recomputation**: `Nat20ScoreBonusSystem` now accepts `Nat20LootSystem` + `Nat20EquipmentListener`. After applying score bonuses, iterates all equipped items and re-`putModifier`s affix values with updated stat scaling. Idempotent overwrite avoids the clamp-down problem.

10. **INT elemental damage bonus**: `Nat20ScoreDamageSystem` resolves four elemental `DamageCause` indices (`Nat20Fire`, `Nat20Ice`, `Nat20Void`, `Nat20Poison`). When a player deals elemental damage, adds `INT_modifier * 10.0` flat bonus.

### Files Created

- `src/main/resources/Server/Entity/Damage/Nat20Critical.json`
- `src/main/resources/Server/Entity/Damage/Nat20Bleed.json`
- `src/main/resources/Server/Entity/Damage/Nat20Fire.json`
- `src/main/resources/Server/Entity/Damage/Nat20Ice.json`
- `src/main/resources/Server/Entity/Damage/Nat20Void.json`
- `src/main/resources/Server/Entity/Damage/Nat20Poison.json`
- `src/main/resources/Server/Entity/Stats/Nat20CritChance.json`
- `src/main/resources/Server/Entity/Stats/Nat20CritDamage.json`
- `src/main/resources/Server/Entity/Effects/Nat20BleedEffect.json`
- `src/main/resources/Server/Particles/Combat/Nat20_CritMega.particlesystem`
- `src/main/resources/Server/Particles/Combat/Nat20_BleedSplat.particlesystem`
- `src/main/resources/loot/affixes/effect/crit_chance.json`
- `src/main/resources/loot/affixes/effect/crit_damage.json`
- `src/main/java/com/chonbosmods/combat/Nat20CritSystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20CombatParticleSystem.java`
- `src/main/java/com/chonbosmods/combat/Nat20MovementSpeedSystem.java`
- `src/main/java/com/chonbosmods/commands/TestCritWeaponCommand.java`

### Files Modified

- `src/main/java/com/chonbosmods/combat/Nat20DeepWoundsSystem.java` (ScheduledExecutor → EffectControllerComponent)
- `src/main/java/com/chonbosmods/combat/Nat20AttackSpeedSystem.java` (animation sync)
- `src/main/java/com/chonbosmods/combat/Nat20ScoreBonusSystem.java` (gear recomp)
- `src/main/java/com/chonbosmods/combat/Nat20ScoreDamageSystem.java` (INT elemental)
- `src/main/java/com/chonbosmods/loot/Nat20EquipmentListener.java` (expose cache)
- `src/main/java/com/chonbosmods/loot/registry/Nat20AffixRegistry.java` (crit affixes)
- `src/main/java/com/chonbosmods/commands/Nat20Command.java` (testcritweapon)
- `src/main/java/com/chonbosmods/Natural20.java` (registration, removed bleed ticker lifecycle)

### Phase 4 Divergences from Plan

1. **Attack speed uses full server-side timing pipeline, no AAF dependency.** Thorough AAF decompilation (AttackAnimationsFramework-0.2.0) revealed that Hytale's client plays interaction animations at a fixed rate determined by the animation asset: no per-player API exists to change it. `UpdateItemPlayerAnimations` packets are ignored during active interactions. AAF's `HytaleHardAnimationOverrideManager` patches assets globally, which is unsuitable for a per-player affix. Final implementation: `setGlobalTimeShift()` + per-chain `setTimeShift()` + `entry.setTimestamp()` + `Interaction.TIME_SHIFT` meta + `sendSyncPacket()` nudge every 24ms. At production values (10-22%), the tail-end animation frames get trimmed imperceptibly.
2. **INT bonus limited to four specific elements, not generic "magical".** Plan said "distinguish melee from magical DamageCauses." Instead, INT bonus applies only to four custom elemental DamageCauses: `Nat20Fire`, `Nat20Ice`, `Nat20Void`, `Nat20Poison`. These are the four damage types our affixes will use.
3. **Gear recomp uses dirty flag, not PlayerScoreChangeEvent.** Plan called for a custom event. The existing `Nat20ScoreDirtyFlag` already fires on score changes. `Nat20ScoreBonusSystem` recomputes gear affixes in the same dirty-flag tick.
4. **`MovementSettings` is at `com.hypixel.hytale.protocol.MovementSettings`**, not in the player movement package as originally assumed.
5. **Crit affixes are EFFECT type, not STAT.** `StaticModifier(ModifierTarget.MAX)` only increases the stat's max value: the current value stays at 0 for non-regen stats, making the stat-based approach non-functional. Crit system scans weapon affixes directly (same pattern as DeepWounds/AttackSpeed).
6. **Bleed migrated to native EntityEffect.** `store.invoke(targetRef, new Damage(...))` does route through the pipeline (confirmed via decompilation of `DamageSystems.executeDamage`), but `EntityUIEvents` filters out non-`EntitySource` damage (no floating numbers for effect ticks). The correct pattern: `EffectControllerComponent.addEffect()` with an authored `EntityEffect` JSON asset — proven by SimpleEnchantments' BurnEnchantment/PoisonEnchantment. Hytale's `ActiveEntityEffect.tickDamage` dispatches damage via `commandBuffer.invoke(ref, damageEvent)` which flows through all pipeline groups.
7. **`DamageTextColor` is architecturally blocked for PvE floating numbers.** `CombatTextUpdate` packet has only `(hitAngleDeg, text)` — no color field. Color comes from the defender's `CombatTextUIComponent` asset. `DamageTextColor` only flows into `DamageInfo` (packet 112), sent by `PlayerHitIndicators` only when the victim is a player. Stripped `DamageTextColor` from all custom DamageCause JSONs. Visual feedback now uses particle overlays.
8. **`/nat20 testcritweapon` added.** Gives a sword with `nat20:force_crit` marker affix. Nat20CritSystem detects this marker and forces 100% crit chance with base 1.5x multiplier, bypassing softcap and RNG. Used to verify crit visual/sound pipeline.

### Resolved Visual Systems (2026-04-11)

1. **Crit feedback** ✅: `Nat20CombatParticleSystem` (DamageEventSystem, Inspect Group) spawns `Nat20_CritMega` particle (Explosion_Medium + gold dust + crit sparks) and plays `SFX_Golem_Earth_Slam_Impact` at the target position on every `Nat20Critical` cause damage event.

2. **Bleed feedback** ✅: `Nat20BleedEffect` EntityEffect provides native entity tint (red), flinch/hurt animation, `Dagger_Signature_Status` continuous particle, and `SFX_Daggers_T2_Stab_Impact` on application. Per-tick damage fires through the pipeline with `Nat20Bleed` cause: `Nat20CombatParticleSystem` spawns `Nat20_BleedSplat` (Sauce_Splash_Stream + Drops, LifeSpan=0.4s) and the EntityEffect plays `SFX_Effect_Burn_World` per tick. Cooldown: 2.0s, Duration: 20s, 10 ticks total.

### Test Criteria

- `/nat20 testcritweapon`, hit dummy: every swing produces Explosion_Medium golden burst + slam sound + 1.5x damage
- `/nat20 testweapon crit_chance`, hit dummy: ~8% chance of crit with particle/sound
- `/nat20 testweapon deep_wounds`, hit dummy: 25% proc → red entity tint + sauce splat every 2s for 20s + flinch + burn sound
- `/nat20 setstats DEX 20`: movement visibly faster. DEX 10: normal speed
- Attack speed weapon: swing animation visually faster
- Equip stat-scaled weapon, then `/nat20 setstats STR 20`: debug log shows updated affix values
- INT elemental: infrastructure for Phase 5 affixes (Fire, Arcane Edge)

---

## Phase 5: Gear Affix Expansion — CODE COMPLETE (2026-04-12)

**Goal**: implement all remaining combat affixes. Each affix maps to a proven pattern from Phase 2/4. The affix list has been reviewed, edited, and finalized through a brainstorming session.

**Status**: All affix systems implemented. `./gradlew compileJava` passes. **Smoke testing in progress**: Batches 1-5 PASSED (2026-04-13). Fear affix removed. Unified DOT tick system, debuff replace-not-stack pattern, hex short-duration visual cleanup all proven. See `docs/plans/2026-04-11-phase5-gear-affix-expansion.md` for per-affix test procedures and results.

**Deferred to SDK investigation during smoke test**: Flinch Resist, Guard Break Resist, Resilience (no system classes: need API for flinch suppression, block stamina drain, effect duration manipulation). Water Breathing, Light Foot (need stat name discovery for breath and sprint stamina cost). Block Proficiency (system exists but blocking state detection is a TODO).

### Design Constraints

- **EntityEffects do not stack.** Reapplying an active effect refreshes its duration. Systems must not assume stacking.
- **Categories use existing granular values**: `melee_weapon`, `ranged_weapon`, `armor`, `tool`. No new category types.
- **No tool affixes.** None of the Phase 5 affixes make thematic sense on tools.
- **Aura system deferred.** Commanding Presence and Resolve cut from Phase 5. Spatial broadcast to allies is post-MVP scope.
- **Test variants.** Every affix gets a cranked-up test variant for `/nat20 testweapon` verification (same pattern as `force_crit`).

### Proven Patterns Available (from Phase 2 + Phase 4)

| Pattern | Proven By | Key API |
|---------|-----------|---------|
| Filter Group damage intercept | Absorption, Crit | `DamageEventSystem` in `getFilterDamageGroup()`, `damage.setAmount()` |
| Inspect Group proc | Deep Wounds | `DamageEventSystem` in `getInspectDamageGroup()` |
| EntityEffect DOT | Deep Wounds (Phase 4) | `EffectControllerComponent.addEffect(ref, effect, commandBuffer)` with authored EntityEffect JSON |
| EntityEffect debuff w/ StatModifier | — (new, but EntityEffect + StatModifier both proven separately) | `EffectControllerComponent.addEffect()` with `StatModifiers` in EntityEffect JSON |
| Particle overlay on damage cause | Crit, Bleed (Phase 4) | `Nat20CombatParticleSystem` cause-match → `ParticleUtil.spawnParticleEffect()` |
| Sound on damage cause | Crit (Phase 4) | `SoundUtil.playSoundEvent3d(soundIdx, SoundCategory.SFX, x, y, z, store)` |
| ECS ticking system | Focused Mind, MovementSpeed | `EntityTickingSystem<EntityStore>` |
| Per-tick regen intercept | Focused Mind, ScoreRegen | Detect regen delta, multiply, `addStatValue()` |
| StaticModifier on stat | CON→MaxHP, INT→MaxMana | `EntityStatMap` StaticModifier with keyed modifier |
| Kill detection | — (new pattern) | `DeathComponent` check in Inspect Group, resolve killer from `Damage.EntitySource` |

### Removed Affixes (from old Phase 5 list)

| Affix | Reason |
|-------|--------|
| Spell Attunement (INT) | Cut |
| Arcane Edge (INT) | Cut |
| Health on Kill (WIS) | Replaced by Life Leech |
| Mana on Kill (INT) | Replaced by Mana Leech |
| Knockback Resist (CON) | Cut |
| Vampiric | Replaced by Life Leech |
| Revitalizing | Cut |
| Thunderstruck | Cut |
| Commanding Presence (CHA) | Deferred: aura system post-MVP |
| Resolve (CHA) | Deferred: aura system post-MVP |
| Demoralize (CHA) | Renamed/split: Fear (weapon) + Gallant (armor) |

---

### 5A: Flat Elemental Damage (Weapon-Only)

Four affixes that add flat elemental damage on every hit. No stat scaling on the affix itself: the base value comes purely from rarity and loot level. INT amplifies the damage downstream through the existing `Nat20ScoreDamageSystem` elemental bonus (`intMod * 10.0` on elemental DamageCauses).

| Affix | DamageCause | Categories | Particle |
|-------|-------------|------------|----------|
| Fire | `Nat20Fire` | `melee_weapon`, `ranged_weapon` | Fire impact |
| Frost | `Nat20Ice` | `melee_weapon`, `ranged_weapon` | Ice/frost burst |
| Poison | `Nat20Poison` | `melee_weapon`, `ranged_weapon` | Green toxic splash |
| Void | `Nat20Void` | `melee_weapon`, `ranged_weapon` | Purple/dark burst |

**Implementation**: Inspect Group system. On hit, resolve attacker's weapon affix, fire a secondary `Damage` event with the elemental `DamageCause` and flat damage amount. `Nat20CombatParticleSystem` matches the cause for the visual. Each element gets its own particle asset.

**JSON structure** (example `fire.json`):
```json
{
  "Id": "nat20:fire",
  "Type": "EFFECT",
  "DisplayName": "server.nat20.affix.fire",
  "NamePosition": "PREFIX",
  "Categories": ["melee_weapon", "ranged_weapon"],
  "ValuesPerRarity": {
    "Common": { "Min": 1.0, "Max": 2.0 },
    "Uncommon": { "Min": 2.0, "Max": 4.0 },
    "Rare": { "Min": 4.0, "Max": 7.0 },
    "Epic": { "Min": 7.0, "Max": 11.0 },
    "Legendary": { "Min": 11.0, "Max": 16.0 }
  }
}
```

No `StatScaling` field. No `ProcChance`. Fires every hit.

**Files per affix**: 1 affix JSON + 1 particle asset. System class shared: `Nat20ElementalDamageSystem`.

---

### 5B: Elemental Proc DOTs (Weapon-Only)

Four affixes that proc an elemental DOT EntityEffect on hit. WIS scaling governs proc chance and DOT strength. Same EntityEffect pattern proven by Deep Wounds/Bleed in Phase 4.

| Affix | DOT Effect | DamageCause | Categories | Particle |
|-------|------------|-------------|------------|----------|
| Ignite | `Nat20IgniteEffect` | `Nat20Fire` | `melee_weapon`, `ranged_weapon` | Burning flames on entity |
| Cold | `Nat20ColdEffect` | `Nat20Ice` | `melee_weapon`, `ranged_weapon` | Frost crystals on entity |
| Infect | `Nat20InfectEffect` | `Nat20Poison` | `melee_weapon`, `ranged_weapon` | Green toxic cloud on entity |
| Corrupt | `Nat20CorruptEffect` | `Nat20Void` | `melee_weapon`, `ranged_weapon` | Purple void wisps on entity |

**Implementation**: Inspect Group system. On hit, scan weapon for DOT affix, roll proc chance (WIS-scaled), on proc apply the EntityEffect via `EffectControllerComponent.addEffect()`. Reapplication refreshes duration (no stacking). `Nat20CombatParticleSystem` spawns per-tick particles matching the elemental DamageCause.

**EntityEffect JSON structure** (example `Nat20IgniteEffect.json`):
```json
{
  "ApplicationEffects": {
    "EntityBottomTint": "#4a0000",
    "EntityTopTint": "#ff4400",
    "EntityAnimationId": "Hurt",
    "Particles": "Server/Particles/Combat/Nat20_Ignite.particlesystem"
  },
  "DamageCalculator": {
    "BaseDamage": { "Nat20Fire": 0.5 }
  },
  "DamageCalculatorCooldown": 2.0,
  "Duration": 16,
  "Debuff": true,
  "DeathMessageKey": "server.general.deathCause.ignite"
}
```

**Scaling note**: same open issue as Deep Wounds bleed scaling (Phase 6). Per-tick damage is hardcoded in the EntityEffect JSON. WIS scaling affects proc chance in the system class but not the per-tick DOT amount. Phase 6 will address DOT scaling across all five DOT affixes (Bleed, Ignite, Cold, Infect, Corrupt) with a unified solution.

**Files per affix**: 1 affix JSON + 1 EntityEffect JSON + 1 particle asset. System class shared: `Nat20ElementalDotSystem`.

---

### 5C: Elemental Weakness Debuffs (Weapon-Only)

Four affixes that debuff the target to take increased damage from a specific element for a short period. WIS scaling governs the vulnerability multiplier. Team synergy: a WIS warrior applies Fire Weakness, then an INT mage's fire hits deal bonus damage.

| Affix | Element | Categories | Visual |
|-------|---------|------------|--------|
| Fire Weakness | `Nat20Fire` | `melee_weapon`, `ranged_weapon` | Orange entity tint |
| Frost Weakness | `Nat20Ice` | `melee_weapon`, `ranged_weapon` | Light blue entity tint |
| Void Weakness | `Nat20Void` | `melee_weapon`, `ranged_weapon` | Purple entity tint |
| Poison Weakness | `Nat20Poison` | `melee_weapon`, `ranged_weapon` | Green entity tint |

**Implementation**: Two systems working together.

1. **Application** (Inspect Group): on hit, scan weapon for Weakness affix, apply `Nat20<Element>WeaknessEffect` EntityEffect on target. The EntityEffect provides the entity tint visual and marks the target as vulnerable. Duration: 8-12 seconds (WIS-scaled via system, not EntityEffect JSON).

2. **Amplification** (Filter Group): before elemental damage is applied, check if target has the matching Weakness effect active. If so, multiply the elemental damage by `1.0 + weaknessMultiplier`. The multiplier is stored per-target in a `ConcurrentHashMap` by the application system and read by the amplification system.

**Values**: Conservative. Common +10-15% elemental damage taken, Legendary +30-45%.

**Files per affix**: 1 affix JSON + 1 EntityEffect JSON. Two shared system classes: `Nat20WeaknessApplySystem` (Inspect) + `Nat20WeaknessAmplifySystem` (Filter).

---

### 5D: Elemental Resistance (Armor-Only)

Four affixes that reduce incoming elemental damage. INT scaling. Mirror of the Weakness debuffs but defensive.

| Affix | Element | Categories |
|-------|---------|------------|
| Fire Resistance | `Nat20Fire` | `armor` |
| Frost Resistance | `Nat20Ice` | `armor` |
| Void Resistance | `Nat20Void` | `armor` |
| Poison Resistance | `Nat20Poison` | `armor` |

**Implementation**: Filter Group system. On incoming elemental damage, scan target's armor for matching resistance affix, reduce `damage.setAmount()` by the effective percentage. INT scaling on the affix base value.

**Values**: Common 5-8% reduction, Legendary 20-30%.

**Physical Resistance (STR)** uses the same system but matches all physical DamageCauses. Separate affix JSON, same system class.

| Affix | Element | Categories | Stat |
|-------|---------|------------|------|
| Physical Resistance | Physical | `armor` | STR |

**Files**: 5 affix JSONs. One shared system class: `Nat20ResistanceSystem`.

---

### 5E: On-Hit Weapon Effects

#### Crushing Blow (STR)

Reduces target's current life by a percentage on hit. Most effective at high HP, diminishes as target gets lower: natural boss-killer mechanic.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: STR
- **Pattern**: Inspect Group. Read `target.health.get()`, compute `currentHP * percentage`, apply via `subtractStatValue(healthIdx, amount)`.
- **Values (conservative)**: Common 2-4%, Legendary 8-12%.
- **Test variant**: 50% for verification.
- **Visual**: Smoking puff particle on hit. New `Nat20_CrushingBlow.particlesystem` asset.
- **Files**: 1 affix JSON + 1 particle asset. New system: `Nat20CrushingBlowSystem`.

#### Vicious Mockery (CHA)

Debuff: target takes increased damage from all sources while active.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: CHA
- **Pattern**: Inspect Group applies `Nat20ViciousMockeryEffect` EntityEffect. The EntityEffect includes a negative damage resistance `StatModifier` so all incoming damage is amplified. Reapplication refreshes duration.
- **Values**: Common +8-12% damage taken, Legendary +25-35%.
- **Duration**: 6-10 seconds.
- **Visual**: Red `!` particle above target entity head (EntityEffect-managed, auto-tracks entity). New `Nat20_ViciousMockery.particlesystem`.
- **Files**: 1 affix JSON + 1 EntityEffect JSON + 1 particle asset. System: `Nat20ViciousMockerySystem`.

#### Hex (WIS)

Curse: target takes bonus damage from your next hit only, then consumed.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: WIS
- **Pattern**: Two systems.
  1. **Application** (Inspect Group): on hit, apply `Nat20HexEffect` EntityEffect on target. Store hex state (bonus damage amount, applier UUID) in `ConcurrentHashMap<Ref, HexState>`.
  2. **Consumption** (Filter Group): before damage applies, check if target has hex from this attacker. If so, add bonus damage, remove the EntityEffect via `EffectControllerComponent.removeEffect()` (investigate API), clear state map. If `removeEffect()` doesn't exist, set a very short duration on application and rely on natural expiry as safety net.
- **Values**: Common +15-25% bonus on next hit, Legendary +60-80%.
- **Safety timeout**: EntityEffect expires after 15 seconds if not consumed.
- **Visual**: Purple particle above target head (repurpose poison skull particle tinted purple). EntityEffect-managed. New `Nat20_Hex.particlesystem`.
- **Files**: 1 affix JSON + 1 EntityEffect JSON + 1 particle asset. System: `Nat20HexSystem`.

#### Fear (CHA)

Debuff: reduces target's damage output.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: CHA
- **Pattern**: Inspect Group applies `Nat20FearEffect` EntityEffect. The EntityEffect includes a `StatModifier` that reduces target's attack damage stat.
- **Values**: Common -8-12% damage dealt, Legendary -25-35%.
- **Duration**: 6-10 seconds.
- **Visual**: Entity tint (dark/muted). EntityEffect-managed particle TBD.
- **Files**: 1 affix JSON + 1 EntityEffect JSON. System: `Nat20FearSystem`.

#### Life Leech (DEX)

Steal a percentage of damage dealt as health.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: DEX
- **Pattern**: Inspect Group. After damage resolves, read `damage.getAmount()`, compute percentage, `addStatValue(healthIdx, healAmount)` on attacker. Fires every hit, no proc chance.
- **Values**: Common 2-4%, Legendary 8-12%.
- **Visual**: Blood drop particle on attacker position. New `Nat20_LifeLeech.particlesystem` (similar to bleed splat but on the attacker, red drop2 pattern).
- **Files**: 1 affix JSON + 1 particle asset. System: `Nat20LifeLeechSystem`.

#### Mana Leech (INT)

Steal a percentage of damage dealt as mana. Same pattern as Life Leech.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: INT
- **Pattern**: Inspect Group. After damage resolves, compute percentage of damage as mana, `addStatValue(manaIdx, amount)` on attacker. Fires every hit.
- **Values**: Common 2-4%, Legendary 8-12%.
- **Visual**: Blue water splash / blue drop particle on attacker. New `Nat20_ManaLeech.particlesystem`.
- **Files**: 1 affix JSON + 1 particle asset. System: `Nat20ManaLeechSystem`.

#### Backstab (DEX)

Bonus damage if the target isn't aggro'd on you.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: DEX
- **Pattern**: Filter Group. Check target's aggro/locked target state. If the target's current locked target is not the attacker (or has no target), multiply damage. If the target IS aggro'd on the attacker, no bonus.
- **Values**: Common +15-25% bonus, Legendary +50-75%.
- **Implementation detail**: read target NPC's locked target via `NPCEntity` API or behavior tree state. If no reliable API exists for reading NPC aggro target, fall back to checking if target is facing the attacker (angle-based backstab).
- **Files**: 1 affix JSON. System: `Nat20BackstabSystem`.

---

### 5F: Defensive Armor Affixes

#### Thorns (CON)

Return flat damage to melee attackers.

- **Categories**: `armor`
- **StatScaling**: CON
- **Pattern**: Inspect Group. On incoming melee damage, resolve attacker from `Damage.EntitySource`, fire a reverse `Damage` event back at attacker with custom `Nat20Thorns` DamageCause. Thorns system ignores incoming `Nat20Thorns` damage to prevent infinite ping-pong.
- **DamageCause**: New `Nat20Thorns.json` inheriting Physical.
- **Values**: Common 1-3 flat damage, Legendary 8-14 flat damage (scaled by CON modifier).
- **Visual**: Metallic spark/clang particle on attacker. New `Nat20_Thorns.particlesystem`.
- **Files**: 1 affix JSON + 1 DamageCause JSON + 1 particle asset. System: `Nat20ThornsSystem`.

#### Evasion (DEX)

Chance to fully dodge a melee hit.

- **Categories**: `armor`
- **StatScaling**: DEX
- **Pattern**: Filter Group. On incoming melee damage, scan armor for evasion affix, roll chance, cancel via `damage.setAmount(0)`. Play dodge feedback on the player (not the attacker).
- **Values**: Conservative. Common 2-4% chance, Legendary 10-15%.
- **Softcap**: K=0.20 (hard cap around 20% dodge chance).
- **Visual**: Wind/blur whoosh particle at player position + swoosh sound. New `Nat20_Evasion.particlesystem`.
- **Files**: 1 affix JSON + 1 particle asset. System: `Nat20EvasionSystem`.

#### Gallant (CHA)

Chance when struck that the attacker receives a damage reduction debuff.

- **Categories**: `armor`
- **StatScaling**: CHA
- **Pattern**: Inspect Group. On incoming melee damage, roll proc chance, apply `Nat20GallantEffect` EntityEffect on attacker. The EntityEffect reduces attacker's damage output via `StatModifier`. Reapplication refreshes duration.
- **Values**: Proc chance Common 8-12%, Legendary 20-30%. Damage reduction -10-20%.
- **Duration**: 6-8 seconds.
- **Visual**: Purple `?` particle above attacker's head. EntityEffect-managed. New `Nat20_Gallant.particlesystem`.
- **Files**: 1 affix JSON + 1 EntityEffect JSON + 1 particle asset. System: `Nat20GallantSystem`.

#### Flinch Resist (CON)

Cancel hit animation below a damage threshold.

- **Categories**: `armor`
- **StatScaling**: CON
- **Pattern**: Filter Group. On incoming damage, if amount is below threshold, suppress flinch/stagger. SDK investigation needed: determine if `damage.setAmount(0)` in a secondary Filter pass after `ApplyDamage` suppresses the hit animation, or if a different API controls flinch independently. May require `HitAnimationComponent` or similar.
- **Values**: Threshold Common 2-4 damage, Legendary 8-14 damage.
- **Files**: 1 affix JSON. System: `Nat20FlinchResistSystem`.

#### Guard Break Resist (CON)

Reduce stamina drain from blocked hits.

- **Categories**: `armor`
- **StatScaling**: CON
- **Pattern**: Filter Group. On incoming damage while blocking, reduce the stamina cost of the block. SDK investigation needed: determine how blocking stamina drain is calculated (likely a stat or component on the block interaction).
- **Values**: Common 10-15% reduced stamina drain, Legendary 30-45%.
- **Files**: 1 affix JSON. System: `Nat20GuardBreakResistSystem`.

#### Water Breathing (WIS)

Boost max breath/oxygen.

- **Categories**: `armor`
- **StatScaling**: WIS
- **Pattern**: StaticModifier on breath/oxygen EntityStatType. Same proven pattern as CON→MaxHP. SDK investigation needed: identify the breath stat name.
- **Values**: Common +20-40% max breath, Legendary +100-180%.
- **Files**: 1 affix JSON. Implementation in `Nat20ScoreBonusSystem` or new `Nat20WaterBreathingSystem`.

#### Light Foot (DEX)

Reduced stamina cost while sprinting.

- **Categories**: `armor`
- **StatScaling**: DEX
- **Pattern**: StaticModifier on sprint stamina drain stat if one exists. **Cut if no API.** No fallback implementation.
- **Values**: Common 8-12% reduced cost, Legendary 25-35%.
- **Files**: 1 affix JSON. Conditional implementation.

#### Resilience (STR)

Reduce duration of all debuffs received.

- **Categories**: `armor`
- **StatScaling**: STR
- **Pattern**: SDK investigation needed. Options:
  1. If `EffectControllerComponent.addEffect()` has a duration parameter we can intercept: wrap or hook the application to scale duration down.
  2. Per-tick system that checks active effects and removes them early based on STR-scaled reduction.
  3. Track effect application times and manually remove via `removeEffect()` after shortened duration.
- **Values**: Common 10-15% duration reduction, Legendary 30-45%.
- **Files**: 1 affix JSON. System: `Nat20ResilienceSystem`.

---

### 5G: On-Kill Weapon Effects

#### Rally (CHA)

On kill, nearby allies receive a temporary damage bonus.

- **Categories**: `melee_weapon`, `ranged_weapon`
- **StatScaling**: CHA
- **Pattern**: Inspect Group with kill detection. Check `DeathComponent` on target after damage. On kill, spatial query for nearby allied players within radius (15-20 blocks). Apply `Nat20RallyEffect` EntityEffect on each ally. The EntityEffect includes a `StatModifier` boosting attack damage. Reapplication refreshes duration (no stacking).
- **Values**: Common +5-8% damage bonus, Legendary +15-25%. Duration: 10-15 seconds. Radius: 15-20 blocks.
- **Visual**: Golden glow / buff particle on affected allies. EntityEffect-managed. New `Nat20_Rally.particlesystem`.
- **Files**: 1 affix JSON + 1 EntityEffect JSON + 1 particle asset. System: `Nat20RallySystem`.

---

### Phase 5 Asset Summary

**New affix JSONs** (34 total):
- 4 flat elemental (fire, frost, poison, void)
- 4 elemental DOT (ignite, cold, infect, corrupt)
- 4 elemental weakness (fire_weakness, frost_weakness, void_weakness, poison_weakness)
- 5 elemental/physical resistance (fire_resistance, frost_resistance, void_resistance, poison_resistance, physical_resistance)
- 7 on-hit weapon (crushing_blow, vicious_mockery, hex, fear, life_leech, mana_leech, backstab)
- 6 defensive armor (thorns, evasion, gallant, flinch_resist, guard_break_resist, resilience)
- 2 conditional armor (water_breathing, light_foot)
- 1 on-kill weapon (rally)
- 1 block proficiency (block_proficiency — see below)

**Block Proficiency (STR)**: Listed in old Phase 5 but not discussed in brainstorming. Survives as a weapon affix: intercept blocked hits in Filter Group, scale damage reduction. Categories: `melee_weapon`. Implementation straightforward if blocking API is accessible.

**New EntityEffect JSONs** (12):
- `Nat20IgniteEffect.json`, `Nat20ColdEffect.json`, `Nat20InfectEffect.json`, `Nat20CorruptEffect.json`
- `Nat20FireWeaknessEffect.json`, `Nat20FrostWeaknessEffect.json`, `Nat20VoidWeaknessEffect.json`, `Nat20PoisonWeaknessEffect.json`
- `Nat20ViciousMockeryEffect.json`, `Nat20FearEffect.json`, `Nat20HexEffect.json`, `Nat20GallantEffect.json`
- `Nat20RallyEffect.json`

**New DamageCause JSONs** (1):
- `Nat20Thorns.json`

**New particle assets** (~16):
- 4 elemental hit (fire, frost, poison, void impact)
- 4 elemental DOT (ignite flames, cold crystals, infect cloud, corrupt wisps)
- Crushing Blow smoke puff
- Vicious Mockery red `!`
- Hex purple skull/particle
- Life Leech red blood drop
- Mana Leech blue drop/splash
- Thorns metallic spark
- Evasion wind whoosh
- Gallant purple `?`
- Rally golden glow

**New Java system classes** (~14):
- `Nat20ElementalDamageSystem` (flat elemental, shared)
- `Nat20ElementalDotSystem` (proc DOTs, shared)
- `Nat20WeaknessApplySystem` + `Nat20WeaknessAmplifySystem` (weakness pair)
- `Nat20ResistanceSystem` (all resistances, shared)
- `Nat20CrushingBlowSystem`
- `Nat20ViciousMockerySystem`
- `Nat20HexSystem`
- `Nat20FearSystem`
- `Nat20LifeLeechSystem`
- `Nat20ManaLeechSystem`
- `Nat20BackstabSystem`
- `Nat20ThornsSystem`
- `Nat20EvasionSystem`
- `Nat20GallantSystem`
- `Nat20RallySystem`
- `Nat20FlinchResistSystem`
- `Nat20GuardBreakResistSystem`
- `Nat20ResilienceSystem`
- `Nat20WaterBreathingSystem` (if not folded into ScoreBonusSystem)
- `Nat20BlockProficiencySystem`

### Implementation Batching

Systems that share the same pattern should be implemented together:

**Batch 1: Flat elemental damage** (4 affixes, 1 system)
Simplest new pattern: Inspect Group, secondary damage event, particle per element.

**Batch 2: Elemental DOTs** (4 affixes, 1 system)
Deep Wounds clone with different DamageCauses and EntityEffects.

**Batch 3: Leech pair** (2 affixes, 2 systems)
Inspect Group, `addStatValue` on attacker. Life and Mana are near-identical.

**Batch 4: Debuff/curse weapon effects** (4 affixes: Vicious Mockery, Fear, Hex, Gallant)
All apply EntityEffects with StatModifiers. Hex adds consumption logic.

**Batch 5: Elemental weakness + resistance** (9 affixes, 3 systems)
Weakness apply/amplify pair + resistance reduction. All elemental damage filtering.

**Batch 6: Remaining weapon effects** (3 affixes: Crushing Blow, Backstab, Block Proficiency)
Each is a unique mechanic but straightforward single-system implementations.

**Batch 7: Defensive armor** (5 affixes: Thorns, Evasion, Flinch Resist, Guard Break Resist, Resilience)
Mixed patterns. Thorns and Evasion are proven-pattern. Flinch/Guard Break/Resilience need SDK investigation.

**Batch 8: Utility armor + on-kill** (3 affixes: Water Breathing, Light Foot, Rally)
Water Breathing is a StaticModifier (trivial). Light Foot conditional on API. Rally needs kill detection + spatial query.

### Test Criteria

Each affix needs:
1. `/nat20 testweapon <affix>` with cranked-up test values for clear verification
2. Hit combat dummy, observe effect in debug log
3. Visual feedback (particle/sound/tint) fires correctly
4. Effect expires or consumes correctly
5. Reapplication refreshes (does not stack)

**Cross-affix combos to verify**:
- Flat Fire + Ignite DOT on same weapon: both fire independently
- Fire Weakness + flat Fire: weakness amplifies the flat damage
- Hex + Crushing Blow: hex consumed on the crushing blow hit
- Life Leech + Crit: leech applies to the crit-amplified damage
- Thorns + Gallant: both proc independently when struck
- Evasion dodges Thorns reflection (attacker has evasion armor, target has thorns armor)
- Resilience reduces Ignite/Cold/Infect/Corrupt DOT duration
- Rally buff on allies does not stack from multiple kills

---

## Phase 6: Polish & Softcap Tuning

**Goal**: tune all softcap `k` values, verify affix interactions under stacking, and harden edge cases.

### Softcap Tuning

- All `k` values in config JSON, hot-reloadable via `withConfig()`
- Test each affix at extreme values: score 20 (modifier +5), Legendary scale factor (0.12)
- Priority targets: Crit Chance, Attack Speed, Movement Speed, Absorption percentage
- Verify diminishing returns feel right: first few points of an affix should feel impactful, stacking past 3-4 items of the same affix should plateau

### Deep Wounds Bleed Damage Scaling

Phase 4 migrated Deep Wounds from a custom `ScheduledExecutorService` to a native `Nat20BleedEffect` EntityEffect applied via `EffectControllerComponent.addEffect()`. The effect ticks natively through Hytale's damage pipeline (hurt animation, resist, death handling, debuff icon, entity tint all free), and `Nat20CombatParticleSystem` draws the red splat on each tick by filtering on the `Nat20Bleed` DamageCause.

**Open issue**: per-tick damage is hardcoded to `0.5` in `Server/Entity/Effects/Nat20BleedEffect.json` via `DamageCalculator.BaseDamage.Nat20Bleed`. Affix rarity, weapon loot level, and STR scaling are computed and logged in `Nat20DeepWoundsSystem` but do not affect the number, so a Legendary high-STR bleed deals the same DPS as a Common low-STR bleed.

Phase 6 options to restore scaling:
- **Variant effects** (simplest): ship multiple EntityEffects (`Nat20BleedEffect_Light/Medium/Heavy/Severe`) with different `BaseDamage` values and pick at proc time from the softcapped rolled total. Discrete buckets are cheap and preserve all native behavior.
- **Duration scaling** (medium): call the `addEffect(ref, effect, duration, overlap, accessor)` overload with `duration = softcappedTotal / baseDamagePerTick` so higher rolls last longer at the same per-tick DPS. Changes the feel of the DoT rather than the burst.
- **Custom damage calculator** (deepest): decompile `DamageCalculator` / `ActiveEntityEffect.tickDamage` to see if there's a way to inject per-application damage multipliers, or fall back to a secondary Filter-group system that scales `damage.getAmount()` on `Nat20Bleed` cause events based on per-target state we stash on application.

Decision should be made after Phase 5 so we know how many other DoT affixes share the same constraint. Phase 5 adds four more DOTs (Ignite, Cold, Infect, Corrupt) for a total of five DOT affixes sharing this limitation: the custom-damage-calculator investigation almost certainly pays for itself.

### Interaction Testing

| Combo | Verify |
|-------|--------|
| Crit Chance + Crit Damage + Deep Wounds | Crit can proc bleed |
| Absorption + Flinch Resist | Absorption reduces first, flinch checks reduced amount |
| Attack Speed + Backstab | Fast swings don't trivialize aggro window |
| Fire Weakness + flat Fire + Ignite | Weakness amplifies both flat and DOT damage |
| Hex + Crushing Blow | Hex consumed on crushing blow hit, bonus applies to % drain |
| Life Leech + Crit | Leech percentage applies to crit-amplified damage |
| Thorns + Gallant | Both proc independently when struck |
| Resilience + Ignite/Cold/Infect/Corrupt | DOT duration shortened by STR scaling |
| Rally on kill + Rally on kill (two players) | Effects refresh, don't stack |
| Evasion vs Thorns | Dodged hit doesn't trigger thorns reflection |
| Multiple `nat20:` modifiers on same stat | Overwrite prevents drift |

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
Phase 1: Test Harness ✅
    ↓
Phase 2: Four Proof Affixes ✅ ←── also builds softcap utility
    ↓
Phase 3: Persistent Bonuses ✅ ←── uses softcap, regen intercept pattern from Phase 2
    ↓
Phase 4: Deferred + Crit/Bleed VFX ✅ ←── DEX speed, attack speed anim sync, gear recomp,
    ↓                                       INT elemental, crit particle+sound, bleed EntityEffect+particle
Phase 5: Gear Affix Expansion ✅ (code) ←── 34 affixes in 8 batches, all proven patterns
    ↓                                        pending systematic smoke test
Phase 5b: Smoke Testing ←── per-affix manual verification, SDK investigation for 5 deferred affixes
    ↓
Phase 6: Polish & Tuning ←── DOT scaling (5 DOTs), softcap k values, interaction combos
```
