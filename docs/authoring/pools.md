# Pool Entry Authoring Reference (v2 Coherent Triplets)

The v2 topic system uses 16 pool files in `src/main/resources/topics/pools/v2/`, one per template category. Each pool file contains an `entries` array of coherent triplets: self-contained clusters of intro, details, reactions, and an optional stat check. This document is the complete mechanical reference for authoring new entries.


## Entry Schema

Each pool file wraps a flat array of entry objects:

```json
{
  "pool": "sighting",
  "version": 2,
  "entries": [
    {
      "id": 0,
      "intro": "Something's been leaving tracks near {subject_focus_the}...",
      "details": [
        "The tracks are too large for any animal I know. Deep prints, evenly spaced.",
        "Whatever it is, it circles the same area every night. Always the same path.",
        "I found broken branches at shoulder height. Something tall passed through."
      ],
      "reactions": [
        "I don't like it. Something that deliberate isn't just passing through.",
        "People are pretending not to notice. That worries me more than the tracks."
      ],
      "statCheck": {
        "pass": "You noticed the smaller tracks alongside? Something was following it. Keeping pace.",
        "fail": "The ground's too churned up to tell much. Rain hasn't helped."
      }
    }
  ]
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | int | yes | Unique within the pool file. Sequential starting from 0. |
| `intro` | string | yes | The NPC's opening statement on the topic. |
| `details` | string[] | yes | 2-3 follow-up lines when the player asks to hear more. |
| `reactions` | string[] | yes | 2-3 opinion/emotional response lines when the player asks how the NPC feels. |
| `statCheck` | object | no | Contains `pass` (string) and `fail` (string) for skill check outcomes. |

The `statCheck` object, when present, must contain both `pass` and `fail`. Omit the entire `statCheck` field if the entry has no skill check content.


## Pool Files

One pool file per template category. All 16 files live in `topics/pools/v2/`:

| File | Category | Deck |
|------|----------|------|
| `danger.json` | danger | rumor |
| `sighting.json` | sighting | rumor |
| `treasure.json` | treasure | rumor |
| `corruption.json` | corruption | rumor |
| `conflict.json` | conflict | rumor |
| `disappearance.json` | disappearance | rumor |
| `migration.json` | migration | rumor |
| `omen.json` | omen | rumor |
| `trade.json` | trade | smalltalk |
| `weather.json` | weather | smalltalk |
| `craftsmanship.json` | craftsmanship | smalltalk |
| `community.json` | community | smalltalk |
| `nature.json` | nature | smalltalk |
| `nostalgia.json` | nostalgia | smalltalk |
| `curiosity.json` | curiosity | smalltalk |
| `festival.json` | festival | smalltalk |


## Valid Binding Variables

Pool entries can use 23 binding variables via `{variable_name}` syntax. These are resolved at generation time by regex substitution. Missing variables become empty string.

### Subject Focus Variables (7)

| Variable | Resolves to | Example (common) | Example (proper) |
|----------|------------|-------------------|-------------------|
| `{subject_focus}` | Raw value, no article | "old watchtower" | "Thornfield" |
| `{subject_focus_bare}` | Without leading "the" | "old watchtower" | "Thornfield" |
| `{subject_focus_the}` | Mid-sentence with article | "the old watchtower" | "Thornfield" |
| `{subject_focus_The}` | Sentence-start with article | "The old watchtower" | "Thornfield" |
| `{subject_focus_is}` | Conjugated "be" | "is" | "is" |
| `{subject_focus_has}` | Conjugated "have" | "has" | "has" |
| `{subject_focus_was}` | Conjugated past "be" | "was" | "was" |

Plural subjects conjugate differently: `_is` resolves to "are", `_has` to "have", `_was` to "were".

Proper nouns: `_the` and `_The` skip the article automatically (e.g. "Thornfield", not "the Thornfield").

Subjects beginning with "the" (e.g. "the old watchtower"): `_The` capitalizes to "The old watchtower", `_the` passes through as "the old watchtower".

### Other Variables (5)

| Variable | Source | Example |
|----------|--------|---------|
| `{npc_name}` | Another NPC's generated name | "Aldric" |
| `{time_ref}` | Drop-in pool draw | "not long ago" |
| `{direction}` | Drop-in pool draw | "past the ridge" |
| `{tone_opener}` | Disposition-keyed pool | "Between you and me. " |
| `{tone_closer}` | Disposition-keyed pool | " Take that as you will." |

Notes on tone variables: `{tone_opener}` includes a trailing space when non-empty. `{tone_closer}` includes a leading space when non-empty. Both may resolve to empty string depending on framing shape roll. Authors do not use these in pool entries: they are injected by the template's intro pattern (`{tone_opener}{entry_intro}{tone_closer}`).

### System-Internal Variables (do not use in pool entries)

`{entry_intro}`, `{entry_reaction}`: resolved by the template layer, not by pool entries.

### Quest-Only Variables (not used in pool entries)

These exist only in quest-bearing topics and are populated by the quest generation pipeline:

`{quest_threat}`, `{quest_stakes}`, `{quest_focus}` (each with `_the`, `_The`, `_is`, `_has`, `_was` variants), `{quest_objective_summary}`, `{quest_plot_step}`, `{quest_outro}`, `{quest_exposition}`, `{quest_detail}`, `{quest_accept_response}`, `{enemy_type}`, `{enemy_type_plural}`, `{quest_item}`, `{fetch_variant}`.


## Prompt Groups

Templates reference prompt groups for player-facing button text. These are defined in `topics/pools/prompt_groups.json`. Each template's `detailPrompts` and `reactionPrompts` arrays list which groups to draw from.

| Group | Entries | Used for |
|-------|---------|----------|
| `ask_more` | "Tell me more.", "Go on.", "What else?", "I am listening.", "And then?" | Detail prompts |
| `press_details` | "Can you describe what you saw?", "What exactly happened?", "Walk me through it.", "Give me the details." | Detail prompts |
| `press_source` | "How did you find out?", "Who told you this?", "Where did you hear that?", "How do you know?" | Detail prompts |
| `ask_context` | "What is the situation there?", "How long has this been going on?", "When did this start?", "What changed?" | Detail prompts |
| `ask_opinion` | "What do you make of it?", "What do you think?", "How do you feel about that?", "Does that worry you?" | Reaction prompts |
| `ask_feeling` | "Are you alright?", "How are people handling it?", "Is anyone doing something about it?", "What are people saying?" | Reaction prompts |

Authors do not edit prompt groups. They are referenced here so you understand what the player sees when clicking through your content.


## Authoring Rules

### 1. No hardcoded NPC names

NPCs are spawned dynamically: any invented name will not match a real NPC in the world. Use role-based references ("the old potter", "a hunter", "the herbalist") or `{npc_name}` to mention another NPC by their generated name. The speaking NPC always uses first person ("I", "me", "my"): never use `{npc_name}` for self-reference.

- Bad: `"Old Maren told me she saw it too."`
- Good: `"{npc_name} told me she saw it too."`
- Good: `"The herbalist told me she saw it too."`

### 2. Details must directly continue the intro

If the intro mentions strange tracks, the details must be about those tracks. Never write generic details that could fit any intro. This is the core value of coherent triplets: every piece of the entry belongs to the same observation.

- Good: intro about poisoned well -> detail about water color, dead insects, timing
- Bad: intro about poisoned well -> detail about "things have been strange lately"

### 3. Reactions close the same emotional arc

If the intro is frightening, reactions should express fear, resolve, or unease about that specific thing. Not a generic "times are hard."

- Good: intro about strange lights -> reaction: "I keep my shutters closed now. Don't need to see them again."
- Bad: intro about strange lights -> reaction: "People around here don't talk about the hard things."

### 4. Use article suffixes for subject references

Use `{subject_focus_the}` for mid-sentence, `{subject_focus_The}` for sentence-start. Never manually write `"the {subject_focus}"` because proper nouns would produce "the Thornfield."

- Good: `"We must protect {subject_focus_the}."`
- Bad: `"We must protect the {subject_focus}."`
- Good: `"{subject_focus_The} {subject_focus_is} not what it used to be."`
- Bad: `"The {subject_focus} is not what it used to be."`

### 5. Use conjugation helpers when subject is sentence subject

When the subject focus is the grammatical subject of a clause, pair it with the conjugation variable. Hardcoded "is"/"has"/"was" breaks on plural subjects.

- Good: `"{subject_focus_The} {subject_focus_is} getting worse."`
- Bad: `"{subject_focus_The} is getting worse."` (produces "Strange lights is getting worse")

It is safe to hardcode conjugation when the subject focus is not the grammatical subject: `"Something is wrong with {subject_focus_the}."` works for any subject.

### 6. No em dashes

Use colons instead. This applies to all authored content: intros, details, reactions, stat check text.

- Good: `"The shape was wrong: arms too long, head too narrow."`
- Bad: `"The shape was wrong — arms too long, head too narrow."`

### 7. statCheck text is always NPC dialogue

The NPC is speaking, not a narrator. Never write second-person narration like "You look around and see nothing." Write what the NPC says: "You are looking in the right place, but there is nothing left to find." The skill check result line (e.g. `[WIS] Perception 8 : Failure`) already communicates the mechanical action.

- Good: `"You noticed the smaller tracks alongside? Something was following it."`
- Bad: `"You kneel down and examine the tracks closely."`

### 8. statCheck pass reveals hidden information

The NPC reacts to the player's demonstrated competence. Pass text should surface something the NPC couldn't or wouldn't share voluntarily: a crucial detail, a connection they hadn't made, or a secret they'll only entrust to someone who proved sharp.

- Good: `"I had the elder listen to the child's song. He went pale and said it was an old warding chant: one that hasn't been spoken in this region for generations."`

### 9. statCheck fail is the NPC's response to a failed attempt

The NPC either didn't notice the attempt, brushes it off, or redirects. Never narrate what the player does or sees.

- Good: `"In the dark, everything looks like it could be something. I have scanned that tree line a dozen times since and nothing stands out."`
- Bad: `"You squint into the fog but see nothing unusual."`

### 10. statCheck stat distribution

The skill used for each stat check comes from the template's `skills` array (see `templates.md`). Across all dialogue, the target stat distribution is: CHA ~50%, WIS ~25%, INT ~15%, STR/DEX/CON ~10% combined. When writing statCheck pass/fail text, consider what skill is likely to trigger it based on the template's skills list, and write the NPC's response to match that type of competence (social insight for CHA, observation for WIS, knowledge for INT, physical presence for STR).

### 11. Author 2-3 details and 2-3 reactions per entry

The system randomly includes each detail (70% chance per detail, hard cap of 2 shown). A topic might display 0, 1, or 2 details. Author enough that any subset works independently. There is no end-topic response: topics exhaust naturally when the player has explored all available options.

Reactions work similarly: the system draws from the reactions pool, so each reaction must stand on its own without depending on another reaction having been shown first.

### 12. Write as a real NPC speaks

Natural, conversational, not flowery or overwrought. Short sentences. Concrete observations over abstract commentary. Contractions are allowed and encouraged where natural ("I've", "don't", "there's").

- Good: `"The farmer says it's a wolf. The trapper says it's too big for a wolf. The tanner says it's got scales."`
- Bad: `"A most peculiar creature of uncertain taxonomy has been observed by the local populace."`

### 13. Each entry in a pool should feel distinct

Cover different angles of the theme: different causes, different witnesses, different evidence, different emotional responses. Two entries about "animal sighted near the subject" with different adjectives is not enough variety. One should be about tracks, another about a flying shape, another about sounds at night.

### 14. Biome-neutral

Reference universal terrain: ridges, ruins, paths, walls, water sources, old structures. Not biome-specific features (specific tree types, sand, snow, jungle). The same pool serves all settlements regardless of biome.

- Good: `"near the old wall"`, `"down by the water"`, `"past the ridge"`
- Bad: `"in the pine forest"`, `"across the dunes"`, `"through the snow"`

### 15. No hardcoded conjugation after variable-plurality nouns

If an entry uses `{subject_focus}` or any subject variable as a sentence subject, use the conjugation helper. If the subject is not the grammatical subject, restructure the sentence.

- Bad: `"{subject_focus} is getting worse."` (breaks on plural: "strange lights is getting worse")
- Good: `"{subject_focus_The} {subject_focus_is} getting worse."`
- Good: `"Things are getting worse with {subject_focus_the}."`

### 16. Subject-referencing entries must work with named POIs

Test mentally with a proper noun like "Blackrock Mine" or "The Greyveil Tower." The sentence `"Nobody goes near {subject_focus_the} anymore"` works with both "the old watchtower" and "Blackrock Mine." The sentence `"The place near {subject_focus_the} is cursed"` works with "the ridge" but reads oddly with "The Greyveil Tower" ("The place near The Greyveil Tower is cursed"). Use `{subject_focus_the}` (lowercase) to avoid this: it suppresses the article for proper nouns.


## Generation Pipeline

How pool entries become dialogue at runtime. Authors should understand this flow to write entries that work well with the system's randomization.

1. **Subject assignment.** `TopicGenerator` draws a subject for each NPC from the subject focus pool. Each subject has category tags that determine which template (and therefore which pool) it matches.

2. **Pool entry draw.** One `PoolEntry` is drawn from the matching v2 pool via `PercentageDedup` (80% of pool entries are seen before any repeat). Larger pools produce more variety before cycling.

3. **Graph construction.** `TopicGraphBuilder` constructs a dialogue graph from the drawn entry:
   - **Entry node**: the intro text with detail and reaction branch options.
   - **Detail branches** (0-2): each detail has a 70% independent chance to appear, hard capped at 2 shown. An entry with 3 details will show 0, 1, or 2 of them.
   - **Reaction branches**: each included detail has a 30% chance of getting a reaction follow-up.
   - **Stat check** (0-1): 60% chance to appear when the entry has one authored. DC range 8-16, chosen randomly.
   - Pass: +5 disposition. Fail: -3 disposition.

4. **Variable resolution.** All `{variable_name}` tokens are resolved via regex substitution. Missing variables become empty string. Post-processing cleans double articles, dangling punctuation, and double spaces.

### Tuning Constants

| Constant | Value |
|----------|-------|
| Detail include chance | 70% per detail |
| Max details shown | 2 |
| Reaction followup chance | 30% after included detail |
| Stat check chance | 60% |
| Stat check DC range | 8-16 |
| Stat check pass disposition | +5 |
| Stat check fail disposition | -3 |
| Dedup threshold | 80% of pool seen before repeats |


## Support Pools

These non-v2 pools live in `topics/pools/` (not the `v2/` subdirectory) and provide system-level content. Pool entry authors do not edit these files. They are listed here for completeness.

| File | Purpose | Format |
|------|---------|--------|
| `subject_focuses.json` | Subject nouns that anchor topics | Object array with value/plural/proper/categories. See `subjects.md`. |
| `greeting_lines.json` | NPC greetings and return greetings | Bracket-keyed string arrays |
| `tone_openers.json` | Disposition-keyed intro framing | Bracket-keyed: hostile/unfriendly/neutral/friendly/loyal |
| `tone_closers.json` | Disposition-keyed outro framing | Same bracket keys |
| `time_refs.json` | Drop-in temporal references | String array |
| `directions.json` | Drop-in spatial references | String array |
| `perspective_details.json` | Quest exposition flavor text | String array |
| `prompt_groups.json` | Player-facing button text | Object with named group arrays |
