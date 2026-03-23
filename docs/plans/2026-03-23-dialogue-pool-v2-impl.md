# Dialogue Pool Architecture v2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix incoherent dialogue chains by enforcing one-fragment-per-intro, adding 18 topic-matched pools, rewiring all templates, and adding `_the`/`_The` article suffix bindings.

**Architecture:** Add `proper` boolean to SubjectFocus and quest NarrativeEntry. Register 18 new pool files (9 L0 + 9 L1) in TopicPoolRegistry with typed accessors. Generate 18 new bindings plus `_the`/`_The` suffixes in TopicGenerator.buildBindings(). Rewrite all 16 templates to use strict three-part intros and correctly-wired exploratories.

**Tech Stack:** Java 25 (Hytale server plugin), JSON pool files, Gradle build

**Design doc:** `docs/plans/2026-03-23-dialogue-pool-architecture-v2.md`

**No test framework exists in this project.** Verification is `./gradlew compileJava` for code and dev server smoke tests for content.

---

### Task 1: Add `proper` field to SubjectFocus and SubjectEntry

**Files:**
- Modify: `src/main/java/com/chonbosmods/topic/SubjectFocus.java`
- Modify: `src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java` (SubjectEntry record and parseSubjects)
- Modify: `src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java` (NarrativeEntry record)

**Step 1: Add `proper` field to SubjectFocus**

Add the field, constructor parameter, and getter:

```java
// In SubjectFocus.java, add field after line 14:
private final boolean proper;

// Update constructor (line 21) to accept proper:
public SubjectFocus(String subjectId, String subjectValue, boolean plural, boolean proper,
                    boolean questEligible, TopicCategory category) {
    this.subjectId = subjectId;
    this.subjectValue = subjectValue;
    this.plural = plural;
    this.proper = proper;
    this.questEligible = questEligible;
    this.category = category;
}

// Add getter after line 42:
public boolean isProper() { return proper; }
```

**Step 2: Update SubjectEntry record in TopicPoolRegistry**

```java
// Line 19: add proper field
public record SubjectEntry(String value, boolean plural, boolean proper, boolean questEligible) {}
```

Update `parseSubjects()` (line 225-234):
```java
private void parseSubjects(JsonObject root) {
    for (JsonElement el : root.getAsJsonArray("subjects")) {
        JsonObject obj = el.getAsJsonObject();
        subjectFocuses.add(new SubjectEntry(
            obj.get("value").getAsString(),
            obj.has("plural") && obj.get("plural").getAsBoolean(),
            obj.has("proper") && obj.get("proper").getAsBoolean(),
            obj.has("questEligible") && obj.get("questEligible").getAsBoolean()
        ));
    }
}
```

**Step 3: Update NarrativeEntry in QuestPoolRegistry**

```java
// Line 31: add proper field
public record NarrativeEntry(String value, boolean plural, boolean proper) {}
```

Update any parsing code in QuestPoolRegistry that constructs NarrativeEntry to read `proper` from JSON (same pattern: `obj.has("proper") && obj.get("proper").getAsBoolean()`).

**Step 4: Fix all callers of SubjectFocus constructor**

In `TopicGenerator.java`, update every `new SubjectFocus(...)` call (lines 74, 104, 412) to pass `entry.proper()`:
```java
// Line 74:
subjects.add(new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(), entry.questEligible(), category));

// Line 104:
subjects.set(qi, new SubjectFocus(newId, eligible.value(), eligible.plural(), eligible.proper(),
    eligible.questEligible(), focus.getCategory()));

// Line 412:
SubjectFocus newFocus = new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(), entry.questEligible(), category);
```

**Step 5: Verify compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/topic/SubjectFocus.java src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java src/main/java/com/chonbosmods/topic/TopicGenerator.java src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java
git commit -m "refactor: add proper field to SubjectFocus, SubjectEntry, and NarrativeEntry"
```

---

### Task 2: Generate `_the` and `_The` bindings in TopicGenerator

**Files:**
- Modify: `src/main/java/com/chonbosmods/topic/TopicGenerator.java` (buildBindings method, lines 298-372)

**Step 1: Add subject_focus _the/_The bindings**

After line 306 (existing `subject_focus_was` binding), add:
```java
// Article-aware bindings
bindings.put("subject_focus_the", focus.isProper() ? focus.getSubjectValue() : "the " + focus.getSubjectValue());
bindings.put("subject_focus_The", focus.isProper() ? focus.getSubjectValue() : "The " + focus.getSubjectValue());
```

**Step 2: Add quest variable _the/_The bindings**

After the quest_threat suffix block (lines 351-354), add:
```java
bindings.put("quest_threat_the", threat.proper() ? threat.value() : "the " + threat.value());
bindings.put("quest_threat_The", threat.proper() ? threat.value() : "The " + threat.value());
```

After the quest_stakes suffix block (lines 357-360), add:
```java
bindings.put("quest_stakes_the", stakes.proper() ? stakes.value() : "the " + stakes.value());
bindings.put("quest_stakes_The", stakes.proper() ? stakes.value() : "The " + stakes.value());
```

**Step 3: Verify compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/topic/TopicGenerator.java
git commit -m "feat: generate _the and _The article suffix bindings for subject and quest variables"
```

---

### Task 3: Add `proper` field to pool entry JSON files

**Files:**
- Modify: `src/main/resources/topics/pools/subject_focuses.json`

**Step 1: Add `proper: false` to all entries**

All current entries are common nouns (old watchtower, collapsed mine, etc.), so all get `"proper": false`. Example:
```json
{ "value": "old watchtower", "plural": false, "proper": false, "questEligible": true }
```

Apply to all 34 entries. No entries are currently proper nouns (no settlement names yet), but the field must be present for the parser.

**Step 2: Add `proper` to quest pool JSON files**

Find the quest pool files that define threat/stakes entries (check `src/main/resources/quests/pools/` for files parsed by QuestPoolRegistry). Add `"proper": false` to every entry (current quest threats and stakes are all common nouns).

**Step 3: Verify compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 4: Commit**

```bash
git add src/main/resources/topics/pools/subject_focuses.json src/main/resources/quests/pools/
git commit -m "content: add proper field to subject focus and quest pool entries"
```

---

### Task 4: Create 9 new L0 pool files

**Files:**
- Create: `src/main/resources/topics/pools/weather_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/craft_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/community_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/nature_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/nostalgia_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/curiosity_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/festival_observations.json` (12 entries)
- Create: `src/main/resources/topics/pools/treasure_rumors.json` (12 entries)
- Create: `src/main/resources/topics/pools/conflict_rumors.json` (12 entries)

**Authoring rules for all L0 entries:**
1. Complete thoughts: reads naturally as something an NPC would say
2. Tone-neutral: no friendliness or hostility baked in
3. Biome-neutral: universal terrain features only (ridges, ruins, paths, walls, water sources)
4. Self-contained: temporal and directional context baked in if needed (no relying on `{time_ref}` or `{direction}`)
5. **`strange_events` vs `curiosity_observations`:** strange_events imply danger or the uncanny. curiosity_observations are odd but not threatening

**Format:** Simple JSON string array:
```json
[
  "Entry one here",
  "Entry two here"
]
```

**Example entries per pool:**

`weather_observations.json`:
- "The fog rolled in three mornings straight and would not lift until past noon"
- "A dry spell settled over the area and the wells have been dropping fast"

`craft_observations.json`:
- "The forge has not been drawing right for weeks and nobody can figure out why"
- "Someone brought in a piece of metalwork last week that had the whole workshop talking"

`community_observations.json`:
- "Two of the elders have not spoken to each other since the last council meeting"
- "A family packed up and left in the night without telling anyone where they were going"

`nature_observations.json`:
- "The birds along the western ridge went silent about a week ago and have not come back"
- "Something has been digging up the ground near the old stones at night"

`nostalgia_observations.json`:
- "There used to be a market day every tenday and the whole settlement turned out for it"
- "The old wall was twice as tall before the last bad winter brought half of it down"

`curiosity_observations.json`:
- "Someone carved symbols into the old standing stone and nobody will admit to doing it"
- "A stranger left a package at the gate two days ago and walked off without a word"

`festival_observations.json`:
- "The preparations for the gathering started early this year and people are already arguing about the arrangements"
- "Last time we had a proper celebration the whole settlement turned out and the singing went until dawn"

`treasure_rumors.json`:
- "A peddler came through swearing they found worked stone and old coin scattered near a collapsed structure"
- "An old map turned up in the archives that marks a cache nobody has found"

`conflict_rumors.json`:
- "Two families on the east side stopped trading with each other and their people are choosing sides"
- "A disagreement over water rights turned into a shouting match at the council and neither side backed down"

Write 12 entries per file following these patterns.

**Step 2: Commit**

```bash
git add src/main/resources/topics/pools/weather_observations.json src/main/resources/topics/pools/craft_observations.json src/main/resources/topics/pools/community_observations.json src/main/resources/topics/pools/nature_observations.json src/main/resources/topics/pools/nostalgia_observations.json src/main/resources/topics/pools/curiosity_observations.json src/main/resources/topics/pools/festival_observations.json src/main/resources/topics/pools/treasure_rumors.json src/main/resources/topics/pools/conflict_rumors.json
git commit -m "content: add 9 new L0 fragment pools (weather, craft, community, nature, nostalgia, curiosity, festival, treasure, conflict)"
```

---

### Task 5: Create 9 new L1 pool files

**Files:**
- Create: `src/main/resources/topics/pools/weather_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/craft_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/community_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/nature_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/nostalgia_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/curiosity_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/festival_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/treasure_details.json` (17 entries)
- Create: `src/main/resources/topics/pools/conflict_details.json` (17 entries)

**Same authoring rules as L0.** L1 entries provide follow-up detail: responses to exploratory questions. They are typically one sentence describing a specific observation, consequence, or reaction within the topic category.

**Format:** Simple JSON string array, same as L0.

**Example entries per pool:**

`weather_details.json`:
- "The wind shifted direction twice in one afternoon. The older folk say that means something."
- "Rain came down so hard the drainage channels overflowed and flooded the lower storage."

`craft_details.json`:
- "The blade held an edge longer than anything I have made with local ore. Whatever that metal was, it was not from here."
- "Three orders came in at once and we are behind on all of them. The workshop has not been this busy in years."

`community_details.json`:
- "The council voted to restrict evening gatherings after the incident. Not everyone agrees it was necessary."
- "Attendance at the morning assembly has dropped by half. People are tired of hearing the same arguments."

`nature_details.json`:
- "The creek changed color for about two days. Muddy brown, like something upstream churned up the bottom."
- "Tracks that size do not come from anything I recognize. Too wide for a wolf, too narrow for a bear."

`nostalgia_details.json`:
- "We used to leave our doors open at night. Not out of carelessness, but because there was no reason not to."
- "The old market bell still hangs in the square but nobody rings it anymore. The rope rotted years ago."

`curiosity_details.json`:
- "I went back the next morning and the marks were gone. Not worn away. Gone. As if they had never been there."
- "The strange thing is that nobody seems surprised. As if everyone was expecting it but nobody wanted to say so."

`festival_details.json`:
- "The archery contest drew twice the usual crowd. Turns out the new arrivals have some real talent."
- "The musicians argued about which songs to play until someone just started playing and the rest gave in."

`treasure_details.json`:
- "The coins were old. Not just tarnished old, but a mint mark nobody in the settlement recognized."
- "Whoever hid it did not want it found quickly. The entrance was behind a false wall that only collapsed by accident."

`conflict_details.json`:
- "Both sides think they are in the right. That is what makes it dangerous."
- "It was civil at first. Formal complaints, written responses. Then someone's storehouse burned and the tone changed."

Write 17 entries per file following these patterns.

**Step 2: Commit**

```bash
git add src/main/resources/topics/pools/weather_details.json src/main/resources/topics/pools/craft_details.json src/main/resources/topics/pools/community_details.json src/main/resources/topics/pools/nature_details.json src/main/resources/topics/pools/nostalgia_details.json src/main/resources/topics/pools/curiosity_details.json src/main/resources/topics/pools/festival_details.json src/main/resources/topics/pools/treasure_details.json src/main/resources/topics/pools/conflict_details.json
git commit -m "content: add 9 new L1 fragment pools (weather, craft, community, nature, nostalgia, curiosity, festival, treasure, conflict)"
```

---

### Task 6: Register new pools in TopicPoolRegistry

**Files:**
- Modify: `src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java`

**Step 1: Add 18 new field declarations**

After line 38 (`travelerNews`), add new L0 fields:
```java
// Fragment pools: Layer 0 (new topic-matched)
private final List<String> weatherObservations = new ArrayList<>();
private final List<String> craftObservations = new ArrayList<>();
private final List<String> communityObservations = new ArrayList<>();
private final List<String> natureObservations = new ArrayList<>();
private final List<String> nostalgiaObservations = new ArrayList<>();
private final List<String> curiosityObservations = new ArrayList<>();
private final List<String> festivalObservations = new ArrayList<>();
private final List<String> treasureRumors = new ArrayList<>();
private final List<String> conflictRumors = new ArrayList<>();
```

After line 44 (`locationDetails`), add new L1 fields:
```java
// Fragment pools: Layer 1 (new topic-matched)
private final List<String> weatherDetails = new ArrayList<>();
private final List<String> craftDetails = new ArrayList<>();
private final List<String> communityDetails = new ArrayList<>();
private final List<String> natureDetails = new ArrayList<>();
private final List<String> nostalgiaDetails = new ArrayList<>();
private final List<String> curiosityDetails = new ArrayList<>();
private final List<String> festivalDetails = new ArrayList<>();
private final List<String> treasureDetails = new ArrayList<>();
private final List<String> conflictDetails = new ArrayList<>();
```

**Step 2: Add load calls in loadAll()**

In the classpath loading block (after line 73, traveler_news load), add:
```java
// Fragment pools: Layer 0 (new topic-matched)
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "weather_observations.json", weatherObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "craft_observations.json", craftObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "community_observations.json", communityObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nature_observations.json", natureObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nostalgia_observations.json", nostalgiaObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "curiosity_observations.json", curiosityObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "festival_observations.json", festivalObservations);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "treasure_rumors.json", treasureRumors);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "conflict_rumors.json", conflictRumors);
```

In the L1 classpath loading block (after line 79, location_details load), add:
```java
// Fragment pools: Layer 1 (new topic-matched)
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "weather_details.json", weatherDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "craft_details.json", craftDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "community_details.json", communityDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nature_details.json", natureDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nostalgia_details.json", nostalgiaDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "curiosity_details.json", curiosityDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "festival_details.json", festivalDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "treasure_details.json", treasureDetails);
loadStringPoolFromClasspath(CLASSPATH_PREFIX + "conflict_details.json", conflictDetails);
```

Add matching filesystem override blocks in the `if (poolsDir != null)` section (same pattern, using `loadStringPool` instead of `loadStringPoolFromClasspath`).

**Step 3: Add 18 new accessor methods**

After the existing L0 accessors (after line 322), add:
```java
// --- Fragment pool accessors: Layer 0 (new topic-matched) ---

public String randomWeatherObservation(Random random) {
    if (weatherObservations.isEmpty()) return "the weather has been unpredictable lately";
    return weatherObservations.get(random.nextInt(weatherObservations.size()));
}

public String randomCraftObservation(Random random) {
    if (craftObservations.isEmpty()) return "the workshop has been busy";
    return craftObservations.get(random.nextInt(craftObservations.size()));
}

public String randomCommunityObservation(Random random) {
    if (communityObservations.isEmpty()) return "people have been talking";
    return communityObservations.get(random.nextInt(communityObservations.size()));
}

public String randomNatureObservation(Random random) {
    if (natureObservations.isEmpty()) return "the wilds have been restless";
    return natureObservations.get(random.nextInt(natureObservations.size()));
}

public String randomNostalgiaObservation(Random random) {
    if (nostalgiaObservations.isEmpty()) return "things were different before";
    return nostalgiaObservations.get(random.nextInt(nostalgiaObservations.size()));
}

public String randomCuriosityObservation(Random random) {
    if (curiosityObservations.isEmpty()) return "something odd has been happening";
    return curiosityObservations.get(random.nextInt(curiosityObservations.size()));
}

public String randomFestivalObservation(Random random) {
    if (festivalObservations.isEmpty()) return "there is talk of a celebration";
    return festivalObservations.get(random.nextInt(festivalObservations.size()));
}

public String randomTreasureRumor(Random random) {
    if (treasureRumors.isEmpty()) return "someone found something valuable out there";
    return treasureRumors.get(random.nextInt(treasureRumors.size()));
}

public String randomConflictRumor(Random random) {
    if (conflictRumors.isEmpty()) return "tensions have been rising between neighbors";
    return conflictRumors.get(random.nextInt(conflictRumors.size()));
}
```

After the existing L1 accessors (after line 344), add:
```java
// --- Fragment pool accessors: Layer 1 (new topic-matched) ---

public String randomWeatherDetail(Random random) {
    if (weatherDetails.isEmpty()) return "It has been like this for days.";
    return weatherDetails.get(random.nextInt(weatherDetails.size()));
}

public String randomCraftDetail(Random random) {
    if (craftDetails.isEmpty()) return "The work goes on, one way or another.";
    return craftDetails.get(random.nextInt(craftDetails.size()));
}

public String randomCommunityDetail(Random random) {
    if (communityDetails.isEmpty()) return "People have their opinions.";
    return communityDetails.get(random.nextInt(communityDetails.size()));
}

public String randomNatureDetail(Random random) {
    if (natureDetails.isEmpty()) return "The wilds are full of surprises.";
    return natureDetails.get(random.nextInt(natureDetails.size()));
}

public String randomNostalgiaDetail(Random random) {
    if (nostalgiaDetails.isEmpty()) return "Times change whether we want them to or not.";
    return nostalgiaDetails.get(random.nextInt(nostalgiaDetails.size()));
}

public String randomCuriosityDetail(Random random) {
    if (curiosityDetails.isEmpty()) return "Nobody seems to have an explanation.";
    return curiosityDetails.get(random.nextInt(curiosityDetails.size()));
}

public String randomFestivalDetail(Random random) {
    if (festivalDetails.isEmpty()) return "The preparations are well underway.";
    return festivalDetails.get(random.nextInt(festivalDetails.size()));
}

public String randomTreasureDetail(Random random) {
    if (treasureDetails.isEmpty()) return "That is all I know about it.";
    return treasureDetails.get(random.nextInt(treasureDetails.size()));
}

public String randomConflictDetail(Random random) {
    if (conflictDetails.isEmpty()) return "It is a delicate situation.";
    return conflictDetails.get(random.nextInt(conflictDetails.size()));
}
```

**Step 4: Verify compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java
git commit -m "feat: register 18 new topic-matched pool files in TopicPoolRegistry"
```

---

### Task 7: Add new bindings in TopicGenerator.buildBindings()

**Files:**
- Modify: `src/main/java/com/chonbosmods/topic/TopicGenerator.java` (buildBindings method)

**Step 1: Add 9 new L0 bindings**

After line 325 (`traveler_news` binding), add:
```java
// Fragment pool bindings: Layer 0 (new topic-matched)
bindings.put("weather_observation", topicPool.randomWeatherObservation(random));
bindings.put("craft_observation", topicPool.randomCraftObservation(random));
bindings.put("community_observation", topicPool.randomCommunityObservation(random));
bindings.put("nature_observation", topicPool.randomNatureObservation(random));
bindings.put("nostalgia_observation", topicPool.randomNostalgiaObservation(random));
bindings.put("curiosity_observation", topicPool.randomCuriosityObservation(random));
bindings.put("festival_observation", topicPool.randomFestivalObservation(random));
bindings.put("treasure_rumor", topicPool.randomTreasureRumor(random));
bindings.put("conflict_rumor", topicPool.randomConflictRumor(random));
```

**Step 2: Add 9 new L1 bindings**

After line 331 (`location_detail` binding), add:
```java
// Fragment pool bindings: Layer 1 (new topic-matched)
bindings.put("weather_detail", topicPool.randomWeatherDetail(random));
bindings.put("craft_detail", topicPool.randomCraftDetail(random));
bindings.put("community_detail", topicPool.randomCommunityDetail(random));
bindings.put("nature_detail", topicPool.randomNatureDetail(random));
bindings.put("nostalgia_detail", topicPool.randomNostalgiaDetail(random));
bindings.put("curiosity_detail", topicPool.randomCuriosityDetail(random));
bindings.put("festival_detail", topicPool.randomFestivalDetail(random));
bindings.put("treasure_detail", topicPool.randomTreasureDetail(random));
bindings.put("conflict_detail", topicPool.randomConflictDetail(random));
```

**Step 3: Verify compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/topic/TopicGenerator.java
git commit -m "feat: add 18 new pool bindings in buildBindings()"
```

---

### Task 8: Rewrite SmallTalk templates

**Files:**
- Modify: `src/main/resources/topics/SmallTalk/templates.json`

**Rewrite all 8 templates following these rules:**

1. **Intro pattern:** `"{tone_opener} {topic_L0_var}. {tone_closer}"` -- strict three-part, one fragment only
2. **Exploratories use topic-matched L1:** each template uses its paired L1 pool from the wiring table
3. **Cross-cutting allowed:** `{location_detail}` for "where?" questions, `{time_ref}` for "when?" assembly
4. **L2 restriction:** weather/craft/community/nostalgia/festival use only `{local_opinion}` and `{personal_reaction}`. Nature and curiosity also get `{danger_assessment}`.
5. **2-3 perspectives per template, each with 2 exploratories, each exploratory with 1 sub-exploratory**
6. **questHookPerspectives remain** but also use the three-part intro pattern with `{quest_exposition}` replacing tone wrappers

**Wiring per template:**

| Template | L0 Intro | L1 Primary | L1 Cross-cut | L2 |
|---|---|---|---|---|
| smalltalk_weather | `{weather_observation}` | `{weather_detail}` | `{location_detail}` | opinion, reaction |
| smalltalk_trade | `{trade_gossip}` | `{trade_detail}` | `{location_detail}` | opinion, reaction |
| smalltalk_craftsmanship | `{craft_observation}` | `{craft_detail}` | `{location_detail}` | opinion, reaction |
| smalltalk_community | `{community_observation}` | `{community_detail}` | `{location_detail}` | opinion, reaction |
| smalltalk_nature | `{nature_observation}` | `{nature_detail}` | `{location_detail}`, `{creature_detail}` | all 3 |
| smalltalk_nostalgia | `{nostalgia_observation}` | `{nostalgia_detail}` | `{location_detail}` | opinion, reaction |
| smalltalk_curiosity | `{curiosity_observation}` | `{curiosity_detail}` | `{location_detail}`, `{event_detail}` | all 3 |
| smalltalk_festival | `{festival_observation}` | `{festival_detail}` | `{location_detail}` | opinion, reaction |

**Example rewritten template (smalltalk_weather):**

```json
{
  "id": "smalltalk_weather",
  "label": "{subject_focus}",
  "perspectives": [
    {
      "intro": "{tone_opener} {weather_observation}. {tone_closer}",
      "exploratories": [
        {
          "prompt": "When did that start?",
          "response": "{time_ref}. {weather_detail}",
          "exploratories": [
            {
              "prompt": "What do people make of it?",
              "response": "{local_opinion}"
            }
          ]
        },
        {
          "prompt": "Is that normal for this area?",
          "response": "{weather_detail}",
          "exploratories": [
            {
              "prompt": "How are you handling it?",
              "response": "{personal_reaction}"
            }
          ]
        }
      ]
    },
    {
      "intro": "{tone_opener} {weather_observation}. {tone_closer}",
      "exploratories": [
        {
          "prompt": "Where is it worst?",
          "response": "{location_detail}",
          "exploratories": [
            {
              "prompt": "Does it worry you?",
              "response": "{personal_reaction}"
            }
          ]
        },
        {
          "prompt": "Has anyone figured out why?",
          "response": "{weather_detail}",
          "exploratories": [
            {
              "prompt": "What are the old-timers saying?",
              "response": "{local_opinion}"
            }
          ]
        }
      ]
    }
  ],
  "questHookPerspectives": [
    {
      "intro": "{quest_exposition} The weather around {subject_focus_the} is not natural. {quest_detail}",
      "exploratories": [
        {
          "prompt": "What is at stake?",
          "response": "{quest_stakes_detail}"
        }
      ],
      "decisive": {
        "prompt": "I will look into the source.",
        "response": "{quest_accept_response}"
      }
    }
  ]
}
```

Apply the same pattern to all 8 templates. Each template has unique exploratory prompts appropriate to its topic but follows the structural pattern above.

**Step 2: Commit**

```bash
git add src/main/resources/topics/SmallTalk/templates.json
git commit -m "content: rewrite SmallTalk templates with one-fragment intros and topic-matched pools"
```

---

### Task 9: Rewrite Rumor templates

**Files:**
- Modify: `src/main/resources/topics/Rumors/templates.json`

**Same rules as Task 8. Wiring per template:**

| Template | L0 Intro | L1 Primary | L1 Cross-cut | L2 |
|---|---|---|---|---|
| rumor_danger | `{creature_sighting}` | `{creature_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_sighting | `{creature_sighting}` | `{creature_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_migration | `{creature_sighting}` | `{creature_detail}` | `{location_detail}` | all 3 |
| rumor_disappearance | `{strange_event}` | `{event_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_corruption | `{strange_event}` | `{event_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_omen | `{strange_event}` | `{event_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_treasure | `{treasure_rumor}` | `{treasure_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |
| rumor_conflict | `{conflict_rumor}` | `{conflict_detail}` | `{location_detail}`, `{rumor_source}` | all 3 |

**Rumor-specific exploratories** can include:
- "Who told you?" -> `{rumor_source}` (rumor-only pattern)
- "Is it dangerous?" -> `{danger_assessment}` (all rumors get this)

**Example rewritten template (rumor_danger):**

```json
{
  "id": "rumor_danger",
  "label": "{subject_focus}",
  "perspectives": [
    {
      "intro": "{tone_opener} {creature_sighting}. {tone_closer}",
      "exploratories": [
        {
          "prompt": "What did it look like?",
          "response": "{creature_detail}",
          "exploratories": [
            {
              "prompt": "Should we be worried?",
              "response": "{danger_assessment}"
            }
          ]
        },
        {
          "prompt": "Where was this?",
          "response": "{location_detail}",
          "exploratories": [
            {
              "prompt": "What do people think?",
              "response": "{local_opinion}"
            }
          ]
        }
      ]
    },
    {
      "intro": "{tone_opener} {creature_sighting}. {tone_closer}",
      "exploratories": [
        {
          "prompt": "Who saw it?",
          "response": "{rumor_source}",
          "exploratories": [
            {
              "prompt": "Do you trust them?",
              "response": "{personal_reaction}"
            }
          ]
        },
        {
          "prompt": "Tell me more about it.",
          "response": "{creature_detail}",
          "exploratories": [
            {
              "prompt": "Has anyone tried to deal with it?",
              "response": "{local_opinion}"
            }
          ]
        }
      ]
    }
  ],
  "questHookPerspectives": [
    {
      "intro": "{quest_exposition} The danger from {subject_focus_the} grows with each passing day. {quest_detail}",
      "exploratories": [
        {
          "prompt": "What is at stake?",
          "response": "{quest_stakes_detail}"
        }
      ],
      "decisive": {
        "prompt": "I will handle it.",
        "response": "{quest_accept_response}"
      }
    }
  ]
}
```

Apply to all 8 rumor templates. Each differentiates through exploratory prompt framing: danger focuses on threat, sighting on mystery, migration on ecology, treasure on value/legend, conflict on social tension, etc.

**Step 2: Commit**

```bash
git add src/main/resources/topics/Rumors/templates.json
git commit -m "content: rewrite Rumor templates with one-fragment intros and topic-matched pools"
```

---

### Task 10: Update time_refs pool for clause completeness

**Files:**
- Modify: `src/main/resources/topics/pools/time_refs.json`

**Step 1: Review and rewrite entries**

Current entries like "last night", "just before dawn" need to read naturally as opening clauses before a period and a follow-up sentence. Ensure all entries work in context: `"{time_ref}. {weather_detail}"` should read like `"A few days back. The wind shifted direction twice in one afternoon."`.

Entries that are too terse (e.g., bare "yesterday") should be expanded to clause form ("It was just yesterday" or replaced).

Current entries are mostly fine: "not three days past", "a few days back", "before the last rain". Review and fix any that sound abrupt.

**Step 2: Commit**

```bash
git add src/main/resources/topics/pools/time_refs.json
git commit -m "content: update time_refs for clause completeness in exploratory assembly"
```

---

### Task 11: Compile and smoke test

**Step 1: Full compile**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 2: Wipe player saves**

```bash
rm -rf devserver/universe/players
```

Required because the new bindings may conflict with cached dialogue graphs.

**Step 3: Launch dev server**

Run: `./gradlew devServer`

Validate:
1. Server starts without errors related to pool loading
2. Connect with client, find a settlement with NPCs
3. Talk to an NPC: verify intro is a single coherent thought (tone + fragment + tone)
4. Click an exploratory: verify the response is topically relevant to the question
5. Click a deeper exploratory: verify L2 response makes sense
6. Check server console for any null pointer exceptions or missing pool warnings

**Step 4: Commit any fixes discovered during smoke test**

---

### Reference: File Inventory

**Java files modified (3):**
- `src/main/java/com/chonbosmods/topic/SubjectFocus.java`
- `src/main/java/com/chonbosmods/topic/TopicPoolRegistry.java`
- `src/main/java/com/chonbosmods/topic/TopicGenerator.java`
- `src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java`

**New pool files (18):**
- L0: `weather_observations.json`, `craft_observations.json`, `community_observations.json`, `nature_observations.json`, `nostalgia_observations.json`, `curiosity_observations.json`, `festival_observations.json`, `treasure_rumors.json`, `conflict_rumors.json`
- L1: `weather_details.json`, `craft_details.json`, `community_details.json`, `nature_details.json`, `nostalgia_details.json`, `curiosity_details.json`, `festival_details.json`, `treasure_details.json`, `conflict_details.json`

**Modified pool files (1-2):**
- `subject_focuses.json` (add proper field)
- Quest pool files (add proper field)

**Template files rewritten (2):**
- `SmallTalk/templates.json`
- `Rumors/templates.json`

**Updated pool files (1):**
- `time_refs.json` (clause completeness)
