# Quest Template Authoring Guide

This document defines how to write quest template JSON files for the Natural20 randomizing quest system. Follow these rules exactly. When in doubt, test your dialogue mentally with 3 different variable substitutions before committing.

---

## System Overview

The quest system generates quests by combining:
1. A **dramatic situation** (1 of 36, determines NPC tone)
2. A **phase sequence** (Exposition → optional Conflicts → Resolution)
3. **Pool-drawn variables** (action, focus, stakes, threat, items, mobs, NPCs)
4. **Dialogue variants** (templates with `{variable}` tokens, written per-situation)

All story content comes from variables. Templates provide emotional framing only.

### Phase Sequence Probabilities
- 10% chance: short quest (Exposition → Resolution, no Conflict)
- 90% chance: standard quest (Exposition → 1+ Conflicts → Resolution)
- After Conflict: 40% chance to add another Conflict
- After Resolution: 25% chance to loop back to Conflict
- Hard cap: 6 phases maximum

---

## File Structure

Each situation directory contains 5 files:

```
quests/{SituationName}/
  exposition_variants.json   # Quest intro (NPC presents the problem)
  conflict_variants.json     # Complications (things get harder)
  resolution_variants.json   # Conclusion (wrap-up + reward)
  references.json            # Cross-quest reference hooks
  npc_weights.json           # NPC role → selection weight
```

---

## Variant JSON Schema

### Exposition and Conflict variants

```json
{
  "variants": [
    {
      "id": "situation_phase_01",
      "dialogueChunks": {
        "intro": "Opening line. Sets the emotional tone.",
        "plotStep": "The core ask. Uses variables for specifics.",
        "outro": "Closing line. Motivates the player to act."
      },
      "objectivePool": ["GATHER_ITEMS", "KILL_MOBS", "EXPLORE_LOCATION"],
      "objectiveConfig": {
        "GATHER_ITEMS": { "countMin": 5, "countMax": 12 },
        "KILL_MOBS": { "countMin": 3, "countMax": 8 }
      }
    }
  ]
}
```

### Resolution variants

Same structure but:
- `"objectivePool": []` (MUST be empty)
- `"objectiveConfig": {}` (MUST be empty)

### Player Responses

Player responses are NOT defined in template files. They are drawn from tone-based
pools at quest generation time (`quests/pools/responses_accept.json` and
`quests/pools/responses_decline.json`). Each situation is mapped to a tone via
`quests/pools/situation_tones.json`.

Each generated quest gets exactly 1 accept response and 1 decline response, both
matching the situation's tone. Responses are self-contained statements that do not
require the NPC to respond or acknowledge the player's choice.

Available tones: `desperate`, `bold`, `urgent`, `somber`, `conspiratorial`, `conflicted`

---

## Variable Reference

### Always Available (guaranteed to have a value)

| Token | Type | Example | Template usage |
|---|---|---|---|
| `{player_name}` | proper noun | "Adventurer" | Use directly: `"{player_name}, I need your help."` |
| `{enemy_type}` | noun with article | "Trork Brute" | Use directly: `"The {enemy_type} attacked."` |
| `{quest_item}` | lowercase noun | "leather" | Use directly: `"Gather {quest_item} for us."` |
| `{location_hint}` | directional phrase | "north-west, about 200 blocks" | Use directly: `"Head {location_hint}."` |
| `{quest_action}` | verb phrase | "hold the line" | Use directly: `"We need to {quest_action}."` |
| `{quest_focus}` | bare noun (NO article) | "old watchtower" | ADD "the": `"at the {quest_focus}"` |
| `{quest_stakes}` | bare noun (NO article) | "refugee families" | ADD "the": `"the {quest_stakes} depend on us"` |
| `{quest_threat}` | bare noun (NO article) | "advancing raiders" | ADD "the": `"the {quest_threat} grows worse"` |

### Verb Conjugation Helpers (for subject-verb agreement)

These resolve to "is"/"are", "has"/"have", or "was"/"were" based on whether the pool value is singular or plural.

| Token | Singular result | Plural result |
|---|---|---|
| `{quest_focus_is}` | "is" | "are" |
| `{quest_focus_has}` | "has" | "have" |
| `{quest_focus_was}` | "was" | "were" |
| `{quest_stakes_is}` | "is" | "are" |
| `{quest_stakes_has}` | "has" | "have" |
| `{quest_stakes_was}` | "was" | "were" |
| `{quest_threat_is}` | "is" | "are" |
| `{quest_threat_has}` | "has" | "have" |
| `{quest_threat_was}` | "was" | "were" |

**Usage:** `"The {quest_stakes} {quest_stakes_is} in danger."` → "The refugee families are in danger." OR "The harvest is in danger."

### Optional Variables (may be empty: unresolved tokens are stripped cleanly)

| Token | Example | Notes |
|---|---|---|
| `{target_npc}` | "Elara Thornwick" | Full name. Required only when TALK_TO_NPC/DELIVER_ITEM objective. |
| `{quest_ally}` | "Old Maren" | Full name. NPC in same settlement as quest giver. |
| `{quest_origin}` | "last winter's famine" | Full phrase with article. Backstory. |
| `{quest_time_pressure}` | "before the next storm" | Full phrase. Urgency. |
| `{quest_reward_hint}` | "my family's heirloom" | Full phrase. Foreshadows reward. |
| `{location}` | "Ironhaven" | Settlement name. |

---

## Authoring Rules

### Rule 1: Templates add "the", pool values do not

Pool values for `quest_focus`, `quest_stakes`, and `quest_threat` are bare nouns without articles. The template MUST add "the" before them.

```
GOOD: "We must protect the {quest_focus}."
BAD:  "We must protect {quest_focus}."
      → renders as "We must protect old watchtower." (missing article)
```

The `DialogueResolver` collapses double articles automatically ("the the" → "the"), so accidentally writing "the the {quest_focus}" is safe but sloppy.

### Rule 2: Use conjugation helpers when a pool variable is the sentence subject

When `quest_focus`, `quest_stakes`, or `quest_threat` is the grammatical subject of a verb, use the matching `_is`, `_has`, or `_was` helper instead of hardcoding the verb.

```
GOOD: "The {quest_threat} {quest_threat_has} grown worse."
      → "The advancing raiders have grown worse." (plural)
      → "The spreading blight has grown worse." (singular)

BAD:  "The {quest_threat} has grown worse."
      → "The advancing raiders has grown worse." (BROKEN with plural)
```

**When NOT to use helpers:** When the variable is NOT the subject:
```
OK:   "I fear the {quest_threat} more than anything."
      (quest_threat is the object, "I" is the subject: no helper needed)
```

### Rule 3: Respect semantic roles

Each variable has a specific semantic meaning. Using them in the wrong context creates nonsense.

| Variable | Semantic role | It represents... | Use it as... |
|---|---|---|---|
| `{quest_focus}` | PLACE or THING | Where/what the action targets | A location or object: "at the {quest_focus}", "near the {quest_focus}" |
| `{quest_stakes}` | AT-RISK entity | Who/what suffers if quest fails | People or resources: "the {quest_stakes} will suffer", "to protect the {quest_stakes}" |
| `{quest_threat}` | DANGER SOURCE | What causes the problem | An enemy or force: "the {quest_threat} grows stronger", "caused by the {quest_threat}" |
| `{quest_action}` | TASK | What needs doing | A verb phrase: "we need to {quest_action}", "help me {quest_action}" |

```
GOOD: "The {quest_threat} {quest_threat_has} reached the {quest_focus}."
      → "The advancing raiders have reached the old watchtower."

BAD:  "The {quest_focus} has been going on for decades."
      → "The old watchtower has been going on for decades." (place can't "go on")
```

### Rule 4: Optional variables must be in removable clauses

Optional variables (`{quest_origin}`, `{quest_time_pressure}`, `{quest_reward_hint}`, `{quest_ally}`) may not resolve. When stripped, the surrounding text must still be grammatical.

**Pattern A: Leading clause (best)**
```
GOOD: "Ever since {quest_origin}, things have been difficult."
      → stripped: "things have been difficult."
```

**Pattern B: Trailing clause**
```
GOOD: "We must act quickly, {quest_time_pressure}."
      → stripped: "We must act quickly."
```

**Pattern C: Parenthetical**
```
GOOD: "{quest_ally} says the situation is worsening."
      → stripped: "says the situation is worsening." (still readable)
```

**Anti-patterns:**
```
BAD:  "The {quest_origin} caused all of this."
      → stripped: "The caused all of this." (BROKEN)

BAD:  "We must finish {quest_time_pressure} or everything is lost."
      → stripped: "We must finish or everything is lost." (awkward)

BAD:  "Only {quest_ally} can save us now."
      → stripped: "Only can save us now." (BROKEN)
```

### Rule 5: Situation = tone, NOT content

The dramatic situation controls HOW the NPC speaks. It does NOT control WHAT the quest is about. All story specifics come from pool variables.

| Situation | Tone keywords | NPC voice |
|---|---|---|
| Supplication | desperate, humble, pleading | Begging for help, emotionally vulnerable |
| DaringEnterprise | bold, confident, ambitious | Excited about a grand plan, recruiting |
| Pursuit | urgent, focused, tactical | Briefing on a target, mission-oriented |
| Disaster | panicked, rallying, urgent | Responding to crisis, needs immediate action |
| Revolt | defiant, conspiratorial | Secret resistance, whispering about uprising |
| Remorse | penitent, haunted, seeking peace | Confessing past wrongs, asking for redemption |
| ... | ... | ... |

```
GOOD (Supplication tone): "Please, {player_name}. The {quest_stakes} {quest_stakes_is}
      all we have left. If you could {quest_action} at the {quest_focus}, we might survive."

GOOD (Pursuit tone): "{player_name}, we have a target. The {quest_threat} {quest_threat_was}
      last spotted near the {quest_focus}. I need you to {quest_action} before they disappear."
```

### Rule 6: Each variant = different phrasing, NOT different story

Variants within a file must differ in sentence structure, emotional angle, and which optional variables they include. They must NOT differ in story content, because story content comes from pools.

```
Variant 1: Opens with a question, uses {quest_origin}, skips {quest_ally}
Variant 2: Opens with desperation, uses {quest_time_pressure}, includes {quest_ally}
Variant 3: Opens with calm explanation, uses {quest_reward_hint}, skips optionals
```

### Rule 7: No hardcoded names, places, or specifics

Every proper noun, location, item, NPC name, action, threat, and stakes MUST be a variable. The only hardcoded text is the NPC's emotional framing.

```
GOOD: "{quest_ally} spotted the {enemy_type} near the {quest_focus}."
BAD:  "Old Maren spotted the Trork near the watchtower."
```

### Rule 8: Test with 3 random substitutions

Before finalizing any template, mentally substitute 3 different pool value sets:

**Test set A:**
- action="seal the entrance", focus="collapsed mine", stakes="village children"
- threat="spreading blight", enemy="Spider", item="bone", ally="Bren Ironjaw"

**Test set B:**
- action="protect the caravan", focus="river crossing", stakes="winter supplies"
- threat="deserter gangs", enemy="Trork Grunt", item="wheat", ally="Tessa Brightwater"

**Test set C:**
- action="root out the corruption", focus="burial ground", stakes="last of our iron"
- threat="political unrest", enemy="Skeleton", item="cotton fiber", ally="Old Maren"

If any substitution produces broken grammar, semantic nonsense, or awkward phrasing: revise the template.

---

## Objective Types

| Type | Description | Config fields |
|---|---|---|
| `GATHER_ITEMS` | Collect resources | `countMin` (3-8), `countMax` (10-20) |
| `KILL_MOBS` | Kill hostile mobs | `countMin` (2-5), `countMax` (5-10) |
| `DELIVER_ITEM` | Bring item to NPC | none needed |
| `EXPLORE_LOCATION` | Visit a location | none needed |
| `FETCH_ITEM` | Retrieve from chest | `locationPreference`: `"DUNGEON"` or `"SETTLEMENT"` (optional) |
| `TALK_TO_NPC` | Interact with NPC | none needed |
| `KILL_NPC` | Eliminate target NPC | none needed |

- Exposition and Conflict: `objectivePool` must have 2-4 types
- Resolution: `objectivePool` MUST be `[]`

---

---

## Pool Value Authoring

Pool values live in `quests/pools/*.json`. When adding new entries:

### Item and mob pools (gather_items.json, hostile_mobs.json)
```json
{ "id": "Hytale:ItemId", "label": "display name" }
```
- `label` is what appears in dialogue (lowercase, natural language)

### Narrative pools (quest_focuses.json, quest_stakes.json, quest_threats.json)
```json
{ "value": "noun phrase without article", "plural": true/false }
```
- `value`: bare noun phrase, NO "the" prefix
- `plural`: determines verb conjugation helpers
- Keep values 1-4 words
- Values must work as both subjects and objects in sentences

### String pools (quest_actions.json, quest_origins.json, etc.)
```json
"verb phrase" or "full phrase"
```
- `quest_actions`: verb phrases that follow "to" or "we need to" (e.g., "hold the line")
- `quest_origins`: full phrases that follow "since" or "because of" (e.g., "last winter's famine")
- `quest_time_pressures`: full phrases that work as deadlines (e.g., "before the next storm")
- `quest_reward_hints`: full phrases the NPC says about reward (e.g., "my family's heirloom")

---

## Checklist Before Submitting

- [ ] No `bindings` object in any variant (all variables come from pools)
- [ ] No `playerResponses` in any variant (responses come from tone pools)
- [ ] All `{quest_focus}`, `{quest_stakes}`, `{quest_threat}` preceded by "the" in templates
- [ ] Conjugation helpers used when pool variable is sentence subject
- [ ] Optional variables in removable clauses (grammar survives stripping)
- [ ] No hardcoded names, places, items, or story specifics
- [ ] Resolution variants have empty `objectivePool` and `objectiveConfig`
- [ ] Each variant has different phrasing/structure, not different content
- [ ] Tested mentally with 3 different variable substitutions
- [ ] Valid JSON (no trailing commas, proper brackets)
