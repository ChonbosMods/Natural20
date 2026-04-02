# Natural 20 Dialogue System: Authoring Guide

Complete reference for writing NPC dialogue files. Covers every JSON field, node type, response mode, topic behavior, condition, action, and edge case in the current system.

---

## File Structure

Dialogue files are JSON, placed in `src/main/resources/dialogues/`. The loader auto-discovers all `.json` files in this directory at startup. Each file defines one NPC's complete conversation graph.

### Top-Level Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `npcId` | string | yes | - | Must match the NPC's Hytale role name (e.g. `"ArtisanBlacksmith"`) |
| `defaultDisposition` | int | no | 50 | Starting disposition for first-time interactions (0-100) |
| `greetingNodeId` | string | yes | - | Node displayed on first contact with this NPC |
| `returnGreetingNodeId` | string | no | null | Node displayed on subsequent visits. If null, greeting is replayed |
| `topics` | array | no | [] | Conversation topics the player can select |
| `nodes` | object | yes | - | Map of node ID to node definition |

```json
{
  "npcId": "ArtisanBlacksmith",
  "defaultDisposition": 50,
  "greetingNodeId": "greeting",
  "returnGreetingNodeId": "return_greeting",
  "topics": [],
  "nodes": {}
}
```

---

## Topics

Topics are the left-panel buttons that organize NPC conversation into selectable branches. The player clicks a topic to enter it.

### Topic Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `id` | string | yes | - | Unique topic identifier |
| `label` | string | yes | - | Text shown on the topic button |
| `entryNodeId` | string | yes | - | Node to process when topic is selected |
| `scope` | string | no | `"LOCAL"` | `"LOCAL"` or `"GLOBAL"` |
| `startLearned` | bool | no | true | If false, topic is hidden until learned |
| `condition` | object | no | null | Condition that must pass for topic to appear |
| `statPrefix` | string | no | null | Stat abbreviation shown on button (e.g. `"CHA"`) |
| `sortOrder` | int | no | 0 | Display order in topic list (ascending) |
| `recapText` | string | no | null | NPC speech shown when clicking an exhausted (grayed) topic |

### Topic Scope

- **LOCAL**: Visible only for this specific NPC. Exhaustion state is per-NPC.
- **GLOBAL**: Shared across all NPCs. Once learned via `UNLOCK_TOPIC`, the topic appears on every NPC that defines it. Requires `startLearned: false` to be meaningful: the topic starts hidden and only appears after being unlocked.

### Topic Lifecycle

1. **Hidden**: Topic does not appear in the sidebar. Either `startLearned` is false and the topic hasn't been learned, or its condition fails, or it has been exhausted to HIDDEN state.
2. **Active**: Topic appears in the sidebar and can be clicked. Processes the entry node normally.
3. **Grayed**: Topic has been auto-exhausted (all decisive responses consumed, no fresh exploratories remain). Appears in the sidebar but grayed out. Clicking it replays the entry node's NPC speech (or `recapText` if provided).

### Topic Exhaustion

Topics can be exhausted in two ways:

**Automatic (GRAYED)**: When a branch completes and the entry node has no remaining fresh responses (all decisives consumed, all exploratories already visited), the topic auto-exhausts to GRAYED state. It stays visible but grayed out. This is the default for most topics.

**Explicit (HIDDEN)**: When a node has `"exhaustsTopic": true`, the topic is immediately hidden. Use this for topics that should completely disappear after completion (e.g. one-time quests).

### Topic Example

```json
"topics": [
  {
    "id": "repair",
    "label": "Repair",
    "entryNodeId": "repair_greeting",
    "scope": "LOCAL",
    "startLearned": true,
    "sortOrder": 1
  },
  {
    "id": "bandit_camp",
    "label": "Bandit Camp",
    "entryNodeId": "bandit_camp_response",
    "scope": "GLOBAL",
    "startLearned": false,
    "condition": { "type": "TOPIC_LEARNED", "topicId": "bandit_camp" },
    "sortOrder": 11
  }
]
```

In this example, "Repair" is always visible. "Bandit Camp" is hidden until the player learns it via an `UNLOCK_TOPIC` action (e.g. by talking to another NPC about rumors), and it only appears on NPCs that have the condition `TOPIC_LEARNED` check passing.

---

## Node Types

Every entry in the `nodes` object is a node definition. The `type` field determines which node type it is.

### DIALOGUE

The core node type. Displays NPC speech and presents player response options.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `type` | string | yes | - | Must be `"DIALOGUE"` |
| `speakerText` | string | no | `""` | NPC's spoken text, shown in the conversation log |
| `responses` | array | no | [] | Player response options |
| `onEnter` | array | no | [] | Actions executed when this node is entered |
| `exhaustsTopic` | bool | no | false | If true, immediately hides the active topic |
| `locksConversation` | bool | no | false | If true, locks topic sidebar and goodbye until player picks a response |

**Leaf nodes**: A DIALOGUE node with empty `responses` (or all responses filtered out by conditions/consumption) is a leaf. After displaying the speech, the system runs `returnCheck`: if the topic's entry node still has unconsumed responses, the player is returned there; otherwise, the topic auto-exhausts.

**locksConversation**: By default, the player can freely click other topics or goodbye while response options are showing. Set `locksConversation: true` when the NPC demands an answer: all topic buttons are disabled, the goodbye button is disabled, and pressing Esc reopens the dialogue instead of closing it.

```json
"repair_greeting": {
  "type": "DIALOGUE",
  "speakerText": "Repairs? Aye, I can fix up most blades. Bring me the weapon and 50 gold.",
  "responses": [
    {
      "id": "repair_too_expensive",
      "displayText": "That's too expensive.",
      "targetNodeId": "repair_haggle",
      "mode": "EXPLORATORY"
    },
    {
      "id": "repair_accept",
      "displayText": "Deal. Here's my sword.",
      "targetNodeId": "repair_accepted",
      "mode": "DECISIVE"
    }
  ]
}
```

### SKILL_CHECK

Triggers a D20 dice roll with animated UI. The result routes to a pass or fail node.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `type` | string | yes | - | Must be `"SKILL_CHECK"` |
| `skill` | string | yes | - | Skill name (see Skills table below) |
| `stat` | string | no | skill default | Stat override for the roll |
| `baseDC` | int | yes | - | Base difficulty class before disposition scaling |
| `dispositionScaling` | bool | no | false | If true, DC adjusted by disposition bracket modifier |
| `passNodeId` | string | yes | - | Node if check succeeds |
| `failNodeId` | string | yes | - | Node if check fails |
| `onEnter` | array | no | [] | Actions executed before the roll |

**Disposition scaling modifiers** (9-bracket system):

| Bracket | Disposition | DC Modifier |
|---------|-------------|-------------|
| Hostile | 0-10 | +5 |
| Scornful | 11-24 | +3 |
| Unfriendly | 25-39 | +2 |
| Wary | 40-49 | +1 |
| Neutral | 50-59 | 0 |
| Cordial | 60-69 | -1 |
| Friendly | 70-79 | -2 |
| Trusted | 80-89 | -3 |
| Loyal | 90-100 | -4 |

Effective DC is always clamped to [1, 30].

```json
"repair_persuasion_check": {
  "type": "SKILL_CHECK",
  "skill": "PERSUASION",
  "stat": "CHA",
  "baseDC": 12,
  "dispositionScaling": true,
  "passNodeId": "repair_persuade_pass",
  "failNodeId": "repair_persuade_fail"
}
```

### ACTION

Executes a list of actions, then optionally chains to another node. No player-facing UI: the player only sees the result of the next node.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `type` | string | yes | - | Must be `"ACTION"` |
| `actions` | array | no | [] | Actions to execute |
| `next` | string | no | null | Node ID to process after actions |
| `onEnter` | array | no | [] | Actions executed on entry (in addition to `actions`) |
| `exhaustsTopic` | bool | no | false | If true, immediately hides the active topic |

Action chains have a depth limit of 10 (ACTION -> next -> ACTION -> next ...) to prevent infinite loops.

```json
"repair_accepted": {
  "type": "ACTION",
  "actions": [
    { "type": "REMOVE_ITEM", "itemId": "gold", "quantity": 50 },
    { "type": "SET_FLAG", "flagId": "repair_in_progress", "value": true }
  ],
  "next": "repair_accepted_dialogue",
  "exhaustsTopic": true
}
```

### TERMINAL

Ends the current branch. No speech, no responses. Triggers `returnCheck` to determine if the player should be returned to the topic's entry node or if the topic should auto-exhaust.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `type` | string | yes | - | Must be `"TERMINAL"` |
| `onEnter` | array | no | [] | Final actions to execute |
| `exhaustsTopic` | bool | no | false | If true, immediately hides the active topic |

```json
"repair_refused": {
  "type": "TERMINAL"
}
```

---

## Responses

Responses are the player's choices within a DIALOGUE node. Each response is a button in the follow-up area.

### Response Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `id` | string | yes | - | Unique ID within this node |
| `displayText` | string | yes | - | Button text shown to the player |
| `targetNodeId` | string | no* | null | Node to process when selected |
| `mode` | string | no | `"DECISIVE"` | `"DECISIVE"` or `"EXPLORATORY"` |
| `condition` | object | no | null | Condition for this response to appear |
| `skillCheckRef` | string | no | null | Node ID of a SKILL_CHECK to run before the target |
| `statPrefix` | string | no | null | Stat abbreviation shown as colored prefix (e.g. `"CHA"`) |
| `linkedResponses` | array | no | null | IDs of other responses consumed together (DECISIVE only) |

*`targetNodeId` is required unless `skillCheckRef` is provided, which routes through the skill check first.

### Response Modes

**DECISIVE** (default): One-shot choices. Once selected, the response is permanently consumed for this topic on this NPC. It will never appear again, even if the player returns to the entry node. Use for commitments: accepting a deal, making a permanent choice, spending resources.

**EXPLORATORY**: Information-gathering choices. Never consumed. Can be selected unlimited times. After first selection, the response appears grayed out (disabled, gray text) to indicate it's already been explored. The response becomes available again on future visits. Use for "tell me more" and "what about X" options.

### Linked Responses

Linked responses are DECISIVE responses that form a mutually exclusive group. When the player picks one, all linked responses are consumed together.

Rules:
- Only valid on DECISIVE responses
- All linked IDs must be on the same node
- Links should be bidirectional (A links to B, B links to A)
- The loader validates these rules and warns on violations

```json
"responses": [
  {
    "id": "repair_persuade",
    "displayText": "Surely you can do better for a fellow adventurer?",
    "targetNodeId": "repair_persuade_pass",
    "mode": "DECISIVE",
    "statPrefix": "CHA",
    "skillCheckRef": "repair_persuasion_check",
    "linkedResponses": ["repair_accept"]
  },
  {
    "id": "repair_accept",
    "displayText": "Deal. Here's my sword.",
    "targetNodeId": "repair_accepted",
    "mode": "DECISIVE",
    "linkedResponses": ["repair_persuade"]
  }
]
```

In this example, if the player tries to persuade (and the check runs), the "Deal" option is also consumed. If the player just accepts the deal, the persuade option is also consumed. The player gets exactly one of the two.

### Skill-Checked Responses

A response with `skillCheckRef` routes through a SKILL_CHECK node before reaching the target. The flow is:

1. Player clicks the response
2. System processes the skill check node (dice roll UI)
3. Pass/fail determines the actual destination

The `targetNodeId` on the response is used as the pass destination. The fail destination comes from the skill check node's `failNodeId`. If you want a separate pass node, set the skill check node's `passNodeId` accordingly and omit `targetNodeId` on the response.

### Stat Prefix Display

When `statPrefix` is set (e.g. `"CHA"`), the response button shows a colored bracket prefix: `[CHA] Surely you can do better...` The color matches the stat's theme color. This signals to the player that the choice involves a stat check.

Available stats: `STR` (red), `DEX` (green), `CON` (orange), `INT` (blue), `WIS` (light purple), `CHA` (purple).

---

## Actions

Actions are side effects triggered by `onEnter` on any node type, or the `actions` list on ACTION nodes. Each action is an object with a required `type` field.

### Available Actions

| Action | Parameters | Description |
|--------|-----------|-------------|
| `SET_FLAG` | `flagId`, `value` (default `"true"`) | Sets a persistent global flag on the player |
| `MODIFY_DISPOSITION` | `amount` | Adds to current disposition (can be negative). Clamped to [0, 100] |
| `UNLOCK_TOPIC` | `topicId`, `scope` (default `"LOCAL"`) | For GLOBAL scope: marks topic as learned for this player across all NPCs |
| `EXHAUST_TOPIC` | `topicId` (optional, defaults to active topic) | Immediately hides the specified topic (HIDDEN state) |
| `REACTIVATE_TOPIC` | `topicId`, `newEntryNodeId` (optional) | Removes exhaustion, clears consumed decisives and grayed exploratories. Optionally overrides the entry node for future visits |
| `CHANGE_REPUTATION` | `factionId`, `amount` (default 0) | Modifies reputation with a faction |
| `GIVE_ITEM` | `itemId`, `quantity` (default 1) | Stub: logged but not implemented |
| `REMOVE_ITEM` | `itemId`, `quantity` (default 1) | Stub: logged but not implemented |
| `EXECUTE_COMMAND` | `command` | Stub: logged but not implemented |
| `GIVE_QUEST` | `questId` | Stub: logged but not implemented |
| `COMPLETE_QUEST` | `questId` | Stub: logged but not implemented |
| `OPEN_SHOP` | (none) | Stub: logged but not implemented |

### Action Examples

```json
"onEnter": [
  { "type": "MODIFY_DISPOSITION", "amount": 5 },
  { "type": "SET_FLAG", "flagId": "blacksmith_special_unlocked", "value": "true" }
]
```

```json
"onEnter": [
  { "type": "UNLOCK_TOPIC", "topicId": "bandit_camp", "scope": "GLOBAL" }
]
```

### REACTIVATE_TOPIC Details

Reactivation resets a topic so it can be played again. It:
- Removes the exhaustion state (GRAYED or HIDDEN)
- Clears all consumed decisive responses for that topic
- Clears grayed exploratory state for the entry node's responses
- Optionally sets a new entry node via `newEntryNodeId` (persisted in player data, used on next click)

Use case: a quest that unlocks new dialogue on a previously-exhausted topic.

---

## Conditions

Conditions control visibility of topics and responses. A condition is an object with a `type` field and parameters, or a composite using `all`/`any`.

### Condition Types

| Type | Parameters | True when... |
|------|-----------|-------------|
| `HAS_FLAG` | `flagId`, `value` (default `"true"`) | Player's global flag matches value |
| `STAT_CHECK` | `stat`, `minValue` (default 0) | Player's stat >= minValue |
| `DISPOSITION_MIN` | `minDisposition` (default 0) | Current disposition >= minDisposition |
| `TOPIC_EXHAUSTED` | `topicId` | Topic is in exhausted state for this NPC |
| `TOPIC_LEARNED` | `topicId` | Global topic has been learned |
| `HAS_ITEM` | - | Always true (stub) |

Unknown condition types evaluate to **false** (fail-closed).

### Composite Conditions

**AND**: All children must pass.
```json
{
  "all": [
    { "type": "HAS_FLAG", "flagId": "quest_started" },
    { "type": "DISPOSITION_MIN", "minDisposition": 60 }
  ]
}
```

**OR**: At least one child must pass.
```json
{
  "any": [
    { "type": "HAS_FLAG", "flagId": "bribed" },
    { "type": "STAT_CHECK", "stat": "CHA", "minValue": 14 }
  ]
}
```

Composites can nest arbitrarily.

### Condition on Topics vs Responses

- **Topic condition**: Controls whether the topic appears in the sidebar at all. Evaluated every time the topic list refreshes.
- **Response condition**: Controls whether the response button appears when the node is displayed. Evaluated when the node is entered or re-entered.

---

## Conversation Flow

### Opening

1. Player interacts with NPC (F key)
2. System loads dialogue graph by matching `npcId` to the NPC's role name
3. If a saved session exists (player disconnected mid-conversation), it resumes with a return divider and return greeting
4. Otherwise, the greeting node is processed
5. Topics are shown in the left sidebar. NPC speech appears in the right-panel log

### During Conversation

The UI has two panels:
- **Left sidebar**: Topic buttons (always visible unless locked), disposition display, goodbye button
- **Right panel**: Scrollable conversation log (top), response buttons (bottom)

**Topic selection**: Player clicks a topic. A topic header appears in the log, then the entry node's speech and responses are shown. Topics remain clickable while responses are showing (unless `locksConversation` is set).

**Response selection**: Player clicks a response. It's logged as a selected response (gray, with `>` prefix). The target node is processed. If it's a leaf (no responses), the system checks if the entry node has remaining options:
- If yes: a divider is added to the log and the remaining responses are re-shown
- If no: the topic auto-exhausts (grayed)

**Switching topics**: The player can click a different topic at any time (unless locked). The new topic's entry node is processed and the old follow-ups are replaced. The old topic's state is preserved: consumed decisives and grayed exploratories persist.

**Goodbye**: Ends the conversation. If there are pending follow-ups (player was mid-choice), the session is saved for resumption on next interaction.

### Disposition Display

The disposition label in the lower-left shows the numeric value and bracket name:
- `0-10`: **Hostile** (red)
- `11-24`: **Scornful** (red-orange)
- `25-39`: **Unfriendly** (orange)
- `40-49`: **Wary** (yellow-orange)
- `50-59`: **Neutral** (yellow)
- `60-69`: **Cordial** (yellow-green)
- `70-79`: **Friendly** (green)
- `80-89`: **Trusted** (blue-green)
- `90-100`: **Loyal** (blue)

For NPC speech tone selection, the 9 brackets are grouped into 5 text pools: hostile (0-19), unfriendly (20-39), neutral (40-59), friendly (60-79), loyal (80-100).

### Session Resumption

If a player disconnects while responses are pending, the session is serialized and saved. On the next interaction with the same NPC:
1. The previous conversation log is restored
2. A divider and return greeting are appended
3. The pending responses are re-shown
4. The player picks up where they left off

If the saved data is corrupted, it's discarded and the conversation starts fresh.

---

## Skills and Stats

### Skills

| Skill | Default Stat | DC Offset |
|-------|-------------|-----------|
| INTIMIDATION | STR | -1 |
| COMMAND | STR | -2 |
| SLEIGHT | DEX | -2 |
| REFLEX | DEX | -2 |
| ENDURANCE | CON | -2 |
| RESISTANCE | CON | -2 |
| DEDUCTION | INT | -1 |
| RECALL | INT | -1 |
| PERCEPTION | WIS | 0 |
| INSIGHT | WIS | 0 |
| PERSUASION | CHA | 0 |
| DECEPTION | CHA | -1 |
| CHARM | CHA | -1 |

DC Offset is applied to the effective DC before the roll. A skill with offset -2 is easier than one with offset 0 at the same base DC.

### Stats

| Stat | Color | Full Name |
|------|-------|-----------|
| STR | #FF4444 | Strength |
| DEX | #44DD66 | Dexterity |
| CON | #DD8833 | Constitution |
| INT | #4488FF | Intelligence |
| WIS | #CCCCDD | Wisdom |
| CHA | #BB66FF | Charisma |

---

## UI Limits

| Element | Max Count | Overflow Behavior |
|---------|-----------|-------------------|
| Topics | 10 | Warning logged, extras ignored |
| Follow-ups | 6 | Warning logged, extras ignored |
| Log entries | 30 visible | Oldest entries scroll off; "... earlier conversation ..." indicator shown |

---

## Log Colors

| Entry Type | Color | Notes |
|------------|-------|-------|
| Topic headers | #666666 | Shown as `-- Topic Name --` |
| NPC speech | #FFCC00 | Gold text |
| Selected responses | #888888 | Shown with `> ` prefix |
| System text | #66BB77 | Plugin-generated messages |
| Return greetings | #FFCC00 | Same as NPC speech |
| Return dividers | #555555 | Shown as `---` |

---

## Common Patterns

### Haggling / Negotiation

Use linked decisive responses to create mutually exclusive choices. The player commits to one path and the other is permanently consumed.

```json
"responses": [
  {
    "id": "persuade",
    "displayText": "Can you do better?",
    "mode": "DECISIVE",
    "statPrefix": "CHA",
    "skillCheckRef": "persuasion_check",
    "linkedResponses": ["accept"]
  },
  {
    "id": "accept",
    "displayText": "Fine, I'll pay full price.",
    "targetNodeId": "deal_done",
    "mode": "DECISIVE",
    "linkedResponses": ["persuade"]
  }
]
```

### Information Gathering

Use exploratory responses for non-committal questions. They gray out after use but never block topic exhaustion.

```json
"responses": [
  {
    "id": "ask_bandits",
    "displayText": "Tell me more about these bandits.",
    "targetNodeId": "bandit_detail",
    "mode": "EXPLORATORY"
  },
  {
    "id": "ask_location",
    "displayText": "Where exactly is this watchtower?",
    "targetNodeId": "location_detail",
    "mode": "EXPLORATORY"
  }
]
```

After the player explores both, the topic auto-exhausts (since there are no decisive responses).

### Cross-NPC Knowledge

Use GLOBAL topics to share information between NPCs. NPC A unlocks the topic, and NPC B has it as a conditional topic.

**NPC A (unlocks)**:
```json
"onEnter": [
  { "type": "UNLOCK_TOPIC", "topicId": "bandit_camp", "scope": "GLOBAL" }
]
```

**NPC B (receives)**:
```json
{
  "id": "bandit_camp",
  "label": "Bandit Camp",
  "entryNodeId": "bandit_response",
  "scope": "GLOBAL",
  "startLearned": false,
  "condition": { "type": "TOPIC_LEARNED", "topicId": "bandit_camp" },
  "sortOrder": 11
}
```

### Forced Choice (Locked Conversation)

When the NPC demands an answer and the player shouldn't be able to navigate away:

```json
"ultimatum": {
  "type": "DIALOGUE",
  "speakerText": "I need an answer. Now. Are you with us or against us?",
  "locksConversation": true,
  "responses": [
    {
      "id": "with_them",
      "displayText": "I'm with you.",
      "targetNodeId": "alliance_formed",
      "mode": "DECISIVE"
    },
    {
      "id": "against_them",
      "displayText": "I'll never join you.",
      "targetNodeId": "hostility",
      "mode": "DECISIVE"
    }
  ]
}
```

This disables all topic buttons, the goodbye button, and blocks Esc dismissal until the player picks a response.

### Topic with Stat-Gated Entry

A topic where clicking it immediately triggers a skill check:

```json
{
  "id": "flattery",
  "label": "Flattery",
  "entryNodeId": "flattery_check",
  "statPrefix": "CHA",
  "sortOrder": 3
}
```

The entry node is a SKILL_CHECK, not a DIALOGUE. The `statPrefix` on the topic shows `[CHA]` on the button. Because the entry node has no text to return to, the topic auto-exhausts after the check resolves (regardless of pass/fail).

### Reactivating an Exhausted Topic

After a quest is complete, reactivate a topic with new dialogue:

```json
{ "type": "REACTIVATE_TOPIC", "topicId": "repair", "newEntryNodeId": "repair_after_quest" }
```

This clears all prior consumption state and sets a new entry point. The next time the player clicks "Repair", they'll see `repair_after_quest` instead of the original `repair_greeting`.

### Disposition Gates

Show a response only when the NPC likes the player enough:

```json
{
  "id": "ask_secret",
  "displayText": "I've heard you know something about the ruins...",
  "targetNodeId": "secret_reveal",
  "mode": "DECISIVE",
  "condition": { "type": "DISPOSITION_MIN", "minDisposition": 70 }
}
```

This response only appears if disposition is 70+. The player never sees it at lower disposition.

---

## Procedural Topic System

The procedural topic system generates NPC conversation topics from pool-based templates. For authoring guidance, see `docs/authoring/`.

---

## Validation Rules

The loader validates the following on startup:

1. `greetingNodeId` must exist in `nodes`
2. `returnGreetingNodeId` (if present) must exist in `nodes`
3. Every topic's `entryNodeId` must exist in `nodes`
4. Linked responses must be bidirectional (A links B, B links A)
5. Linked responses must all be DECISIVE mode
6. Linked response IDs must exist on the same node

Validation failures are logged as warnings (linked responses) or errors (missing nodes, which prevent graph loading).

---

## Persistence Summary

The following player state persists across sessions:

| Data | Scope | Lifetime |
|------|-------|----------|
| Disposition | Per-NPC per-player | Permanent |
| Consumed decisives | Per-topic per-NPC per-player | Permanent (unless REACTIVATE_TOPIC) |
| Global flags | Per-player | Permanent |
| Learned global topics | Per-player | Permanent |
| Topic exhaustion state | Per-NPC per-player | Permanent (unless REACTIVATE_TOPIC) |
| Reputation | Per-faction per-player | Permanent |
| Saved session (mid-conversation) | Per-NPC per-player | Until next conversation start |

Grayed exploratory state is per-session only: it resets when the player starts a new conversation with the NPC.
