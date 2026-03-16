# Settlement NPC AI & Combat Design

## Goal

Make settlement NPCs behave realistically when encountering hostile mobs and aggressive players: Guards fight, civilians flee, everyone can take damage and die.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Who fights vs flees | Guards fight, all civilians flee |
| Flee behavior | Run away from threat (no guard-seeking) |
| Detection | Line-of-sight + proximity |
| Vulnerability | All NPCs vulnerable (remove Invulnerable) |
| Respawn timer | 5 minutes (up from 30 seconds) |
| Threat sources | Aggressive mobs always; neutral mobs + players on first hit |
| Guard combat style | Simple melee chase, no leash limit |
| Guard equipment | Mithril sword, full cobalt armor |
| Player hostility | Marked hostile until NPC calms down (5s no threat) |
| Disposition | -5 per hit, permanent, consequences are dialogue-only |
| Scope | Combat/flee/detection only; no wandering or object interaction |

## Architecture

Three layers:

1. **Data layer (JSON):** attitude groups, role behavior trees, equipment
2. **Plugin layer (Java):** threat detection from damage events, hostility marking, disposition tracking, dialogue cancellation
3. **Behavior tree layer (role JSON states):** StateEvaluator conditions drive state transitions for Combat/Fleeing/ReturnToPost

Key constraint: behavior trees are immutable after load. Dynamic targeting (marking a player as hostile at runtime) is handled by the plugin setting marked entity targets via `npcEntity.setMarkedTarget()`, which the behavior tree's `HasTarget` sensor reads.

## Attitude & Detection

### New file: `Server/NPC/Attitude/Roles/Nat20_Settlement.json`

```json
{
  "Groups": {
    "Hostile": ["Trork", "Undead", "Saurian"],
    "Friendly": ["Player", "Kweebec", "Human"]
  },
  "DefaultNPCAttitude": "Ignore",
  "DefaultPlayerAttitude": "Neutral"
}
```

Players start as Neutral. Aggressive-group mobs are always hostile. Neutral mobs and players become threats only when they deal damage to a settlement NPC (handled by plugin code).

### New file: `Server/NPC/Groups/Nat20_Settlement.json`

```json
{
  "IncludeRoles": [
    "Nat20/Guard",
    "Nat20/Villager",
    "Nat20/ArtisanBlacksmith",
    "Nat20/ArtisanAlchemist",
    "Nat20/ArtisanCook",
    "Nat20/Traveler",
    "Nat20/TavernKeeper"
  ]
}
```

### Detection via StateEvaluator

Each role gets a `StateEvaluator` block. Conditions score states 0.0-1.0 each tick:

- `KnownTargetCount` (from AttitudeView): hostile entities nearby
- `LineOfSight`: can the NPC see the threat
- `HasTarget`: has the plugin marked a specific entity as hostile
- `TargetDistance`: scoring by proximity

Example condition for triggering Fleeing:

```json
{
  "Conditions": [
    { "Type": "KnownTargetCount", "Attitude": "Hostile", "Min": 1 },
    { "Type": "LineOfSight" }
  ],
  "Score": 0.9
}
```

Fleeing/Combat scores (0.9) override Idle (0.1) and Watching (0.3).

## Guard Role: Combat Behavior

### State Machine

```
Idle (standing at post)
  ↓ hostile detected (LoS + proximity) OR marked target set by plugin
Combat (engage, melee attack)
  ↓ target moves out of melee range
Pursuing (chase target)
  ↓ target dies or leaves detection range (~16 blocks)
ReturnToPost (walk back to leash point)
  ↓ arrives at post
Idle
```

### Role JSON Changes

- Remove `Invulnerable: true`
- Health: 30 (unchanged, tougher than civilians)
- Detection range: ~16 blocks
- Add `StateEvaluator` with conditions for Combat, Pursuing, ReturnToPost
- Add `CombatActionEvaluator` config for melee attacks
- Equipment: mithril sword + full cobalt armor
- No leash limit on pursuit: chase until threat is eliminated or escapes detection range

## Civilian Roles: Flee Behavior

Applies to: Villager, ArtisanBlacksmith, ArtisanAlchemist, ArtisanCook, Traveler, TavernKeeper.

### State Machine

```
Idle / Watching / $Interaction (existing behavior)
  ↓ hostile detected (LoS + proximity) OR marked target set by plugin
Fleeing (run away from threat)
  ↓ no threats detected for ~5 seconds
Idle (return to normal, clear hostile player marks)
```

### Role JSON Changes

- Remove `Invulnerable: true`
- Health: 20 (unchanged)
- Detection range: ~10 blocks
- Add `StateEvaluator` condition for Fleeing (score 0.9, overrides all states including $Interaction)
- Fleeing state uses motion away from threat entity
- Return to normal: clears any hostile-marked players (back to neutral)

### Dialogue Interruption

When a civilian enters Fleeing, any active dialogue session is cancelled. `DialogueManager.cancelSession(npcRef)` closes the UI for the player in conversation.

## Plugin Systems (Java)

### `SettlementThreatSystem.java` (new)

Listens for damage events on settlement NPCs:

- **Trigger:** non-hostile entity (player or neutral mob) damages a settlement NPC
- **Actions:**
  - Set attacker as marked target on the damaged NPC
  - Set attacker as marked target on all Guards in the same settlement (via `SettlementRegistry` cell key lookup)
  - For players: decrease disposition by -5 on ALL NPCs in the settlement
- **Does not fire** for aggressive-group mobs (already detected via AttitudeView)

### `SettlementThreatClearSystem.java` (new)

Scheduled tick (~1s interval):

- Checks NPCs that have a player marked as hostile
- If no hostile target within detection range for 5+ seconds, clears the marked target
- NPC's StateEvaluator naturally transitions back to Idle once `HasTarget` condition fails
- Civilians return to normal behavior near their leash point

### `SettlementNpcDeathSystem.java` (modified)

- Change respawn delay from 30 seconds to 5 minutes

### `DialogueManager.java` (modified)

- Add `cancelSession(npcRef)` method
- Called by `SettlementThreatSystem` when a civilian enters Fleeing
- Closes the dialogue UI for the player who was in conversation

### `Natural20.java` (modified)

- Register `SettlementThreatSystem` and `SettlementThreatClearSystem`

## Disposition System

- **-5 disposition per attack** on any settlement NPC (cumulative)
- **Applied to all NPCs** in the same settlement (not just the one hit)
- **Floor:** -100
- **Persistence:** permanent (survives NPC death/respawn)
- **Consequences:** dialogue-only (handled by existing dialogue system, future work)
- **Hostility marking:** temporary (clears when NPC returns to normal after 5s no threat)

## File Change Summary

### New JSON Files
- `Server/NPC/Attitude/Roles/Nat20_Settlement.json`
- `Server/NPC/Groups/Nat20_Settlement.json`

### Modified JSON Files (all 7 roles)
- Remove `Invulnerable: true`
- Add `StateEvaluator` blocks with detection conditions
- Guard: Combat/Pursuing/ReturnToPost states, `CombatActionEvaluator`, mithril sword + cobalt armor
- 6 civilian roles: Fleeing state with flee-from-threat motion

### New Java Files
- `SettlementThreatSystem.java`
- `SettlementThreatClearSystem.java`

### Modified Java Files
- `SettlementNpcDeathSystem.java` (respawn 30s → 5min)
- `DialogueManager.java` (add `cancelSession(npcRef)`)
- `Natural20.java` (register new systems)

### No Changes To
- Settlement spawning, persistence, name generation, prefab placement
