# Situation 08: Enigma

**Polti classification:** Enigma — a puzzle, question, or mystery that demands an answer.

**Tone arc:** puzzled/uneasy → clarity

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

The NPC has noticed something that doesn't fit. Not a catastrophe — a nagging inconsistency, an unanswered question, a pattern that doesn't make sense. They are curious, possibly unsettled, but not in danger. The quest is built on the NPC's need to *understand*, not their need to survive.

The key distinction: the mystery must resolve within the quest's own phases. This is not a setup for a larger conspiracy. The answer, when it comes, should be mundane, bittersweet, or quietly surprising — never world-shaking. A neighbor has been acting strange because they're planning a surprise. Supplies keep disappearing because animals found a gap in the wall. Someone left town because of a personal grudge, not a dark secret.

Enigma quests can use any objective type because "finding answers" takes many forms: TALK_TO_NPC to ask around, FETCH_ITEM to retrieve evidence, KILL_MOBS to clear a threat that was the answer all along, COLLECT_RESOURCES to test a theory.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC presents the question. They should sound like someone who's been chewing on this — not reporting a crisis, but sharing a thought that's been bothering them. The tone is conversational curiosity with an edge of unease. The NPC has noticed something and can't let it go.

Must establish: what the NPC has noticed, why it doesn't sit right with them, and implicitly that they want someone else to look into it. Avoid framing the mystery as sinister or ominous — it's a question, not a warning.

### `acceptText`

Appreciation and validation. The NPC has been carrying this question alone and is relieved someone else finds it worth investigating. Not desperate relief — more like "I'm glad I'm not the only one who thinks this is odd." The NPC feels heard.

### `declineText`

Low stakes. The NPC is curious, not endangered. Being told "no" is a mild disappointment, not a crisis. The NPC might express that they'll keep thinking about it on their own. Shrug energy. No guilt, no hostility — just "well, suit yourself."

### `expositionTurnInText`

The first piece of the puzzle lands. The NPC reacts to new information — processing, recalibrating, possibly surprised. This is the moment the question gets sharper. The vague "something's off" becomes "oh, so THAT'S what's going on" or "wait, that doesn't explain the other thing." The NPC's curiosity intensifies or redirects.

### `conflict phases` (conflict1 through conflict4)

The NPC has a theory or a next step. The question has narrowed from "something's off" to a specific thread worth pulling. The ask is focused: talk to a specific person, find a specific thing, check a specific area. The NPC is collaborating now — thinking out loud with the player as their legs.

In longer chains, each conflict should refine the question further. Early conflicts narrow the scope ("it's not what I thought — it's actually about this"). Later conflicts close in on the answer. The NPC's curiosity intensifies with each phase. Avoid introducing completely new mysteries in later conflicts — each phase should feel like the same question getting sharper, not a new question appearing.

### `conflictTurnInText` (any conflict turn-in)

Pieces clicking together. The NPC is processing the answer or near-answer. Their reaction depends on what the answer turned out to be. Mundane answer: wry self-awareness about overthinking it. Bittersweet answer: quiet understanding. Surprising answer: genuine fascination. If another conflict follows, the NPC realizes the current answer raised a better, more specific question.

### `resolutionText`

Clarity. The NPC's question has been answered. The emotional landing depends on the answer's nature, but the NPC should feel *settled* — the itch has been scratched, the nagging thought has been put to rest. Even if the answer is disappointing or sad, knowing is better than wondering. The NPC should sound like someone who can finally stop thinking about this. The reward should feel like genuine appreciation for intellectual partnership, not payment for services.

### `skillCheck.passText`

The NPC shares a hunch they've been holding back — a suspicion too specific or too strange to voice without knowing if the other person would take it seriously. The passed check means the player seems sharp enough to hear it. The hunch should add texture to the mystery without giving away the answer. Best fit skills: INVESTIGATION (player asks the right probing question), INSIGHT (NPC trusts the player's judgment enough to speculate openly), PERCEPTION (player notices a detail that confirms the NPC's unspoken theory), NATURE (if the mystery involves animal behavior or environmental patterns the NPC has observed but can't explain), HISTORY (player recognizes the pattern from something that happened before in the region).

### `skillCheck.failText`

The hunch stays private. The NPC gives the surface-level version of their concern. The deflection should feel like the NPC self-editing — they were about to say something more specific and decided against it.

---

## Anti-Patterns Specific to Enigma

- **The mystery is actually dangerous.** If the answer involves a genuine threat to the settlement, this is Disaster or Pursuit, not Enigma. Enigma's stakes are curiosity and peace of mind, not survival.
- **The mystery doesn't resolve.** The answer must come within the quest's phases. "I guess we'll never know" is not a valid resolution for Enigma. The NPC wanted clarity and must get it.
- **The answer is a conspiracy or cosmic truth.** The answer should be scaled to village life. A neighbor's odd behavior, a mundane explanation for a strange pattern, a personal reason someone won't admit to. Not a dark cult, not an ancient prophecy, not a hidden evil.
- **The exposition is ominous.** Enigma is curious, not foreboding. "Something strange has been happening at the mine" reads as a horror hook. "I keep noticing {settlement_npc} going out past the gate at odd hours and I can't figure out why" reads as Enigma.
- **The decline text carries weight.** Enigma declines should be the lightest in the catalog. The NPC is not in need. They're curious. If the decline text makes the player feel guilty, the situation has been misframed as something more urgent.
