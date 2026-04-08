# Quest Template Batch Generation — Claude Code Orchestration Prompt

Paste this into Claude Code to generate the initial set of quest templates.

---

## Prompt

You are orchestrating the first batch of quest templates for Natural 20's v2 quest system. The authoring system is complete and lives in the `authoring/quest_authoring/` directory (or wherever the user has placed the quest_authoring folder). Your job is to spin up sub-agents that each produce templates for a subset of the 22 dramatic situations, then collect, validate, and output the results.

### Step 1: Locate and verify the authoring system

Find the `quest_authoring/` folder. Verify these files exist:
- `quest_authoring_rules.md`
- `quest_text_field_definitions.md`
- `quest_template_schema.json`
- `quest_sub_agent_prompt.md`
- `quest_variable_palette.md`
- `quest_situations.md`
- `situations/01_supplication.md` through `situations/22_obstacles_to_love.md` (22 files)

If any are missing, stop and report which files are absent.

### Step 2: Plan the batch

Generate **3 templates per situation** for all 22 situations = **66 templates total**.

Split the work across sub-agents by grouping situations that share emotional registers so each agent builds internal consistency:

**Agent 1 — Desperate/Urgent (12 templates):**
- 01_supplication (3)
- 02_deliverance (3)
- 06_disaster (3)
- 14_self_sacrifice_for_kindred (3)

**Agent 2 — Proactive/Determined (12 templates):**
- 05_pursuit (3)
- 09_vengeance (3)
- 04_daring_enterprise (3)
- 17_ambition (3)

**Agent 3 — Practical/Grounded (9 templates):**
- 07_obtaining (3)
- 03_recovery (3)
- 13_self_sacrifice_for_an_ideal (3)

**Agent 4 — Investigative/Social (12 templates):**
- 08_enigma (3)
- 11_rivalry_of_kinsmen (3)
- 18_mistaken_jealousy (3)
- 19_erroneous_judgment (3)

**Agent 5 — Emotional/Relational (12 templates):**
- 20_remorse (3)
- 21_involuntary_crimes_of_love (3)
- 22_obstacles_to_love (3)
- 12_madness (3)

**Agent 6 — Dark/Heavy (9 templates):**
- 15_necessity_of_sacrificing_loved_ones (3)
- 16_loss_of_loved_ones (3)
- 10_conflict_with_fate (3)

### Step 3: Spin up each sub-agent

For each agent, provide these files as context:

1. `quest_sub_agent_prompt.md` — the agent's system instructions
2. `quest_authoring_rules.md` — hard constraints
3. `quest_text_field_definitions.md` — text field structural roles
4. `quest_variable_palette.md` — available variables and scoping rules
5. `quest_template_schema.json` — the JSON contract (focus on the schema, the example template, and the variable reference)
6. The specific `situations/XX_situation_name.md` files for the situations assigned to that agent

Give each agent this task instruction:

```
You are generating quest templates for Natural 20. Read all provided authoring documents before writing anything. Read the Entity Grounding section of quest_sub_agent_prompt.md FIRST — entity grounding failures are the #1 cause of template rejection.

Your assignment: generate 3 templates for each of the following situations: [list situations]

For each situation:
- Read the situation document fully — it defines your emotional frame, tone arc, available objectives, per-field guidance, skill check advice, and anti-patterns
- Produce 3 distinct templates that vary in: objective combinations, chain length (2-4 objectives), NPC voice, specific problem described, and skill type (if skill check is included)
- Include a skill check on at least 2 of the 3 templates per situation
- Each template must be a valid JSON object matching quest_template_schema.json
- Template IDs follow the format: situation_slug_nn (e.g., supplication_01, supplication_02, supplication_03)

Output a single JSON array containing all your templates. No commentary outside the JSON.

ENTITY GROUNDING — THE #1 REJECTION REASON:
Before writing each template, internalize this: the game generates settlements with NPCs and enemies in the world. It does NOT generate homesteads, kennels, shrines, trails, camps, halls, ridges, pastures, workshops, or any specific spatial feature. When the player gets a KILL_MOBS objective, enemies spawn generically near the settlement. When the player gets a FETCH_ITEM, the item spawns generically.

THE LITMUS TEST — run on every spatial noun in every text field:
"Does the player need to reach this place to complete an objective?"
- If NO → the spatial reference is descriptive context and can stay ("I ran out of supplies at my workshop" is fine — the player doesn't go to the workshop)
- If YES → the place must be a settlement variable ({settlement_name}, {target_npc_settlement}, {other_settlement}) or it must be removed

WRITE THE WHY, NOT THE WHERE. The NPC tells the player why they need help. The game handles where the player goes.

Examples of REJECTED text:
- "Kill the {enemy_type_plural} at the old homestead" (invented target location)
- "Find the {quest_item} at the shrine past the pasture" (invented fetch location)  
- "Clear them from the north trail" (directional landmark as target)
- "The item is in the rafters of the meeting hall" (POI interior + invented structure)

Examples of ACCEPTED text:
- "{enemy_type_plural} have been getting closer to {settlement_name} every night" (generic, no target location)
- "I need {gather_count} {quest_item} — my workshop is useless without supplies" (workshop is descriptive WHY, not WHERE)
- "Talk to {target_npc} in {target_npc_settlement}" (settlement variable, deliverable)

Other critical reminders:
- Per-objective variables ({kill_count}, {enemy_type}, {quest_item}, {gather_count}) are ONLY valid in text fields bound to the matching objective type. Check the binding table.
- {target_npc}, {target_npc_role}, {target_npc_settlement} are ONLY available when a TALK_TO_NPC objective exists.
- {settlement_npc} is FLAVOR ONLY — never frame as an objective target.
- Max 4 sentences per text field. Most should use 2-3.
- resolutionText must reference {quest_reward} and must close the arc — no cliffhangers.
- declineText tone is situation-specific — check the situation document.
- Every objective in the chain must be referenced in its corresponding text field.
- Only use objective types listed as available for each situation.
- Run the MANDATORY PRE-OUTPUT SELF-CHECK from quest_sub_agent_prompt.md on every template before including it in your output.
```

### Step 4: Collect and validate

As each agent returns its JSON array:

1. **Parse the JSON.** If it fails to parse, report the agent and the parse error.

2. **Schema check each template:**
   - Required fields present: id, situation, objectives, expositionText, acceptText, declineText, expositionTurnInText, conflict1Text, conflict1TurnInText, resolutionText, rewardText, valence
   - objectives array length = 1 + number of conflict phases with text fields
   - Optional conflict fields (2-4) present only when matching objectives exist
   - ID format matches `situation_slug_nn`
   - situation field matches an enum value
   - All objective types are valid enum values (KILL_MOBS, COLLECT_RESOURCES, FETCH_ITEM, TALK_TO_NPC)

3. **Variable scoping check each template:**
   - Scan all text fields for `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}` — verify they only appear in fields bound to KILL_MOBS objectives
   - Scan for `{quest_item}`, `{gather_count}` — verify they only appear in fields bound to COLLECT_RESOURCES or FETCH_ITEM objectives
   - Scan for `{target_npc}`, `{target_npc_role}`, `{target_npc_settlement}` — verify a TALK_TO_NPC objective exists in the chain
   - Scan for forbidden smalltalk variables: `{mob_type}`, `{npc_name}`, `{npc_name_2}`, `{npc_role}`, `{poi_type}`, `{food_type}`, `{crop_type}`, `{wildlife_type}`, `{resource_type}`, `{direction}`, `{location_hint}`, `{time_ref}`, `{tone_opener}`, `{tone_closer}`, `{subject_focus}` and variants — reject any template containing these
   - Verify `{quest_reward}` appears in resolutionText

4. **Entity grounding check each template (THE MOST IMPORTANT CHECK):**
   - Scan all text fields for spatial nouns (structures, landmarks, terrain features, interiors)
   - For each spatial noun, apply the litmus test: does the player need to reach this place to complete an objective?
   - REJECT if any of these appear as objective target locations: homestead, kennel, shrine, hall, camp, roadhouse, cabin, outbuilding, cellar, barn, workshop (as target), forge (as target), any named/directional landmark (north trail, eastern ridge, old bridge, south road), any POI interior detail (tunnel, shaft, room, rafters, cellar)
   - PASS if spatial nouns are descriptive context only: "my workshop" explaining why the NPC needs supplies, "the gate" as scene-setting, "my brother" as family reference
   - Flag borderline cases for human review rather than auto-passing

5. **Situation constraint check:**
   - For each template, load the situation's available objectives from quest_situations.md
   - Verify every objective type in the chain is permitted for that situation
   - Flag any violations (e.g., KILL_MOBS in a Mistaken Jealousy template, COLLECT_RESOURCES in a Vengeance template)

6. **Duplicate check across all agents:**
   - No duplicate template IDs
   - No two templates with identical objective chains AND identical situation

7. **Report validation results.** For each template: PASS or FAIL with specific violations listed. Do not silently drop failing templates — report them so they can be fixed.

### Step 5: Output

1. Combine all passing templates into a single JSON file: `quest_templates_v2_initial.json`
   Format:
   ```json
   {
     "version": 2,
     "generated": "YYYY-MM-DD",
     "templates": [ ...all passing templates... ]
   }
   ```

2. Write a summary report: `quest_batch_report.md`
   Include:
   - Total templates generated vs. total passed validation
   - Per-situation breakdown (count, objective type distribution, skill check coverage)
   - Per-agent pass/fail counts
   - Any failing templates with their specific violations
   - Any situations with fewer than 3 passing templates (these need reruns)

3. If any situations have fewer than 3 passing templates, note which situations need a follow-up run and what the common failure mode was.

### Notes for the orchestrator

- If a sub-agent returns commentary mixed with JSON, extract the JSON array and discard the commentary.
- If a sub-agent produces more or fewer than the requested template count, note the discrepancy but validate what was produced.
- If validation failures are concentrated in variable scoping, the sub-agent likely didn't internalize the binding table. Re-running that agent with the binding table emphasized in the task instruction may help.
- The initial batch target is 66 templates. Expect ~10-15% failure rate on first pass. A follow-up run for failed templates is normal.
