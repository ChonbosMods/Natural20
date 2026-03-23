# Dialogue Pool Architecture v2

**Date:** 2026-03-23
**Scope:** Fix incoherent exploratory chains, enforce one-fragment-per-intro rule, add topic-matched pools, add `_the` article suffix system

---

## 1. Problem

Current templates stuff multiple independently-sampled fragment variables into intro fields, producing word salad:

> "People have been saying. I've been busy at the workbench just before dawn. a caravan guard quit on the spot rather than take the western route A craftsman's hands are never idle for long. Take that for what it's worth."

This is `{tone_opener}` + static text + `{time_ref}` + `{trade_gossip}` + static text + `{tone_closer}`. Four independent thoughts with no connective tissue. The fragments are individually well-written but concatenation destroys coherence.

Additionally, exploratory follow-ups use the wrong Layer 1 pools: a weather template responds with trade economics, a craftsmanship template responds with paranormal phenomena. This is an authoring wiring bug, not a design flaw.

---

## 2. Two-Tier Assembly Rule

### Intros: Strict Three-Part, No Assembly

Every perspective intro resolves to exactly three parts:

```
"{tone_opener} {topic_fragment}. {tone_closer}"
```

One L0 fragment carries the entire thought. No `{time_ref}`, `{direction}`, `{traveler_news}`, or other drop-ins in the intro. If a fragment needs temporal or directional context, it is authored into the fragment itself: "A few days back, someone spotted tracks along the eastern ridge" -- not assembled from parts.

### Exploratories: Assembly Allowed

Drop-in variables (`{time_ref}`, `{direction}`, `{rumor_source}`) live at the exploratory level alongside L1 fragments. The player's question constrains expectations, making multi-variable assembly coherent:

```
"When was this?" -> "{time_ref}. {weather_detail}"
"Where exactly?" -> "{location_detail}"
"Who told you?" -> "{rumor_source}"
```

### Combinatorial Math

The intro goes from ~9.7 million combinations to roughly 10 openers x 40 fragments x 8 closers = 3,200 per template. Across 5-8 templates per topic category: 16,000-25,000 unique intros. More than sufficient since no player hears even a fraction.

The real variety compounds at Layer 1 and 2 through exploratory branches. A single exploratory response of `"{time_ref}. {creature_detail}"` is 15 x 20 = 300 unique responses for one branch. Stack three exploratories per template, each assembling from different pool combinations, and you are back in the millions across the full conversation tree.

The intro is a hook, not a novel. Depth comes through interaction.

---

## 3. Pool Architecture

### Layer 0 Pools (12 total: 3 existing, 9 new)

Each template topic has exactly one matching L0 pool. One fragment per intro, tone-framed.

| L0 Pool | Serves Templates | ~Entries | Status |
|---|---|---|---|
| `creature_sightings` | rumor_danger, rumor_sighting, rumor_migration | 30+ | existing |
| `strange_events` | rumor_disappearance, rumor_corruption, rumor_omen | 25+ | existing |
| `trade_gossip` | smalltalk_trade | 20+ | existing |
| `weather_observations` | smalltalk_weather | 12 | new |
| `craft_observations` | smalltalk_craftsmanship | 12 | new |
| `community_observations` | smalltalk_community | 12 | new |
| `nature_observations` | smalltalk_nature | 12 | new |
| `nostalgia_observations` | smalltalk_nostalgia | 12 | new |
| `curiosity_observations` | smalltalk_curiosity | 12 | new |
| `festival_observations` | smalltalk_festival | 12 | new |
| `treasure_rumors` | rumor_treasure | 12 | new |
| `conflict_rumors` | rumor_conflict | 12 | new |

**Demoted to exploratory-only:** `local_complaints`, `traveler_news`. No longer used in intros, available for exploratory assembly where topically appropriate.

### Layer 1 Pools (13 total: 4 existing, 9 new)

Each template topic has exactly one matching L1 pool. Clean 1:1 mapping with L0.

| L1 Pool | Paired With L0 | ~Entries | Status |
|---|---|---|---|
| `creature_details` | creature_sightings | 20+ | existing |
| `event_details` | strange_events | 25+ | existing |
| `trade_details` | trade_gossip | 20+ | existing |
| `location_details` | cross-cutting "where?" | 20+ | existing |
| `weather_details` | weather_observations | 17 | new |
| `craft_details` | craft_observations | 17 | new |
| `community_details` | community_observations | 17 | new |
| `nature_details` | nature_observations | 17 | new |
| `nostalgia_details` | nostalgia_observations | 17 | new |
| `curiosity_details` | curiosity_observations | 17 | new |
| `festival_details` | festival_observations | 17 | new |
| `treasure_details` | treasure_rumors | 17 | new |
| `conflict_details` | conflict_rumors | 17 | new |

`location_details` is the one cross-cutting L1 pool: no dedicated L0 counterpart, used for "where?" exploratories across all templates.

**Wiring rule:** Template `{topic}` uses `{topic}_observation` (or `_sighting`/`_rumor`) at L0, `{topic}_detail` at L1. The template-to-pool wiring table is trivial: `rumor_creature` uses `creature_sightings` at L0 and `creature_details` at L1. `smalltalk_weather` uses `weather_observations` at L0 and `weather_details` at L1.

### Layer 2 Pools (3, unchanged)

| L2 Pool | Description |
|---|---|
| `local_opinions` | What people think. Topic-agnostic. |
| `personal_reactions` | NPC's own feelings. Topic-agnostic. |
| `danger_assessments` | Risk evaluation. Topically restricted. |

**L2 validity per template category:**
- **Rumors (all 8) + smalltalk_nature + smalltalk_curiosity:** all three L2 pools
- **Smalltalk weather/craft/community/nostalgia/festival:** `local_opinions` and `personal_reactions` only. No `danger_assessments`.

### Content Budget

- 9 new L0 pools x ~12 entries = ~108 fragments
- 9 new L1 pools x ~17 entries = ~153 fragments
- **Total: ~261 new fragments**

---

## 4. Full Wiring Table

| Template | L0 Intro Var | L1 Detail Var | L1 Cross-cutting | L2 Vars |
|---|---|---|---|---|
| rumor_danger | `creature_sighting` | `creature_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_sighting | `creature_sighting` | `creature_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_migration | `creature_sighting` | `creature_detail` | `location_detail` | all 3 |
| rumor_disappearance | `strange_event` | `event_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_corruption | `strange_event` | `event_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_omen | `strange_event` | `event_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_treasure | `treasure_rumor` | `treasure_detail` | `location_detail`, `rumor_source` | all 3 |
| rumor_conflict | `conflict_rumor` | `conflict_detail` | `location_detail`, `rumor_source` | all 3 |
| smalltalk_trade | `trade_gossip` | `trade_detail` | `location_detail` | opinions, reactions |
| smalltalk_weather | `weather_observation` | `weather_detail` | `location_detail` | opinions, reactions |
| smalltalk_craft | `craft_observation` | `craft_detail` | `location_detail` | opinions, reactions |
| smalltalk_community | `community_observation` | `community_detail` | `location_detail` | opinions, reactions |
| smalltalk_nature | `nature_observation` | `nature_detail` | `location_detail`, `creature_detail` | all 3 |
| smalltalk_nostalgia | `nostalgia_observation` | `nostalgia_detail` | `location_detail` | opinions, reactions |
| smalltalk_curiosity | `curiosity_observation` | `curiosity_detail` | `location_detail`, `event_detail` | all 3 |
| smalltalk_festival | `festival_observation` | `festival_detail` | `location_detail` | opinions, reactions |

**Note on rumor template duplication:** rumor_danger, rumor_sighting, and rumor_migration share identical wiring (same L0, L1, cross-cutting, L2). They differentiate through template text only: danger frames the creature as a threat, sighting as a mystery, migration as an ecological event. Same applies to the three event-based rumors (disappearance, corruption, omen). Shared wiring, different narrative angles.

### Reusable Exploratory Prompt Patterns

- "Who told you?" -> `{rumor_source}` (rumors only)
- "When was this?" -> `{time_ref}. {topic_detail}` (assembly)
- "Where exactly?" -> `{location_detail}` (cross-cutting)
- "Tell me more." -> `{topic_detail}` (topic-matched L1)
- "What do people think?" -> `{local_opinion}` (L2)
- "How do you feel about it?" -> `{personal_reaction}` (L2)
- "Is it dangerous?" -> `{danger_assessment}` (L2, rumors + nature + curiosity only)

---

## 5. The `_the` Suffix System

### Problem

Template authors must handle article insertion manually. "Have you been to mountain trail lately?" needs "the" but "Have you been to Thornfield lately?" does not. The current system has no way to express this.

### Solution

Add a `proper` boolean to pool entries alongside the existing `plural` flag. Generate `_the` and `_The` bindings in the same loop that generates `_is`/`_has`/`_was`.

### Pool Entry Format

```json
{ "value": "mountain trail", "plural": false, "proper": false, "questEligible": true }
{ "value": "Thornfield", "plural": false, "proper": true, "questEligible": true }
```

### Binding Generation

```java
// Existing:
bindings.put("subject_focus", focus.subjectValue());
bindings.put("subject_focus_is", focus.plural() ? "are" : "is");
bindings.put("subject_focus_has", focus.plural() ? "have" : "has");
bindings.put("subject_focus_was", focus.plural() ? "were" : "was");

// New:
bindings.put("subject_focus_the", focus.proper() ? focus.subjectValue() : "the " + focus.subjectValue());
bindings.put("subject_focus_The", focus.proper() ? focus.subjectValue() : "The " + focus.subjectValue());
```

`_the` for mid-sentence, `_The` for sentence-initial. Proper nouns resolve identically for both (already capitalized).

### Template Usage

```
"Have you been to {subject_focus_the} lately?"
-> "Have you been to the mountain trail lately?"
-> "Have you been to Thornfield lately?"

"{subject_focus_The} {subject_focus_is} not what it used to be."
-> "The mountain trail is not what it used to be."
-> "Thornfield is not what it used to be."

"{subject_focus} could be worth checking out."
-> "Mountain trail could be worth checking out."  (raw, no article)
```

### Quest Variable Extension

`{quest_focus}`, `{quest_stakes}`, `{quest_threat}` already have the `plural` flag on their pool entries. Adding `proper` and generating `_the`/`_The` variants cleans up quest dialogue too: authors use `{quest_focus_the}` instead of manually writing "the {quest_focus}" and hoping it is not a proper noun.

---

## 6. Authoring Rules

### Fragment Rules (unchanged from v1, reinforced)

1. Fragments are complete thoughts. Every entry reads naturally as something an NPC would say out loud.
2. Fragments are tone-neutral. No baked-in friendliness or hostility.
3. Fragments are biome-neutral. Universal terrain features only.

### New Rules

4. **One fragment per intro.** Intros use exactly: `{tone_opener} {L0_fragment}. {tone_closer}`. No stacking.
5. **Assembly only in exploratories.** Drop-in variables (`{time_ref}`, `{direction}`, `{rumor_source}`) combine with fragments only in exploratory responses, where the player's question constrains expectations.
6. **1:1 pool-to-template mapping at L0 and L1.** Each template uses its topic-matched pool. No cross-wiring.
7. **`location_details` is the one cross-cutting L1 pool.** Valid for "where?" exploratories in any template.
8. **L2 restriction.** `danger_assessments` only in rumors, nature, and curiosity. Other smalltalk templates use `local_opinions` and `personal_reactions` only.
9. **`strange_events` vs `curiosity_observations`.** `strange_events` imply danger or the uncanny (rumor territory). `curiosity_observations` are odd but not threatening (smalltalk territory).
10. **`{time_ref}` entries must be complete opening clauses.** "About three days ago", "Sometime last week", "Not long before the last rain". Not bare words like "yesterday" that sound abrupt before a period.
11. **Use `{subject_focus_the}` / `{subject_focus_The}` for article-aware references.** `_the` for mid-sentence, `_The` for sentence-initial. Raw `{subject_focus}` when no article is needed.

---

## 7. Implementation Scope

### Java Changes (3 files)

1. **`SubjectFocus.java`**: Add `boolean proper` field. Update constructor and deserialization.

2. **`TopicPoolRegistry.java`**: Add 18 new accessor methods (9 L0 + 9 L1). Load 18 new JSON pool files.

3. **`TopicGenerator.buildBindings()`**:
   - Add 18 new bindings (9 L0 + 9 L1 fragment variables)
   - Add `_the` and `_The` suffix generation for `subject_focus` (and quest variables)
   - `{time_ref}` and `{direction}` remain as bindings (used in exploratory assembly), but are no longer placed in intro templates

### Pool Content (18 new JSON files)

- 9 new L0 pools x ~12 entries = ~108 fragments
- 9 new L1 pools x ~17 entries = ~153 fragments

### Template Rewrites (2 files)

- `SmallTalk/templates.json`: Rewrite all 8 templates. Strip intros to three-part pattern, rewire exploratories per wiring table, restrict L2 per category rules.
- `Rumors/templates.json`: Rewrite all 8 templates. Same pattern.

### Pool Entry Updates

- `subject_focuses.json`: Add `proper` field to all entries.
- Quest pool files: Add `proper` field where applicable.

### Not in Scope

- Biome-specific pool variants (deferred)
- Live disposition tone resolution (deferred)
- New L2 pools (current three + restrictions are sufficient)
- Repetition tracking (pool size handles it statistically)
