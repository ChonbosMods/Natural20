# Quest Template Validation Checklist

Human-review checklist for spot-checking sub-agent quest template output. For each template in a batch, verify:

---

## Entity Grounding

- [ ] No invented locations (storehouses, ridges, copses, ruins, specific buildings)
- [ ] No spatial modification of POI types (no "collapsed shaft," no "east face of the mine")
- [ ] No unnamed-but-specific characters ("the old hermit," "a mysterious stranger")
- [ ] Every promise maps to an objective in the chain (if NPC says "go kill them," KILL_MOBS exists)
- [ ] Invented events stay within objective scope (no earthquakes if no objective addresses earthquakes)
- [ ] `{settlement_npc}` is never framed as someone the player should interact with

## Variable Scoping

- [ ] Per-objective variables appear only in their bound text field:
  - `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}` → only in fields bound to KILL_MOBS objectives
  - `{quest_item}`, `{gather_count}` → only in fields bound to COLLECT_RESOURCES or FETCH_ITEM objectives
  - Field-to-objective binding is correct (expositionText → objectives[0], conflict1Text → objectives[1], etc.)
- [ ] `{target_npc}`, `{target_npc_role}`, `{target_npc_settlement}` only appear when a TALK_TO_NPC objective exists in the chain
- [ ] `{other_settlement}` used only as worldbuilding flavor, not as an objective destination (unless a TALK_TO_NPC objective targets it)
- [ ] `{self_role}` used sparingly — only when the role is relevant to the quest giver's problem
- [ ] No smalltalk variables (`{mob_type}`, `{npc_name}`, `{wildlife_type}`, `{food_type}`, `{resource_type}`, `{poi_type}`, `{subject_focus}`)

## Voice

- [ ] Every text field is max 4 sentences
- [ ] First person or direct address only — no narrator voice, no third-person reporting
- [ ] Passes the briefing test: would a real person say this to someone they're asking for help?
- [ ] No mechanical references ("accept this quest," "return when complete," "quest log," "objective")
- [ ] resolutionText hands over exactly one gift (`{reward_item}`) and never coordinates it with a second noun phrase the player might read as a second item
- [ ] No lore dumps — NPC tells you their problem, not regional history

## Structural Integrity

- [ ] objectives array length = 1 + number of conflict phases written
- [ ] Minimum 2 objectives (exposition + conflict1)
- [ ] Maximum 5 objectives (exposition + conflict1-4)
- [ ] Every objective in the JSON is referenced in its corresponding text field (no dead objectives)
- [ ] Optional conflict fields (conflict2-4) are present only when matching objectives exist
- [ ] expositionTurnInText bridges naturally into conflict1Text
- [ ] Each conflictTurnInText bridges into the next phase or sets up resolution
- [ ] resolutionText references `{reward_item}` naturally as a single handover
- [ ] resolutionText feels like an ending — no cliffhangers, no sequel hooks, no loose threads

## Situation Fidelity

- [ ] `situation` field matches the actual emotional content of the template
- [ ] Tone arc is traceable through the text fields (read exposition → accept → turns/conflicts → resolution in order)
- [ ] Tone arc matches the situation document's specified arc (e.g., desperate → grateful for Supplication)
- [ ] declineText matches situation-specific guidance:
  - Supplication: guilt-tripping, deflated
  - Vengeance: hostile, contemptuous
  - Enigma: shrug, mild disappointment
  - Obtaining: mild disappointment, "I'll figure it out"
  - Disaster: heavy, weary absorption
  - (check the specific situation document for others)
- [ ] resolutionText arrives at the situation's tone arc destination (grateful, settled, hopeful, etc.)
- [ ] Template could not be trivially re-tagged as a different situation — the emotional frame is specific

## Skill Check

- [ ] If present: skill type is coherent with passText content
  - INSIGHT → emotional truth, personal motivation
  - PERCEPTION → physical detail, environmental observation
  - PERSUASION → NPC was reluctant, player earned trust
  - INVESTIGATION → logical gap probed, fuller picture
  - NATURE → creature behavior, terrain, weather
  - HISTORY → past events, old knowledge, patterns
- [ ] passText reveals something the NPC wouldn't have volunteered
- [ ] passText is NPC speech, not a narrator aside or tooltip
- [ ] failText is a natural deflection, max 2 sentences
- [ ] failText does not say "you failed" in any form
- [ ] failText does not reveal the same information as passText in reduced form
- [ ] DC is reasonable for the situation (not trivially easy, not impossibly hard)

## Topic Header

- [ ] `topicHeader` field is present
- [ ] Recommended 2-4 words; maximum 6
- [ ] No template variables
- [ ] Evocative, not descriptive or mechanical ("A Debt Unpaid" not "Kill Goblins")
- [ ] Does not spoil the quest plot
- [ ] Reads naturally as a conversation topic label
- [ ] Reflects the situation's emotional register
- [ ] Works as both an initiation label and a turn-in label
- [ ] Unique across the catalog — no other template shares this header

## Objective Chain

- [ ] Every objective type used is listed as available for this situation (check the situation document)
- [ ] countMin/countMax ranges are reasonable (small: 2-5, medium: 4-8, large: 6-12)
- [ ] countMin ≤ countMax
- [ ] TALK_TO_NPC objectives don't appear in situations that exclude them (Vengeance, Conflict with Fate)
- [ ] KILL_MOBS objectives don't appear in situations that exclude them (Madness, Mistaken Jealousy, Erroneous Judgment)
- [ ] COLLECT_RESOURCES objectives don't appear in situations that exclude them (Vengeance, Obtaining, Pursuit, etc.)

---

## Batch-Level Checks

After reviewing individual templates, check the batch as a whole:

- [ ] No two templates describe the same specific problem
- [ ] Objective combinations vary across the batch (not all KILL_MOBS → COLLECT_RESOURCES)
- [ ] Chain lengths vary (mix of 2, 3, and occasional 4-5 objective templates)
- [ ] NPC voice varies — some terse, some eloquent, some rambling
- [ ] Skill types vary across templates with skill checks
- [ ] Templates feel like they belong to the assigned situation — the emotional frame is consistent but the specifics differ

---

## Red Flags

If you see any of these, the template should be rejected or rewritten:

| Red Flag | What It Means |
|---|---|
| "The [specific landmark]..." | Invented location. |
| "Eliminate [N] hostiles in the area" | Briefing voice. Not a person talking. |
| "Accept this quest to begin" | Mechanical reference. |
| `{kill_count}` in a field bound to TALK_TO_NPC | Variable scoping error. |
| `{target_npc}` with no TALK_TO_NPC in the chain | Unbound variable. |
| "Go talk to {settlement_npc}" | settlement_npc is flavor only, not an objective target. |
| "But perhaps there's more to discover..." in resolution | Cliffhanger. Resolution must close. |
| "Take {reward_item}. And a warm meal." / "{reward_item}, plus silver besides." | Two-gift anti-pattern. Only `{reward_item}` is handed over. |
| passText reveals emotion, skill is NATURE | Skill type mismatch. |
| "For centuries, our ancestors..." | Lore dump. |
| Exposition could be swapped to a different situation with no changes | Situation fidelity failure. |
| Same objective chain as another template in the batch | Insufficient variety. |
| "Old Garm who lives past the hill told me..." | Invented character (unless "Old Garm" is `{settlement_npc}`). |
| NPC says "escort" / "deliver" / "protect" | Objective types that don't exist yet. |
| Topic header > 6 words | Too long for UI and waypoint. (2-4 recommended.) |
| Topic header contains `{variable}` | Headers are static strings. |
| Topic header is mechanical ("Kill Quest") | Must feel like a conversation topic. |
| Topic header duplicates another template | Must be unique across catalog. |
