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
  collect_resources.json     # Gatherable items: { "id": "...", "label": "..." }
  evidence_items.json        # Evidence items: { "id": "...", "label": "..." }
  keepsake_items.json        # Keepsake items: { "id": "...", "label": "..." }
  hostile_mobs.json          # Mobs: { "id": "...", "label": "..." }
  quest_actions.json         # Verb phrases (string array)
  quest_focuses.json         # Noun phrases: { "value": "...", "plural": bool, "proper": bool }
  quest_stakes.json          # Noun phrases: { "value": "...", "plural": bool, "proper": bool }
  quest_stakes_abstract.json # Abstract stakes (string array)
  quest_stakes_human.json    # Human-centric stakes (string array)
  quest_threats.json         # Noun phrases: { "value": "...", "plural": bool, "proper": bool }
  quest_threats_abstract.json # Abstract threats (string array)
  quest_threats_animate.json # Animate threats (string array)
  quest_origins.json         # Backstory phrases (string array)
  quest_time_pressures.json  # Urgency phrases (string array)
  quest_reward_hints.json    # Reward phrases (string array)

  # Player response pools (keyed by situation name + tone fallback)
  responses_accept.json            # Player accept lines
  responses_decline.json           # Player decline lines
  responses_stat_check.json        # Player stat-gated lines (keyed by stat type)

  # NPC counter-response pools (keyed by tone)
  responses_counter_accept.json    # Quest giver reacts to accept
  responses_counter_decline.json   # Quest giver reacts to decline
  responses_counter_stat_pass.json # Quest giver reacts to stat pass
  responses_counter_stat_fail.json # Quest giver reacts to stat fail

  # TALK_TO_NPC pools (keyed by situation name + tone fallback)
  responses_send_to_npc.json       # Quest giver sends player to target NPC
  responses_target_npc.json        # Target NPC dialogue (info + handoff sections)

  # Mapping
  situation_tones.json             # Situation name → tone mapping
```

---

## Conversation Flows

### Quest Acceptance Flow

When a quest phase is presented, the player sees 3 options:

```
[ACCEPT]                    → no disposition change
[STR/DEX/CON/INT/WIS/CHA]  → stat roll required
[DECLINE]                   → negative disposition
```

The quest giver counter-responds (max 2 exchanges total):

| Player choice | Quest giver reaction | Disposition |
|---|---|---|
| Accept | Grateful acknowledgment | No change |
| Stat check pass | Impressed reaction | + positive |
| Stat check fail | Mild disappointment (quest still accepted) | - negative |
| Decline | Disappointed reaction | - negative, - reputation |
| Quest completion | (on return) | + positive, + reputation |

### TALK_TO_NPC Flow

When a TALK_TO_NPC objective is rolled, the full flow is:

**Step 1: Quest giver sends player** (from `responses_send_to_npc.json`)
```
[Quest Giver Name]
  "Find {target_npc} {location_hint}. They know about the {quest_focus}..."
```

**Step 2: Player travels to target NPC**

**Step 3: Target NPC dialogue** (from `responses_target_npc.json`)

75% chance: **HANDOFF** (target NPC gives a sub-objective)
```
[Target NPC Name]
  "I can help with the {quest_focus}, but I need {quest_item} first.
   Bring some from {location_hint}..."
  → Player completes sub-objective (GATHER/KILL/FETCH/EXPLORE)
  → Player returns to original quest giver
```

25% chance: **INFO ONLY** (target NPC shares intelligence)
```
[Target NPC Name]
  "The {quest_focus} is in worse shape than {quest_giver_name} realizes.
   The {quest_threat} has been accelerating..."
  → Objective complete
  → Player returns to quest giver
```

**Step 4: Quest giver's next phase acknowledges the visit** (via existing phase variant dialogue that references `{target_npc}`)

---

## Variant JSON Schema

### Exposition and Conflict variants

```json
{
  "variants": [
    {
      "id": "situation_phase_01",
      "dialogueChunks": {
        "intro": "Opening line. Sets emotional tone.",
        "plotStep": "The core ask. Uses variables for specifics.",
        "outro": "Closing line. Motivates player to act."
      },
      "objectivePool": ["COLLECT_RESOURCES", "KILL_MOBS", "FETCH_ITEM"],
      "objectiveConfig": {
        "COLLECT_RESOURCES": { "countMin": 5, "countMax": 12 },
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

- **No `playerResponses`**: drawn from response pools
- **No `bindings`**: all narrative variables from external pools
- **No hardcoded names/places/items**: all specifics are `{variable}` tokens

---

## Variable Reference

### Always Available

| Token | Type | Example | Usage |
|---|---|---|---|
| `{player_name}` | proper noun | "Adventurer" | Direct: `"{player_name}, I need help."` |
| `{enemy_type}` | noun | "Trork Brute" | Direct: `"The {enemy_type} attacked."` |
| `{quest_item}` | lowercase noun | "leather" | Direct: `"Gather {quest_item}."` |
| `{location_hint}` | phrase | "north-west, about 200 blocks" | Direct: `"Head {location_hint}."` |
| `{quest_action}` | verb phrase | "hold the line" | Direct: `"We need to {quest_action}."` |
| `{quest_giver_name}` | proper noun | "Aldric" | Direct: `"Tell {quest_giver_name} what you found."` |
| `{quest_focus}` | bare noun | "old watchtower" | Use `_the` suffix: `"at {quest_focus_the}"` |
| `{quest_stakes}` | bare noun | "refugee families" | Use `_the` suffix: `"{quest_stakes_the} depend on us"` |
| `{quest_threat}` | bare noun | "advancing raiders" | Use `_the` suffix: `"{quest_threat_the} grows worse"` |

### Article Helpers (`_the` suffix)

Pool entries for `quest_focus`, `quest_stakes`, and `quest_threat` have a `proper` boolean. The system generates article-aware bindings:

| Suffix | Resolves to | Example (common) | Example (proper) |
|---|---|---|---|
| `_the` | mid-sentence "the X" or proper noun | "the old watchtower" | "Thornfield" |
| `_The` | sentence-initial "The X" or proper noun | "The old watchtower" | "Thornfield" |
| (none) | raw value, no article | "old watchtower" | "Thornfield" |

**Prefer `_the`/`_The` over manually writing "the".** This handles proper nouns correctly:
```
GOOD: "We must protect {quest_focus_the}."
      → "We must protect the old watchtower."
      → "We must protect Thornfield."

BAD:  "We must protect the {quest_focus}."
      → "We must protect the old watchtower."  (ok)
      → "We must protect the Thornfield."      (WRONG)
```

For sentence-initial position:
```
GOOD: "{quest_focus_The} {quest_focus_is} under threat."
      → "The old watchtower is under threat."
      → "Thornfield is under threat."
```

### Verb Conjugation Helpers

| Token | Singular | Plural |
|---|---|---|
| `{quest_focus_is}` / `{quest_focus_has}` / `{quest_focus_was}` | is / has / was | are / have / were |
| `{quest_stakes_is}` / `{quest_stakes_has}` / `{quest_stakes_was}` | is / has / was | are / have / were |
| `{quest_threat_is}` / `{quest_threat_has}` / `{quest_threat_was}` | is / has / was | are / have / were |

Usage: `"{quest_stakes_The} {quest_stakes_is} in danger."` → "The refugee families are in danger." OR "The harvest is in danger."

### Optional Variables (stripped cleanly if unresolved)

| Token | Example | Notes |
|---|---|---|
| `{target_npc}` | "Elara Thornwick" | Required for TALK_TO_NPC |
| `{quest_ally}` | "Old Maren" | NPC in same settlement |
| `{quest_origin}` | "last winter's famine" | Full phrase with article |
| `{quest_time_pressure}` | "before the next storm" | Full phrase |
| `{quest_reward_hint}` | "my family's heirloom" | Full phrase |
| `{location}` | "Ironhaven" | Settlement name |

### Response Bindings (resolved by generator, used by dialogue system)

| Token | Source |
|---|---|
| `{response_accept}` | Accept response pool |
| `{response_decline}` | Decline response pool |
| `{response_stat_check}` | Stat check response pool |
| `{stat_check_type}` | Random stat (STR/DEX/CON/INT/WIS/CHA) |
| `{counter_accept}` | Quest giver counter to accept |
| `{counter_decline}` | Quest giver counter to decline |
| `{counter_stat_pass}` | Quest giver counter to stat pass |
| `{counter_stat_fail}` | Quest giver counter to stat fail |
| `{send_to_npc_dialogue}` | Quest giver sends player to target NPC |
| `{target_npc_dialogue}` | Target NPC info or handoff dialogue |
| `{talk_npc_is_handoff}` | "true" or "false" |

---

## Authoring Rules

### Rule 1: Use `_the` suffix for article-aware references

```
GOOD: "We must protect {quest_focus_the}."
BAD:  "We must protect the {quest_focus}."   ← breaks on proper nouns
BAD:  "We must protect {quest_focus}."       ← missing article for common nouns
```

### Rule 2: Use conjugation helpers when pool variable is sentence subject

```
GOOD: "{quest_threat_The} {quest_threat_has} grown worse."
BAD:  "The {quest_threat} has grown worse."
```

### Rule 3: Respect semantic roles

| Variable | Role | Use as... |
|---|---|---|
| `{quest_focus}` | PLACE/THING | "at {quest_focus_the}", "near {quest_focus_the}" |
| `{quest_stakes}` | AT-RISK | "{quest_stakes_the} will suffer" |
| `{quest_threat}` | DANGER | "{quest_threat_the} grows stronger" |
| `{quest_action}` | TASK | "we need to {quest_action}" |

### Rule 4: Optional variables in removable clauses

```
GOOD: "Ever since {quest_origin}, things have been difficult."
      → stripped: "things have been difficult."

BAD:  "The {quest_origin} caused all of this."
      → stripped: "The caused all of this."
```

### Rule 5: Situation = tone, NOT content

### Rule 6: Each variant = different phrasing, NOT different story

### Rule 7: No hardcoded names, places, or specifics

Use `{quest_giver_name}` for the quest giver, `{target_npc}` for target NPCs, `{quest_ally}` for allies. Never write literal NPC names.

### Rule 8: Test with 3 substitution sets

**Set A:** action="seal the entrance", focus="collapsed mine" (common), stakes="village children" (plural), threat="spreading blight", enemy="Spider", item="bone", quest_giver_name="Aldric", target_npc="Elara Thornwick"

**Set B:** action="protect the caravan", focus="river crossing" (common), stakes="winter supplies" (plural), threat="deserter gangs" (plural), enemy="Trork Grunt", item="wheat", quest_giver_name="Berta", target_npc="Captain Dregg"

**Set C (proper nouns):** action="root out the corruption", focus="Thornfield" (proper), stakes="last of our iron", threat="Blackthorn Company" (proper, plural), enemy="Skeleton", item="cotton fiber", quest_giver_name="Miriel", target_npc="Finn Copperhand"

Set C specifically tests `_the` with proper nouns: `{quest_focus_the}` → "Thornfield" (not "the Thornfield"), `{quest_threat_the}` → "Blackthorn Company" (not "the Blackthorn Company").

---

## Objective Types

| Type | Description | Config |
|---|---|---|
| `COLLECT_RESOURCES` | Collect resources | `countMin` (3-8), `countMax` (10-20) |
| `KILL_MOBS` | Kill hostile mobs | `countMin` (2-5), `countMax` (5-10) |
| `FETCH_ITEM` | Retrieve from chest | `locationPreference`: `"DUNGEON"`/`"SETTLEMENT"` (optional) |
| `TALK_TO_NPC` | Talk to target NPC | none (triggers send-to-NPC + target NPC dialogue flow) |
| `KILL_NPC` | Eliminate target NPC | none |

- Exposition/Conflict: `objectivePool` must have 2-4 types
- Resolution: `objectivePool` MUST be `[]`

---

## Pool Value Authoring

### Item/mob pools
```json
{ "id": "Hytale:ItemId", "label": "display name" }
```

### Narrative pools (focuses, stakes, threats)
```json
{ "value": "noun phrase without article", "plural": true/false, "proper": true/false }
```
- `plural`: controls conjugation helpers (`_is`/`_has`/`_was`)
- `proper`: controls article generation (`_the`/`_The`). Set `true` for named places/people, `false` for common nouns

### String pools (actions, origins, time pressures, reward hints)
```json
"verb phrase" or "full phrase"
```

### Response pools (accept, decline, send-to-NPC, target NPC)

All response pools use situation-specific keys with tone fallback:
```json
{
  "tones": {
    "desperate": ["tone fallback line 1", ...],
    "Supplication": ["situation-specific line 1", ...]
  }
}
```

The generator checks for a situation key first (e.g., `"Supplication"`). If not found, it falls back to the situation's mapped tone (e.g., `"desperate"`).

**Rules for all response/dialogue pool entries:**
- Use `{quest_giver_name}` for the quest giver (NEVER "quest giver" or "your contact")
- Use `{target_npc}` for the target NPC
- Use `{quest_focus_the}`, `{quest_stakes_the}`, `{quest_threat_the}` for article-aware references (NEVER manually write "the {quest_focus}")
- Use `{quest_focus_The}` etc. for sentence-initial position
- Use conjugation helpers (`_is`/`_has`/`_was`) when these variables are sentence subjects
- Self-contained: no questions expecting replies (except handoff sub-objectives)
- Match the situation's emotional register

**Voice rules (applies to all NPC dialogue lines; see also `docs/authoring-detail-responses.md` for encyclopedic voice and bias patterns):**
- Frame information as hearsay where natural: "I've heard," "They say," "Word is." NPCs share gossip, not omniscient narration
- Play mundane observations straight: NPCs who mention pests, weather, or road conditions do so with genuine conviction, not sarcasm
- Allow incomplete thoughts and hedging: "It's probably nothing, but..." and trailing uncertainty make NPCs feel like real people thinking out loud (use sparingly: ~15-20% of entries)
- Match disposition without caricature: desperate quest givers sound strained, not melodramatic. Bold ones sound confident, not arrogant
- No editorial framing: avoid "interestingly," "believe it or not," "funny story." The NPC is talking, not narrating
- Let NPCs be biased: when mentioning third parties (the council, guards, traders), give the NPC an opinion about them. "The council finally did something" vs "the council overreacted again." Neutral reporting is for history books
- Don't smooth disposition extremes: hostile quest givers are genuinely resentful or contemptuous, not merely curt. Desperate ones sound strained and raw, not politely concerned

### Target NPC pool structure (`responses_target_npc.json`)

This file has TWO sections, not one:

```json
{
  "info": {
    "desperate": ["info-only lines..."],
    "Supplication": ["situation-specific info lines..."]
  },
  "handoff": {
    "desperate": ["handoff lines ending with sub-objective..."],
    "Supplication": ["situation-specific handoff lines..."]
  }
}
```

**Info entries** (25% chance): 2-3 sentences sharing intelligence about the quest. Reference `{quest_giver_name}` by name. End with directing the player to return.

**Handoff entries** (75% chance): 2-3 sentences ending with giving the player a sub-objective (gather `{quest_item}`, kill `{enemy_type}`, explore `{location_hint}`). Reference `{quest_giver_name}` by name.

### Counter-response pools

Keyed by tone only (no situation-specific entries):
```json
{ "tones": { "desperate": [...], "bold": [...], ... } }
```

### Stat check response pool

Keyed by stat type:
```json
{ "stats": { "STR": [...], "DEX": [...], ... } }
```

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
- [ ] All `{quest_focus}`, `{quest_stakes}`, `{quest_threat}` use `_the`/`_The` suffixes (never manual "the")
- [ ] Conjugation helpers (`_is`/`_has`/`_was`) used when pool variable is sentence subject
- [ ] Optional variables in removable clauses
- [ ] No hardcoded names (use `{quest_giver_name}`, `{target_npc}`, `{quest_ally}`)
- [ ] Resolution variants have empty `objectivePool` and `objectiveConfig`
- [ ] Each variant has different phrasing, not different content
- [ ] Tested mentally with 3 substitution sets (including at least one proper noun focus)
- [ ] Valid JSON

### Response/dialogue pool entries
- [ ] Uses `{quest_giver_name}` (never "quest giver")
- [ ] Uses `_the`/`_The` suffixes for focus/stakes/threat references (never manual "the")
- [ ] Self-contained statements (no unanswered questions)
- [ ] Matches situation/tone emotional register
- [ ] Variable tokens used with proper conjugation helpers
- [ ] Target NPC handoff entries end with a clear sub-objective
- [ ] Target NPC info entries direct player to return to `{quest_giver_name}`
- [ ] 3-5 entries per situation key, 5-8 per tone key

### Narrative pool entries
- [ ] `value` is a bare noun phrase without article
- [ ] `plural` flag matches the noun's number
- [ ] `proper` flag is true for named places/people, false for common nouns
