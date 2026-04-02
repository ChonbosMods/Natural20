# Voice Guide: Intense Pool Categories

This document covers voice, tone, and craft guidance for writing entries in the 10 intense pool categories. For JSON format, field definitions, variable bindings, and mechanical rules, see `pools.md`. This doc tells you how entries should sound, not what fields they need.

The 10 intense categories (template `reactionIntensity: "intense"`):

| Category | Core question |
|----------|--------------|
| danger | What threat appeared? |
| sighting | What was seen? |
| treasure | What was found or uncovered? |
| corruption | Who is abusing power? |
| conflict | Who is fighting and why? |
| disappearance | Who or what is missing? |
| migration | What moved and why? |
| omen | What sign appeared? |
| nature | What natural phenomenon feels wrong? |
| curiosity | What strange thing defies explanation? |


## 1. Intense vs Mild

Both intense and mild entries are casual NPC speech. The difference is stakes.

An intense topic implies the world changed or might change. Something happened, something is wrong, something is coming. A mild topic implies the world is continuing as usual: a craftsman's odd success, a festival being planned, trade shifting. The NPC might be interested, amused, or proud, but nobody is losing sleep.

```
Mild:    "The forge has been drawing poorly for weeks. We are managing."
Intense: "Lights flickering in the old tower, steady like a heartbeat."
```

The mild intro describes an ongoing, mundane inconvenience. The intense intro implies an event: something started, something changed. That implication of change is the defining quality of intense entries. If you can read your intro and nothing seems to have happened, it belongs in a mild pool.


## 2. The Gossip Web

The most effective technique for making a settlement feel alive: structure entries within a pool as a gossip web.

Write 2-3 entries that describe different angles on the same type of phenomenon. No single NPC has the full picture. Each one has a fragment, often wrong, often secondhand. The player who talks to multiple NPCs gets a richer picture. This creates a reason to talk to everyone.

Here is a concrete example: three complete entries for a danger pool, each describing a different angle on something prowling near the subject focus.

```json
{
  "id": 0,
  "intro": "Something's been leaving tracks near {subject_focus_the}. Big ones, deep into the mud, and they don't match anything I've seen in thirty years of living here.",
  "details": [
    "Three toes, clawed, wider than my hand spread flat. The stride between prints is longer than I am tall.",
    "They circle the same area every night. Whatever it is, it's watching something.",
    "The tracks stop at the tree line and don't come back out. Like it just vanished."
  ],
  "reactions": [
    "I don't scare easy, but I stopped walking that path after dark.",
    "People keep saying it's a bear. It's not a bear. Bears don't walk in circles."
  ],
  "statCheck": {
    "pass": "There were smaller tracks alongside the big ones, just for a stretch. Like something was being led.",
    "fail": "The mud is too churned up out there. I already told you everything worth seeing."
  }
}
```

This NPC has the physical evidence: tracks, stride length, pattern. They report what they saw.

```json
{
  "id": 1,
  "intro": "The wolves near {subject_focus_the} have been acting wrong. Not aggressive: the opposite. They sit at the edge of the village at night and just watch.",
  "details": [
    "Six of them, every night, same spot by the tree line. They don't hunt, don't howl. Just sit there.",
    "When you walk toward them, they don't run. They stand up, look at you, then sit back down.",
    "The pack used to stay deep in the hills. Something drove them closer, and whatever it is, they seem more afraid of it than they are of us."
  ],
  "reactions": [
    "It's the stillness that gets to me. Wolves are supposed to move. These ones just stare.",
    "Half the village thinks they're protecting us. The other half thinks they're waiting for something. Neither option helps me sleep."
  ]
}
```

This NPC has behavioral evidence: the wolves aren't acting like wolves. They haven't seen the tracks, but their observation points to the same root cause.

```json
{
  "id": 2,
  "intro": "A merchant heading through {subject_focus_the} turned back halfway and warned everyone not to take the eastern road. Wouldn't say why.",
  "details": [
    "He was pale and his hands were shaking. This is a man who runs the northern passes in winter: he doesn't rattle easy.",
    "He just said the road was wrong. That's the word he used: wrong. Not blocked, not dangerous. Wrong."
  ],
  "reactions": [
    "Two other traders have since gone around the long way without anyone asking them to. That tells me he wasn't exaggerating.",
    "I pressed him for details over a drink, but he just shook his head and changed the subject."
  ],
  "statCheck": {
    "pass": "After a few drinks he let one thing slip: he said the road was there, but the landmarks were in the wrong places. Like the land itself had shifted.",
    "fail": "He will not say more. I pressed him once and he just gave me a long look and said he had already said more than he should have."
  }
}
```

This NPC has secondhand testimony: they didn't see anything themselves, but they know someone who did, and that person's reaction tells a story. The player who hears all three entries gets tracks, panicking wolves, and a shaken merchant: three fragments that compose into something larger than any one NPC could deliver.

You don't need every entry in a pool to participate in a gossip web. Some entries stand alone. But when you can write 2-3 that orbit the same kind of event from different angles, do it. The web is what turns individual NPCs into a community.


## 3. The Rumor Formula

Structure every intro as: **[event or problem]** + **[location or person]** + **[emotional reaction or opinion]**. Three hooks in one line.

Consider this intro:

> "A shepherd went missing near {subject_focus_the} about a week ago. His flock came back on their own, but he didn't."

- **Event**: a shepherd went missing.
- **Location**: near the subject focus.
- **Emotional hook**: the flock returned without him. This is the detail that makes the player lean in.

The three hooks don't all need to land in the intro. The v2 entry structure distributes them naturally:

- The **event** goes in the **intro**. This is the opening claim: what happened, where, and why it matters enough to mention.
- The **location or person** is elaborated in the **details**. "His crook was found stuck in the ground, straight up, like someone planted it there on purpose." This grounds the rumor in something tangible the player could investigate.
- The **emotional reaction** is expressed in the **reactions**. "His wife keeps a candle lit for him. I don't have the heart to tell her what I think happened." This is where the NPC stops reporting and starts feeling.

Not every intro hits all three hooks. But the best ones do, and you should aim for at least two. An intro with only an event ("Something happened.") gives the player nothing to grab. An intro with an event and a location ("Something happened near the ridge.") is better. An intro with all three ("Something happened near the ridge, and the guard captain won't talk about it.") makes the player want to keep asking.


## 4. Hearsay Layering

Intros should feel like they passed through multiple people. Frame as secondhand: "I heard from...", "They say...", "Someone mentioned...". This makes NPCs part of a community rather than isolated witnesses, and it creates natural uncertainty: the player doesn't know how much has been distorted in the retelling.

### Source Attribution Patterns

Good hearsay names a source without naming a person:

- "A traveling merchant mentioned it last week."
- "My cousin says it's been going on for months."
- "The guards are talking, though they won't say much."
- "One of the farmers brought it up at market."
- "The elder's wife heard it from the washerwomen."

These patterns anchor the rumor in the community. The NPC isn't an oracle delivering facts: they're a person who heard something from another person, who may have heard it from someone else.

### Certainty Gradient

Not every NPC is equally sure. Vary the confidence level to create texture:

| Confidence | Pattern |
|------------|---------|
| High | "I saw it myself." / "It's true, I was there." |
| Medium | "I've heard." / "They say." / "Word is." |
| Low | "Someone mentioned... could be nothing." / "I'm not sure I believe it, but..." |
| Deflection | "People keep talking about it. I try not to listen." |

High-confidence intros work best when the NPC has direct sensory evidence: "I saw something moving through the trees near {subject_focus_the} two nights ago." Low-confidence intros work when the NPC is relaying third-hand information or when the claim is hard to believe: "A child near {subject_focus_the} says she's been playing with a fox that talks. We all laughed until she started repeating things no child her age should know."

The deflection tier is underused and valuable. An NPC who refuses to engage with a rumor but clearly knows about it creates a different kind of hook: "Everyone keeps asking about {subject_focus_the}. I don't want to talk about it." That itself tells the player something is going on.


## 5. Category-Specific Tips

### danger / sighting / migration

Focus on sensory details: tracks, sounds, smells, movement patterns. Avoid naming creatures definitively unless the NPC saw it clearly. Uncertainty is more compelling than certainty: "something large" beats "a wolf."

The best danger intros put the evidence first and the fear second:

> "Found three deer near {subject_focus_the}, all dead in a line. No wounds, no blood. Just lying there like they dropped mid-step."

This is a report. The NPC found something wrong and is telling you about it. The fear comes through in the details: "No scavengers touched them. Not a single crow, not a fox. Three days and nothing."

For sightings, lean on the unreliability of perception. Night, distance, motion, and panic all distort what someone sees. A good sighting entry admits this uncertainty while still conveying that something was there:

> "I saw something moving through the trees near {subject_focus_the} two nights ago. Tall, thin, walking upright but not like any person I know."

For migration, the key is disruption of pattern. Animals moving is normal. Animals moving wrong is the hook: wrong season, wrong direction, wrong speed, wrong species behaving the same way.

### disappearance / corruption / omen

Grounded observations of uncanny or unjust phenomena. Time-anchor when possible: "started about a week ago", "three nights running", "hasn't been seen in over a week." Temporal specificity makes rumors feel real.

For disappearance: show what was left behind. An abandoned cart with the mule still hitched. A half-eaten supper. Flowers bundled on a rock. The things a person left behind tell a story about the moment they stopped being here.

For corruption: show the mechanism, not the moral. Don't write "the tax collector is evil." Write "the tax collector marks down one number in his ledger and collects a different amount." Let the player judge. The NPC may have opinions in the reactions array, but the intro and details should present evidence.

For omen: don't explain the cause. The mystery IS the hook. A standing stone that hums. A crow that won't move. A green moon. The NPC reports what they observed and maybe what the elders say about it, but the entry never reveals what's actually happening. That's for gameplay to answer.

### treasure

Frame as discovery: someone stumbled onto something, an old map surfaced, a collapse revealed worked stone. The best treasure entries include a reason it hasn't been claimed:

- Danger: "The ruin isn't exactly stable."
- Superstition: "Everyone avoids that spot."
- Difficulty: "The vein is deep and the rock is hard."
- Uncertainty: "Nobody knows if the coins are worth anything."

Without a reason for inaction, the player wonders why the NPC isn't going after it themselves. The obstacle makes the opportunity believable.

### conflict

Name the sides generically: "two families", "the hunters and the woodcutters", "the upstream crew and the downstream crew." Show escalation: started as disagreement, now involves property damage, sabotage, or silence.

The NPC has a side, even if they try to sound neutral. Let that bias leak through in the reactions:

> "They're both right, which is the problem. The village needs timber and it needs meat. Nobody's figured out how to have both."

This sounds balanced, but the word "problem" signals that the NPC sees the situation as intractable, which is itself a position. Let the NPC's stance be subtle but present.

### nature

Natural phenomena with weight. Not mundane weather but things that feel wrong: rivers flowing backward, animals clustering in unusual patterns, plants growing where or when they shouldn't, perfect geometric formations appearing in wild growth.

The key distinction from curiosity: nature entries involve living things or natural forces. The old oak bloomed in autumn. The creek reversed. Mushrooms grew in measured circles. The phenomenon is grounded in the natural world, but the behavior is unnatural.

### curiosity

Odd but not threatening. Sealed doors nobody remembers, sundials that tell the wrong time, caves where shadows point the wrong direction, wells that whisper. The hook is strangeness, not danger.

Curiosity entries have a distinctive emotional tone: fascination mixed with unease. The NPC isn't scared. They're puzzled, maybe slightly unsettled, but mostly intrigued. This separates curiosity from danger and omen:

> "There's a cave near {subject_focus_the} where your shadow doesn't follow you. You walk in and your shadow stays at the entrance."

There's no threat here. Just something that shouldn't be possible, presented plainly. The reactions should mirror this tone: intellectual engagement, not fear.


## 6. Entry Quality Test

Before adding an entry to an intense pool, run it through these checks:

1. **Is it secondhand?** The NPC should sound like they're relaying gossip, not reading a report. Even first-person sightings should feel like a person talking about what they saw, not a narrator describing a scene.

2. **Does it name a subject?** A good intro points to a place, person, or event the player could investigate. "Strange things have been happening" is too vague. "Strange things have been happening near {subject_focus_the}" is better. "Three deer dropped dead near {subject_focus_the} with no wounds" is best.

3. **Does the NPC have a reaction?** Even a short one in the reactions array: "Not surprised." "Worries me." "Could be nothing." The reaction is what makes the NPC a person rather than a bulletin board.

4. **Does the intro imply change?** If you removed the hedging, would it still imply something happened or might happen? If not, it belongs in a mild pool. Test by asking: "If this were a newspaper headline, would it be news?" A tree blooming out of season is news. A tree existing is not.

5. **Do the details continue the intro?** Each detail should elaborate on what the intro established, not introduce new unrelated information. If the intro mentions a missing tinker, every detail should be about the tinker, his cart, his route, or his absence. Not about the weather that week.

6. **Do the reactions close the arc?** They should express feeling about the specific thing from the intro, not generic unease. "Times are hard" is not a reaction. "I keep expecting to see him roll in any day now with a story. But each week that passes makes that harder to believe" is a reaction: it's about this tinker, this absence, this hope fading.

7. **Is the stat check NPC dialogue?** The NPC speaks in both pass and fail. Pass reveals hidden information the NPC shares because the player proved sharp. Fail deflects without narrating what the player does. "The mud is too churned up out there" is good. "You kneel down and examine the tracks closely" is narration, not dialogue.
