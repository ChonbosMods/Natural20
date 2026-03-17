# Quest Narrative Cohesion: Design

## Problem

Quest phases (Exposition, Conflict, Resolution) are independently authored and randomly combined. When phases from different variants play sequentially, hardcoded details clash: the exposition mentions a watchtower, the conflict mentions a mine shaft, the resolution mentions a bridge. The overall theme (e.g., enemy attack) stays consistent, but specific narrative details break immersion.

## Solution

Add shared narrative variables defined by the exposition variant and inherited by all subsequent phases. Specific story details move from hardcoded dialogue text into a `bindings` object, and conflict/resolution variants reference them via `{variable}` tokens.

## Variable Set (14 total)

### Always Resolved
| Variable | Example | Source |
|---|---|---|
| `{player_name}` | "Wanderer" | Player entity |
| `{quest_location_name}` | "the old watchtower" | POI registry |
| `{quest_action}` | "hold the line" | Exposition variant bindings |
| `{quest_focus}` | "the watchtower perimeter" | Exposition variant bindings |
| `{quest_stakes}` | "the outer farms" | Exposition variant bindings (25% chance = quest_focus) |
| `{quest_threat}` | "the advancing warband" | Exposition variant bindings (fallback: auto-generated from situation + enemy_type) |

### Per-Objective
| Variable | Example | Source |
|---|---|---|
| `{quest_item}` | "WoodLog" | Item pool |
| `{enemy_type}` | "Trork Brute" | Mob pool |
| `{location_hint}` | "north-west, about 250 blocks" | DirectionUtil |

### Optional
| Variable | Example | Source |
|---|---|---|
| `{location}` | "Ironhaven" | Settlement registry |
| `{target_npc}` | "Elara Thornwick" | NPC from nearby settlement (required if TALK_TO_NPC/DELIVER_ITEM objective) |
| `{quest_ally}` | "my apprentice Rodrik" | NPC in same settlement as quest giver |
| `{quest_reward_hint}` | "my grandfather's sword" | Exposition variant bindings |
| `{quest_time_pressure}` | "before the next full moon" | Exposition variant bindings |
| `{quest_origin}` | "the old war" | Exposition variant bindings |

## Schema Changes

### Exposition Variant: gains `bindings` object

```json
{
  "id": "supplication_expo_01",
  "bindings": {
    "quest_action": "hold the line",
    "quest_focus": "the watchtower perimeter",
    "quest_stakes": "the outer farms and their families",
    "quest_threat": "the advancing warband",
    "quest_reward_hint": "my grandfather's blade",
    "quest_time_pressure": "before the next moon rises",
    "quest_origin": "the border skirmishes last autumn"
  },
  "dialogueChunks": {
    "intro": "{player_name}, I need your help. Ever since {quest_origin}, the {enemy_type} have been growing bolder.",
    "plotStep": "I'm going to {quest_action} at {quest_focus}. If we fail, {quest_stakes} will be lost. Head {location_hint} and gather {quest_item} for the barricades.",
    "outro": "We have {quest_time_pressure}. When this is done, {quest_reward_hint} is yours."
  },
  "playerResponses": [...],
  "objectivePool": [...],
  "objectiveConfig": {...}
}
```

### Conflict/Resolution Variants: inherit bindings, no `bindings` object

```json
{
  "id": "supplication_conf_01",
  "dialogueChunks": {
    "intro": "Things have changed since you helped me {quest_action}.",
    "plotStep": "{quest_ally} reported that the {enemy_type} regrouped near {quest_focus}. {quest_stakes} are still at risk.",
    "outro": "We can't let {quest_origin} repeat itself."
  },
  "playerResponses": [...],
  "objectivePool": [...],
  "objectiveConfig": {...}
}
```

### References and npc_weights: no changes

## Generator Changes

### Step 4: Variable Resolution (updated)

**4a: World bindings** (unchanged): Resolve `{player_name}`, `{enemy_type}`, `{quest_item}`, `{location}`, `{location_hint}`, `{target_npc}` from settlement registry, item pools, mob pools.

**4b: Narrative bindings from exposition variant**: Read the selected exposition variant's `bindings` object. Copy each key-value pair into the quest's variable bindings map. Special cases:

- `{quest_stakes}`: 25% chance to copy `{quest_focus}` value instead of the authored value
- `{quest_ally}`: If not in bindings, optionally resolve from a different NPC in the same settlement as the quest giver. Skip if no other NPCs available.
- `{quest_threat}`: If not in bindings, auto-generate from situation name + enemy_type (e.g., "the Trork Brute raids")

### Step 6b: Quest Ally Topic (new)

After storing QuestInstance, if `{quest_ally}` resolved to an actual settlement NPC, roll 35% chance to unlock a dialogue topic on that NPC. The topic contains context about the quest they were involved in. This is independent of the reference system: no escalation tiers, just a direct topic unlock.

### Variable Substitution

New utility method `resolveDialogue(String template, Map<String, String> bindings)`:
- Replace all `{var}` tokens with bound values
- Unresolved `{var}` tokens (optional variables not set) are stripped: replaced with empty string, surrounding whitespace cleaned up

## Template Rework Scope

108 of 180 files need updating:

- **36 exposition variant files**: Each variant gains a `bindings` object. Hardcoded story details extracted into bindings, dialogue rewritten to use `{variable}` tokens.
- **36 conflict variant files**: Hardcoded references replaced with `{quest_action}`, `{quest_focus}`, `{quest_stakes}`, `{quest_threat}`, `{quest_ally}`.
- **36 resolution variant files**: Same treatment as conflict, referencing outcomes of `{quest_action}` at `{quest_focus}`.
- **72 reference + npc_weights files**: No changes.

## Quest Ally Behavior

- Resolved from NPCs in the same settlement as the quest giver (not the quest giver themselves)
- 35% chance on quest completion to gain a dialogue topic
- Topic is a direct unlock, not part of the reference escalation system
- The ally is already known to the player through quest dialogue, so no directional guidance needed
- Higher narrative value than random references because the player has existing context
