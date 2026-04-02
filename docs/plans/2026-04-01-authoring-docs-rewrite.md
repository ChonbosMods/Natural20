# Authoring Documentation Rewrite Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace all stale authoring documentation with accurate v2-system docs, consolidate into `docs/authoring/`, and slim in-tree AUTHORING_RULES.md files to pure checklists.

**Architecture:** 8 documentation files (6 in `docs/authoring/`, 2 in-tree checklists), plus cleanup of 4 old files. Mechanical reference docs (pools, subjects, templates, quest-content) describe schemas and rules. Voice guide docs (rumor-pools, smalltalk-pools, detail-responses) describe tone and writing craft. In-tree checklists are compact pointers.

**Key constraint:** All content must describe the **v2 coherent triplet system** (intro + details[] + reactions[] + optional statCheck). The old L0/L1/L2 layer system, perspectives/intents system, and per-category pool files (creature_sightings, weather_observations, etc.) no longer exist and must not appear anywhere.

---

## Context: The V2 System (verified from Java source)

### Pool Entry Schema
```json
{
  "entries": [
    {
      "id": 0,
      "intro": "Something's been leaving tracks near {subject_focus_the}...",
      "details": [
        "Detail 1 continuing the intro.",
        "Detail 2 continuing the intro.",
        "Detail 3 continuing the intro."
      ],
      "reactions": [
        "Reaction 1 closing the emotional arc.",
        "Reaction 2 closing the emotional arc."
      ],
      "statCheck": {
        "pass": "NPC reveals hidden info on successful skill check.",
        "fail": "NPC deflects on failed skill check."
      }
    }
  ]
}
```

### Valid Binding Variables (23 total)
| Variable | Source | Example |
|----------|--------|---------|
| `{subject_focus}` | Subject value | "old watchtower" |
| `{subject_focus_bare}` | Without leading "the" | "old watchtower" |
| `{subject_focus_the}` | With article (mid-sentence) | "the old watchtower" / "Thornfield" |
| `{subject_focus_The}` | With article (sentence-start) | "The old watchtower" / "Thornfield" |
| `{subject_focus_is}` | Conjugated be | "is" / "are" |
| `{subject_focus_has}` | Conjugated have | "has" / "have" |
| `{subject_focus_was}` | Conjugated past be | "was" / "were" |
| `{npc_name}` | Other NPC's generated name | "Aldric" |
| `{time_ref}` | Drop-in pool | "not long ago" |
| `{direction}` | Drop-in pool | "past the ridge" |
| `{tone_opener}` | Disposition-keyed pool | "Between you and me." |
| `{tone_closer}` | Disposition-keyed pool | "Take that as you will." |
| `{entry_intro}` | Pre-resolved intro text | (system-generated) |
| `{entry_reaction}` | Pre-resolved first reaction | (system-generated) |

Quest-only variables: `{quest_threat}`, `{quest_stakes}`, `{quest_focus}` (each with `_the/_The/_is/_has/_was` variants), `{quest_objective_summary}`, `{quest_plot_step}`, `{quest_outro}`, `{quest_exposition}`, `{quest_detail}`, `{quest_accept_response}`, `{enemy_type}`, `{enemy_type_plural}`, `{quest_item}`, `{fetch_variant}`.

### Template Structure (templates.json)
16 templates, each with: `id`, `label`, `requiresConcrete`, `subjectRequired`, `skills[]`, `reactionIntensity` ("intense"/"mild"), `intro` pattern, `detailPrompts[]`, `reactionPrompts[]`.

Template IDs: danger, sighting, treasure, corruption, conflict, disappearance, migration, omen, weather, trade, craftsmanship, community, nature, nostalgia, curiosity, festival.

### Prompt Groups (prompt_groups.json)
6 groups: `ask_more`, `press_details`, `press_source`, `ask_context`, `ask_opinion`, `ask_feeling`.

### Generation Pipeline Constants (TopicGraphBuilder)
- DETAIL_INCLUDE_CHANCE: 0.70 (70% per detail)
- REACTION_FOLLOWUP_CHANCE: 0.30 (30% reaction after detail)
- STAT_CHECK_CHANCE: 0.60 (60% stat check appears)
- MAX_DETAILS: 2 (hard cap shown)
- STAT_CHECK_DC range: 8-16
- STAT_CHECK_PASS_DISPOSITION: +5
- STAT_CHECK_FAIL_DISPOSITION: -3

### Files Loaded at Runtime
**Coherent pools:** `topics/pools/v2/{templateId}.json` (16 files)
**Support pools:** `topics/pools/{subject_focuses,greeting_lines,perspective_details,time_refs,directions,tone_openers,tone_closers,prompt_groups}.json`
**Quest pools:** `quests/pools/*.json` (36 files)
**Quest situations:** `quests/{SituationName}/*.json` (36 dirs x 5 files)

---

## Task 1: Write `docs/authoring/pools.md`

The main mechanical reference for authoring v2 pool entries.

**File:** `docs/authoring/pools.md`
**Replaces:** Old `docs/authoring/pools.md` (stale: describes L0/L1/L2 tiers, old pool file names, dedup windows)

### Section Structure

1. **Header**: One-line purpose. Link to voice guides for tone guidance.
2. **Pool Directory**: `src/main/resources/topics/pools/v2/` with 16 files, one per template category.
3. **Entry Schema**: JSON format with annotated example. Fields: `id` (int), `intro` (string), `details` (array of 2-3 strings), `reactions` (array of 2-3 strings), `statCheck` (optional object with `pass` and `fail` strings).
4. **Variable Reference**: Table of all valid variables with types, examples, and usage notes. Include the `_the`/`_The` suffix system, conjugation helpers (`_is`/`_has`/`_was`), and proper noun behavior.
5. **Prompt Groups Reference**: The 6 groups from `prompt_groups.json` with their entries. Explain that templates reference these via `detailPrompts` and `reactionPrompts`.
6. **Authoring Rules**: Expanded from the current 11 rules in `v2/AUTHORING_RULES.md`. Each rule gets a title, explanation, and good/bad examples:
   - No hardcoded NPC names (use role refs or `{npc_name}` for others, first person for self)
   - Details must directly continue the intro
   - Reactions close the same emotional arc
   - Variable usage (article suffixes, conjugation helpers)
   - No em dashes (use colons)
   - statCheck text is always NPC dialogue (never second-person narration)
   - statCheck pass reveals hidden information
   - statCheck fail is NPC's response to failed attempt
   - Author 2-3 details per entry (probabilistic inclusion: 70% chance, max 2 shown)
   - Write as a real NPC speaks (natural, conversational, concrete)
   - Each entry in a pool should feel distinct (different angles of the theme)
   - Fragments are biome-neutral (universal terrain only)
   - No hardcoded conjugation after variable-plurality nouns
   - Subject-referencing entries must work with proper noun POIs
7. **Generation Pipeline Summary**: Brief explanation of how entries become dialogue: TopicGenerator draws entry via PercentageDedup, TopicGraphBuilder constructs nodes with probabilistic detail/reaction/statCheck inclusion. Include the tuning constants table.
8. **Support Pool Reference**: Brief table of the 7 non-v2 pool files (tone_openers, tone_closers, time_refs, directions, greeting_lines, perspective_details, prompt_groups) with their format and purpose.

### Sources to Preserve
- All 11 rules from current `src/main/resources/topics/pools/v2/AUTHORING_RULES.md` (expand with examples)
- Rules 1-4, 10-15 from old `docs/authoring/pools.md` (fragment completeness, tone-neutrality, biome-neutrality, subject reference rules) adapted to v2 terminology
- Variable reference verified from `TopicGenerator.buildBindings()`

### Content to Remove
- All L0/L1/L2 tier references
- All old pool file names (creature_sightings, weather_observations, etc.)
- Dedup window table (implementation detail, not authoring guidance)
- Bracket-keyed pool format (not used in v2 entries)
- Drop-in pool references that don't exist (rumor_sources, rumor_details, smalltalk_openers)

**Step 1:** Write the file with all sections.
**Step 2:** Verify no stale references: grep the written file for "L0", "L1", "L2", "creature_sighting", "weather_observation", "rumor_source", "perspective", "intent". None should appear.

---

## Task 2: Write `docs/authoring/subjects.md`

Subject focuses reference, lightly updated.

**File:** `docs/authoring/subjects.md`
**Replaces:** Old `docs/authoring/subjects.md` (mostly valid but references old template IDs)

### Changes from Current
- Replace all `rumor_danger` / `smalltalk_weather` style template IDs with bare IDs: `danger`, `weather`, etc.
- Replace path refs `topics/Rumors/templates.json` / `topics/SmallTalk/templates.json` with `topics/templates.json`
- Remove "Rumor categories" / "Smalltalk categories" grouping labels. Instead use "Intense categories" / "Mild categories" (matching `reactionIntensity` field) or just list all 16 flat.
- Update "How Subjects Flow Through the System" section to reference v2 coherent pools (not perspectives/intents)
- Keep everything else: entry format, POI types, naming guidelines, distribution targets, all 10 rules

### Section Structure
1. **Header**: Purpose, file path (`src/main/resources/topics/pools/subject_focuses.json`)
2. **Entry Format**: JSON schema with annotated example (same as current, verified correct)
3. **POI Types**: Table (same as current)
4. **Categories**: All 16 in a single flat table with descriptions. Group visually as "Intense" and "Mild" with a note about what that means (maps to template `reactionIntensity`).
5. **Rules**: All 10 rules (same as current, updated references)
6. **How Subjects Flow**: Updated pipeline description (draw → collision check → template matching → variable substitution → quest attachment)

**Step 1:** Write the file.
**Step 2:** Verify no stale references: grep for "rumor_", "smalltalk_", "Rumors/", "SmallTalk/".

---

## Task 3: Write `docs/authoring/templates.md`

Template system reference, fully rewritten.

**File:** `docs/authoring/templates.md`
**Replaces:** Old `docs/authoring/templates.md` (stale: describes perspectives/intents/archetypes system)

### Section Structure

1. **Header**: Purpose, file path (`src/main/resources/topics/templates.json`)
2. **Template Fields**: Table of all fields with types and descriptions:
   - `id` (string): matches pool file name and subject category
   - `label` (string): topic button text, supports `{subject_focus_The}` etc.
   - `requiresConcrete` (bool): filters out abstract subjects
   - `subjectRequired` (bool, default true): whether a subject is needed
   - `skills` (string[]): skill names for stat checks (PERCEPTION, INSIGHT, etc.)
   - `reactionIntensity` ("intense" | "mild"): affects tone framing
   - `intro` (string): pattern for intro text (default: `{tone_opener}{entry_intro}{tone_closer}`)
   - `detailPrompts` (string[]): prompt group names for detail branches
   - `reactionPrompts` (string[]): prompt group names for reaction branches
3. **The 16 Templates**: Table showing each template's id, label, requiresConcrete, skills, reactionIntensity, detailPrompts, reactionPrompts. (Copy from current templates.json, formatted as reference table.)
4. **Template-to-Pool Mapping**: Each template ID maps to `topics/pools/v2/{id}.json`. One pool file per template. No cross-cutting pools at the entry level.
5. **How Templates Drive Dialogue Construction**: Brief pipeline:
   - Template selected via subject category matching
   - One PoolEntry drawn from the matching v2 pool
   - TopicGraphBuilder creates: entry node (intro) → detail branches (from entry.details[]) → reaction branches (from entry.reactions[]) → optional stat check (from entry.statCheck)
   - Detail prompts drawn from template's `detailPrompts` groups
   - Reaction prompts drawn from template's `reactionPrompts` groups
   - Probabilistic inclusion constants (table from Context section above)
6. **Adding a New Template**: Steps required (add to templates.json, create matching v2 pool file, ensure subjects exist with matching category)

### Content to Remove (from old version)
- Perspectives and archetypes (Report/Confession/Warning): gone
- Intent system (detail/context/source/stakes/opinion/reaction): replaced by prompt groups
- L0/L1/L2 pool wiring table: replaced by 1:1 template-to-pool mapping
- Quest hook perspectives: quest topics are now built by TopicGraphBuilder from quest bindings, not from template perspectives
- `{L0_fragment}`, `{personal_reaction}`, `{local_opinion}` variables: replaced by `{entry_intro}`, `{entry_reaction}`

**Step 1:** Write the file.
**Step 2:** Verify no stale references: grep for "perspective", "intent", "archetype", "L0", "L1", "L2", "deepener".

---

## Task 4: Write `docs/authoring/rumor-pools.md`

Voice guidance for intense categories.

**File:** `docs/authoring/rumor-pools.md`
**Replaces:** Old `docs/authoring-rumor-pools.md` (stale: references L0 fragments, old pool names)

### Section Structure

1. **Header**: Purpose. This is a voice guide for writing v2 pool entries in the 10 intense categories. For JSON format and rules, see `pools.md`.
2. **Intense Categories**: List the 10: danger, sighting, treasure, corruption, conflict, disappearance, migration, omen, nature, curiosity. One-line description of each.
3. **Rumors vs Mild Topics**: Preserved from old doc. Rumors carry weight: something happened or might change. Mild topics are everyday. The distinction is stakes, not formality.
4. **The Gossip Web**: Preserved from old doc. Write 2-3 entries per pool that could be about the same subject from different angles. Different NPCs get different entries, creating a fragmentary picture. Adapt examples to v2 entry format (show a complete entry with intro + details + reactions, not an L0 fragment).
5. **The Rumor Formula**: Preserved. [event/problem] + [location/person] + [emotional reaction]. Adapt examples to show how this maps to v2 fields: event goes in intro, location/person elaborated in details, emotional reaction in reactions.
6. **Hearsay Layering**: Preserved. Frame information as secondhand. Source attribution patterns. Certainty gradient (high/medium/low/deflection). Show how hearsay works in intro text and how certainty varies across details within the same entry.
7. **Category-Specific Tips**: Adapted from old doc's template-specific guidance:
   - danger/sighting/migration: sensory details, avoid naming creatures definitively, uncertainty > certainty
   - disappearance/corruption/omen: grounded observations, time-anchor, don't explain the cause
   - treasure: frame as discovery, include reason it hasn't been claimed
   - conflict: name sides generically, show escalation, NPC has a side
   - nature/curiosity: natural phenomena, odd but (nature) threatening or (curiosity) merely strange
8. **Entry Quality Test**: Adapted from old doc's test. Before adding an entry: Is it secondhand? Does it name a subject? Does the NPC have a reaction? Does the intro imply something changed?

### Voice Guidance to Preserve
- Gossip web structure and examples
- Rumor formula ([event] + [location] + [opinion])
- Hearsay layering (source attribution patterns)
- Certainty gradient table
- Category-specific writing tips
- Quality test checklist

### Content to Remove
- L0 fragment references
- `creature_sightings`, `strange_events`, `treasure_rumors`, `conflict_rumors` pool names
- `{creature_sighting}`, `{strange_event}` variable names
- References to separate rumor_sources pool
- "Pool targets:" annotations per category

**Step 1:** Write the file.
**Step 2:** Verify no stale references: grep for "L0", "L1", "creature_sighting", "strange_event", "rumor_source", "Pool targets".

---

## Task 5: Write `docs/authoring/smalltalk-pools.md`

Voice guidance for mild categories.

**File:** `docs/authoring/smalltalk-pools.md`
**Replaces:** Old `docs/authoring-smalltalk-pools.md` (stale: references old pool names, L0 concept)

### Section Structure

1. **Header**: Purpose. Voice guide for the 6 mild categories. For JSON format, see `pools.md`.
2. **Mild Categories**: List the 6: weather, trade, craftsmanship, community, nostalgia, festival. One-line description of each.
3. **Self-Contained Entries**: Preserved core principle. Each entry (intro + details + reactions) must work as a standalone unit. The intro works regardless of what the player asks next. Details elaborate without contradicting. Reactions close naturally. Adapted from old "Self-Contained Observations" with v2 framing.
4. **Category Archetypes**: Preserved from old doc, mapped to the 6 mild categories:
   - weather: natural phenomena, seasonal observations, practical weather impact
   - trade: commerce, goods, routes, prices, merchant opinions
   - craftsmanship: materials, technique, workshop life, craft pride
   - community: village life, governance, social dynamics, local disputes
   - nostalgia: the past, memory, tradition, how things used to be
   - festival: celebrations, gatherings, ceremonies, preparation, aftermath
5. **Archetype Color**: Preserved. Write entries that sound like different types of people (guard, merchant, craftsperson, scholar, elder, gossip, laborer). Variety emerges naturally. Include the archetype flavoring table.
6. **The Modular Formula**: Preserved. "I [verb] a [local thing] [timeframe]. [One-sentence opinion]." Show how this maps to v2: the formula generates the intro, then write 2-3 details that elaborate on the observation, and 2 reactions that give the NPC's take. Include adapted examples.
7. **The Opinion Pattern**: Preserved. For every factual intro, consider adding opinion in the reactions. Fact version vs opinion version table. The trailing opinion suffix technique.
8. **Entry Quality Test**: Adapted. Delete everything before the intro: does it stand alone? Imagine three player follow-ups (the details): do they elaborate naturally? Read it out loud: fence-leaning test. Check category spread.

### Voice Guidance to Preserve
- Self-contained observation principle
- Category archetype descriptions and examples
- Archetype color table (guard, merchant, etc.)
- Modular formula ("I [verb] a [local thing] [timeframe]")
- Opinion-as-smalltalk pattern
- Quality test checklist

### Content to Remove
- L0 fragment concept
- Old pool names (creature_sightings, nature_observations, etc.)
- "Pool targets:" annotations
- References to separate detail/opinion pools

**Step 1:** Write the file.
**Step 2:** Verify no stale references: grep for "L0", "L1", "creature_sighting", "weather_observation", "community_observation", "Pool targets".

---

## Task 6: Write `docs/authoring/detail-responses.md`

Voice guidance for writing details[], reactions[], and statCheck.

**File:** `docs/authoring/detail-responses.md`
**Replaces:** Old `docs/authoring-detail-responses.md` (stale: references L1/L2 layers, old pool names)

### Section Structure

1. **Header**: Purpose. Voice guide for the three content arrays within a v2 pool entry. For JSON format, see `pools.md`.
2. **The Three Fields**: Brief explanation of how each field is used in the dialogue graph:
   - `details[]`: follow-up branches when player asks to hear more. 70% inclusion chance per detail, max 2 shown. Player sees a prompt from the template's `detailPrompts` groups.
   - `reactions[]`: follow-up branches when player asks the NPC's opinion/feeling. Drawn from template's `reactionPrompts` groups.
   - `statCheck`: optional skill check branch. 60% chance to appear. Pass reveals hidden info, fail deflects.
3. **Writing Details: The Encyclopedic Voice**: Preserved from old doc. Details are where NPCs deliver actual information. Knowledgeable, confident, but filtered through NPC perspective. Write the base answer, then add bias. When mentioning third parties, take a side. Include the "Let me tell you about X" template adapted to v2. Include the voice archetype table (knowledgeable local, practical laborer, suspicious gossip, weary authority).
4. **Details Must Continue the Intro**: Rule from v2 AUTHORING_RULES. If the intro mentions strange tracks, all details must be about those tracks. Show good/bad examples with complete entries.
5. **The Advice / Secret Pattern**: Preserved. Some details are public knowledge (advice), others are private (secrets). Advice is freely given. Secrets cost the NPC something. Different DCs gate different tiers. Show how this maps to details[] ordering (earlier details are advice-tier, later details lean toward secrets) and statCheck (pass = the real secret).
6. **Writing Reactions: Emotional Closure**: Reactions close the same emotional arc as the intro. If the intro is frightening, reactions express fear, resolve, or unease about that specific thing. Show good/bad examples.
7. **Reaction Patterns**:
   - Community-level reactions: "Most folk think...", "People are saying..."
   - Personal reactions: "Honestly? It rattled me."
   - Danger assessments (intense categories only): "If this is what I think it is..."
8. **Writing statCheck Pass/Fail**: Preserved from old doc.
   - Pass: earned trust. Player demonstrated competence. NPC opens up with specific, useful info that wasn't available through normal conversation. Include skill-specific examples (PERCEPTION, INSIGHT).
   - Fail: not punishment. NPC deflects naturally: misunderstand, vague non-answer, change subject. Player should feel they missed something, not insulted.
   - Both pass and fail are NPC dialogue, never second-person narration.
9. **NPC Self-Description**: Preserved. Frame as confessions, not resumes. Reveal limitation, preference, or complaint. Good/bad examples.
10. **Quality Test**: Adapted. For details: does the NPC have a perspective? Is there a concrete detail? Does it continue the intro? For reactions: does it close the emotional arc? For statCheck: does pass reveal something new? Does fail feel natural, not punishing?

### Voice Guidance to Preserve
- Encyclopedic voice concept and examples
- Bias patterns ("write the base answer, then add bias")
- Third-party opinion technique
- Advice/secret tier pattern
- Stat check pass = earned trust, fail = not punishment
- Skill-specific pass/fail examples
- NPC self-description as confessions
- Quality test checklists

### Content to Remove
- L1/L2 terminology
- Old pool names (local_opinions, personal_reactions, danger_assessments as separate files)
- References to "intent" system
- "deepenerResponse" concept
- Table of L0/L1/L2 layers with triggered-by/length/purpose

**Step 1:** Write the file.
**Step 2:** Verify no stale references: grep for "L1", "L2", "local_opinion", "personal_reaction", "danger_assessment", "deepener", "intent".

---

## Task 7: Write `docs/authoring/quest-content.md`

Quest template and pool authoring reference, moved from in-tree.

**File:** `docs/authoring/quest-content.md`
**Source:** Current `src/main/resources/quests/AUTHORING_RULES.md` (490 lines, current and accurate)

### Approach
This is primarily a **move**, not a rewrite. The quest AUTHORING_RULES.md is current and accurate. Copy the full content with these adjustments:

1. Add header linking back to the in-tree checklist: "Quick checklist: `src/main/resources/quests/AUTHORING_RULES.md`"
2. Add cross-reference to `pools.md` for the `_the`/`_The` suffix system (shared concept)
3. Keep everything else: system overview, file structure, conversation flows, variant schema, variable reference, authoring rules, objective types, pool value authoring, response pool structure, tone reference, checklists
4. Remove the redundant `_the` suffix explanation if it's already fully covered in `pools.md` (replace with a cross-reference to avoid duplication). Actually, keep it: quest variables have their own set (`quest_focus_the`, `quest_stakes_the`, `quest_threat_the`) and the quest doc should be self-contained for quest authors.

**Step 1:** Write the file (copy from current quest AUTHORING_RULES.md with header additions).
**Step 2:** Verify content matches the current source.

---

## Task 8: Slim `src/main/resources/topics/pools/v2/AUTHORING_RULES.md`

Replace with compact checklist pointing to expanded docs.

**File:** `src/main/resources/topics/pools/v2/AUTHORING_RULES.md`

### Target Content (~30 lines)

```markdown
# Pool Entry Checklist

For expanded guidance, variable reference, and voice direction, see `docs/authoring/`.
Schema and rules: `docs/authoring/pools.md`
Voice (intense): `docs/authoring/rumor-pools.md`
Voice (mild): `docs/authoring/smalltalk-pools.md`
Writing details/reactions/statCheck: `docs/authoring/detail-responses.md`

## Entry Format

Each entry: `id` (int), `intro` (string), `details` (2-3 strings), `reactions` (2-3 strings), optional `statCheck` ({ pass, fail }).

## Rules

1. No hardcoded NPC names. Use role references or `{npc_name}` for other NPCs. First person for self.
2. Details must directly continue the intro.
3. Reactions close the same emotional arc as the intro.
4. Use `{subject_focus_the}` (mid-sentence) and `{subject_focus_The}` (sentence-start). Never manual "the {subject_focus}".
5. Use conjugation helpers (`{subject_focus_is}`, `_has`, `_was`) when subject is sentence subject.
6. No em dashes. Use colons.
7. statCheck pass/fail text is NPC dialogue, never second-person narration.
8. statCheck pass reveals hidden information. statCheck fail deflects naturally.
9. Author 2-3 details per entry. System shows 0-2 (70% chance each, max 2).
10. Write natural, conversational NPC speech. Short sentences. Concrete observations.
11. Each entry in a pool should feel distinct: different angles of the theme.
12. Biome-neutral: universal terrain features only.
13. `{npc_name}` is for mentioning OTHER NPCs, never self-reference.
```

**Step 1:** Overwrite the file with the compact checklist.
**Step 2:** Verify it fits on one screen (~30 lines).

---

## Task 9: Slim `src/main/resources/quests/AUTHORING_RULES.md`

Replace with compact checklist pointing to expanded docs.

**File:** `src/main/resources/quests/AUTHORING_RULES.md`

### Target Content (~50 lines)

```markdown
# Quest Content Checklist

For expanded guidance, see `docs/authoring/quest-content.md`.

## Variant Files

Each situation directory has: `exposition_variants.json`, `conflict_variants.json`, `resolution_variants.json`, `references.json`, `npc_weights.json`.

## Rules

1. Use `_the`/`_The` suffixes for `{quest_focus}`, `{quest_stakes}`, `{quest_threat}`. Never manual "the".
2. Use conjugation helpers (`_is`/`_has`/`_was`) when pool variable is sentence subject.
3. Respect semantic roles: focus = place/thing, stakes = at-risk, threat = danger, action = task.
4. Optional variables (`{quest_origin}`, `{quest_time_pressure}`, etc.) in removable clauses only.
5. Situation = tone, not content. Each variant = different phrasing, not different story.
6. No hardcoded names. Use `{quest_giver_name}`, `{target_npc}`, `{quest_ally}`.
7. Resolution variants: `objectivePool` and `objectiveConfig` MUST be empty.
8. No em dashes. Use colons.

## Response Pool Rules

1. Use `{quest_giver_name}` (never "quest giver" or "your contact").
2. Use `_the`/`_The` suffixes (never manual "the").
3. Self-contained statements (no unanswered questions).
4. Match situation/tone emotional register.
5. Target NPC handoff entries end with a clear sub-objective.
6. Target NPC info entries direct player to return to `{quest_giver_name}`.
7. 3-5 entries per situation key, 5-8 per tone key.

## Checklist

- [ ] No `bindings` or `playerResponses` in variants
- [ ] All pool-variable references use `_the`/`_The` suffixes
- [ ] Optional variables in removable clauses
- [ ] Resolution variants have empty objectivePool/objectiveConfig
- [ ] Tested with proper noun substitution set
- [ ] Valid JSON
```

**Step 1:** Overwrite the file with the compact checklist.
**Step 2:** Verify it's concise and complete.

---

## Task 10: Cleanup

Delete old files and strip stale content.

### Files to Delete
- `docs/authoring-rumor-pools.md` (replaced by `docs/authoring/rumor-pools.md`)
- `docs/authoring-smalltalk-pools.md` (replaced by `docs/authoring/smalltalk-pools.md`)
- `docs/authoring-detail-responses.md` (replaced by `docs/authoring/detail-responses.md`)

### Files to Update
- `docs/dialogue-authoring-guide.md`: Delete the "Procedural Topic System" section (starts at ~line 657 with "## Procedural Topic System (SmallTalk & Rumors)") through the end of the file. Replace with a short pointer:
  ```
  ## Procedural Topic System

  The procedural topic system generates NPC conversation topics from pool-based templates. For authoring guidance, see `docs/authoring/`.
  ```

**Step 1:** Delete the 3 old files.
**Step 2:** Edit `docs/dialogue-authoring-guide.md` to replace stale section with pointer.
**Step 3:** Verify no broken cross-references in remaining docs.

---

## Execution Order

Tasks 1-7 are independent and can be parallelized.
Task 8 depends on Task 1 (needs to reference the expanded doc).
Task 9 depends on Task 7 (needs to reference the expanded doc).
Task 10 depends on all others (cleanup after new docs exist).

## Commit Strategy

One commit after all files are written and verified:
```
docs: rewrite authoring guides for v2 coherent triplet system

Consolidates 6 stale docs into docs/authoring/ with accurate v2 system
references. Slims in-tree AUTHORING_RULES.md to checklists with pointers.
Strips stale Procedural Topic System section from dialogue-authoring-guide.
```
