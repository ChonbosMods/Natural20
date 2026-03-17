# Quest Template Authoring Rules

## Variable Reference

### Always available
| Token | Type | Example value | Notes |
|---|---|---|---|
| `{player_name}` | name | "Adventurer" | |
| `{enemy_type}` | noun | "Trork Brute" | Includes article if needed |
| `{quest_item}` | noun | "leather" | Lowercase, no article |
| `{location_hint}` | phrase | "north-west, about 200 blocks" | Full directional phrase |
| `{quest_action}` | verb phrase | "hold the line" | Always a verb phrase |
| `{quest_focus}` | noun | "old watchtower" | NO article: use "the {quest_focus}" |
| `{quest_stakes}` | noun | "refugee families" | NO article: use "the {quest_stakes}" |
| `{quest_threat}` | noun | "advancing raiders" | NO article: use "the {quest_threat}" |

### Verb conjugation helpers
| Token | Singular | Plural | Usage |
|---|---|---|---|
| `{quest_focus_is}` | "is" | "are" | "The {quest_focus} {quest_focus_is} in danger" |
| `{quest_focus_has}` | "has" | "have" | "The {quest_focus} {quest_focus_has} been compromised" |
| `{quest_focus_was}` | "was" | "were" | "The {quest_focus} {quest_focus_was} destroyed" |
| `{quest_stakes_is}` | "is" | "are" | "The {quest_stakes} {quest_stakes_is} at risk" |
| `{quest_stakes_has}` | "has" | "have" | |
| `{quest_stakes_was}` | "was" | "were" | |
| `{quest_threat_is}` | "is" | "are" | "The {quest_threat} {quest_threat_is} growing" |
| `{quest_threat_has}` | "has" | "have" | |
| `{quest_threat_was}` | "was" | "were" | |

### Optional (may not be resolved: unresolved tokens are stripped)
| Token | Example | Notes |
|---|---|---|
| `{target_npc}` | "Elara Thornwick" | Full name, no article needed |
| `{quest_ally}` | "Old Maren" | Full name, no article needed |
| `{quest_origin}` | "last winter's famine" | Full phrase with article if needed |
| `{quest_time_pressure}` | "before the next storm" | Full phrase |
| `{quest_reward_hint}` | "my family's heirloom" | Full phrase |
| `{location}` | "Ironhaven" | Settlement name |

## Rules

### Rule 1: Articles go in the TEMPLATE, not the pool value
Pool values for `quest_focus`, `quest_stakes`, and `quest_threat` do NOT include "the".
Templates MUST add "the" before these variables.

GOOD: `"the {quest_focus} is in danger"`
BAD:  `"{quest_focus} is in danger"` (reads as "old watchtower is in danger")

### Rule 2: Use verb conjugation helpers for subject-verb agreement
When a pool variable is the subject of a sentence, use the matching conjugation helper.

GOOD: `"The {quest_stakes} {quest_stakes_is} depending on you"`
BAD:  `"The {quest_stakes} is depending on you"` (breaks with plural values)

GOOD: `"The {quest_threat} {quest_threat_has} grown worse"`
BAD:  `"The {quest_threat} has grown worse"` (breaks with plural values)

### Rule 3: Use variables for their semantic role ONLY
Each variable has a specific semantic role. Do not use them interchangeably.

| Variable | Semantic role | Use for |
|---|---|---|
| `{quest_focus}` | PLACE/THING | Where action happens or what is acted upon |
| `{quest_stakes}` | WHAT'S AT RISK | Who/what suffers if the quest fails |
| `{quest_threat}` | DANGER SOURCE | What is causing the problem |
| `{quest_action}` | PLAN/TASK | What needs to be done |

GOOD: `"The {quest_threat} {quest_threat_has} reached the {quest_focus}"`
BAD:  `"The {quest_focus} has been going on for decades"` (focus is a place, not an activity)

### Rule 4: Write dialogue that works with ANY pool value
Test your dialogue mentally with multiple substitutions:

Template: `"We need to {quest_action} at the {quest_focus} before the {quest_threat} destroys the {quest_stakes}."`

Test 1: "We need to hold the line at the old watchtower before the advancing raiders destroys the refugee families."
Test 2: "We need to seal the entrance at the collapsed mine before the spreading blight destroys the harvest."
Test 3: "We need to escort the survivors at the river crossing before the rising floodwaters destroys the village children."

If any substitution sounds wrong, revise the template.

### Rule 5: Optional variables should be in standalone clauses
Since optional variables may be stripped, they should not be embedded mid-sentence
where their removal would break grammar.

GOOD: `"Ever since {quest_origin}, things have been difficult."` (strips to "things have been difficult.")
BAD:  `"The {quest_origin} caused {quest_threat}."` (strips to "The caused advancing raiders.")

GOOD: `"We must act {quest_time_pressure}."` (strips to "We must act.")
BAD:  `"If we don't finish {quest_time_pressure} then..."` (strips to "If we don't finish then...")

### Rule 6: Situation determines TONE, not content
The dramatic situation (Supplication, Pursuit, etc.) controls how the NPC speaks:
desperate, bold, urgent, conflicted. It does NOT control what the quest is about.
All story specifics come from variables.

### Rule 7: Each variant = different phrasing, same flexibility
Variants within a situation differ in sentence structure, emotional angle, and
which optional variables they use. They do NOT differ in story specifics
(those come from pools).

### Rule 8: No hardcoded names, places, or specifics in dialogue
Every proper noun, location, item, and NPC name MUST be a variable.
The only hardcoded text should be the NPC's emotional framing.
