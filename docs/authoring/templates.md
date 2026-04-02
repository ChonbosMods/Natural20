# Template System Reference (v2)

Templates define the structural shape of NPC conversations: what skill checks are available, what prompts the player sees, and how the dialogue branches. Each template maps 1:1 to a pool file, and `TopicGraphBuilder` constructs the dialogue graph from the pool entry's coherent triplet.

**Source file:** `src/main/resources/topics/templates.json`


## Template Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `id` | string | yes | - | Matches pool file name and subject category (e.g., `"danger"` maps to `v2/danger.json`) |
| `label` | string | yes | - | Topic button text in UI. Supports `{subject_focus_The}` and similar variables |
| `requiresConcrete` | bool | no | `false` | If true, only concrete subjects (physical places/objects) match this template |
| `subjectRequired` | bool | no | `true` | If false, template works without a subject (e.g., weather: "The Weather Lately") |
| `skills` | string[] | no | `null` | Skills for stat checks (e.g., `["PERCEPTION", "INSIGHT"]`). One is picked at random per topic |
| `reactionIntensity` | string | no | `null` | `"intense"` or `"mild"`: affects NPC tone framing |
| `intro` | string | yes | - | Pattern for intro text. Typically `"{tone_opener}{entry_intro}{tone_closer}"` |
| `detailPrompts` | string[] | yes | - | Prompt group names for detail branch buttons (e.g., `["ask_more", "press_details"]`) |
| `reactionPrompts` | string[] | yes | - | Prompt group names for reaction branch buttons (e.g., `["ask_opinion", "ask_feeling"]`) |


## The 16 Templates

| ID | Label | Concrete | Skills | Intensity | Detail Prompts | Reaction Prompts |
|----|-------|----------|--------|-----------|----------------|------------------|
| `danger` | `{subject_focus_The}` | no | PERCEPTION, INSIGHT | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `sighting` | `{subject_focus_The}` | yes | PERCEPTION, NATURE | intense | ask_more, press_source | ask_opinion |
| `treasure` | `{subject_focus_The}` | no | INVESTIGATION, HISTORY | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `corruption` | `{subject_focus_The}` | no | INSIGHT, INVESTIGATION | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `conflict` | `{subject_focus_The}` | no | INSIGHT, PERSUASION | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `disappearance` | `{subject_focus_The}` | no | INVESTIGATION, PERCEPTION | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `migration` | `{subject_focus_The}` | no | NATURE, PERCEPTION | intense | ask_more, press_source | ask_opinion |
| `omen` | `{subject_focus_The}` | no | ARCANA, RELIGION | intense | ask_more, press_details | ask_opinion, ask_feeling |
| `weather` | The Weather Lately | subjectRequired: false | NATURE, PERCEPTION | mild | ask_more, ask_context | ask_opinion |
| `trade` | Trade at {subject_focus_the} | yes | PERSUASION, INVESTIGATION | mild | ask_more, ask_context | ask_opinion, ask_feeling |
| `craftsmanship` | `{subject_focus_The}` | no | INSIGHT, HISTORY | mild | ask_more, press_details | ask_opinion |
| `community` | `{subject_focus_The}` | no | PERSUASION, INSIGHT | mild | ask_more, ask_context | ask_opinion, ask_feeling |
| `nature` | `{subject_focus_The}` | no | NATURE, PERCEPTION | intense | ask_more, press_details | ask_opinion |
| `nostalgia` | `{subject_focus_The}` | no | HISTORY, INSIGHT | mild | ask_more, ask_context | ask_opinion, ask_feeling |
| `curiosity` | `{subject_focus_The}` | no | INVESTIGATION, ARCANA | intense | ask_more, press_details | ask_opinion |
| `festival` | `{subject_focus_The}` | no | PERFORMANCE, PERSUASION | mild | ask_more, ask_context | ask_opinion, ask_feeling |


## Template-to-Pool Mapping

Each template ID maps directly to a pool file:

```
Template "danger"         →  topics/pools/v2/danger.json
Template "sighting"       →  topics/pools/v2/sighting.json
Template "treasure"       →  topics/pools/v2/treasure.json
Template "corruption"     →  topics/pools/v2/corruption.json
Template "conflict"       →  topics/pools/v2/conflict.json
Template "disappearance"  →  topics/pools/v2/disappearance.json
Template "migration"      →  topics/pools/v2/migration.json
Template "omen"           →  topics/pools/v2/omen.json
Template "weather"        →  topics/pools/v2/weather.json
Template "trade"          →  topics/pools/v2/trade.json
Template "craftsmanship"  →  topics/pools/v2/craftsmanship.json
Template "community"      →  topics/pools/v2/community.json
Template "nature"         →  topics/pools/v2/nature.json
Template "nostalgia"      →  topics/pools/v2/nostalgia.json
Template "curiosity"      →  topics/pools/v2/curiosity.json
Template "festival"       →  topics/pools/v2/festival.json
```

This is a strict 1:1 mapping. There are no cross-cutting pools or shared detail/reaction pools. Each pool file contains complete coherent entries (intro + details + reactions + optional statCheck).


## Coherent Triplet: Pool Entry Structure

Each pool entry is a self-contained unit of conversation content:

```json
{
  "id": 0,
  "intro": "Something's been leaving tracks near {subject_focus_the}. Big ones...",
  "details": [
    "Three toes, clawed, wider than my hand spread flat...",
    "They circle the same area every night...",
    "The tracks stop at the tree line and don't come back out..."
  ],
  "reactions": [
    "I don't scare easy, but I stopped walking that path after dark.",
    "People keep saying it's a bear. It's not a bear."
  ],
  "statCheck": {
    "pass": "There were smaller tracks alongside the big ones...",
    "fail": "The mud is too churned up out there..."
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | int | yes | Unique within this pool file |
| `intro` | string | yes | NPC's opening statement. Supports `{variable}` tokens |
| `details` | string[] | yes | Follow-up statements the NPC gives when the player asks for more. Up to 3 |
| `reactions` | string[] | yes | NPC's personal opinions or emotional responses |
| `statCheck` | object | no | Pass/fail text for skill check branches |
| `statCheck.pass` | string | yes (if statCheck) | Text shown on successful skill check |
| `statCheck.fail` | string | yes (if statCheck) | Text shown on failed skill check |

The intro, details, and reactions are authored together as a coherent unit. They should read as a natural conversation about the same specific situation, not as interchangeable fragments.


## How Templates Drive Dialogue Construction

When a topic is generated for an NPC:

### 1. Template Selection

Subject's `categories` array is matched against template IDs. `TopicTemplateRegistry.randomTemplateForSubject()` filters templates whose ID matches a category, respects `requiresConcrete`, and picks one at random. If no match is found, it falls back to any template (still respecting the concrete constraint).

### 2. Entry Selection

One `PoolEntry` is drawn from the matching v2 pool via `PercentageDedup`: 80% of the pool is exhausted before any repeat. This is tracked per pool across the entire settlement, so NPCs in the same village won't tell the same story.

### 3. Graph Construction

`TopicGraphBuilder.buildTopic()` assembles the dialogue tree:

```
Entry Node (intro text)
├── Detail 0 (70% chance) → player prompt from detailPrompts
│   └── Optional reaction follow-up (30% chance)
├── Detail 1 (70% chance, hard cap of 2 details) → player prompt from detailPrompts
│   └── Optional reaction follow-up (30% chance)
└── Stat Check (60% chance, only if entry has statCheck + template has skills)
    ├── Pass → pass text, +5 disposition
    └── Fail → fail text, -3 disposition
```

Each detail and reaction branch is rolled independently. A topic might end up with 0 details, 1 detail, or 2 details. Reactions are drawn from the entry's `reactions` array (not from a separate pool).

### 4. Prompt Selection

For each detail branch, a player-facing button prompt is drawn from the template's `detailPrompts` groups via `PromptGroupRegistry`. The system picks a random group name from the array, then picks a random prompt string from that group. Reaction branch prompts work the same way using `reactionPrompts`.

### 5. Variable Resolution

All `{variable}` tokens are replaced via regex. Missing variables become empty string. Post-processing cleans double articles, dangling punctuation, and extra spaces.


## Generation Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `DETAIL_INCLUDE_CHANCE` | 0.70 | 70% chance each detail appears (rolled independently per detail) |
| `MAX_DETAILS` | 2 | Hard cap on detail branches shown |
| `REACTION_FOLLOWUP_CHANCE` | 0.30 | 30% chance a reaction follows a detail |
| `STAT_CHECK_CHANCE` | 0.60 | 60% chance the stat check appears (when the entry has one authored) |
| `STAT_CHECK_DC_MIN` | 8 | Minimum difficulty class |
| `STAT_CHECK_DC_MAX` | 16 | Maximum difficulty class |
| `STAT_CHECK_PASS_DISPOSITION` | +5 | Disposition gain on successful check |
| `STAT_CHECK_FAIL_DISPOSITION` | -3 | Disposition loss on failed check |


## Category Decks and Topic Budgets

Templates are organized into two stratified decks that control how topics are distributed to NPCs:

**Rumor deck** (intense, event-driven topics):
`danger`, `sighting`, `treasure`, `corruption`, `conflict`, `disappearance`, `migration`, `omen`

**Smalltalk deck** (mild, everyday topics):
`trade`, `weather`, `craftsmanship`, `community`, `nature`, `nostalgia`, `curiosity`, `festival`

Each NPC gets a budget of topics based on their role:

| Role type | Min topics | Max topics |
|-----------|-----------|-----------|
| Social roles (TavernKeeper, Artisans, Traveler) | 2 | 4 |
| Functional roles (all others) | 0 | 2 |

40% of a NPC's budget comes from the rumor deck, the rest from smalltalk. Decks are shuffled per-NPC (seeded deterministically from the settlement key + NPC index).


## Prompt Groups Reference

Templates reference these groups from `topics/pools/prompt_groups.json`:

### Detail prompt groups (used in `detailPrompts`)

**`ask_more`**: "Tell me more.", "Go on.", "What else?", "I am listening.", "And then?"

**`press_details`**: "Can you describe what you saw?", "What exactly happened?", "Walk me through it.", "Give me the details."

**`press_source`**: "How did you find out?", "Who told you this?", "Where did you hear that?", "How do you know?"

**`ask_context`**: "What is the situation there?", "How long has this been going on?", "When did this start?", "What changed?"

### Reaction prompt groups (used in `reactionPrompts`)

**`ask_opinion`**: "What do you make of it?", "What do you think?", "How do you feel about that?", "Does that worry you?"

**`ask_feeling`**: "Are you alright?", "How are people handling it?", "Is anyone doing something about it?", "What are people saying?"


## Variable Reference

These variables are available in pool entry text:

| Variable | Source | Example |
|----------|--------|---------|
| `{subject_focus}` | Subject value as-is | "strange lights" |
| `{subject_focus_bare}` | Without leading "the" | "strange lights" |
| `{subject_focus_the}` | With lowercase "the" prefix | "the strange lights" |
| `{subject_focus_The}` | With uppercase "The" prefix | "The Strange Lights" |
| `{subject_focus_is}` | Conjugated "is"/"are" | "are" (for plural subjects) |
| `{subject_focus_has}` | Conjugated "has"/"have" | "have" |
| `{subject_focus_was}` | Conjugated "was"/"were" | "were" |
| `{npc_name}` | NPC's generated name | "Kael" |
| `{time_ref}` | Temporal context (drop-in pool) | "A few days back" |
| `{direction}` | Spatial hint (drop-in pool) | "past the ridge" |
| `{tone_opener}` | Disposition-based opening phrase | "Listen..." |
| `{tone_closer}` | Disposition-based closing phrase | "...if you ask me." |

Proper nouns skip the "the" prefix automatically. Plural subjects get correct verb conjugation.


## Adding a New Template

To add a new topic category beyond the current 16:

1. Add a template entry to `topics/templates.json` with a unique `id`
2. Create a matching pool file at `topics/pools/v2/{id}.json`
3. Ensure at least 20 subjects exist with the matching category tag in `subject_focuses.json`
4. Add at least 5 pool entries to the new pool file (recommended: 10+ for dedup variety)
5. Add the new ID to either `RUMOR_DECK` or `SMALLTALK_DECK` in `TopicGenerator.java`
6. The template is automatically discovered by `TopicTemplateRegistry` at startup


## Quest Topics

When a topic carries a quest, the normal detail/reaction/statCheck branches are replaced by a quest exposition flow:

```
Entry Node (quest exposition + objective summary)
├── [Explore] "What happened?" → plot step node (if available)
│   ├── [Explore] "Is there anything else?" → outro node (if available)
│   │   ├── [Accept] "I will handle it." → GIVE_QUEST action
│   │   └── [Decline] "Maybe not right now." → decline
│   ├── [Accept] "I will handle it." → GIVE_QUEST action
│   └── [Decline] "Maybe not right now." → decline
├── [Accept] "I will handle it." → GIVE_QUEST action
└── [Decline] "Maybe not right now." → decline
```

Accept/decline options appear at every level so the player can commit without exploring further. The quest exposition text and plot steps come from the quest system's bindings, not from the pool entry.
