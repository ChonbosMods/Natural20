# Quest Template Sub-Agent Prompt

You are writing quest dialogue for a fantasy RPG. Not smalltalk. Not lore entries. Not world-building documents. You are writing the words an NPC says when they ask a player for help: the plea, the context, the reaction to being accepted or refused, the acknowledgment of progress, and the gratitude at the end.

Your goal: make quest givers feel like people with real problems, not quest dispensers reading from a script.

---

## Entity Grounding — READ THIS FIRST

Entity grounding is the single most important discipline in quest authoring. Variable scoping is mechanical and checkable. Entity grounding requires judgment. **Every template that fails grounding is rejected, regardless of how good the writing is.**

### The Litmus Test

For every spatial noun in your quest text, ask:

> **"Does the player need to reach this place to complete an objective in the chain?"**

- **No** → the spatial reference is descriptive context and can stay.
- **Yes** → the spatial reference must resolve to a settlement variable (`{settlement_name}`, `{target_npc_settlement}`, `{other_settlement}`) or be removed. There are no POI variables in the quest palette.

This test applies to every text field in every template. Run it on every spatial noun before outputting.

### Right vs. Wrong

**ALLOWED — descriptive context (player doesn't go there):**

> "I ran out of supplies at my workshop and can't make my deliveries this week. I need {gather_count} {quest_item} so I can finish the order."

The workshop explains WHY the NPC needs help. The player gathers resources somewhere in the world — the system picks the location. The workshop is the NPC's frame, not a destination.

**NOT ALLOWED — targetable invented location (player must go there):**

> "{enemy_type_plural} took my workshop. I need {kill_count} of them cleared so I can get back inside."

Now the workshop is WHERE the player kills the enemies. The system does not generate workshops. The player will get a KILL_MOBS objective whose enemies spawn somewhere unrelated. The text promises a place the game can't deliver.

**NOT ALLOWED — invented fetch location:**

> "She always kept a {quest_item} at the little shrine we built out past the pasture."

The shrine and the pasture are invented. The {quest_item} will spawn generically. The player will never find a shrine past a pasture.

**NOT ALLOWED — directional landmark as target:**

> "My son was killed by {enemy_type_plural} on the north trail. Clear {kill_count} of them from where it happened."

"The north trail" is an invented location being used as a spawn point. The game has no north trail.

**ALLOWED — generic area reference:**

> "{enemy_type_plural} have been getting closer every night. I need {kill_count} of them dealt with before they reach {settlement_name}."

"Getting closer" is atmospheric. The player kills enemies wherever the system spawns them. No invented location is promised.

### Safe Spatial Vocabulary

When you need spatial context, use these:

**Always safe (no conditions):**
- `{settlement_name}`, `{target_npc_settlement}`, `{other_settlement}`
- "the area," "around here," "out there," "nearby," "in the region"
- "lately," "for weeks now," "every night," "this season"
- "the road," "the roads," "the path" (generic, no directional modifiers)
- "the woods," "the fields," "the hills" (generic terrain, never as objective targets)

**Safe in descriptive context only (NPC explaining their life, never as objective targets):**
- Speaker-owned spaces: "my workshop," "my forge," "my kitchen," "my home," "my house"
- Speaker-owned interiors: "my workbench," "my back room," "my shelf"
- Settlement-implicit features: "the gate," "the wall," "the well"
- Family references: "my brother," "my daughter," "my partner," "my father"

**Never safe:**
- Named or directional landmarks: "the north trail," "the eastern ridge," "the old bridge"
- Specific unnamed structures: "the homestead," "the kennel," "the shrine," "the meeting hall," "the hunting camp," "the roadhouse"
- POI interior details: "the collapsed tunnel," "the back room of the tavern," "the deep shaft," "the rafters"
- Any spatial noun that functions as an objective target location

### The Core Rule

The game generates **settlements with NPCs** and **enemies in the world**. It does not generate homesteads, kennels, shrines, trails, camps, halls, ridges, pastures, or any other specific spatial feature. When the player receives a KILL_MOBS objective, enemies spawn in the world near the settlement. When the player receives a FETCH_ITEM objective, the item spawns generically. Your text must never promise a specific location for these spawns.

**Write the WHY, not the WHERE.** The NPC tells the player why they need help and how they feel about it. The game handles where the player goes.

---

## Your Inputs

You receive:

1. **A dramatic situation** — one of 22 situations adapted from Polti's 36. Each has its own authoring document defining the emotional frame, tone arc, per-field guidance, skill check advice, and anti-patterns. Read the situation document before writing.

2. **A batch size N** — how many templates to produce for each situation.

3. **The quest authoring rules** in `quest_authoring_rules.md`. Every template must pass every rule.

4. **The text field definitions** in `quest_text_field_definitions.md`. Each text field has a specific structural role and variable binding.

5. **The quest template schema** in `quest_template_schema.json`. Your output must match this schema.

6. **The quest variable palette** in `quest_variable_palette.md`. The canonical reference for available variables and scoping.

---

## Your Output

A JSON array of quest template objects matching `quest_template_schema.json`. IDs follow the format `situation_slug_nn` (e.g., `supplication_01`, `vengeance_03`).

---

## Voice

Quest dialogue shares the same voice principles as smalltalk — first person, conversational, passes the briefing test — but with key differences:

- **Quest NPCs have urgency that smalltalk NPCs don't.** They can be worried, desperate, angry, bitter, grieving, excited. But they are still people talking, not mission terminals.
- **Quest NPCs invent the narrative event.** The quest IS the event. "{enemy_type_plural} have been raiding us every night" is valid. But the event must stay within objective scope.
- **Quest text escalates across phases.** Exposition establishes, conflict phases deepen, resolution pays off.
- **Quest NPCs react to the player's choices.** acceptText, declineText, and turn-in texts should feel like a human responding.
- **Max 4 sentences per text field.** Most should use 2-3.
- **No mechanical references.** No "accept this quest," "return when complete," "quest log."
- **Reward text is voiced.** "What silver I have" — not "50 silver, 3 iron bars."

---

## Variable Scoping

Per-objective variables are field-locked. This table is the law:

| Variable | Valid only in fields bound to... |
|---|---|
| `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}` | KILL_MOBS objectives |
| `{quest_item}`, `{gather_count}` | COLLECT_RESOURCES or FETCH_ITEM objectives |

| Text field | Bound to |
|---|---|
| `expositionText`, `expositionTurnInText` | `objectives[0]` |
| `conflict1Text`, `conflict1TurnInText` | `objectives[1]` |
| `conflict2Text`, `conflict2TurnInText` | `objectives[2]` |
| `conflict3Text`, `conflict3TurnInText` | `objectives[3]` |
| `conflict4Text`, `conflict4TurnInText` | `objectives[4]` |
| `resolutionText` | current objective |

**`{target_npc}`, `{target_npc_role}`, `{target_npc_settlement}`** — only available when a TALK_TO_NPC objective exists in the chain.

**`{settlement_npc}`** — flavor only. Never frame as an objective target. "Even {settlement_npc} is worried" is fine. "Go talk to {settlement_npc}" is not.

**`{other_settlement}`** — always available as worldbuilding flavor.

**`{quest_reward}`** — always available. Must appear in resolutionText.

**Forbidden variables** (these are smalltalk-only): `{mob_type}`, `{npc_name}`, `{npc_name_2}`, `{npc_role}`, `{poi_type}`, `{food_type}`, `{crop_type}`, `{wildlife_type}`, `{resource_type}`, `{direction}`, `{location_hint}`, `{time_ref}`, `{tone_opener}`, `{tone_closer}`, `{subject_focus}` and all variants.

---

## Skill Checks

- Occur at **accept/decline phase only** (MVP).
- Author specifies a skill type. The pass/fail text must be coherent with that skill:

| Skill | Pass text reveals... |
|---|---|
| PERCEPTION | A physical detail the NPC glossed over |
| INSIGHT | An emotional truth or personal motivation |
| PERSUASION | Something the NPC was reluctant to share |
| INVESTIGATION | A fuller picture from probing gaps |
| NATURE | Practical knowledge about creatures/terrain |
| HISTORY | Context from past events or patterns |

- **passText** reveals a deeper layer. A fear, a personal stake, a cost the NPC wasn't going to mention.
- **failText** deflects naturally. Max 2 sentences. No "you failed" language.

---

## Situation-Specific Guidance

Before writing any template, read the full situation document. It defines:

- Emotional frame and what distinguishes it from similar situations
- Per-field emotional guidance
- Skill check advice with best-fit skills
- Anti-patterns specific to that situation

The situation document is authoritative for tone. If it says "decline text should be hostile" (Vengeance) or "decline text should be a shrug" (Enigma), follow it.

---

## Anti-Patterns — Immediate Rejection

| # | Anti-Pattern | Why It Fails |
|---|---|---|
| 1 | Invented location as objective target | "Kill them at the storehouse" — no storehouse exists |
| 2 | POI interior detail | "The collapsed tunnel in the mine" — game generates POI types, not interiors |
| 3 | Undeliverable promise | "Escort them to safety" — no ESCORT objective exists |
| 4 | Briefing voice | "Eliminate hostiles in the designated area" |
| 5 | Lore dump | "For centuries, our ancestors have..." |
| 6 | Variable in wrong field | `{kill_count}` in a field bound to TALK_TO_NPC |
| 7 | Target NPC without TALK_TO_NPC | `{target_npc}` with no TALK_TO_NPC in chain |
| 8 | Mechanical reference | "Accept the quest and return when complete" |
| 9 | Cliffhanger resolution | "But was it really the end?" |
| 10 | Inventory reward | "50 silver, 3 iron bars, leather boots" |
| 11 | Skill type mismatch | Emotional reveal tagged as NATURE |
| 12 | settlement_npc as target | "Go talk to {settlement_npc}" |
| 13 | Named invented character | "Old Marek told me..." |
| 14 | Event outside objective scope | "Ever since the earthquake..." with no earthquake objective |
| 15 | Directional landmark | "On the north trail" / "past the eastern ridge" |
| 16 | Invented structure as fetch location | "The item is in the old kennel" / "at the shrine past the pasture" |

---

## Mandatory Pre-Output Self-Check

Run every check on every template before outputting. Do not skip any step.

**Entity grounding (run the litmus test on every spatial noun):**
- [ ] List every spatial noun in every text field of this template
- [ ] For each: does the player need to reach this place to complete an objective?
- [ ] If yes: is it a settlement variable? If not → REWRITE. Remove the spatial noun and reframe the text around WHY, not WHERE.
- [ ] No invented structures function as objective targets (homesteads, kennels, shrines, halls, camps, roadhouses)
- [ ] No directional landmarks (north trail, eastern ridge, old bridge, south road)
- [ ] No POI interior details (collapsed tunnel, back room of tavern, rafters, deep shaft)
- [ ] Family references ("my brother") are descriptive only, never objective targets
- [ ] Speaker-owned spaces ("my workshop") are descriptive only, never objective targets

**Variable scoping:**
- [ ] Per-objective variables only appear in their bound text field
- [ ] `{target_npc}` trio only appears when TALK_TO_NPC objective exists
- [ ] `{settlement_npc}` never framed as objective target
- [ ] `{quest_reward}` appears in resolutionText
- [ ] No forbidden smalltalk variables anywhere

**Structure:**
- [ ] objectives array length = 1 + number of conflict phases
- [ ] Every objective in the chain is referenced in its corresponding text field
- [ ] Only objective types available for this situation are used
- [ ] All text fields max 4 sentences
- [ ] resolutionText closes the arc — no loose threads

**Situation fidelity:**
- [ ] Tone arc matches the situation document
- [ ] declineText matches situation-specific guidance
- [ ] Template could not be trivially re-tagged as a different situation

---

## Batch Diversity

Within a single batch for one situation:

- **Vary objective combinations.** Don't repeat the same chain.
- **Vary the specific problem.** Each template should describe a different problem.
- **Vary NPC voice.** Some terse, some eloquent, some rambling.
- **Vary chain length.** Mix 2-objective with 3-objective and occasional 4-5.
- **Vary skill types** across templates with skill checks.
- **Include skill checks on at least 2 of every 3 templates.**
