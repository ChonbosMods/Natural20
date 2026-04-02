# Authoring Subjects

Subjects are the nouns at the center of NPC conversation topics. When you add a subject, you're adding a thing that NPCs can talk about.

**File:** `src/main/resources/topics/pools/subject_focuses.json`

## Entry Format

```json
{
  "value": "Blackrock Mine",
  "plural": false,
  "proper": true,
  "questEligible": true,
  "concrete": true,
  "categories": ["danger", "treasure"],
  "poiType": "hostile_location",
  "questAffinities": ["KILL_MOBS", "FETCH_ITEM"]
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `value` | string | yes | Display name. Appears in topic labels, dialogue text via `{subject_focus}`, and quest waypoints. |
| `plural` | boolean | yes | Grammatically plural. Controls `{subject_focus_is}` (is/are), `{subject_focus_has}` (has/have), `{subject_focus_was}` (was/were). |
| `proper` | boolean | yes | Proper noun. Controls articles: `{subject_focus_the}` = "the collapsed mine" for common, "Blackrock Mine" for proper. |
| `questEligible` | boolean | yes | Can anchor a quest. True for physical locations a player could travel to. |
| `concrete` | boolean | no | Defaults true. False for abstract concepts. Templates with `requiresConcrete: true` skip non-concrete subjects. |
| `categories` | string[] | yes | Exactly 2 category tags. Determines which templates match. |
| `poiType` | string | yes | One of 5 POI types (see table). |
| `questAffinities` | string[] | if questEligible | Objective types that make narrative sense. |

## POI Types

| POI Type | Description | questEligible | Default questAffinities |
|----------|-------------|---------------|------------------------|
| `hostile_location` | Dangerous place: mines, ruins, caves, camps, lairs | true | `["KILL_MOBS", "FETCH_ITEM"]` |
| `settlement_npc` | Named person at a settlement | true | `["TALK_TO_NPC"]` |
| `gathering_ground` | Place where resources exist naturally | true | `["COLLECT_RESOURCES"]` |
| `landmark` | Versatile named location | true | 2-3 affinities from any type |
| `narrative_only` | Abstract concept, not visitable | false | `[]` |

### Naming Guidelines

- **hostile_location**: foreboding or abandoned: "Blackrock Mine", "The Sunken Barracks", "Greyhollow Cave"
- **settlement_npc**: real-sounding: "Old Maren", "Warden Aldric", "Farrier Dunn"
- **gathering_ground**: terrain/resource: "Silverleaf Ridge", "Copper Creek", "Stonecutter's Quarry"
- **landmark**: evocative, memorable: "Millford Bridge", "The Standing Stones", "Beacon Hill"
- **narrative_only**: lowercase descriptive: "rising prices", "the drought", "guard rotation"

### Target Distribution

| POI Type | Target % | ~Count (of 370) |
|----------|----------|-----------------|
| hostile_location | 30% | ~110 |
| narrative_only | 25% | ~95 |
| settlement_npc | 15% | ~55 |
| gathering_ground | 15% | ~55 |
| landmark | 15% | ~55 |

## Categories

16 categories in two intensity groups (matching template `reactionIntensity`):

**Intense categories** (topics that carry weight: something happened or might change):

| Category | Use for subjects about... |
|----------|--------------------------|
| `danger` | Inherently dangerous places or things: bandit hideouts, flooded mines, cliffs |
| `sighting` | Things seen or spotted: creature tracks, campfire smoke, strange figures |
| `treasure` | Hidden or valuable things: sealed doors, locked chests, sunken boats |
| `corruption` | Decay, rot, taint: blighted groves, foul water, blackened stones |
| `conflict` | Disputes, fights, tension: border outposts, old barracks, rival claims |
| `disappearance` | Missing people or things: empty huts, abandoned carts, scouts who never returned |
| `migration` | Movement of people or creatures: empty farmsteads, herds, displaced families |
| `omen` | Signs, portents, superstition: standing stones, strange dreams, burial grounds |

**Mild categories** (everyday topics: the world continuing as usual):

| Category | Use for subjects about... |
|----------|--------------------------|
| `weather` | Climate, seasons: drought, spring thaw, unseasonal weather |
| `trade` | Commerce, goods, routes: trade depots, merchant caravans, rising prices |
| `craftsmanship` | Making things, workshops: forges, kilns, smithing techniques |
| `community` | Village life, governance: town council, curfew hours, well water |
| `nature` | The wild, plants, terrain: overgrown paths, hidden springs, creek beds |
| `nostalgia` | The past, memory, tradition: old foundations, weathered statues |
| `curiosity` | Odd, unexplained: sealed doors, hollow stumps, mossy ruins |
| `festival` | Celebrations, gatherings: harvest gathering, naming ceremony, market day |

## Rules

1. **Exactly 2 categories per subject.** Pick the two most inherent. Not "what could theoretically involve this" but "what IS this about."
   - "logging camp" -> `["trade", "craftsmanship"]` (it IS a trade/craft operation)
   - "bandit hideout" -> `["danger", "conflict"]` (danger and conflict are inherent)
   - "village well" -> `["community", "nature"]`

2. **No category above 25% of total subjects.** Check distribution before adding a batch.

3. **No category below 20 subjects.** Every category needs material for stratified drawing.

4. **Danger must be inherent, not hypothetical.** "Old orchard" is not dangerous. "Flooded mine shaft" is.

5. **Use `craftsmanship`, not `craft`.** The template ID is `craftsmanship`.

6. **Subjects work as standalone UI labels.** The `value` appears as a clickable topic button. Short noun phrase: "collapsed mine", "rising prices", "The Saltway". Not a sentence or verb phrase.

7. **Mark abstract subjects `concrete: false`.** Events, conditions, social phenomena, processes. Templates with `requiresConcrete: true` won't match them.

8. **Quest-eligible subjects are places the player could visit.** "collapsed mine" yes, "rising prices" no.

9. **Quest-eligible subjects must be named POIs.** The `value` becomes the quest waypoint label. "Blackrock Mine" works on a compass. "collapsed mine" doesn't.

10. **Quest affinities constrain objective types.** A mine with `["KILL_MOBS", "FETCH_ITEM"]` never generates TALK_TO_NPC. An NPC with `["TALK_TO_NPC"]` never generates a kill quest.

## How Subjects Flow Through the System

1. **Drawing**: `TopicGenerator` draws subjects via stratified category cycling (not flat random). Shuffles 8 intense + 8 mild categories, draws one subject per category slot for breadth.
2. **Collision check**: No duplicate subjects within a settlement. Redraws up to 3 times on collision.
3. **Template matching**: Subject's categories determine which template it pairs with. `TopicTemplateRegistry.randomTemplateForSubject()` filters templates whose `id` matches one of the subject's 2 categories, picks one at random.
4. **Variable substitution**: Subject's `value` fills `{subject_focus}` and its article/conjugation variants throughout the template's dialogue.
5. **Quest attachment**: If selected as quest bearer and quest-eligible, a quest is generated. Non-eligible subjects get swapped.

## File Organization

Group entries by POI type:
1. `hostile_location` entries
2. `gathering_ground` entries
3. `landmark` entries
4. `settlement_npc` entries
5. `narrative_only` entries (abstract, `questEligible: false`)
