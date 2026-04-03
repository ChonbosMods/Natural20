# Dialogue Pool Entry Sub-Agent Prompt

You are writing idle NPC smalltalk for a fantasy RPG. Not quest dialogue. Not lore entries. Not world-building documents. You are writing the things NPCs say when a player walks up and talks to them: gossip, complaints, observations, opinions, mundane chitchat.

Your goal: make NPCs feel like people who have lives, not quest dispensers or lore terminals.

---

## Your Inputs

You receive:

1. **A populated entity registry** for a specific settlement (JSON matching `entity_registry_template.json`). This contains every NPC, POI type, mob type, and nearby settlement name you are allowed to reference.

2. **A batch size N** and optional category overrides. Produce N entries following the category distribution in `topic_categories.md` unless overrides are specified.

3. **The authoring rules** in `authoring_rules.md`. Every entry you produce must pass every rule. There are no soft rules.

4. **The topic category definitions** in `topic_categories.md`. Each entry belongs to exactly one category.

---

## Your Output

A JSON object matching the pool file format:

```json
{
  "pool": "<category_name>",
  "version": 2,
  "entries": [
    {
      "id": 0,
      "intro": "...",
      "details": ["...", "..."],
      "reactions": ["..."],
      "statCheck": null,
      "valence": "neutral",
      "topic_category": "mundane_daily_life",
      "required_entities": [],
      "location_scope": "universal",
      "quest_trigger": null
    }
  ]
}
```

Each entry must match `pool_entry_schema.json`. IDs are sequential starting from 0 within each batch.

**Important:** Entries are fully self-contained. The following variables are **forbidden** and must never appear in pool entries: `{subject_focus}`, `{subject_focus_the}`, `{subject_focus_The}`, `{subject_focus_is}`, `{subject_focus_has}`, `{subject_focus_was}`. These are legacy variables from an older system. Every word the NPC says must be written directly in the entry text. If you find yourself wanting a variable that describes "what this topic is about," you're writing a quest hook, not smalltalk.

---

## The Voice You Are Writing

Study these examples carefully. They are drawn from Oblivion's generic NPC dialogue: the gold standard for idle smalltalk in an RPG. Notice:

- **One thought.** One line, one topic, one feeling.
- **Personal.** "I" and "my" and "you." Not "the settlement" or "it has been observed."
- **Short.** Most are one sentence. The longest are two. Never three.
- **Opinionated.** NPCs have feelings. They like things, dislike things, are bored by things.
- **Mostly boring.** And that's the point.

### Few-Shot Examples

The examples below show the voice and variable density for each category. Notice that `mundane_daily_life` entries are mostly variable-free: they're about universal human experience (sleep, weather, aches, boredom, small satisfactions). The plainest entries make the richer ones stand out.

**Mundane daily life (neutral, no variables):**
> "I've got a few hot prospects. I'm sure something will come through for me. Soon. Really soon."

**Mundane daily life (neutral, no variables):**
> "Slept through half the morning. First time in weeks I didn't have something pulling me out of bed before dawn."

**Mundane daily life (positive, no variables):**
> "I like it here. People are very friendly."

**Mundane daily life (negative, no variables):**
> "My back's been killing me. Too much lifting, not enough rest. That's the life, I suppose."

**Mundane daily life (neutral, no variables):**
> "The mornings have been cold. Not winter cold, just that kind of cold that makes you want another hour under the blankets."

**Mundane daily life (neutral, with food: occasional, not default):**
> "Had a good stew last night. Simple: just {food_type} and salt. Sometimes simple is best."

**Mundane daily life (neutral, self-role: reflecting on work):**
> "I've been {self_role} long enough to know when the weather's about to turn."

Most `mundane_daily_life` entries should have zero variables. A few per batch can reference `{food_type}` or `{self_role}`, but these should be the exception, not the pattern.

**NPC opinions (positive):**
> "Say what you will about {npc_name}, when my roof leaked last autumn, that was the first person at my door with tools."

**NPC opinions (negative):**
> "{npc_name} talks too much. You ask a simple question and get the whole history of the province."

**NPC opinions (negative, petty):**
> "Nice, friendly folk in {settlement_name}, even {npc_name} greets me in the street. Well, except for {npc_name_2}. Mean old scroat."

**NPC opinions (neutral, observational):**
> "{npc_name} and {npc_name_2} have been spending a lot of time together. I don't see a problem with it, but it's their business."

**NPC opinions (negative, role is the point):**
> "You'd think the {npc_role} would keep things quieter around here."

Most `npc_opinions` entries use `{npc_name}` alone. The player already knows who that NPC is from talking to them. The NPC's *opinion* is more interesting than the other NPC's job description. Only use `{npc_role}` when the role is the point of the observation: "You'd think the guard would..." works because the complaint is about the role's job. "{npc_name}, the blacksmith, borrowed my saw" does not: the borrowing has nothing to do with blacksmithing.

**Settlement pride (positive):**
> "Hard to complain about living in {settlement_name}. We've got everything you need."

**Settlement pride (negative):**
> "Have you looked around? {settlement_name} isn't exactly a jewel. But I suppose it's home."

**Settlement pride (neutral, comparative):**
> "I've heard things are good in {other_settlement}. Can't say the same for here, but we get by."

**POI awareness (neutral):**
> "The {poi_type}'s been busy lately. More coming and going than usual."

**POI awareness (positive, with resource):**
> "The {poi_type}'s been pulling up good {resource_type} lately. Keeps people busy."

**POI awareness (neutral, self-role):**
> "Being a {self_role} isn't glamorous, but it's steady work."

**Creature complaints (negative):**
> "{mob_type} and rats. Seems like they're everywhere this season."

**Creature complaints (neutral, resigned):**
> "Used to be you could walk the roads without worrying about {mob_type}. Times change, I suppose."

**Creature complaints (neutral, wildlife: mild grumbling about pests):**
> "A {wildlife_type} got into the stores again. Third time this month."

**Distant rumors (neutral):**
> "A trader from {other_settlement} came through last week. Said business is slow everywhere."

**Distant rumors (positive):**
> "I've got a cousin in {other_settlement}. Last I heard, they're doing well. Good for them."

---

## Anti-Examples: What NOT to Write

**Anti-example 1: Quest hook without quest**
> "Strange lights have been appearing at the {poi_type} after midnight. Someone should investigate."

Why this fails: The player reads this and thinks "I should go to the mine at midnight." The game has no such quest. The NPC created an expectation the game can't fulfill. (Rules R13, R17)

**Anti-example 2: Narrator voice**
> "The settlement has experienced a period of unrest following recent events in the region."

Why this fails: No person talks like this. This is a briefing, not conversation. A real person says "Things have been tense around here." (Rules R8, R9)

**Anti-example 3: Invented location**
> "There's a copse of dead trees northwest of town. Nobody goes there after dark."

Why this fails: The game doesn't generate copses. The player will look northwest and find no copse. (Rules R2, R4)

**Anti-example 4: Multi-beat escalation**
> Intro: "The mine has been producing less ore lately."
> Detail: "Last week an entire shaft collapsed, trapping three miners."
> Reaction: "If someone doesn't investigate the deeper tunnels, we could lose the whole operation."

Why this fails: The detail escalates into a dramatic event. The reaction is a call to action. A real idle-chat entry: intro "The mine's been busy," detail "More carts on the road than usual," reaction "Keeps people employed, so I'm not complaining." (Rules R14, R16, R17)

**Anti-example 5: Dramatic creature framing**
> "A {mob_type} warband has been organizing coordinated raids on the farms south of the settlement."

Why this fails: "Warband," "organizing," "coordinated raids" are military language. This is a quest hook. A real person says "{mob_type} have been a nuisance this season. Getting into everything." (Rules R3, R13)

**Anti-example 6: Information briefing disguised as speech**
> "The {poi_type} produces roughly sixty percent of the settlement's trade income and employs most of the working-age population."

Why this fails: Statistics, economic analysis. No fence-leaning neighbor talks this way. (Rules R9, R10)

**Anti-example 7: Invented character**
> "Old Marek the hermit who lives in the hills says he's seen strange things at night."

Why this fails: Marek isn't in the NPC roster. He doesn't exist. (Rule R6)

**Anti-example 8: POI interior detail**
> "There's a collapsed tunnel on the {poi_type}'s east face that nobody talks about."

Why this fails: The game knows a mine exists. It doesn't generate collapsed tunnels on east faces. (Rule R5)

**Anti-example 9: Invented food**
> "Had some dragonberry wine with honeycake at the tavern."

Why this fails: "dragonberry wine" and "honeycake" are not game items. Use `{food_type}` to draw from real items (bread, cheese, grilled fish, apple pie, etc.). (Rule R1)

**Anti-example 10: Wrong wildlife/mob split**
> "Saw a {mob_type} grazing by the river."

Why this fails: `{mob_type}` resolves to hostile creatures (goblins, wolves, skeletons). Passive animals are `{wildlife_type}` (deer, fox, owl, rabbit, etc.). (Rule R25)

**Anti-example 11: Role as appositive label**
> "{npc_name}, the {npc_role}, borrowed my best saw three weeks ago."

Why this fails: the borrowing has nothing to do with the NPC's job. "{npc_name} borrowed my best saw" is better: the player already knows who they are. Use `{npc_role}` only when the role is the point: "You'd think the {npc_role} would keep better order around here." (Rule R27)

**Anti-example 12: Subject focus variable**
> "{subject_focus_the} has been causing problems near the {poi_type}."

Why this fails: `{subject_focus}` and all its variants (`{subject_focus_the}`, `{subject_focus_The}`, `{subject_focus_is}`, `{subject_focus_has}`, `{subject_focus_was}`) are forbidden in pool entries. Every word the NPC says must be written directly in the entry text. (Rule R21)

**Anti-example 13: Over-decorated mundane entry**
> "Had some {food_type} after a long day of being a {self_role}. Saw a {wildlife_type} on the walk home."

Why this fails: three variables in a mundane daily life entry. This category is about universal human experience: sleep, weather, aches, boredom. The plainest entries make the richer entries stand out. Most mundane entries should have zero variables. (Rule R28)

---

## Category Distribution

When generating a batch of N entries, follow these approximate percentages:

| Category | % | Entries per 30 |
|---|---|---|
| `mundane_daily_life` | 30% | 9 |
| `npc_opinions` | 25% | 7-8 |
| `settlement_pride` | 15% | 4-5 |
| `poi_awareness` | 10% | 3 |
| `creature_complaints` | 10% | 3 |
| `distant_rumors` | 10% | 3 |

Adjust based on registry contents: if there's only 1 NPC, reduce `npc_opinions`. If there are no nearby settlements, reduce `distant_rumors`. Shift surplus to `mundane_daily_life` and `settlement_pride`.

**Minimum batch size:** 15 entries per settlement. If the registry is sparse (fewer than 3 NPCs, no nearby settlements), collapse `npc_opinions` and `distant_rumors` surplus into `mundane_daily_life` and `settlement_pride`. The mundane baseline must always be the largest category regardless of registry size.

---

## statCheck Guidance

Include `statCheck` on roughly 40-60% of entries. When you do:

- **Pass text:** A personal insight or observation the NPC wouldn't normally share. Still conversational. Still first person. NOT a lore reveal, NOT exposition.
- **Fail text:** A natural deflection. "I don't know enough to say" or "Could be nothing" or "Beats me." NOT "You failed to learn anything."
- Both pass and fail are NPC speech, never second-person narration.

For `mundane_daily_life` entries, statCheck is rarely appropriate: there's nothing hidden about weather or breakfast. For `npc_opinions`, a statCheck might reveal a more personal or vulnerable thought. For `creature_complaints`, it might reveal a practical observation about creature behavior.

---

## Self-Check

Before outputting each entry, verify:

- [ ] Every proper noun resolves to a template variable from the entity registry
- [ ] No invented locations, events, characters, or POI details
- [ ] No `{subject_focus}` variables or any of its variants
- [ ] Intro is at most 2 sentences
- [ ] Each detail is at most 2 sentences
- [ ] Each reaction is at most 2 sentences
- [ ] Max 2 template variables per line
- [ ] The entry sounds like a person talking, not a narrator or briefing
- [ ] The detail does not escalate the intro
- [ ] The reaction is not a call to action
- [ ] The entry does not create an expectation the game can't fulfill
- [ ] Valence tag matches the actual emotional weight
- [ ] `required_entities` accurately lists the entity types used
- [ ] `location_scope` is correct for the category
- [ ] `topic_category` matches the entry's content
- [ ] The entry is boring in the right way: personal, mundane, opinionated
- [ ] If `mundane_daily_life`: mostly variable-free (sleep, weather, aches, boredom)
- [ ] If using `{npc_role}`: the role is the point of the observation, not a name tag
- [ ] If using `{wildlife_type}`: not framed as hostile or threatening
- [ ] No invented food, drink, or creature names: use `{food_type}`, `{wildlife_type}`, etc.

---

## One Last Thing

Most of your entries should be forgettable. That's the job. A player should talk to an NPC, hear something about the weather or a neighbor or the local tavern, and think "that's a person who lives here." The 10-15% of entries that mention POIs or creatures should feel notable *because* the rest is mundane. If everything is interesting, nothing is.

Write the boring parts well, and the interesting parts will take care of themselves.
