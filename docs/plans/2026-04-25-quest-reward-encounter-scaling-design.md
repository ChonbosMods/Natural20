# Quest Reward + Encounter Scaling

**Date:** 2026-04-25
**Branch:** TBD (likely `feat/quest-reward-encounter-scaling` off main)
**Status:** Design locked, implementation pending
**Amends:** `docs/plans/2026-04-19-quest-reward-system.md` (per-phase reward model). Reward roll moves from quest generation to per-phase turn-in; ilvl is no longer hardcoded by difficulty.

## Problem

Quest difficulty (`easy.json` / `medium.json` / `hard.json`) is currently rolled **uniformly at random** at quest generation, with both reward ilvl AND encounter mob ilvl hardcoded per difficulty (Easy=5, Medium=15, Hard=25). Three concrete consequences:

1. **Quest difficulty has no relationship to where the quest is or who's doing it.** A level-3 player in a starter zone has equal chance of getting an Easy quest (ilvl 5 mobs, ilvl 5 reward) or a Hard quest (ilvl 25 mobs, ilvl 25 reward). The Hard one is unwinnable for them; the reward is also unusable until they level up 22 levels.
2. **`AffixRewardRoller.roll()` bypasses the gear filter.** Quest rewards pull from `Nat20LootEntryRegistry.getAllItemIds()` directly, ignoring the blocklist + tier_tokens + tier_item_overrides we just shipped. Blocklisted items (`Weapon_Wand_Root`, all 5 spellbooks, the `_NPC` weapons) can drop as quest rewards even though they were explicitly removed from mob/chest pools.
3. **Quest rewards don't cater to player progression.** Stored on `PhaseReward` at generation, frozen until turn-in. Player levels up mid-quest? Reward stays at the old ilvl.

## Goals

1. **Plug the blocklist hole**: route `AffixRewardRoller` through the gear filter (`Nat20MobLootPool.buildGlobalBuckets`).
2. **Quest difficulty becomes relative**, not absolute. "Easy/Medium/Hard" are bonuses on top of area baseline, not hardcoded ilvl numbers.
3. **Per-player rewards at turn-in**: each crediting party member rolls their own reward at their own catered ilvl based on their level. XP catering already exists.
4. **Encounter mob ilvl scales with area**, not difficulty. Mobs in a starter zone are starter-tier regardless of how the quest is labelled.
5. **Cap reward ilvl at area level**: a level-30 player can't farm starter zones for top-tier gear.

## Locked Decisions

### Reward formula (per-player, computed at phase turn-in)

```
playerInput = clamp(playerLevel, areaLevel - 5, areaLevel)
rewardIlvl  = clamp(playerInput + difficultyBonus, 1, 45)
```

Worked examples (Hard quest = +5 bonus):

| Scenario | Player | Area | playerInput | Reward ilvl |
|---|---|---|---|---|
| Low-level home zone | 3 | 5 | clamp(3, 0, 5) = 3 | 8 |
| Low-level venturing into hard zone | 5 | 15 | clamp(5, 10, 15) = 10 | 15 (slightly under area) |
| Player matched to area | 15 | 15 | clamp(15, 10, 15) = 15 | 20 |
| High-level player at matched area | 30 | 30 | clamp(30, 25, 30) = 30 | 35 |
| **High-level player farming starter** | 30 | 5 | clamp(30, 0, 5) = 5 | 10 (capped — no farming) |

Symmetric clamp around area: player level can pull reward DOWN by up to 5 (rewards low-leveled players for ambition with slightly-worse-than-area gear) but never UP above area (farming protection).

### Encounter formula (shared per quest, computed at quest spawn)

```
mobIlvl  = clamp(areaLevel + difficultyBonus, 1, 45)
bossIlvl = clamp(mobIlvl + bossIlvlOffset, 1, 45)   // bossIlvlOffset stays at +3
```

No player level on the encounter side. World stays consistent — Hard quest in starter zone spawns ilvl-10 mobs (5 + 5), not ilvl-25.

### Difficulty bonus + revised tier ranges (Conservative)

| Difficulty | `ilvlBonus` | `tierMin` | `tierMax` | `xpAmount` | `mobCountMultiplier` | `gatherCountMultiplier` |
|---|---|---|---|---|---|---|
| Easy | **0** | **Uncommon** | Rare | 50 | 0.8 | 0.8 |
| Medium | **2** | Uncommon | Rare | 100 | 1.0 | 1.0 |
| Hard | **5** | Rare | Epic | 200 | 1.3 | 1.3 |

Notes:
- Easy bumped from `Common-Uncommon` to `Uncommon-Rare` to align with mob loot (which never drops Common).
- Easy and Medium share the same rarity range — differentiation is via `ilvlBonus` (0 vs 2) and intensity multipliers.
- Hard rarity unchanged (`Rare-Epic`).

### Difficulty config schema changes

`src/main/resources/quests/difficulty/{easy,medium,hard}.json`:

- **Remove**: `rewardIlvl`, `mobIlvl` (no longer hardcoded; computed at runtime).
- **Add**: `ilvlBonus` (int, 0-10 reasonable range).
- **Keep**: `id`, `xpAmount`, `rewardTierMin`, `rewardTierMax`, `mobBoss`, `bossIlvlOffset`, `mobCountMultiplier`, `gatherCountMultiplier`.

### `QuestInstance.PhaseReward` schema changes

Currently stores `rewardItemId / rewardItemCount / rewardItemDisplayName / rewardItemDataJson` plus `rewardTier / rewardIlvl`. Most of those become moot when rolling shifts to dispense time.

New shape:

```java
class PhaseReward {
    String rewardTier;          // rolled at quest gen (includes dampener); reused per accepter
    int    areaLevelAtSpawn;    // snapshotted at quest gen
    int    ilvlBonus;           // copy of difficulty.ilvlBonus
}
```

Removed: `rewardItemId`, `rewardItemCount`, `rewardItemDisplayName`, `rewardItemDataJson`, `rewardIlvl`. (The Gson codec field-list shrinks accordingly.)

The dialogue `{reward_item}` binding now resolves at turn-in time after the per-player roll. Dialogue-builder code at `DialogueActionRegistry.TURN_IN_V2` does the roll + builds the message in the same step.

### `AffixRewardRoller.roll()` plugs into the gear filter

Replace the flat `entryRegistry.getAllItemIds()` pick with `Nat20MobLootPool.buildGlobalBuckets()` + `CategoryWeightedPicker.pick()`. Same code path mob/chest loot uses. Honors blocklist, allowlist, tier_tokens, tier_item_overrides automatically.

This is **independent** of the formula change and works under both old and new ilvl semantics. Phase 1 of the implementation.

### `KILL_MOBS` populationSpec ilvl source

Three call sites currently read `difficulty.mobIlvl()`:

1. `QuestGenerator.java:621` — primary quest mob spawn
2. `DialogueActionRegistry.java:1113, 1167` — dialogue-driven mob spawns (likely tutorial)
3. `TutorialPhase3Setup.java:195` — tutorial-specific spawn

All three must change to read `clamp(areaLevel + difficultyBonus, 1, 45)`. Tutorial paths may need their own `areaLevel` (probably the spawn-zone area level; check during implementation).

## Architecture Changes Summary

| Aspect | Before | After |
|---|---|---|
| **Reward ilvl source** | `difficulty.rewardIlvl()` (hardcoded) | per-player `clamp(playerLevel, areaLevel-5, areaLevel) + ilvlBonus` |
| **Encounter ilvl source** | `difficulty.mobIlvl()` (hardcoded) | `areaLevel + ilvlBonus` |
| **When reward is rolled** | quest generation | phase turn-in (per-player) |
| **Where pool comes from** | `getAllItemIds()` (all items) | `buildGlobalBuckets(ilvl)` (gear-filter respected) |
| **Stored on PhaseReward** | id, count, name, data, tier, ilvl | tier, areaLevelAtSpawn, ilvlBonus |
| **Dialogue {reward_item}** | binds to pre-rolled name | binds to turn-in-time roll |

## File Structure

| Action | File | Purpose |
|---|---|---|
| Modify | `quest/AffixRewardRoller.java` | Route through `Nat20MobLootPool.buildGlobalBuckets` + `CategoryWeightedPicker` (Phase 1) |
| Modify | `quest/model/DifficultyConfig.java` | Replace `rewardIlvl`/`mobIlvl` fields with `ilvlBonus` |
| Modify | `quest/QuestDifficultyRegistry.java` | Update parser/validator for new field |
| Modify | `resources/quests/difficulty/{easy,medium,hard}.json` | New schema; new tier ranges (Easy → Uncommon-Rare) |
| Modify | `quest/QuestInstance.java` | `PhaseReward` shape change |
| Modify | `quest/QuestGenerator.java` | Stop pre-rolling rewards; capture `areaLevelAtSpawn` + `ilvlBonus` on PhaseReward; replace `difficulty.mobIlvl()` with `areaLevel + ilvlBonus` in KILL_MOBS spec |
| Modify | `quest/Nat20QuestRewardDispatcher.java` | Per-player ilvl + roll at dispense (multi-accepter path) |
| Modify | `action/DialogueActionRegistry.java` | TURN_IN_V2: per-player ilvl + roll for triggering player; update tutorial KILL_MOBS spec |
| Modify | `quest/TutorialPhase3Setup.java` | Replace `difficulty.mobIlvl()` with area-derived ilvl |
| Modify | `quest/TutorialQuestFactory.java` | Audit reward path; align with new model |
| Create | `progression/QuestRewardIlvl.java` (or similar) | Pure helper: `clamp(player, areaLevel-5, areaLevel) + bonus`, plus encounter equivalent. Unit-testable. |

## Algorithm

### Reward dispense (per-player, at phase turn-in)

```
for each accepter who completed the phase:
    playerLevel = peer.getLevel()
    playerInput = clamp(playerLevel, areaLevelAtSpawn - 5, areaLevelAtSpawn)
    ilvl        = clamp(playerInput + ilvlBonus, 1, 45)
    tier        = phaseReward.rewardTier   // pre-rolled at gen with dampener
    itemStack   = AffixRewardRoller.roll(tier, ilvl, random)
    peer.giveItem(itemStack, ...)
    awardXp(peer, xpAmount)
    bindDialogueRewardItem(itemStack.getDisplayName())   // for {reward_item}
```

### Encounter spawn (shared, at quest generation)

```
areaLevel = MobScalingConfig.areaLevelForDistance(npcDistanceFromSpawn)
mobIlvl   = clamp(areaLevel + difficulty.ilvlBonus, 1, 45)
populationSpec = "KILL_MOBS:" + enemyId + ":" + count + ":" + mobIlvl
                + ":" + difficulty.mobBoss + ":" + difficulty.bossIlvlOffset
```

(Caller of `populationSpec` already adds `bossIlvlOffset` to `mobIlvl` when spawning the boss, so we don't recompute it here.)

## Edge Cases

1. **`areaLevel` not yet computed**: NPCs without a chunk-loaded area level default to areaLevel=1. Quest reward at ilvl 1+bonus, encounter at ilvl 1+bonus.
2. **Player joins party AFTER quest accept but before turn-in**: gets the per-player roll at turn-in based on their level, not the original accepter's. (Existing dispatcher already handles this by iterating `quest.getAccepters()`.)
3. **Multi-phase quest, player levels up between phases**: each phase's turn-in computes fresh ilvl. Phase 1 reward at level 5, phase 2 reward at level 7, etc.
4. **Player above area+5 (clamped down)**: high-level players in low-area zones get area-tier rewards. Earlier phases use the same areaLevelAtSpawn snapshot — no re-snapshot if the NPC moves or the area's progression updates.
5. **`tier` rolled at gen with dampener still applies**: dampener (5% bypass) is part of tier-roll logic at gen time. Per-player tier doesn't re-roll dampener; the same `rewardTier` is used for every accepter on a given phase. (Different design choice possible — re-roll tier per player too. Not in this scope.)
6. **Tutorial quests**: their own factory might bypass `difficulty` entirely. Audit + apply the new formula consistently. If the tutorial quest hardcodes ilvl, swap to area+bonus.

## Smoke Test Checklist

Post-implementation, devserver:

- [ ] Boot devserver. Difficulty configs load. No SEVERE about missing `rewardIlvl`/`mobIlvl` (validator updated).
- [ ] `/nat20 setlevel 30` then accept an Easy quest in a starter zone (area-5). Inspect quest mob spawns: should be ilvl 5 (encounter unaffected by player). Turn in phase: reward should be ilvl 5 (capped, not 30). **Farming-protection test.**
- [ ] `/nat20 setlevel 5` then accept a Hard quest in a mid zone (area-15). Quest mobs: ilvl 20. Turn in: reward = clamp(5, 10, 15) + 5 = 15. **Under-leveled-player test.**
- [ ] Two players accept the same quest at different levels (e.g., L10 and L20). Both turn in. Each gets their own ilvl-appropriate reward.
- [ ] Multi-phase quest: complete phase 1 at level 10, level up to 13, complete phase 2. Phase 2 reward at ilvl-of-13.
- [ ] Inspect dialogue `{reward_item}` resolution: matches the actual rolled item, not a stale pre-rolled name.
- [ ] Open a quest reward stack: items honor the gear filter (no spellbooks, no Wand_Root, no _NPC weapons).
- [ ] Tutorial quest still works end-to-end with new ilvl path.

## Open / Deferred

1. **Per-player tier reroll**: today the `rewardTier` is rolled once per phase (with dampener). This design keeps that — every accepter gets the same tier just at their own ilvl. If you want each player to roll their own tier too (so one gets Common, another gets Rare), it's a small extension. Out of scope here.
2. **`xpAmount` per-difficulty stays flat at 50/100/200**. XP per-player catering already exists via `Nat20XpService`. Not in scope.
3. **Difficulty assignment is still random**. A starter zone NPC can still pose a Hard quest. With the new formula that's fine — Hard means `+5 ilvl` over starter, not "absolute ilvl 25". If you later want difficulty to be biased by NPC zone (e.g., starter zones never offer Hard), that's a separate scope.
4. **Stored areaLevel snapshot doesn't refresh**. If world progression changes the NPC's area level after quest generation, the reward stays at the original snapshot. Acceptable trade-off; areaLevel changes are rare.
5. **Tier reroll on multi-accepter dispatcher**: today it reuses stored tier. With the new model, tier is still pre-rolled once at gen, and all accepters share it. Documented above.

## Implementation Phases

1. **Phase 1 (small, standalone)**: `AffixRewardRoller` routes through `Nat20MobLootPool.buildGlobalBuckets` + `CategoryWeightedPicker`. Plugs blocklist hole. Works under both old and new ilvl semantics.
2. **Phase 2 (the redesign)**: difficulty config schema change + per-player turn-in roll + KILL_MOBS string changes + `QuestRewardIlvl` helper + tests.

Phase 1 ships first (small, low-risk); Phase 2 builds on it.
