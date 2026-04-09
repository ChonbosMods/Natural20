# Quest Text Field Definitions

Every quest template contains a fixed set of text fields. Each field is shown to the player at a specific moment in the quest lifecycle and is bound to a specific objective for variable resolution. This document defines the structural role of each field — what it must accomplish, what variables are valid in it, and how it connects to the fields before and after it.

**Situation-specific emotional guidance lives in the individual situation documents.** This document covers the universal structural rules that apply regardless of which dramatic situation the template belongs to.

---

## Quest Phase Structure

All quest templates follow this phase structure:

```
EXPOSITION ──► CONFLICT 1 ──► [CONFLICT 2] ──► [CONFLICT 3] ──► [CONFLICT 4] ──► RESOLUTION
```

Every quest must have at least one conflict phase. The minimum viable template includes: expositionText, acceptText, declineText, expositionTurnInText, conflict1Text, conflict1TurnInText, and resolutionText. Authors may write up to four conflict phases for templates that benefit from longer arcs. All conflict phases are structurally interchangeable — they share the same text field format and variable binding pattern. An author writing conflict3 or conflict4 follows the same rules as conflict1 and conflict2.

---

## Text-Field-to-Objective Binding

Variables like `{kill_count}`, `{enemy_type}`, `{quest_item}`, and `{gather_count}` are overlaid per-objective. Each text field resolves these variables against a specific objective in the chain.

| Text field | Bound to | When shown |
|---|---|---|
| `expositionText` | `objectives[0]` | Player first talks to quest giver |
| `acceptText` | — | Player accepts the quest |
| `declineText` | — | Player declines the quest |
| `skillCheck.passText` | — | Player passes stat check at accept/decline |
| `skillCheck.failText` | — | Player fails stat check at accept/decline |
| `targetNpcOpener` | TALK_TO_NPC objective | Player selects quest topic on target NPC |
| `targetNpcCloser` | TALK_TO_NPC objective | After player presses [CONTINUE] on opener |
| `expositionTurnInText` | `objectives[0]` | Player returns after completing exposition objective |
| `conflict1Text` | `objectives[1]` | Shown after exposition turn-in |
| `conflict1TurnInText` | `objectives[1]` | Player returns after completing conflict 1 |
| `conflict2Text` | `objectives[2]` | Shown after conflict 1 turn-in |
| `conflict2TurnInText` | `objectives[2]` | Player returns after completing conflict 2 |
| `conflict3Text` | `objectives[3]` | Shown after conflict 2 turn-in |
| `conflict3TurnInText` | `objectives[3]` | Player returns after completing conflict 3 |
| `conflict4Text` | `objectives[4]` | Shown after conflict 3 turn-in |
| `conflict4TurnInText` | `objectives[4]` | Player returns after completing conflict 4 |
| `resolutionText` | current objective | Quest complete, final dialogue |

**Critical:** If objectives[0] is KILL_MOBS, then `{kill_count}` and `{enemy_type}` are valid in expositionText and expositionTurnInText. If objectives[1] is COLLECT_RESOURCES, then `{quest_item}` and `{gather_count}` are valid in conflict1Text and conflict1TurnInText. Using a per-objective variable in a field bound to a different objective is an error.

**Always-available variables:** `{quest_giver_name}`, `{settlement_name}`, `{settlement_type}`, `{self_role}`, `{settlement_npc}`, `{settlement_npc_role}`, `{other_settlement}`, `{quest_reward}` are bound at quest generation time and valid in any text field. `{target_npc}`, `{target_npc_role}`, and `{target_npc_settlement}` are available in any text field but only when the quest chain includes a TALK_TO_NPC objective.

---

## Field Definitions

### `expositionText`

**Structural role:** Establish the problem. This is the first thing the player reads when they engage the quest giver. It must communicate: what is wrong, why the NPC cares, and implicitly why they need the player.

**What it must do:**
- Present the situation clearly enough that the player understands what they're being asked to do
- Establish the NPC's emotional relationship to the problem
- Make the player want to know more or want to help

**What it must NOT do:**
- Describe the objective in mechanical terms ("kill 5 goblins")
- Dump backstory or lore
- Reference events or threats that no objective in the chain addresses
- Use per-objective variables from objectives[1] or objectives[2]

**Length guidance:** 2-4 sentences. The sweet spot is 3 — enough to establish the situation without overexplaining.

---

### `acceptText`

**Structural role:** The NPC's immediate reaction to the player saying yes. This is an emotional beat, not an instruction manual. The NPC is reacting to having their request accepted.

**What it must do:**
- Reflect the situation's emotional register (relief for Supplication, grim satisfaction for Vengeance, etc.)
- Feel like a human reacting to good news, not a quest system confirming an action

**What it must NOT do:**
- Repeat the objective ("Great, now go kill those goblins")
- Reference game mechanics ("Your quest has been updated")
- Be generic across all situations — the accept reaction should feel specific to this NPC's emotional state

**Length guidance:** 1-2 sentences. This is a reaction, not a speech.

---

### `declineText`

**Structural role:** The NPC's reaction to the player saying no. Tone is situation-dependent and specified in each situation document. The NPC remains in character — they don't break the fourth wall or become a game system.

**What it must do:**
- Reflect how this specific NPC, in this specific situation, would feel about being refused
- Stay in character

**What it must NOT do:**
- Reference game mechanics ("You can come back later to accept this quest")
- Be uniformly polite across all situations — some NPCs would be hurt, some angry, some indifferent
- Threaten the player with gameplay consequences

**Length guidance:** 1-3 sentences. Hostile or guilt-tripping declines can run longer. Indifferent ones should be short.

---

### `expositionTurnInText`

**Structural role:** The NPC reacts to the player completing the first objective and transitions into the next phase. This is a pivot point — the quest is moving from setup to development.

**What it must do:**
- Acknowledge what the player just accomplished
- Shift the emotional register forward (from desperate to cautiously hopeful, from puzzled to focused, etc.)
- Bridge naturally into conflict1Text — the player will see conflict1Text immediately after this field

**What it must NOT do:**
- Repeat exposition information the player already knows
- Use per-objective variables from objectives[1] (those belong in conflict1Text)
- Feel like a standalone conclusion — the quest is not over

**Length guidance:** 2-3 sentences. One sentence of reaction, one of transition.

---

### `conflictText` (conflict1 through conflict4)

**Structural role:** Introduce the next objective. All conflict phases are structurally interchangeable — they follow the same rules regardless of their position in the chain. Each conflict phase deepens, redirects, or escalates the situation established in the exposition.

**What it must do:**
- Present the next objective naturally, through the NPC's voice
- Advance the situation — not just "more of the same" but a development that justifies another phase
- Use per-objective variables from the matching objectives index correctly (conflict1 uses objectives[1], conflict2 uses objectives[2], etc.)

**What it must NOT do:**
- Repeat the exposition framing verbatim
- Use per-objective variables from a different objective index
- Feel like a disconnected new quest — each conflict must flow from what came before
- Introduce objectives that contradict the situation's emotional frame

**Length guidance:** 2-4 sentences.

**Scaling guidance for multi-conflict templates:** Each successive conflict phase should feel like a natural progression, not padding. If the author cannot articulate why a conflict3 or conflict4 makes the story better, the template should end sooner. A well-written 2-conflict quest is better than a 4-conflict quest where the last two phases feel like filler.

---

### `conflictTurnInText` (conflict1TurnIn through conflict4TurnIn)

**Structural role:** The NPC reacts to the player completing the corresponding objective. Each turn-in advances the NPC's emotional arc toward the tone arc destination.

**What it must do:**
- Acknowledge the player's work
- Advance the NPC's emotional state
- If another conflict phase follows: bridge into it naturally
- If resolution follows: set up the final emotional beat

**What it must NOT do:**
- Use per-objective variables from the next objective index (those belong in the next conflictText)
- Feel conclusive if another conflict phase follows
- Feel transitional if resolution follows — the last turn-in before resolution should have emotional weight

**Length guidance:** 2-3 sentences.

---

### `resolutionText`

**Structural role:** The last thing the NPC says. The quest is over. The emotional arc arrives at its destination. This field must feel like an ending.

**What it must do:**
- Close the emotional arc (see the situation's tone arc destination)
- Reference `{quest_reward}` naturally — not as a transaction but as the NPC's way of expressing what the player's help meant
- Leave the player with a feeling, not a loose thread

**What it must NOT do:**
- Set up a sequel ("But there may be more trouble ahead...")
- Leave the situation unresolved
- Feel transactional ("Here is your payment. Goodbye.")
- Introduce new information or new problems

**Length guidance:** 2-4 sentences. This is the payoff — give it room to land.

---

### `targetNpcOpener`

**Structural role:** What the target NPC says when the player selects the quest topic during a TALK_TO_NPC objective. This is a different NPC from the quest giver. They are providing their perspective on the situation the quest giver described.

**What it must do:**
- Sound like a different person from the quest giver. The target NPC has their own voice, opinions, and relationship to the situation.
- Provide information, context, or perspective that the quest giver couldn't provide themselves.
- Make the player feel like the trip to find this NPC was worthwhile.

**What it must NOT do:**
- Repeat information the quest giver already conveyed.
- Use the quest giver's emotional register. The target NPC has their own feelings about the situation.
- Reference the player's quest objectives mechanically.

**Length guidance:** 2-4 sentences.

**Variable binding:** Only available when the quest chain includes a TALK_TO_NPC objective. Uses the same always-available variables. Per-objective variables from the TALK_TO_NPC phase are valid.

---

### `targetNpcCloser`

**Structural role:** After the player presses [CONTINUE] on the opener, the target NPC delivers a closing remark. This wraps up the target NPC's side of the conversation before the player can choose "I'll pass that along."

**What it must do:**
- Close the target NPC's contribution naturally.
- Give the player something concrete to carry back (advice, confirmation, a perspective).

**What it must NOT do:**
- Introduce new problems or quests.
- Repeat the opener's content.

**Length guidance:** 1-2 sentences. This is a closer, not a second speech.

---

### `skillCheck.passText`

**Structural role:** Shown during the accept/decline phase when the player passes the stat check. The NPC reveals a deeper layer — a fear, a personal stake, tactical context, or emotional vulnerability they wouldn't have volunteered.

**What it must do:**
- Reveal something that adds dimension to the quest without changing what the player does
- Read coherently for the specified skill type (see skill-to-context mapping in `quest_authoring_rules.md`)
- Stay in the NPC's voice — this is them opening up, not a narrator aside

**What it must NOT do:**
- Change the quest objectives
- Dump lore
- Read as a tooltip or game hint

**Length guidance:** 1-3 sentences.

---

### `skillCheck.failText`

**Structural role:** Shown during the accept/decline phase when the player fails the stat check. The NPC pulls back. The deeper layer stays hidden.

**What it must do:**
- Deflect naturally — the NPC changes the subject, gives a vague answer, or stays surface-level
- Feel like a person choosing not to share, not a game mechanic blocking access

**What it must NOT do:**
- Say "you failed" in any form
- Make the NPC hostile (the fail is the player's missed opportunity, not an offense)
- Reveal the same information as the pass text in a reduced form

**Length guidance:** 1-2 sentences. Deflections are short.
