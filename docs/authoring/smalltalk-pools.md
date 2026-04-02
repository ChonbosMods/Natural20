# Writing Smalltalk Pool Entries: Voice Guide for Mild Categories

This guide covers voice and tone for the 6 mild topic categories: **weather**, **trade**, **craftsmanship**, **community**, **nostalgia**, and **festival**. These are the templates with `reactionIntensity: "mild"`: everyday conversation, not urgent rumors.

For JSON format and mechanical rules (field names, variable syntax, stat check conventions), see `pools.md`. This document tells you *how* entries should sound.


## The v2 Entry Format

Every pool entry is a self-contained triplet:

```json
{
  "id": 0,
  "intro": "NPC's opening statement about what they observed.",
  "details": [
    "Elaboration on that observation.",
    "Another angle on the same topic."
  ],
  "reactions": [
    "The NPC's opinion or emotional response.",
    "A different take on the same situation."
  ],
  "statCheck": {
    "pass": "Hidden info the NPC shares when the player demonstrates competence.",
    "fail": "The NPC's deflection when the player's attempt falls short."
  }
}
```

There are no separate intro pools, detail pools, or reaction pools. Each entry is one coherent unit: what the NPC saw, what they know about it, and how they feel.


## 1. Self-Contained Entries

This is the single most important principle. Every entry (intro + details + reactions) must work as a standalone conversation. The intro opens cold: no preceding context, no assumption about what the player asked. Details elaborate on the same observation. Reactions close the emotional arc naturally.

The intro works regardless of what the player does next:

```
GOOD intro: "I saw tracks near the storehouse this morning. Big ones."
  -> works if the player asks "Where exactly?"
  -> works if the player asks "Should we be worried?"
  -> works if the player says nothing at all

BAD intro: "You know what I mean, right?"
  -> demands agreement, breaks if the follow-up is unrelated

BAD intro: "And that's not even the worst part."
  -> implies a preceding statement that may not exist
```

Test every intro by imagining it as the NPC's opening line when the player walks up and clicks a topic button. If it sounds like a continuation, rewrite it.


## 2. Category Archetypes

Each mild category has a natural voice. These aren't rigid templates: they're the grain of the wood. Write with it, not against it.


### Weather

Natural phenomena, seasonal observations, practical weather impact. NPCs treat weather with genuine conviction, not small talk filler. "The creek's been running low for a week" is stated with the same gravity as a bandit sighting. Weather affects livelihoods, plans, moods: it matters.

```json
{
  "id": 0,
  "intro": "The creek near {subject_focus_the} has been running low for a week now. You can see the bottom in places that used to be waist-deep.",
  "details": [
    "The banks are dry enough to walk on. I found fish bones on exposed rocks: they dried out before anything could eat them.",
    "The well water tastes different too. More mineral, like copper. People are noticing."
  ],
  "reactions": [
    "If it doesn't rain in the next few days, we'll feel it. The gardens are already wilting.",
    "Some folk are talking about rationing the well. We're not there yet, but the fact that people are saying it out loud means they're worried."
  ]
}
```

Weather entries ground the world. They connect sky to soil to table. A drought isn't abstract: it's the taste of the well water, the dead fish on exposed rocks, the neighbor who mentions rationing before anyone else does.


### Trade

Commerce, goods, routes, prices, merchant opinions. Trade entries often double as faction opinions: "The road tolls went up and nobody told the merchants" reveals an attitude about governance without stating it directly. Write about route disruptions, price changes, supply shortages, and the people caught in between.

```json
{
  "id": 0,
  "intro": "The supply cart turned back two days ago. The driver said the road was blocked, but he wouldn't say by what.",
  "details": [
    "That's the third delivery missed this season. We can manage without luxuries, but iron and salt are running low.",
    "The merchants in the square are quieter than usual. When traders stop talking about prices, it means the prices are bad."
  ],
  "reactions": [
    "Costs will go up. They always do when supply tightens. The question is how much and for how long.",
    "The merchants are worried, even if they won't admit it. You can see it in how they count their stock."
  ]
}
```

Trade entries work because commerce touches everyone. A blocked road affects the smith, the baker, the healer, and the family that needs salt. Write the ripple, not just the splash.


### Craftsmanship

Materials, technique, workshop life, craft pride. These entries sound like tradespeople: specific about materials ("the ore quality has been terrible"), proud or frustrated about their work, aware of supply chains. A craftsperson's complaint about their tools is as serious to them as a guard's report about the perimeter.

```json
{
  "id": 0,
  "intro": "I've been through three whetstones this month. The new batch from the quarry is soft: crumbles before the blade even takes an edge.",
  "details": [
    "The old quarry stone was dense and even. This new supply has veins of chalk running through it. Useless for fine work.",
    "I sent word to the quarry master, but he says the good seam ran out. What he's cutting now is all that's left."
  ],
  "reactions": [
    "A dull blade is a dangerous blade. I'm not putting substandard edges on tools people trust their hands to.",
    "I'll have to source stone from further out. It'll cost more, but at least it'll do the job."
  ]
}
```

Craft entries are grounded in material reality. The NPC doesn't say "times are hard for craftspeople." They say "the whetstones are soft." Specificity is the entire voice.


### Community

Village life, governance, social dynamics. The richest category for interconnected stories. Infrastructure disputes, new arrivals, communal projects, quiet kindness, youth leaving. NPCs have opinions about the council, the guards, the elders: and those opinions reveal character without the NPC announcing their personality.

```json
{
  "id": 0,
  "intro": "The elder left the meeting hall early today. I've never seen that before.",
  "details": [
    "The meeting was supposed to run until evening. It barely lasted an hour.",
    "Two families were arguing about well access. Louder than usual. The kind of loud where people stop pretending to be polite."
  ],
  "reactions": [
    "Something's shifted. People don't walk out of meetings here. Not the elder, especially.",
    "I don't know what was said, but the mood around the square has been off since."
  ]
}
```

Community entries are gossip in the best sense: the social nervous system of a village. The NPC noticed something, they're telling you about it, and they have a quiet opinion. The best community entries reveal two things at once: what happened, and what the NPC values.


### Nostalgia

The past, memory, tradition, how things used to be. These entries have an older voice: "We went through something like this before, years back." Memory mixed with judgment about the present. Not pure wistfulness: the NPC is comparing then to now, usually unfavorably. But the comparison is earned: they remember specific things, not a vague golden age.

```json
{
  "id": 0,
  "intro": "There was a bell in the tower near {subject_focus_the} that rang every morning at dawn. The whole village woke to it for generations.",
  "details": [
    "It had a tone you could feel in your chest. Deep and warm, not sharp. It didn't jolt you awake: it called you gently.",
    "The bell cracked during a storm eight years ago. They took it down and never replaced it."
  ],
  "reactions": [
    "Some mornings I wake at dawn anyway, expecting to hear it. The silence where the bell should be is the loudest sound I know.",
    "The village uses a horn now. It works, but it's not the same. 'It works' is a sad thing to say about a tradition."
  ]
}
```

Nostalgia entries earn their weight through concrete detail. "Things were better before" is a cliche. "The bell had a tone you could feel in your chest" is a memory. The specificity is what makes the NPC feel like a real person who lived through something, not a sentiment dispenser.


### Festival

Celebrations, preparation, aftermath, ceremonies. Festival entries have three natural phases, and entries can live in any of them:

- **Before the event:** anticipation and logistics. Who's baking, who's brewing, what's being built.
- **During:** observations about who showed up, who didn't, what's happening.
- **After:** what went well, what went wrong, the mess, the glow.

Not just "there's a festival" but the social dynamics around it.

```json
{
  "id": 0,
  "intro": "The harvest festival near {subject_focus_the} is coming up and this year the village is going all out. Three days of feasting, competitions, and music.",
  "details": [
    "The brewers have been working overtime. There are seven different ales lined up for tasting, and the rivalry between the two main brewers is getting personal.",
    "The bonfire is the centerpiece. They've been stacking wood for weeks: it'll be tall as a house this year."
  ],
  "reactions": [
    "It's the one time of year everyone puts their grievances aside. Hard to stay angry at your neighbor when you're both singing off-key around a fire.",
    "I've been baking for three days straight. My back aches, my hands are raw, and I wouldn't trade it for anything."
  ]
}
```

Festival entries are warm but not saccharine. The NPC is tired from baking, annoyed at the brewer rivalry, wryly aware that the festival is a truce more than a harmony. This is affection expressed through observation, not declaration.


## 3. Archetype Color

Even in shared pools, word choice implies who's speaking. Write entries that sound like different types of people. When a guard-flavored line lands on a guard NPC, it feels bespoke. When it lands on a merchant, it still works: the observation is universal, the flavor is a bonus.

| Archetype | Flavor | Example fragment |
|-----------|--------|-----------------|
| Guard / patrol | Security, perimeter, vigilance | "Quiet night. For once." |
| Merchant / trader | Prices, routes, stock, margins | "The road tolls went up and nobody told the merchants." |
| Craftsperson | Materials, technique, workshop | "I've been through three whetstones this month." |
| Scholar / healer | Observation, pattern, caution | "The patterns are unsettling, if you look at them long enough." |
| Elder / authority | Responsibility, memory, judgment | "We went through this before. People forget." |
| Gossip / busybody | Other people, opinions, speculation | "Did you notice the new family hasn't spoken to anyone yet?" |
| Laborer / farmer | Weather, soil, animals, fatigue | "The ground has been too hard to turn for a week." |

Don't tag entries by archetype. Don't try to write "a guard entry" or "a merchant entry." Instead, notice that the verb "counted" sounds like a guard or quartermaster, "noticed" sounds like a gossip, and "found" sounds like a laborer. The variety emerges naturally when you rotate through different verbs and observation styles.


## 4. The Modular Formula

When you need to fill a pool quickly, start here: **"I [verb] a [local thing] [timeframe]. [One-sentence opinion]."**

This formula maps directly to the v2 entry format:

- **The formula generates the intro.** One observation, one time reference, one reaction.
- **Write 2-3 details** that elaborate on what was observed. Concrete, specific, continuing the same thread.
- **Write 2 reactions** that give the NPC's take. Opinion, emotion, or implication.

Examples across categories:

```
weather:
  intro: "I noticed the creek running low yesterday. Third time this month."
  details: ["The banks are dry enough to walk on now. Never seen that before.",
            "The well water tastes different too. More mineral, like copper."]
  reactions: ["If it doesn't rain soon, we'll feel it.",
              "Some folk are already talking about rationing."]

trade:
  intro: "I heard the supply cart turned back two days ago. Bad sign."
  details: ["The driver said the road was blocked. Wouldn't say by what.",
            "That's the third delivery missed this season."]
  reactions: ["Prices will go up. They always do.",
              "The merchants are worried, even if they won't admit it."]

community:
  intro: "I watched the elder leave the meeting hall early today. Never seen that before."
  details: ["The meeting was supposed to run until evening. It barely lasted an hour.",
            "Two families were arguing about the well access. Louder than usual."]
  reactions: ["Something's shifted. People don't walk out of meetings here.",
              "I don't know what was said, but the mood's been off since."]

craftsmanship:
  intro: "I found a crack in the kiln wall this morning. Runs from the base to the vent."
  details: ["The bricks have been heating unevenly for weeks. This was bound to happen.",
            "If we lose the kiln, we lose the only one within a day's travel."]
  reactions: ["I can patch it, but patching isn't fixing. It needs rebuilding.",
              "The mason says he can look at it next week. Next week is a long time when you can't fire anything."]

nostalgia:
  intro: "I found my mother's old recipe book in the bottom of a trunk. Pages are falling apart."
  details: ["Half the ingredients she lists aren't available anymore. Herbs that used to grow wild around here.",
            "Her handwriting gets shakier toward the end. The last entries are almost unreadable."]
  reactions: ["I tried one of the recipes. Tasted close, but not right. Memory fills in what the tongue can't match.",
              "My daughter asked if she could have the book. That meant more to me than I expected."]

festival:
  intro: "I counted the lanterns strung up for the walk last night. Fewer than last year."
  details: ["Three families didn't put one out. Two of them moved away. The third just didn't bother.",
            "The ones that are up are beautiful, though. The chandler's wife made hers from colored glass this year."]
  reactions: ["Fewer lights, but the ones that show up mean more.",
              "I'll make two next year. One for me, one for whoever needs it."]
```

The formula works because:

1. **"I [verb]"** makes it personal. The NPC was there.
2. **"[timeframe]"** anchors it in recent memory. This happened, not abstractly but recently.
3. **"[opinion]"** gives the NPC a reaction. They aren't a camera: they have a take.

Vary the verb: *saw, heard, noticed, found, watched, counted, stepped in, overheard, checked, asked about.* Vary the opinion tone: *worried, annoyed, indifferent, suspicious, relieved, resigned, amused.* The combinations are endless.


## 5. The Opinion Pattern

For every factual intro, consider what the NPC thinks about it. The fact is universal: any NPC could observe it. The opinion is individual: it reveals the NPC's priorities, fears, or values.

| Fact (intro) | Opinion (reaction) |
|---|---|
| "The traders' guild is taking on new members." | "Not bad work, if you can get in." |
| "The eastern road has been quiet." | "Makes you wonder what they're planning." |
| "The harvest came in early this year." | "Won't hear me complaining." |
| "The old wall needs patching again." | "Nobody wants to do it, but nobody wants to be the one who didn't." |
| "There's a new well being dug on the south side." | "About time. The north well has been serving too many people for too long." |

The trailing opinion should be:

- **Short.** One clause, rarely more than one sentence.
- **Personality-agnostic.** Works whether the NPC is a guard, a baker, or a farmer.
- **Slightly ambiguous.** "Makes you wonder" doesn't commit to an interpretation. The player reads into it what they bring.

In the v2 entry format, the fact lives in the `intro` and `details`. The opinion lives in the `reactions`. This separation is structural: the player clicks "What do you think?" to hear the opinion. It shouldn't leak into the factual parts.


## 6. Mundane Observations Delivered Straight

This is the most distinctive voice trait of the mild categories. NPCs treat trivial local color with the same gravity as serious events. A guard reports a pest problem with the same conviction as a bandit incursion. A farmer describes the soil with the same weight a soldier would describe a fortification.

This is not sarcasm. It is not comedy writing. The NPC genuinely finds their observation important. The humor, when players notice it, is structural: it comes from the gap between the gravity of delivery and the smallness of the subject. That gap is never acknowledged in the text.

```
GOOD: "The rats in the lower storehouse are back. Bigger this time."
GOOD: "Something has been getting into the grain. Not a lot, but enough to notice."
GOOD: "The fence on the west side is leaning again. Third time this season."

BAD:  "Oh no, the dreaded grain thief strikes again!"   <- sarcasm
BAD:  "Ha, you won't believe what I saw by the well."    <- framing it as funny
BAD:  "It's only a fence, but still."                    <- self-aware diminishment
```

The last example is the subtlest mistake. When the NPC says "it's only a fence, but still," they're telling the player the subject is small. Don't do that. The NPC thinks the fence matters. They're telling you about the fence because it's their fence, their village, their problem. That sincerity is the whole voice.


## 7. Entry Quality Test

Before adding an entry to a mild pool, run it through these checks:

1. **Delete everything before the intro.** Does it stand alone as an opening statement? If it sounds like the middle of a conversation, rewrite it.

2. **Imagine three different player follow-ups.** "Tell me more." "How do you feel about that?" Silence. Do the details elaborate naturally on the intro regardless of which button the player clicked?

3. **Read the intro out loud.** Does it sound like something a person would say while leaning on a fence? If it sounds like a book, a quest log, or a weather report, bring it down to earth.

4. **Check category spread.** If the last 5 entries you wrote in a pool are all about food, switch topics. Pools need internal variety: different aspects of the same category, not the same aspect repeated.

5. **Do the reactions add personality?** If they're just restating the intro in different words, add an opinion, an emotion, or an implication. "The creek is low" restated as "the water level has dropped" is not a reaction. "If it doesn't rain soon, we'll feel it" is.

6. **Does the entry work with a proper noun subject?** Mentally substitute "Thornfield" or "Blackrock Mine" for the subject variable. If the sentence reads awkwardly, adjust your variable usage (see `pools.md` rule 4).

7. **Is the stat check NPC dialogue?** If you wrote "You examine the soil and find it unusually dry," rewrite it as what the NPC says: "The soil here is different than it looks. Dig down an inch and it's powder. Something is pulling the moisture out from below."
