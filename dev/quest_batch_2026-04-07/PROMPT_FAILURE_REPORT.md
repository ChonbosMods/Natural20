# Quest v2 Sub-Agent Prompt Failure Report

**Date:** 2026-04-07
**Project:** Natural 20 (Hytale plugin)
**Author of report:** Orchestrator (Claude) — handing off to a third party for prompt redesign.
**Status:** First batch of 66 quest templates generated. Halted before validation due to systemic entity-grounding failures across multiple agents.

---

## 1. What you need to read first

You will only be able to write a corrective prompt if you understand the authoring system. Read these files before proposing anything:

1. `authoring/quest_v2/quest_authoring_rules.md` — the canonical hard rules. R2, R3, R5, R6 are the ones most violated.
2. `authoring/quest_v2/quest_text_field_definitions.md` — structural role of each text field.
3. `authoring/quest_v2/quest_template_schema.json` — the JSON contract. Note `_variable_reference._example_template`.
4. `authoring/quest_v2/quest_variable_palette.md` — variable scoping; this is the part the agents handled CORRECTLY.
5. `authoring/quest_v2/quest_situations.md` — situation index with available objective types.
6. `authoring/quest_v2/quest_sub_agent_prompt.md` — the **canonical** sub-agent prompt this batch was based on. Note the order of sections: voice → constraints → variable scoping → voice → skill checks. Entity grounding gets a single short paragraph buried mid-prompt.
7. Any of the 22 situation files (`authoring/quest_v2/01_supplication.md` ... `22_obstacles_to_love.md`) for tone guidance.

Also relevant:
- `authoring/quest_v2/quest_validation_checklist.md` — the human-review checklist that should have caught these failures earlier.

The actual sub-agent prompts I dispatched are reconstructable from `dev/quest_batch_2026-04-07/AGENTS_4_AND_5_NOTE.md` plus the structure inside `quest_sub_agent_prompt.md`. I added per-agent overrides for situation assignments and a "CRITICAL VARIABLE BINDING TABLE" section near the top, but I did not restructure the entity grounding section. That was the mistake.

---

## 2. The failure

Six sub-agents were dispatched in parallel to generate 66 quest templates (3 per situation × 22 situations) for the Natural 20 quest v2 system. All agents returned syntactically valid JSON. Variable scoping (the per-objective binding rules for `{kill_count}`, `{quest_item}`, `{target_npc}`, etc.) was handled near-perfectly across all 6 agents. **No** templates used forbidden smalltalk variables like `{mob_type}` or `{npc_name}`. No templates referenced `{target_npc}` without a TALK_TO_NPC objective.

But a large fraction of templates — estimated 15–25 out of 66 — violate **entity grounding rule R2** ("no invented locations"), R3 ("no spatial modification of POI types"), and the closely related R6 ("invented events must stay within objective scope"). The user caught the first instance and stopped the batch before validation completed.

---

## 3. The critical distinction the prompt failed to teach

This is the key insight that needs to land in the corrective prompt. **It is more nuanced than "no spatial nouns."**

> A spatial reference is **acceptable** if the player never has to reach the place to complete an objective. It is **not acceptable** if the place becomes a quest target.

### Acceptable (descriptive context)

> "I ran out of supplies at my workshop and can't make my deliveries this week. I need {gather_count} {quest_item} so I can finish the order."

The workshop is mentioned as scene-setting for *why* the NPC needs help. The player goes nowhere near a workshop. They gather a resource somewhere in the world; the system picks the location. The workshop is the NPC's frame, not a destination. **Allowed.**

### Not acceptable (targetable invented location)

> "{enemy_type_plural} took my workshop. I need {kill_count} of them cleared so I can sit with my brother one last time."

Now the workshop is the **place the player must go** to kill the {enemy_type_plural}. The system does not generate workshops as POIs. There is no "workshop" coordinate the spawner can resolve. The promise the text makes is undeliverable: the player will get a KILL_MOBS objective whose enemies spawn somewhere unrelated to any "workshop." **Not allowed.**

### The litmus test

For every spatial noun in a quest text field, ask:

> "Does the player need to reach this place to complete an objective in the chain?"

- **No** → the spatial reference is descriptive context; it can stay (within the broader voice/grounding rules).
- **Yes** → the spatial reference must resolve to: a real POI type the game generates, or a settlement variable (`{settlement_name}`, `{target_npc_settlement}`, `{other_settlement}`), or be removed.

This litmus test is what the canonical `quest_authoring_rules.md` R2 actually means. The current prompt encodes it as "no invented locations," which agents interpret too narrowly (they think it only means no proper nouns) and too broadly at the same time (they're not sure if "the area" is OK either).

---

## 4. Concrete failures from the batch

Each example below is a real text snippet from a returned template. The forbidden phrase is in bold, and the reason it fails is the litmus test from §3.

### From `necessity_of_sacrificing_loved_ones_01`

> "My brother won't leave **the old homestead**. He's sick, and moving him would kill him faster than staying. The {enemy_type_plural} that took the place have been growing bolder, and I need {kill_count} of them cleared so I can sit with him one last time."

The "old homestead" is the implicit spawn location for the KILL_MOBS objective. There is no homestead POI. The player will be sent to kill mobs somewhere the dialogue does not match. **Targetable invented location.**

### From `necessity_of_sacrificing_loved_ones_03`

> "I need a {quest_item} from **the old kennel** to put him down gently."

The {quest_item} is going to be FETCH_ITEM-resolved by the system. The "old kennel" is presented as the place the item is. There is no kennel POI. **Targetable invented location.**

### From `loss_of_loved_ones_01`

> "She always kept a {quest_item} at **the little shrine we built out past the pasture**, and in the weeks after she died I couldn't bring myself to visit it."

This is the worst kind: invents both a structure (shrine) and surrounding terrain (pasture). The player must reach the {quest_item}, which the system places generically. **Targetable invented location.**

### From `loss_of_loved_ones_02`

> "My son was killed last autumn by {enemy_type_plural} on **the north trail**. ... Clear {kill_count} of them from where it happened."

"The north trail" is referenced as the spawn location for the KILL_MOBS objective ("from where it happened"). The game does not generate named trails. **Targetable invented location.**

### From `loss_of_loved_ones_03`

> "There's a {quest_item} in **the old hunting camp east of {settlement_name}**, and I think it's still where she left it. ... A {quest_item} she kept on a shelf in **the roadhouse where she worked** before she met my father."

Both invented. Both function as fetch-item destinations. **Targetable invented locations.**

### From `conflict_with_fate_02`

> "There's one more thing. The old diviner's {quest_item} is somewhere in **the rafters of the meeting hall**, left there by a traveler years ago."

POI interior detail, AND the meeting hall isn't a generated POI to begin with. Doubly bad. **Targetable invented location + R3 violation.**

### From `self_sacrifice_for_an_ideal_01`

> "I've been working to rebuild **the outer wall around {settlement_name}** stone by stone."

This is borderline because the player isn't necessarily sent TO the wall — they're sent to gather resources. But the entire framing centers on a structure ("outer wall") that may or may not exist as a generated feature. If settlements have walls in the worldgen, this is fine. If they don't, the dialogue promises something the world won't show. The current authoring rules say specific spatial features must be POI types. Walls are not POIs. **Probable violation.**

### From `enigma_02` (a borderline case worth resolving in the prompt)

> "Bring me {gather_count} {quest_item} from around **the perimeter of {settlement_name}**."

"The perimeter of {settlement_name}" sounds spatial, but it's effectively just "around the settlement" — which is where COLLECT_RESOURCES will spawn anyway. This may be acceptable, or it may not. The corrective prompt needs to resolve cases like this explicitly.

---

## 5. Structural issues with the original prompt

The canonical `quest_sub_agent_prompt.md` and the per-agent overrides I dispatched share these structural problems:

### 5.1 Wrong section ordering

The prompt opens with voice principles, then walks into "Critical Constraints" with subsections in this order:

1. Entity Grounding (one paragraph)
2. Variable Scoping (long, detailed, with examples)
3. Voice (long)
4. Skill Checks (long)

Variable scoping gets five times the wordcount of entity grounding. Agents internalize what they read in volume. They handled variable scoping perfectly because it had concrete examples of right and wrong; they handled entity grounding poorly because it had only abstract prohibitions.

### 5.2 The entity grounding section lacks the litmus test

The entity grounding subsection of the canonical prompt says:

> "Every promise must map to an available objective."

That sentence is correct but indirect. It does not tell the agent **how** to check. The litmus test from §3 of this report ("does the player have to reach this place?") is the operationalizable form, and it is missing.

### 5.3 Examples are absent for grounding

Variable scoping has examples like "Using {kill_count} in conflict1Text is only correct if objectives[1] is KILL_MOBS." Entity grounding has no parallel: no "this is OK / this is not OK" pair like the workshop example in §3 of this report.

### 5.4 The anti-pattern catalog at the bottom of `quest_authoring_rules.md` is not surfaced in the prompt

`quest_authoring_rules.md` has a 14-row anti-pattern table. The sub-agent prompt does not include it inline. Agents may or may not have read the linked file in full. They should not need to — the most important anti-patterns should be in the prompt itself.

### 5.5 The validation checklist exists but the prompt doesn't make agents run it

`quest_validation_checklist.md` is intended for human spot-checks. The agents were never asked to self-check against it before output. A "mandatory pre-output self-check" section would catch many of these violations at generation time, before they reach the orchestrator.

### 5.6 No safe-vocabulary list

Agents had no positive list of "things you may say spatially." They had only the negative list (no invented locations, no POI interiors). When forced to write evocative quest dialogue without clear positive guidance, they reach for vivid spatial detail because that is what good fiction looks like to a language model. The prompt needs to actively offer the safe vocabulary so agents have somewhere to go.

### 5.7 The example template in the schema reinforces bad patterns

`quest_template_schema.json._example_template` is the supplication_01 example. It is grounded correctly — but it is the **only** example. There is no second example demonstrating, for instance, an Obtaining quest or a Loss of Loved Ones quest. Agents pattern-match on examples; one example is too few to disambiguate the rule.

### 5.8 Per-objective variables drove a false confidence

Because I gave agents a long, prescriptive table of per-objective variables and forbidden variables in the per-agent prompts, agents treated variable scoping as the *primary* discipline. Once they passed that bar internally, they relaxed on everything else. The corrective prompt needs to communicate that entity grounding is **at least as important** as variable scoping, possibly more.

---

## 6. What a corrective prompt needs to deliver

This is the spec for the third party. Do not write the prompt yet — these are requirements.

### Must include

1. **The litmus test** from §3, verbatim or close to it, in the FIRST third of the prompt.
2. **A right/wrong example pair** showing the workshop case (descriptive ✓ vs targetable ✗).
3. **A short safe-vocabulary list**, derived from what the game actually generates. This list must be reviewed by the user before the prompt ships, but candidates include:
   - Always safe: `{settlement_name}`, `{target_npc_settlement}`, `{other_settlement}`, "the area", "around here", "out there", "lately", "for weeks now", "the road" (generic), "the woods" (generic terrain).
   - Probably safe (needs user confirmation): "my home", "my house", trade-implied first-person spaces ("my forge" for a blacksmith) — **only when used descriptively, never as objective targets**.
   - Probably NOT safe: any specific named structure (homestead, kennel, shrine, hall), any directional landmark (north trail, eastern ridge), any POI interior (tunnel, room, wing, shaft).
4. **An explicit per-template self-check checklist** that the agent runs internally before producing each template. The checklist must include the litmus test as a step. Agents that skip this step in their reasoning trace should be considered failing.
5. **The 14-row anti-pattern table from `quest_authoring_rules.md`** inlined into the prompt, not linked.
6. **A second worked example template** in the prompt body. The first is in the schema (supplication_01); the second should be a different situation, ideally Loss of Loved Ones or Self-Sacrifice for Kindred, since those are the situations that failed hardest in this batch. The user should write the second example, not the third party — it has to reflect the user's actual taste.
7. **Voice fidelity preserved.** The canonical prompt's voice guidance is good and the agents handled voice well. Don't over-constrain it. The fix is grounding, not tone.

### Must NOT do

1. **Do not ban all spatial nouns.** That kills dramatic specificity and produces generic-sounding quest text. The litmus test, not a flat ban, is the rule.
2. **Do not bloat the variable scoping section.** It's already working. Leave it alone or shorten it.
3. **Do not add new soft constraints** ("try to vary your sentence structure", "consider the NPC's personality"). Those are already in the canonical prompt and aren't where the failure is.

### Open questions for the third party to surface

Surface these explicitly in your prompt design and flag them for the user to resolve:

1. **Speaker-owned spaces.** Is "my workshop" allowed in descriptive context, or is even descriptive use forbidden? (My read of the rules: descriptive use is allowed per §3 litmus test, but the user has not explicitly confirmed this.)
2. **Family references.** Is "my brother" / "my wife" / "my son" allowed when they are not quest objective targets? (They appear in `loss_of_loved_ones_01`, `necessity_of_sacrificing_loved_ones_01`, etc. They feel right but they are not in any variable, so R5 is technically a question.)
3. **Settlement-implicit features.** "The gate", "the wall", "the well" — these are visually present in any village in the game, but they are not separately addressable POIs. Are they safe?
4. **"The road" / "the trail."** "The road" feels generic enough to be safe. "The north trail" is clearly not. Where is the line?
5. **Interior detail of speaker-owned spaces.** "My workbench", "my back room" — are these allowed under the same rule as "my workshop", or are interior subdivisions forbidden even in descriptive use?

The corrective prompt should either resolve these or contain placeholder TBDs the user fills in. Do not invent answers to the open questions.

---

## 7. What I want back from you

A revised sub-agent prompt that:

1. Replaces or augments `authoring/quest_v2/quest_sub_agent_prompt.md` (your call which).
2. Addresses every "Must include" item in §6.
3. Raises every "Open question" in §6 in a way the user can answer in one pass.
4. Is no more than 1.5x the length of the canonical prompt. Length is not the answer. Restructuring is.

Once I have your revised prompt, I will:

1. Show it to the user for review.
2. Apply the user's resolutions to the open questions.
3. Re-dispatch all 6 sub-agents with the revised prompt.
4. Validate the new batch using the formal checks in `quest_validation_checklist.md` plus the litmus test from §3.

---

## 8. Files to read in the repo

Absolute paths for direct reference:

- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_authoring_rules.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_text_field_definitions.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_template_schema.json`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_variable_palette.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_situations.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_sub_agent_prompt.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/quest_validation_checklist.md`
- `/home/keroppi/Development/Hytale/Natural20/authoring/quest_v2/01_supplication.md` (and 02–22)

The first batch's preserved JSON outputs (for diagnostic reference, not for fixing in place):

- `/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07/agent1_desperate.json`
- `/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07/agent2_proactive.json`
- `/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07/agent3_practical.json`
- `/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07/agent6_dark.json`
- (Agents 4 and 5 not persisted — see `AGENTS_4_AND_5_NOTE.md`. Re-running from scratch.)
