# Situation 19: Erroneous Judgment

**Polti classification:** Erroneous Judgment — someone has been wrongly blamed and the truth needs to come out.

**Tone arc:** indignant/concerned → justice restored

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

An injustice has occurred: someone is being blamed for something they didn't do. The NPC either believes the accused is innocent, or IS the wrongly accused party. The quest is about establishing the truth and clearing a name.

The key distinction from Mistaken Jealousy (suspicion that deflates comically): Erroneous Judgment has real consequences for the accused. Their reputation, standing, or relationships are damaged. The tone is more serious — this is about fairness, not embarrassment.

The key distinction from Enigma (curiosity-driven investigation): the emotional center is injustice, not curiosity. The NPC cares about setting things right, not satisfying their own questions.

The restricted objective set (FETCH_ITEM, TALK_TO_NPC only) reflects the social and evidentiary nature. Clearing someone's name requires proof and testimony, not combat.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC lays out the accusation and why they believe it's wrong. They should sound indignant on behalf of the accused (or defensively if they are the accused). The injustice should be specific and concrete — what the person is accused of, what the evidence supposedly is, and what the NPC knows or suspects that contradicts it.

### `acceptText`

Grateful and energized. Someone is willing to look at the evidence fairly. The NPC feels the tide turning — truth has an ally now.

### `declineText`

Frustrated and disappointed. The injustice will persist. The NPC might express that without someone willing to dig, the wrongly accused person has no chance. Not hostile — more like the frustration of watching something unfair continue.

### `expositionTurnInText`

The first piece of exonerating evidence or testimony lands. The NPC processes it with a mix of relief and vindication. If more conflicts follow, the truth is more layered than expected — clearing the accused requires establishing what actually happened, not just proving they didn't do it.

### `conflict phases` (conflict1 through conflict4)

Each phase builds the case for innocence. TALK_TO_NPC phases gather testimony or confront the real responsible party. FETCH_ITEM phases retrieve evidence. The NPC's confidence in the outcome grows with each phase. Longer chains can develop the complexity of the accusation — multiple pieces of evidence, multiple witnesses, a truth that emerges piece by piece.

### `conflictTurnInText` (any conflict turn-in)

The NPC reacts to each new piece of evidence with growing vindication. The picture clears with each phase. Later turn-ins should show the NPC preparing for the emotional payoff — the moment when the truth is established and the accused is cleared.

### `resolutionText`

Justice restored. The truth is out. The accused is cleared. The NPC should sound satisfied in a clean, righteous way — this is one of the few situations where the resolution can feel simply good. The reward should reflect genuine gratitude for standing up for what's right.

### `skillcheckPassText`

The NPC reveals additional context that strengthens the case — something they were reluctant to share because it's personal, embarrassing, or implicates someone else. Or they reveal their personal connection to the accused that makes this about more than abstract fairness. Best fit skills: INVESTIGATION (player identifies a logical gap in the accusation), INSIGHT (NPC reveals personal stakes in clearing the accused), PERSUASION (NPC shares information they were holding back out of caution), PERCEPTION (player notices something about the NPC's demeanor that reveals the depth of their investment), HISTORY (player recognizes this kind of false accusation has happened before in the community).

### `skillcheckFailText`

The NPC makes the case with available facts only. The personal dimension and the withheld details stay private.

---

## Anti-Patterns Specific to Erroneous Judgment

- **The accused turns out to be guilty.** The accusation MUST be wrong. If the accused is guilty, this is a different situation entirely.
- **The real culprit is dramatic.** The truth should be proportionate to village life. Someone else made a mistake, something was misunderstood, evidence was misread. Not a conspiracy.
- **Nobody cares about the accusation.** The wrongful blame must have real social consequences — otherwise there's nothing at stake and the quest has no emotional weight.
- **The NPC is investigating for fun.** That's Enigma. Erroneous Judgment is driven by a sense of injustice, not curiosity.
