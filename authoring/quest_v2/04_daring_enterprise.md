# Situation 04: Daring Enterprise

**Polti classification:** Daring Enterprise — a bold expedition or undertaking that involves significant risk for a specific goal.

**Tone arc:** ambitious/nervous → triumphant

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

The NPC wants something that requires courage to obtain. This is voluntary risk — nobody forced this. The NPC has a goal, knows it's dangerous, and has decided the reward justifies the danger. They need someone capable to execute the plan or assist with the hard part.

The key distinction from Pursuit (which is reactive threat elimination) and Recovery (which is about restoring what was lost): Daring Enterprise is proactive and aspirational. The NPC is reaching for something, not defending against something. This makes the emotional register more adventurous and less desperate.

The NPC should acknowledge the risk honestly. This isn't a casual errand they're understating — they know what they're asking and wouldn't ask lightly. The excitement and the fear should coexist.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC presents their plan — what they want, where it is, and why it's risky. The tone balances ambition with honest nerves. The NPC has been thinking about this and has decided to go for it. They should sound like someone who's made a decision, not someone who's still weighing options. The danger should be stated plainly, not dramatized.

### `acceptText`

Excitement tempered by awareness. The NPC is glad to have a partner in this, not just a hired hand. There's a sense of shared venture — "we're doing this." More energized than Supplication's relief or Vengeance's grim satisfaction.

### `declineText`

Disappointment but not desperation. The NPC had a plan and it required help; without help, the plan stalls. They might express frustration at being stuck rather than fear of consequences. The enterprise will wait — it's not going anywhere, but neither is the NPC without assistance.

### `expositionTurnInText`

The first phase went well. The NPC's confidence grows — the plan is working. If more conflicts follow, the enterprise is deeper in than expected but still on track. The NPC should sound energized by progress, not merely relieved.

### `conflict phases` (conflict1 through conflict4)

Each phase pushes deeper into the enterprise. Early phases establish the danger and test the approach. Later phases bring the goal closer. The NPC's nerves give way to focus and determination as the plan unfolds. Longer chains work well for Daring Enterprise — this is a situation that earns multiple phases because each step of the expedition reveals something new.

### `conflictTurnInText` (any conflict turn-in)

The NPC reacts to each phase with growing confidence. The plan is working. There should be a sense of momentum — things are moving, the goal is closer. If the last conflict is complete, the NPC should be on the edge of triumph.

### `resolutionText`

Triumph. The enterprise succeeded. The NPC should sound proud — not arrogant, but genuinely fulfilled. They dared to try something difficult and it worked. The reward should feel like a share of the spoils or a well-earned payment for partnership, not charity.

### `skillcheckPassText`

The NPC reveals the real reason they want this — not just the practical goal, but why it matters personally. Or they share tactical knowledge about the danger that makes the player better prepared. Best fit skills: INSIGHT (personal motivation behind the ambition), PERCEPTION (player notices the NPC has been preparing for this longer than they let on), PERSUASION (NPC admits their confidence is partly performance), NATURE (player assesses the danger and confirms the NPC's plan is sound), INVESTIGATION (player spots a flaw in the plan the NPC overlooked), HISTORY (player recognizes this kind of enterprise has been tried before and offers context).

### `skillcheckFailText`

The NPC stays on the practical surface. The plan is presented without the personal dimension or the deeper tactical detail.

---

## Anti-Patterns Specific to Daring Enterprise

- **The enterprise is routine.** If there's no real risk, this is Obtaining. Daring Enterprise requires genuine danger or difficulty that the NPC acknowledges.
- **The NPC is being reckless.** There's a difference between bold and foolish. The NPC should have a reason and a plan, even if the plan is imperfect. Pure recklessness belongs nowhere — it makes the NPC unsympathetic.
- **The triumph is hollow.** The goal of the enterprise must feel worthwhile at resolution. If the player thinks "we risked all that for THIS?" the enterprise was poorly framed.
- **The danger is understated.** The NPC must be honest about the risk. Understating the danger and then escalating in conflict phases makes the NPC seem either dishonest or foolish.
