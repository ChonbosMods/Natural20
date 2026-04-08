# Situation 14: Self-Sacrifice for Kindred

**Polti classification:** Self-Sacrifice for Kindred — doing something difficult for family, driven by love and obligation.

**Tone arc:** devoted/anxious → relieved

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

The NPC is stretched thin trying to provide for, protect, or care for someone in their family. They can't do what's needed and also be where they need to be. The quest exists because family obligation and practical reality are pulling the NPC in two directions.

The key distinction from Self-Sacrifice for an Ideal (commitment to a principle) and Obtaining (practical need): the emotional center is the relationship. "I need iron" is Obtaining. "I need iron because my kid is sick and I can't leave them long enough to get it myself" is Self-Sacrifice for Kindred. The task might be identical but the reason transforms it.

Use `{settlement_npc}` to ground the family member when possible. The person the NPC is sacrificing for should feel present in the text, not abstract.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC explains the bind they're in. They should sound torn — not between wanting to help and not wanting to, but between two obligations they can't fulfill simultaneously. The family member's need should be concrete and urgent enough to justify the NPC's stress but scaled to village life.

### `acceptText`

Relieved and grateful in a way that's specifically parental or familial. The NPC isn't just glad for help — they're glad they can stay where they're needed. There's a sense of "now I don't have to choose."

### `declineText`

Guilt-inducing but not hostile. The NPC will manage — they always do — but the cost is visible. They might mention what they'll have to sacrifice to handle it alone (leaving the family member, going without sleep, taking a risk they shouldn't). The guilt should come from the situation, not from the NPC directing it at the player.

### `expositionTurnInText`

A weight has been partially lifted. The NPC can breathe. If more conflicts follow, the family situation has revealed additional needs, or addressing the first need uncovered another the NPC was too stressed to notice.

### `conflict phases` (conflict1 through conflict4)

Each phase addresses another layer of the family's needs. The NPC's devotion is the constant — their emotional state shifts from anxious to cautiously hopeful as each need is met. The family member's wellbeing should improve with each phase in a way the NPC notices and comments on.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each step with visible relief tied to the family member. Their comments should reference the person they're caring for — how this helps them, what it means for them. The NPC's own stress is secondary to the family member's improvement.

### `resolutionText`

Relieved. The NPC's family member has what they need and the NPC can stop being stretched in two directions. The gratitude should feel personal and intimate — this wasn't a favor, it was a kindness that touched someone's family. The reward should feel like what the NPC can spare after putting family first.

### `skillCheck.passText`

The NPC reveals how close to breaking they were — how long they've been running on devotion alone, how much they've been hiding from the family member to keep them from worrying. Or they admit a fear about the family member's future they haven't voiced. Best fit skills: INSIGHT (emotional exhaustion the NPC is hiding), PERSUASION (NPC trusts enough to drop the "I'm managing fine" front), PERCEPTION (player notices physical signs of the NPC's exhaustion or stress), INVESTIGATION (player probes the timeline and realizes this has been going on much longer than implied), NATURE (if the family member's need involves health or environmental factors the NPC doesn't fully understand).

### `skillCheck.failText`

The NPC stays strong. The "I'm managing" performance continues.

---

## Anti-Patterns Specific to Self-Sacrifice for Kindred

- **The family member is abstract.** "My family needs help" is too vague. Ground the person with `{settlement_npc}` or specific detail (my daughter, my aging father, my partner).
- **The NPC is resentful.** Self-Sacrifice for Kindred is driven by love, not obligation performed bitterly. The NPC might be exhausted but they don't resent the person they're sacrificing for.
- **The task overshadows the relationship.** The emotional weight should stay on the family bond, not on the practical objective. If the author could swap the family motivation for any other motivation and the quest reads the same, the situation isn't doing its job.
- **The family member's need is trivial.** The NPC's sacrifice should feel proportionate. If they're this stressed over something minor, they seem fragile rather than devoted.
