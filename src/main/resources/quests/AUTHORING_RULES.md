# Quest Template Authoring Guide

This document defines how to write quest template JSON files and response pool entries for the Natural20 randomizing quest system. Follow these rules exactly.

---

## System Overview

The quest system generates quests by combining:
1. A **dramatic situation** (1 of 36, determines NPC tone)
2. A **phase sequence** (Exposition → optional Conflicts → Resolution)
3. **Pool-drawn variables** (action, focus, stakes, threat, items, mobs, NPCs, responses)
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

### Situation Templates (36 directories)

```
quests/{SituationName}/
  exposition_variants.json   # Quest intro: NPC presents the problem
  conflict_variants.json     # Complications: things get harder
  resolution_variants.json   # Conclusion: wrap-up + reward
  references.json            # Cross-quest reference hooks
  npc_weights.json           # NPC role → selection weight
```

### Pool Files (shared across all situations)

```
quests/pools/
  # World variable pools
  gather_items.json          # Items for GATHER_ITEMS objectives
  hostile_mobs.json          # Mobs for KILL_MOBS objectives
  quest_actions.json         # Verb phrases: what needs doing
  quest_focuses.json         # Noun phrases: where/what action targets
  quest_stakes.json          # Noun phrases: who/what is at risk
  quest_threats.json         # Noun phrases: what causes the problem
  quest_origins.json         # Backstory phrases
  quest_time_pressures.json  # Urgency phrases
  quest_reward_hints.json    # Reward foreshadowing phrases

  # Response pools
  responses_accept.json            # Player accept lines (by situation + tone)
  responses_decline.json           # Player decline lines (by situation + tone)
  responses_stat_check.json        # Player stat-gated lines (by stat type)
  responses_counter_accept.json    # NPC counter to player accepting (by tone)
  responses_counter_decline.json   # NPC counter to player declining (by tone)
  responses_counter_stat_pass.json # NPC counter to stat check pass (by tone)
  responses_counter_stat_fail.json # NPC counter to stat check fail (by tone)

  # Mapping
  situation_tones.json       # Situation name → tone mapping
```

---

## Player Response & Disposition System

### Conversation Flow

When a quest is presented, the player sees 3 options:

```
[ACCEPT]     "I'll handle it."                    → no disposition change
[STR/DEX/CON/INT/WIS/CHA]  "I can muscle through this."  → stat roll required
[DECLINE]    "Not my problem."                    → negative disposition
```

After the player picks, the NPC gives a counter-response (max 2 total exchanges):

**If Accept:**
```
Player: "I'll handle it."
NPC:    "Thank you. You have no idea how much this means."
        (no disposition change)
```

**If Stat Check Pass:**
```
Player: "I can endure whatever this throws at me." [CON check]
        → Roll succeeds
NPC:    "That skill might be what saves us."
        (+disposition)
```

**If Stat Check Fail:**
```
Player: "I can endure whatever this throws at me." [CON check]
        → Roll fails
NPC:    "We'll manage somehow."
        (-disposition)
        Quest is still accepted.
```

**If Decline:**
```
Player: "I can't help with this right now."
NPC:    "I understand. Not everyone can carry this weight."
        (-disposition, -reputation)
```

### Disposition Rules Summary

| Action | Disposition | Reputation |
|---|---|---|
| Accept (plain) | No change | No change |
| Accept (stat pass) | + positive | No change |
| Accept (stat fail) | - negative | No change |
| Decline | - negative | - negative |
| Quest completion | + positive | + positive |

### Response Pool Sources

| What | Pool file | Keyed by |
|---|---|---|
| Player accept line | `responses_accept.json` | Situation name, fallback to tone |
| Player decline line | `responses_decline.json` | Situation name, fallback to tone |
| Player stat check line | `responses_stat_check.json` | Stat type (STR/DEX/CON/INT/WIS/CHA) |
| NPC counter to accept | `responses_counter_accept.json` | Tone |
| NPC counter to decline | `responses_counter_decline.json` | Tone |
| NPC counter to stat pass | `responses_counter_stat_pass.json` | Tone |
| NPC counter to stat fail | `responses_counter_stat_fail.json` | Tone |

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

### What is NOT in templates

- **No `playerResponses`**: responses come from response pools
- **No `bindings`**: all narrative variables come from external pools
- **No hardcoded names/places/items**: all specifics are `{variable}` tokens

---

## Variable Reference

### Always Available (guaranteed to have a value)

| Token | Type | Example | Template usage |
|---|---|---|---|
| `{player_name}` | proper noun | "Adventurer" | Use directly: `"{player_name}, I need your help."` |
| `{enemy_type}` | noun | "Trork Brute" | Use directly: `"The {enemy_type} attacked."` |
| `{quest_item}` | lowercase noun | "leather" | Use directly: `"Gather {quest_item} for us."` |
| `{location_hint}` | directional phrase | "north-west, about 200 blocks" | Use directly: `"Head {location_hint}."` |
| `{quest_action}` | verb phrase | "hold the line" | Use directly: `"We need to {quest_action}."` |
| `{quest_focus}` | bare noun (NO article) | "old watchtower" | ADD "the": `"at the {quest_focus}"` |
| `{quest_stakes}` | bare noun (NO article) | "refugee families" | ADD "the": `"the {quest_stakes} depend on us"` |
| `{quest_threat}` | bare noun (NO article) | "advancing raiders" | ADD "the": `"the {quest_threat} grows worse"` |

### Verb Conjugation Helpers (for subject-verb agreement)

These resolve to "is"/"are", "has"/"have", or "was"/"were" based on whether the pool value is singular or plural.

| Token | Singular | Plural | Usage |
|---|---|---|---|
| `{quest_focus_is}` | "is" | "are" | `"The {quest_focus} {quest_focus_is} in danger"` |
| `{quest_focus_has}` | "has" | "have" | `"The {quest_focus} {quest_focus_has} been compromised"` |
| `{quest_focus_was}` | "was" | "were" | `"The {quest_focus} {quest_focus_was} destroyed"` |
| `{quest_stakes_is}` | "is" | "are" | `"The {quest_stakes} {quest_stakes_is} at risk"` |
| `{quest_stakes_has}` | "has" | "have" | |
| `{quest_stakes_was}` | "was" | "were" | |
| `{quest_threat_is}` | "is" | "are" | `"The {quest_threat} {quest_threat_is} growing"` |
| `{quest_threat_has}` | "has" | "have" | |
| `{quest_threat_was}` | "was" | "were" | |

### Optional Variables (may be empty: unresolved tokens are stripped cleanly)

| Token | Example | Notes |
|---|---|---|
| `{target_npc}` | "Elara Thornwick" | Full name. Required only for TALK_TO_NPC/DELIVER_ITEM. |
| `{quest_ally}` | "Old Maren" | Full name. NPC in same settlement as quest giver. |
| `{quest_origin}` | "last winter's famine" | Full phrase with article. Backstory. |
| `{quest_time_pressure}` | "before the next storm" | Full phrase. Urgency. |
| `{quest_reward_hint}` | "my family's heirloom" | Full phrase. Foreshadows reward. |
| `{location}` | "Ironhaven" | Settlement name. |

### Response Bindings (resolved at generation time, used by dialogue system)

These are NOT used in dialogue templates. They are resolved by the generator and consumed by the dialogue presentation layer.

| Token | Source | Example |
|---|---|---|
| `{response_accept}` | Accept response pool | "I'll handle it." |
| `{response_decline}` | Decline response pool | "Not my problem." |
| `{response_stat_check}` | Stat check response pool | "I can muscle through this." |
| `{stat_check_type}` | Random stat | "STR" |
| `{counter_accept}` | Counter-accept pool | "Thank you." |
| `{counter_decline}` | Counter-decline pool | "I understand." |
| `{counter_stat_pass}` | Counter-stat-pass pool | "Impressive." |
| `{counter_stat_fail}` | Counter-stat-fail pool | "We'll manage." |

---

## Authoring Rules

### Rule 1: Templates add "the", pool values do not

Pool values for `quest_focus`, `quest_stakes`, and `quest_threat` are bare nouns. Templates MUST add "the".

```
GOOD: "We must protect the {quest_focus}."
BAD:  "We must protect {quest_focus}."
      → "We must protect old watchtower." (missing article)
```

### Rule 2: Use conjugation helpers when a pool variable is the sentence subject

```
GOOD: "The {quest_threat} {quest_threat_has} grown worse."
      → "The advancing raiders have grown worse." (plural)
      → "The spreading blight has grown worse." (singular)

BAD:  "The {quest_threat} has grown worse."
      → "The advancing raiders has grown worse." (BROKEN)
```

When NOT to use: when the variable is the object, not the subject:
```
OK: "I fear the {quest_threat} more than anything."
```

### Rule 3: Respect semantic roles

| Variable | Role | Use as... |
|---|---|---|
| `{quest_focus}` | PLACE/THING | Location or object: "at the {quest_focus}" |
| `{quest_stakes}` | AT-RISK | People or resources: "the {quest_stakes} will suffer" |
| `{quest_threat}` | DANGER | Enemy or force: "the {quest_threat} grows stronger" |
| `{quest_action}` | TASK | Verb phrase: "we need to {quest_action}" |

### Rule 4: Optional variables in removable clauses

```
GOOD: "Ever since {quest_origin}, things have been difficult."
      → stripped: "things have been difficult."

GOOD: "We must act quickly, {quest_time_pressure}."
      → stripped: "We must act quickly."

BAD:  "The {quest_origin} caused all of this."
      → stripped: "The caused all of this." (BROKEN)

BAD:  "Only {quest_ally} can save us."
      → stripped: "Only can save us." (BROKEN)
```

### Rule 5: Situation = tone, NOT content

| Situation | Tone | NPC voice |
|---|---|---|
| Supplication | desperate | Begging, emotionally vulnerable |
| DaringEnterprise | bold | Excited, recruiting for adventure |
| Pursuit | urgent | Mission briefing, tactical |
| Disaster | urgent | Crisis response, rallying |
| Revolt | conspiratorial | Secret resistance, defiant |
| Remorse | somber | Confessing, seeking redemption |
| AnEnemyLoved | conflicted | Questioning loyalties |
| Ambition | bold | Scheming, driven |
| ... | ... | ... |

### Rule 6: Each variant = different phrasing, NOT different story

```
Variant 1: Opens with a question, uses {quest_origin}, skips {quest_ally}
Variant 2: Opens with desperation, uses {quest_time_pressure}, includes {quest_ally}
Variant 3: Opens with calm explanation, uses {quest_reward_hint}, skips optionals
```

### Rule 7: No hardcoded names, places, or specifics

```
GOOD: "{quest_ally} spotted the {enemy_type} near the {quest_focus}."
BAD:  "Old Maren spotted the Trork near the watchtower."
```

### Rule 8: Test with 3 substitution sets

**Set A:** action="seal the entrance", focus="collapsed mine", stakes="village children", threat="spreading blight", enemy="Spider", item="bone"

**Set B:** action="protect the caravan", focus="river crossing", stakes="winter supplies", threat="deserter gangs", enemy="Trork Grunt", item="wheat"

**Set C:** action="root out the corruption", focus="burial ground", stakes="last of our iron", threat="political unrest", enemy="Skeleton", item="cotton fiber"

---

## Objective Types

| Type | Description | Config |
|---|---|---|
| `GATHER_ITEMS` | Collect resources | `countMin` (3-8), `countMax` (10-20) |
| `KILL_MOBS` | Kill hostile mobs | `countMin` (2-5), `countMax` (5-10) |
| `DELIVER_ITEM` | Bring item to NPC | none |
| `EXPLORE_LOCATION` | Visit a location | none |
| `FETCH_ITEM` | Retrieve from chest | `locationPreference`: `"DUNGEON"` or `"SETTLEMENT"` (optional) |
| `TALK_TO_NPC` | Interact with NPC | none |
| `KILL_NPC` | Eliminate target NPC | none |

- Exposition/Conflict: `objectivePool` must have 2-4 types
- Resolution: `objectivePool` MUST be `[]`

---

## Pool Value Authoring

### Item/mob pools (gather_items.json, hostile_mobs.json)
```json
{ "id": "Hytale:ItemId", "label": "display name" }
```

### Narrative pools (quest_focuses.json, quest_stakes.json, quest_threats.json)
```json
{ "value": "noun phrase without article", "plural": true/false }
```
- NO "the" prefix
- `plural` determines conjugation helpers
- 1-4 words
- Must work as both subject and object

### String pools (quest_actions.json, quest_origins.json, etc.)
```json
"verb phrase" or "full phrase"
```

### Response pools (responses_accept.json, responses_decline.json)

Keyed by situation name (preferred) AND tone (fallback):
```json
{
  "tones": {
    "desperate": ["response1", "response2", ...],
    "Supplication": ["situation-specific response1", ...]
  }
}
```

Rules for response text:
- Self-contained statements only
- NO questions expecting NPC reply (NPC counter-responses are separate)
- NO demands ("What's in it for me?")
- `{quest_action}` and `{quest_focus}` tokens allowed sparingly when natural
- Match the situation's emotional register

### Counter-response pools (responses_counter_*.json)

Keyed by tone only:
```json
{
  "tones": {
    "desperate": ["NPC counter-response1", ...],
    "bold": [...]
  }
}
```

Rules for counter-response text:
- NPC acknowledging the player's choice
- Self-contained: does not expect further player reply
- Matches the tone of the situation
- `{quest_action}`, `{quest_focus}`, `{quest_stakes}` tokens allowed sparingly
- Accept counters: grateful, acknowledging
- Decline counters: disappointed but not hostile
- Stat pass counters: impressed, reassured
- Stat fail counters: mildly disappointed but accepting, quest still proceeds

### Stat check response pool (responses_stat_check.json)

Keyed by stat type:
```json
{
  "stats": {
    "STR": ["strength-themed player line", ...],
    "DEX": ["dexterity-themed player line", ...],
    "CON": [...], "INT": [...], "WIS": [...], "CHA": [...]
  }
}
```

Rules:
- Player asserting their capability in that stat
- Self-contained, no questions
- Should not reference specific quest details (these are stat-generic)

---

## Tone Reference (all 36 situations)

| Situation | Tone |
|---|---|
| Supplication | desperate |
| Deliverance | urgent |
| CrimePursuedByVengeance | urgent |
| VengeanceOfKindred | somber |
| Pursuit | urgent |
| Disaster | urgent |
| FallingPreyToMisfortune | somber |
| Revolt | conspiratorial |
| DaringEnterprise | bold |
| Abduction | urgent |
| TheEnigma | bold |
| Obtaining | bold |
| EnemityOfKinsmen | conflicted |
| RivalryOfKinsmen | conflicted |
| MurderousAdultery | conspiratorial |
| Madness | somber |
| FatalImprudence | somber |
| InvoluntaryCrimesOfLove | conflicted |
| SlayingOfKinUnrecognized | somber |
| SelfSacrificeForAnIdeal | somber |
| SelfSacrificeForKindred | desperate |
| AllSacrificedForPassion | conflicted |
| NecessityOfSacrificingLovedOnes | conflicted |
| RivalryOfSuperiorVsInferior | conspiratorial |
| Adultery | conspiratorial |
| CrimesOfLove | conflicted |
| DiscoveryOfDishonor | urgent |
| ObstaclesToLove | conflicted |
| AnEnemyLoved | conflicted |
| Ambition | bold |
| ConflictWithAGod | somber |
| MistakenJealousy | conflicted |
| ErroneousJudgment | urgent |
| Remorse | somber |
| RecoveryOfALostOne | desperate |
| LossOfLovedOnes | somber |

---

## Checklist Before Submitting

### Template files
- [ ] No `bindings` object in any variant
- [ ] No `playerResponses` in any variant
- [ ] All `{quest_focus}`, `{quest_stakes}`, `{quest_threat}` preceded by "the"
- [ ] Conjugation helpers used when pool variable is sentence subject
- [ ] Optional variables in removable clauses
- [ ] No hardcoded names, places, items, or story specifics
- [ ] Resolution variants have empty `objectivePool` and `objectiveConfig`
- [ ] Each variant has different phrasing, not different content
- [ ] Tested mentally with 3 substitution sets
- [ ] Valid JSON

### Response pool entries
- [ ] Self-contained statements (no questions, no demands)
- [ ] Match situation/tone emotional register
- [ ] Variable tokens used sparingly and naturally
- [ ] Counter-responses don't expect further player reply
- [ ] Stat check responses are stat-themed but quest-generic
- [ ] 5 entries per situation key, 5-8 per tone key
