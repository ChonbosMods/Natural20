# Writing Not-Quite-Greetings: Voice Guide

Not-quite-greetings are NPC greeting lines that avoid the warm-welcome pattern ("Well met, traveler", "A fresh face!", "You look like someone who..."). They live in the `greetings` array of `greeting_lines.json` alongside the standard greetings.

Their purpose is tonal variety. The existing pool of ~1,600 greetings is technically unique but structurally repetitive: 67 lines open with "You look like...", 277 contain "welcome." Players correctly perceive these as the same greeting in different words. Not-quite-greetings break that pattern by being mundane, disinterested, and underwhelming.


## The Arrival Test

This is the single most important rule. Every not-quite-greeting must pass:

**Does this line only make sense because someone just walked up?**

If you could swap it into a topic intro (the NPC's opening line after the player clicks a conversation topic), it's not a greeting: it's a topic that leaked into the wrong pool.

```
PASS: "I just sat down. Of course."
  -> only works because someone arrived and interrupted

PASS: "Loud boots. I heard you coming from the ridge."
  -> directly references the player's approach

PASS: "You're not the supply cart. Obviously."
  -> reacts to a specific person arriving

FAIL: "The fence on the west side has been leaning for weeks."
  -> works perfectly as a community topic intro

FAIL: "I keep finding the same kind of rock in my garden."
  -> free-floating observation, no arrival anchor

FAIL: "Soup is just patience in a bowl."
  -> mundane philosophy, could open any conversation
```

The structural anchor can be explicit ("you showed up") or implicit ("I just sat down" implies the interruption). But it must be there. If the NPC would say it to an empty room, it fails.


## Forbidden Words

Never use in a not-quite-greeting:

- **welcome**, **greetings**, **hail**, **well met**: explicit greeting language
- **traveler**, **stranger**, **friend**: marks the player as special
- **adventurer**, **hero**: breaks the disinterested tone entirely

Also avoid opening with "You look like..." or "You've got the..." as these are the most over-represented patterns in the existing pool.


## Tone

The voice principle from smalltalk pools applies here too: **mundane delivered straight**. The NPC is sincere. They genuinely don't care that you arrived, or they're genuinely preoccupied, or they genuinely think the timing commentary matters more than who showed up. The humor is structural (the gap between "this is a greeting" and "this person clearly doesn't care"), never acknowledged in the text.

```
GOOD: "My mother raised me to acknowledge people. So: acknowledged."
  -> sincere about their reluctance

BAD:  "Oh wow, another visitor. How thrilling."
  -> sarcasm, self-aware diminishment

GOOD: "That's three people today. Unusual for this time of year."
  -> genuinely treating arrivals as data

BAD:  "Great, just what I needed: company."
  -> performed annoyance for the player's benefit
```


## The 12 Categories

Each category is a different structural relationship between the NPC and the player's arrival. When authoring new entries, aim for variety across categories rather than depth within one.


### 1. Preoccupied with Tasks

The NPC is mid-chore and barely registers your presence. They're fixing, carrying, counting, cooking, cleaning. The arrival is an interruption to something more important.

```
"If I stop stirring this it'll burn, so: make it quick."
"Thirty-seven, thirty-eight: sorry, what? Never mind. One, two, three..."
"Somewhere in this pile there's a bolt that actually fits. Somewhere."
```

**Anchor type:** The NPC is doing something. Your arrival is incidental to their task. The line implies they'll go right back to it.


### 2. Barely-There Acknowledgment

Minimal, interjection-based recognition that you exist. Short utterances: "Mm.", "Oh.", "Hm?" followed by a flat observation about your presence.

```
"Mm. You're here."
"Hm? Oh. You. Sure."
"Oh. I thought that was the wind. Nope: it's you."
```

**Anchor type:** The NPC notices you arrived and that's the entire content of their reaction. Nothing more is offered.


### 3. Waiting for Something

The NPC was expecting someone or something else. Your arrival is a disappointment or a non-event: you're not the delivery, the relief shift, the courier, or the friend they were watching for.

```
"You're not the supply cart. Obviously."
"Every time I hear footsteps I think it's the provisioner. It never is."
"They told me to wait here until someone brings the key. So I'm waiting."
```

**Anchor type:** Expectation-mismatch. The NPC reacts to WHO arrived (not the right person) rather than THAT someone arrived.


### 4. Flat Acknowledgment

Full sentences with zero emotional investment. Not interjections (that's category 2): these NPCs form complete thoughts, they just don't put any warmth in them. Polite-ish but thoroughly indifferent.

```
"I see you. You see me. We can move past that part."
"You can talk to me if you want. Or not. Either way."
"Most people who walk up to me want something. Do you want something."
```

**Anchor type:** The NPC acknowledges the social situation (someone approached, conversation is expected) and engages with it at minimum effort.

**Note:** Avoid interjection openers ("Hm?", "Mm.", "Oh.") in this category: those belong in Barely-There.


### 5. Train of Thought Derailed

Your arrival broke the NPC's concentration. They had something going on in their head and now it's gone. The line is about the loss, not about you.

```
"I almost had it. Almost. And then: you."
"Something about fences. Or was it hinges. See, now I'll never know."
"I was mid-calculation. Not a chance I'm finding that number again."
```

**Anchor type:** Cause and effect. The NPC had [thought], your arrival destroyed it, and they're mourning the thought.


### 6. Cataloging Arrivals

The NPC treats your arrival as a data point. They compare you to the last visitor, note the frequency of foot traffic, or file you into their mental tally. You're not a person: you're a statistic.

```
"That's three people today. Unusual for this time of year."
"Nobody for three weeks, then two of you in the same hour."
"The last one came through at a full sprint. Nice to see someone walking for once."
```

**Anchor type:** Your arrival is contextualized against a pattern. The NPC is more interested in the pattern than in you.


### 7. Timing Commentary

The NPC reacts to WHEN you arrived, not WHO arrived. Too early, too late, right when they were about to leave, during their one quiet moment. The timing matters more than the person.

```
"I just sat down. Of course."
"Two more minutes and I'd have been asleep on my feet."
"Right at shift change. Figures."
```

**Anchor type:** Temporal. The NPC's reaction is about the intersection of your arrival with their schedule.


### 8. Distracted Mid-Arrival

Something else catches the NPC's attention right as you arrive. They split focus between you and whatever distracted them. The line references BOTH your arrival AND the distraction.

```
"The birds all went quiet right when you walked up. Probably nothing. Yes?"
"Did you step on something crunchy just now? I keep hearing it. Anyway: what's on your mind?"
"Do you smell smoke? No: it's someone's cookfire carrying on the breeze. What do you need?"
```

**Anchor type:** Dual focus. The NPC acknowledges you but is simultaneously processing something else. Both elements must be present.

**Note:** These tend to run longer (closer to 2 sentences). That's fine: the length comes from juggling two threads, not from being verbose.


### 9. Reluctant Obligation

The NPC feels socially obligated to acknowledge you and makes that obligation visible. They know they SHOULD say something and are doing the bare minimum. Not hostile: transparently performing the greeting role with minimal effort.

```
"My mother raised me to acknowledge people. So: acknowledged."
"I was going to pretend I didn't see you, but that felt worse than this."
"People walk up and you're supposed to say something. That's what I'm doing now."
```

**Anchor type:** Meta-awareness of the social contract. The NPC references the ACT of greeting rather than performing one naturally.

**Caution:** This category is the closest to sarcasm. Keep the NPC sincere: they're not performing reluctance for laughs, they genuinely find the obligation mildly tiresome.


### 10. Resigned to Interaction

The NPC can see a conversation is about to happen and accepts it. Not annoyed, not happy: just resigned. They're mentally shifting from "alone with my thoughts" to "talking to a person now."

```
"And there it is: the sound of footsteps that want something."
"No point pretending I didn't see you coming. What is it?"
"The moment I stopped expecting company, here you are."
```

**Anchor type:** Transition from solitude to social. The NPC narrates the internal shift from alone-mode to conversation-mode.


### 11. Practical Warnings

Instead of greeting you, the NPC immediately gives you a small piece of practical information. Where to step, what to avoid, what's broken nearby. The greeting is skipped entirely in favor of something useful (or uselessly specific).

```
"Watch the third step. It's loose."
"That dog sleeping by the cart bites. Go around."
"Those berries along the fence are ornamental. Saw someone eat a handful yesterday: didn't go well."
```

**Anchor type:** Your arrival triggered a duty-of-care impulse. The NPC saw you approaching and thought "I should warn them about [thing]" rather than "I should greet them."

**Note:** Keep the warnings biome-neutral and mundane. Loose stones, sticky doors, unreliable benches: not monsters, not danger, not quests.


### 12. Sensory Reaction

The NPC's greeting is a comment about how they perceived your approach. They heard your footsteps, saw your shadow, felt the floorboards shift, noticed the gate creak. Their first words are about the sensory experience of your arrival, not about you as a person.

```
"That crunch on the gravel gave you away three paces back."
"The chickens scattered. Something spooked them, and here you are."
"Felt the draft when the door swung: someone's here."
```

**Anchor type:** Sensory cause-and-effect. The NPC detected your arrival through a specific channel and reports it.


## General Rules

1. **Use colons instead of em dashes.** This applies to all authored content.

2. **Keep lines short.** 1-2 sentences max. Not-quite-greetings are punchier than topic intros.

3. **Biome-neutral.** Use ridges, paths, walls, water, old structures. No snow, desert, or jungle specifics.

4. **No sarcasm.** The NPC is sincere. If the line reads as a joke the NPC is telling, rewrite it.

5. **No fourth-wall breaking.** The NPC doesn't know they're an NPC. They don't know what a "greeting" is in game terms.

6. **No player-specialness.** The player is just a person who walked up. Not a hero, not an adventurer, not someone the NPC finds particularly interesting.

7. **Vary sentence structure.** Don't start 5 consecutive lines with "I was..." or "You're...". Mix fragments, questions, and full statements.

8. **Test with the swap.** Before finalizing, try moving the line into a topic pool. If it works there, it doesn't work here.


## Category Distribution

Aim for roughly equal representation across categories. The current 300 not-quite-greetings are distributed 25 per category (with 3 stragglers from mixed batches). When expanding, maintain this balance rather than overloading one category.

For reference, the standard greetings (~1,600 lines) are overwhelmingly warm-welcome tone. The not-quite-greetings are currently ~16% of the total pool: enough to break monotony without dominating the player experience.


## Where Lines That Fail the Arrival Test Go

Lines that read as topic material should not be discarded. Move them to `staged_topic_intros.json`, categorized by their best-fit topic pool (mundane_daily_life, community, craftsmanship, weather, nature, curiosity, nostalgia, trade). These serve as seed intros for future v2 triplet expansion: they need details and reactions added before they ship as pool entries.
