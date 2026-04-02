# Writing Details, Reactions, and Stat Checks

This is the voice guide for the three content fields that follow the intro in a v2 pool entry. For JSON format, field counts, and mechanical rules, see `pools.md`. This document covers craft: how to make the content sound like a real NPC speaking, how to structure information delivery, and how to avoid the most common failure modes.

Each v2 pool entry has this shape:

```json
{
  "id": 0,
  "intro": "Opening statement about something specific.",
  "details": ["follow-up 1", "follow-up 2", "follow-up 3"],
  "reactions": ["opinion/emotion 1", "opinion/emotion 2"],
  "statCheck": { "pass": "revealed info", "fail": "deflection" }
}
```

The intro hooks the player. Everything after it must serve that hook.


## How Players Encounter These Fields

Understanding what the player sees is essential to writing content that lands.

- **details[]**: shown when the player clicks a detail prompt ("Tell me more", "What happened?", "Walk me through it"). Each detail has a 70% independent chance to appear, hard capped at 2. The player might see any one detail in isolation, or two in sequence. Any subset must work.
- **reactions[]**: shown when the player clicks a reaction prompt ("What do you think?", "Does that worry you?"). The NPC's personal take: what they feel, what it means, how the community is responding. Reactions appear as follow-ups to details (30% chance after each detail shown).
- **statCheck**: optional. 60% chance to appear when authored. A skill check prompt is shown alongside the detail/reaction branches. Pass reveals hidden info (+5 disposition). Fail deflects naturally (-3 disposition).

The player is choosing what to explore. Every click should feel rewarded.


---


## 1. Writing Details: The Encyclopedic Voice

Details are where NPCs deliver actual information. The target voice is knowledgeable and confident: complete sentences, somewhat formal phrasing, specifics rather than vagueness. But everything is filtered through the NPC's perspective. Information delivery doubles as characterization.


### Write the base answer, then add bias

Start with the factual core: what happened, what it looked like, where it was, how it works. Then add the NPC's editorial angle in one clause.

```
Fact only (too dry):
"The council voted to restrict evening gatherings."

Fact + bias (right):
"The council voted to restrict evening gatherings after the incident. Not everyone agrees it was necessary."

Fact + bias (different angle, same fact):
"The council finally restricted evening gatherings. Should have done it months ago."
```

The opinion clause is what makes two NPCs with the same information sound like different people. Without it, the NPC is a textbook. With it, they're a local with a point of view.


### When mentioning third parties, take a side

Whenever a detail mentions another group: the council, guards, traders, elders: the NPC should have an opinion. Even subtle: "the council finally did something" implies they usually don't, "the council overreacted again" disagrees with the action. Neutral reporting is for history books, not for a person standing in front of you.


### Voice archetypes

These are not tags or labels. Don't annotate entries with them. They exist so you can notice when you've written five entries in a row with the same cadence and deliberately shift.

| Voice | Example |
|-------|---------|
| Knowledgeable local | "The old wall was twice as tall before the last bad winter brought half of it down. Nobody's rebuilt it." |
| Practical laborer | "The creek changed color for about two days. Muddy brown, like something upstream churned up the bottom." |
| Suspicious gossip | "The strange thing is nobody seems surprised. As if everyone was expecting it." |
| Weary authority | "I've told the council three times. They'd rather spend coin on the festival." |
| Reluctant witness | "I don't talk about it much. But since you asked: the light was blue, and it came from underground." |
| Impressed outsider | "I've worked a forge for twenty years and I've never seen joinery like that. The pieces interlock without pins." |


### Concrete beats abstract

Every detail should contain at least one concrete observation: a measurement, a color, a count, a comparison to something physical. Vague lines waste the player's click. Specific lines reward curiosity.

```
VAGUE (wastes the click):
"Things have gotten worse."
"It was really strange."
"People are concerned."

CONCRETE (rewards the click):
"Three wells went bad at the same time. The shallow ones, not the deep."
"Its stride between prints is longer than I am tall."
"The ash is white, not gray. And the soil underneath is baked hard as pottery."
```


---


## 2. Details Must Continue the Intro

This is the most important structural rule and the most common failure. Every detail must elaborate on what the intro established. If the intro mentions strange tracks, ALL details must be about those tracks: their size, spacing, behavior pattern, what they disturbed.

Good (intro about tracks):
```json
"intro": "Something's been leaving tracks near the old watchtower. Big ones, deep into the mud.",
"details": [
  "Three toes, clawed, wider than my hand spread flat. The stride between prints is longer than I am tall.",
  "They circle the same area every night. Whatever it is, it's watching something.",
  "The tracks stop at the tree line and don't come back out. Like it just vanished."
]
```

Each detail zooms in on a different aspect of the same observation: physical description, behavioral pattern, unexplained terminus. The player is building a picture from the same scene.

Bad (intro about tracks, details wander):
```json
"intro": "Something's been leaving tracks near the old watchtower. Big ones, deep into the mud.",
"details": [
  "Things have been strange lately.",
  "The council should do something about security.",
  "I heard a noise last week near the storehouse."
]
```

None of these elaborate on the tracks. The first is generic filler. The second shifts to governance. The third introduces an unrelated event. The coherent triplet has collapsed into disconnected fragments.

### The zoom-in test

Read the intro, then each detail in isolation. Ask: "Is this detail a closer look at the same thing?" If you have to squint to see the connection, the detail has drifted.


---


## 3. The Advice / Secret Pattern

Details naturally fall into two tiers of information access. Recognizing this pattern helps you write entries with satisfying depth.

**Advice** is safe, general, public knowledge. Any local would share it casually. It costs the NPC nothing.

```
"Best not to wander the east ridge after dark. The footing is terrible."
"If you're heading through the marshes, stick to the stones. The mud will swallow your boots."
"The creek changes course every spring. If you're looking for a crossing, go upstream."
```

**Secrets** are private knowledge shared with reluctance. The NPC is telling the player because they trust them, or because the situation demands it. There's a social cost.

```
"There's a way through the old foundation stones that most people don't know about. Keep it to yourself."
"The elder doesn't want people knowing, but the storehouse has been short for weeks."
"The guard was asleep when it happened. He asked me not to say anything."
```

In a well-structured entry, the details tend to be advice-tier: observations anyone could make, knowledge freely shared. The statCheck pass is where the real secret lives: the thing the NPC only reveals when the player demonstrates competence or earns trust.

You don't need to think about this division consciously for every entry. But if you notice your details are already revealing the deepest information and your statCheck pass has nothing left to add, the tiers have collapsed. Pull the most private detail out of the details array and save it for the pass.


---


## 4. Writing Reactions: Emotional Closure

Reactions close the emotional arc opened by the intro. They're the NPC's personal take: what they feel, what they think it means, how the community is responding. Where details deliver information, reactions deliver character.


### Three reaction registers

**Community-level reactions** capture what people think:
```
"Most folk think it's nothing. I'm not so sure."
"The elders won't talk about it, which worries me more than the thing itself."
"People are pretending everything is fine. It isn't."
```

**Personal reactions** capture what the NPC feels:
```
"Honestly? It rattled me. I haven't slept right since."
"I try not to overthink it. Some things just happen."
"I've seen worse. But not by much."
```

**Danger assessments** are appropriate for intense categories (danger, sighting, corruption, etc.):
```
"If this is what I think it is, staying isn't the brave choice. It's the stupid one."
"I'd keep my distance. Whatever this is, it isn't getting smaller."
```

Mix registers across entries in the same pool. If every reaction in your pool is a community-level reaction, the NPCs start sounding like news reporters instead of individuals.


### Reactions must add something the intro didn't

A reaction that restates the intro is wasted space. The intro establishes the situation. The reaction tells you what it's like to live with it.

Good:
```json
"intro": "The well water has tasted like copper for a week. Nobody knows why.",
"reactions": [
  "I've been boiling everything twice. Probably does nothing, but it makes me feel better.",
  "The children don't notice. That's what scares me: they've gotten used to it."
]
```

The intro is factual: copper taste, unknown cause. The reactions are personal: a coping ritual, a parental fear. New emotional information appears.

Bad:
```json
"intro": "The well water has tasted like copper for a week. Nobody knows why.",
"reactions": [
  "Times are hard.",
  "Something needs to change around here."
]
```

These could follow any intro. They carry no specific emotional response to the copper water. They're placeholders.


---


## 5. Reactions Must Close the Same Arc

This mirrors the detail-continuation rule, but for emotional coherence instead of informational coherence. If the intro is about poisoned water, reactions must express feeling about the poisoned water specifically. Not generic unease, not tangential anxiety about something else.

The test: read the intro, skip the details, and read each reaction directly. Does the reaction make emotional sense as a response to the intro? If the reaction could follow a dozen different intros, it's too generic.


---


## 6. Writing statCheck: Pass and Fail

Both pass and fail text are NPC dialogue. The NPC is speaking. Never second-person narration ("You look around and see nothing"). The game already shows a skill check result line (e.g., `[WIS] Perception 14 : Success`) before the NPC responds. Your text is the NPC's reaction to the player's demonstrated skill or lack thereof.


### Pass: earned trust

The player demonstrated competence or insight. The NPC recognizes this and opens up. The pass information should be a genuine reward: specific, useful, not available through the normal detail branches. It's the secret tier: the thing the NPC was holding back.

Good pass examples, by skill type:

```
PERCEPTION:     "You noticed the smaller tracks alongside? Something was following it. Keeping pace."
INSIGHT:        "You can tell I'm holding something back? Fine. The truth is we've been losing stock for weeks."
NATURE:         "You know your plants. That vine shouldn't be growing this far north. Not naturally."
INVESTIGATION:  "I examined the clay she used that day. It had a streak of reddish mineral that isn't in her normal supply."
HISTORY:        "The song is an old warding chant: one that hasn't been spoken in this region for generations."
```

Notice the pattern: the NPC acknowledges the player's observation, then shares something they wouldn't have volunteered. The acknowledgment is important: it makes the pass feel earned, not random.


### Fail: not punishment

The NPC doesn't mock the player. They don't lecture. They deflect naturally: misunderstand the question, give a vague non-answer, or change the subject. The player should feel they missed something, not that they were insulted or talked down to.

Good fail examples:

```
PERCEPTION:     "Tracks? I wouldn't know what to look for. You'd have to ask someone more observant."
INSIGHT:        "I don't know what you're getting at. Everything's fine. Was there something else?"
NATURE:         "Plants are plants. I don't pay much attention to what grows where."
INVESTIGATION:  "The symbols do not match any writing system I have seen. Could be personal notation or could be something older."
HISTORY:        "It sounds like any other made-up tune, honestly. Without context, there is no telling if it means anything."
```

The NPC isn't lying (usually). They genuinely don't have the frame to engage with what the player tried. The information stays hidden because the NPC doesn't realize it's relevant, not because they're withholding it. This distinction matters: it keeps the NPC sympathetic even when the check fails.


### Fail text should never narrate the player's actions

This rule trips people up. The fail is tempting to write as a scene:

```
BAD:  "You kneel down and examine the tracks closely, but the mud reveals nothing."
BAD:  "You squint into the fog but see nothing unusual."
```

These are narrator lines, not NPC dialogue. The NPC can't describe what the player is doing. Write what the NPC says in response:

```
GOOD: "The mud is too churned up out there. I already told you everything worth seeing."
GOOD: "In the dark, everything looks like it could be something. I have scanned that tree line a dozen times since and nothing stands out."
```


---


## 7. NPC Self-Description: Confessions, Not Resumes

When writing entries where the NPC references their own role (common in craftsmanship and community pools), frame it as a confession or an aside, not a job description. Reveal a limitation, preference, complaint, or quiet pride.

```
GOOD: "I'm a blacksmith. Mostly horseshoes, if I'm being honest. Not much call for swords around here."
GOOD: "Guard duty, technically. Though most nights the only threat is boredom."
GOOD: "I keep the ledger for the granary. It's dull work, but someone has to track what goes in and what comes out."

BAD:  "I am a blacksmith. A blacksmith works with metal to forge weapons and armor."
BAD:  "I guard the settlement perimeter."
BAD:  "I am in charge of the granary supplies."
```

The bad examples read like tooltips. They define the role for someone who doesn't know what a blacksmith is. The good examples assume the player knows what a blacksmith does and instead reveal what this particular blacksmith's life is actually like.

The same principle applies when NPCs mention other roles:

```
GOOD: "The mason offered to do it at cost if they'd just decide. They're still deciding."
GOOD: "Our smith studied them for a full day and said she'd need to take them apart to understand the technique. But she can't figure out how to take them apart."

BAD:  "The mason, who builds with stone, said he could help."
BAD:  "The smith, who works at the forge, looked at them."
```

Trust the player to know what people do. Use the reference to show how they do it, or how they feel about it.


---


## 8. Complete Entry Examples

Two full entries, annotated with what makes them work.

### Example 1: danger pool

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

Why it works:
- **Intro** sets one clear subject: tracks. States the core strangeness (unrecognizable) and establishes NPC credibility (thirty years).
- **Details** zoom in from three angles: physical description, behavioral pattern, unexplained disappearance. Each stands alone. Any two make a compelling pair.
- **Reactions** are personal. One is a behavioral change (stopped walking), the other is an argument with a neighbor's dismissal. Both are specific to the tracks.
- **Pass** reveals a hidden detail the NPC wasn't going to volunteer: a second set of tracks. This reframes the observation from "something dangerous" to "something being led." New information, new implication.
- **Fail** closes the door without insulting the player. The NPC has already shared what they know. The mud genuinely makes further observation difficult.


### Example 2: community pool

```json
{
  "id": 1,
  "intro": "A family of refugees showed up at {subject_focus_the} last week. Eight people, two carts, everything they own packed into bundles.",
  "details": [
    "They say their village was abandoned after the river changed course. No water, no crops, no choice but to move.",
    "The elders offered them the empty plot near the mill, but some of the village folk aren't happy about sharing resources with strangers.",
    "The father is a skilled tanner. He's already started helping at the leather shop without being asked."
  ],
  "reactions": [
    "We've got room and we've got food. Turning people away when they've lost everything isn't who we are.",
    "I understand the worry. Resources are tight enough without eight more mouths. But tight and impossible are different things."
  ],
  "statCheck": {
    "pass": "I talked to the father quietly. He mentioned their village wasn't just abandoned: they were the last to leave. Others left months ago, heading the same direction. Something is pushing people out of the lowlands.",
    "fail": "They are tired and guarded, that family. I asked a few questions myself and got the basics, nothing more. They keep the details close."
  }
}
```

Why it works:
- **Intro** gives a vivid image: eight people, two carts, bundles. You see the scene.
- **Details** cover three aspects of the same event: why they came, how they were received, who they are. Each is independently interesting.
- **Reactions** present two sides of the same moral question without resolving it. The NPC has an opinion (sympathetic) but acknowledges the counterargument.
- **Pass** escalates. The refugee situation isn't isolated: something larger is happening. This is information the NPC only shares because the player earned trust. It recontextualizes the entire entry from a local event to a regional pattern.
- **Fail** is gentle. The NPC tried the same thing the player tried and got the same nothing. Shared frustration, not mockery.


---


## 9. Quality Tests

Run these checks before submitting an entry. If any answer is "no," revise.

**For each detail:**
1. Does the NPC have a perspective? If you removed the opinion clause, would anything be lost?
2. Is there a concrete observation? A measurement, color, count, comparison, or physical description?
3. Does it continue the intro? Is this detail a closer look at the same thing the intro established?
4. Does it stand alone? Each detail is shown independently (70% chance each, max 2). It must make sense even if the other details aren't shown.

**For each reaction:**
1. Does it close the emotional arc? It should respond to the specific situation from the intro, not to the world in general.
2. Does it add something the intro didn't? New emotional information, not a restatement of the facts.
3. Is there personality? Even a single clause of opinion transforms a reaction from reporting to characterization.

**For statCheck pass:**
1. Does it reveal something new? Information that isn't available through the detail branches.
2. Does the NPC acknowledge the player's skill? An opening clause that recognizes the player noticed something.
3. Does it reframe or escalate? The best passes change what the player thinks the entry is about.

**For statCheck fail:**
1. Does it feel natural? A deflection, a non-answer, a change of subject. Not punishment.
2. Is it NPC dialogue? The NPC speaking, never narrating what the player does or sees.
3. Is the NPC sympathetic? The player should feel they missed something, not that the NPC is being hostile.
