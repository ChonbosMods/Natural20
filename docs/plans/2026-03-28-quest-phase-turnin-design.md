# Quest Phase Turn-In & Waypoint Lifecycle Design

## Goal

Replace auto-advancing quest phases with an explicit NPC turn-in step. When objectives complete, a return marker guides the player back to the quest giver. NPC dialogue handles phase advancement, rewards, and next-phase setup.

## Current Flow (broken)

```
Objectives complete → QuestTracker auto-advances phase → XP awarded
→ next phase objectives start tracking immediately
→ player never needs to return to NPC
```

## New Flow

```
1. Quest accepted → POI marker (!) + ring on map
2. Objectives complete → POI marker + ring removed
   → Green return marker (?) placed on settlement
   → Quest phase stays current (not advanced)
3. Player returns to NPC → TURN_IN dialogue
   → NPC acknowledges completion, gives rewards
   → Phase advances
4. If next phase has POI → new POI marker + ring (go to 1)
   If quest is done → return marker removed, quest completed
```

## Architecture Changes

### 1. Stop Auto-Advance in QuestTracker

Currently `QuestTracker.reportProgress()` calls `onPhaseComplete()` which advances the phase and awards rewards. Change: when all objectives in a phase are complete, set `phase_objectives_complete=true` in bindings but do NOT advance the phase. Call `refreshMarkers()` to swap POI → return marker.

The phase advancement and reward distribution move to the new TURN_IN action.

### 2. New TURN_IN Dialogue Action

Register `TURN_IN_PHASE` in `DialogueActionRegistry`:
- Verify current phase objectives are all complete
- Award phase XP via `QuestRewardManager.awardPhaseXP()`
- Award loot if applicable (RESOLUTION or mid-chain)
- Advance phase via `quest.advancePhase()`
- Clear `phase_objectives_complete` flag
- If quest is now complete: `markQuestCompleted()`
- Call `QuestMarkerProvider.refreshMarkers()` to update waypoints
- Return next-phase context to dialogue system for continuation

### 3. Waypoint Lifecycle in QuestMarkerProvider

`refreshMarkers()` determines marker type per quest:

```
For each active quest:
  If poi_available AND NOT phase_objectives_complete:
    → Emit POI MarkerEntry (QuestCenter.png + ring)
  Else if phase_objectives_complete:
    → Emit Return MarkerEntry (QuestReturn.png at settlement, no ring)
  Else:
    → No marker
```

`MarkerEntry` record expanded:
```java
record MarkerEntry(
    String questId,
    String questName,
    double x, double z,
    MarkerType type  // POI or RETURN
)
```

Provider emits ring only for POI type. Return markers use `QuestReturn.png` (green circle with ?), placed at settlement coordinates (from `sourceSettlementId` → `SettlementRegistry` lookup).

### 4. QuestReturn.png Icon

Green circle with `?`, same 64x64 format as QuestCenter.png. Warm green palette to contrast with the orange POI markers.

### 5. TURN_IN Dialogue Templates

New dialogue pool category for NPC reactions when player returns with completed objectives. Needs templates per phase type:

- **EXPOSITION turn-in**: "Good, you're back. Here's what I need next..."
- **CONFLICT turn-in**: "Well done. The threat is lessening, but..."
- **RESOLUTION turn-in**: "You've done it! Here's your reward..."

Templates use existing variable bindings: `{quest_objective_summary}`, `{npc_name}`, `{quest_focus}`, etc.

### 6. Dialogue Graph Integration

The NPC's dialogue graph needs to detect the turn-in state:
- When player talks to quest-giving NPC AND quest has `phase_objectives_complete=true`
- Priority topic: offer TURN_IN dialogue branch
- After TURN_IN action: if quest continues, transition to next-phase briefing
- If quest complete: transition to completion/reward dialogue

## Files to Create/Modify

| File | Action |
|------|--------|
| `QuestTracker.java` | Stop auto-advance, set flag, call refreshMarkers |
| `DialogueActionRegistry.java` | Add TURN_IN_PHASE action |
| `QuestMarkerProvider.java` | Handle POI vs Return marker types |
| `QuestReturn.png` | Create green circle with ? icon |
| `POIKillTrackingSystem.java` | Call refreshMarkers after objective completion |
| Turn-in dialogue templates | Create pool files for turn-in responses |
| `TopicGenerator.java` | Generate turn-in dialogue nodes |

## Open Questions

- Should the return marker also hide within proximity (like POI markers), or always be visible since the player needs to find the NPC directly?
- Should the NPC have a visible indicator (nameplate color? particle?) when they have a turn-in available?
- How does the TALK_TO_NPC objective type interact with this system? (It's already a "go talk to someone" objective)
