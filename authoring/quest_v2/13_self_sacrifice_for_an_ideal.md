# Situation 13: Self-Sacrifice for an Ideal

**Polti classification:** Self-Sacrifice for an Ideal — a person has committed to something bigger than themselves and needs help seeing it through.

**Tone arc:** weary but resolute → fulfilled

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

The NPC believes in something — a project, a principle, a duty to the community — and has given what they can. It's not enough. They need help not because they're weak, but because the cause is bigger than one person. The NPC's commitment should feel admirable, not foolish.

The key distinction from Supplication (personal need, desperation) and Obtaining (practical need, low stakes): Self-Sacrifice is about purpose. The NPC isn't asking because they're overwhelmed — they're asking because the cause matters and they refuse to let it fail. The emotional register is quiet determination, not desperation.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC explains what they've been working toward and what it's cost them. The tone is honest and weary but not defeated. They should sound like someone who has already given more than was expected and would give more if they could. The cause should feel worth the sacrifice — the player should understand why the NPC cares.

### `acceptText`

Deep appreciation. Not the panicked relief of Supplication — more like the gratitude of someone who has been carrying something alone and finally has a partner. The NPC sees the player as joining the cause, not just doing a job.

### `declineText`

Disappointment without bitterness. The NPC is too committed to their ideal to waste energy on resentment. They might express that they'll continue alone, that the cause doesn't stop because one person said no. This should feel like quiet resolve, not martyrdom.

### `expositionTurnInText`

Progress toward the ideal. The NPC's weariness lifts slightly — what they've been working toward is moving forward again. If more conflicts follow, the NPC is energized by progress and clearer about what remains.

### `conflict phases` (conflict1 through conflict4)

Each phase advances the cause. The NPC's emotional state shifts from weary determination to engaged purpose. They become more animated and specific as the ideal gets closer to reality. Longer chains suit this situation — sustained effort toward a meaningful goal creates a satisfying arc.

### `conflictTurnInText` (any conflict turn-in)

The NPC sees the cause advancing. Their weariness gives way to something like hope. Later turn-ins should show the NPC reconnecting with why they started — the fatigue of the journey replaced by the proximity of the destination.

### `resolutionText`

Fulfilled. The ideal has been served. The NPC should sound like someone who can rest — not because they're giving up, but because the work is done. The reward should feel like the NPC sharing what they have because the cause was always more important than personal wealth.

### `skillcheckPassText`

The NPC reveals what the ideal has cost them personally — relationships, health, comfort, other opportunities. They don't regret it, but the cost is real. Or they reveal why this specific cause matters to them so deeply — a personal connection that elevates it beyond abstract principle. Best fit skills: INSIGHT (personal cost of the commitment), PERSUASION (NPC trusts enough to admit doubt — not about the cause, but about their own ability to see it through), PERCEPTION (player notices signs of the toll on the NPC), HISTORY (the ideal connects to something from the NPC's past that gives it personal weight), INVESTIGATION (player asks what happens if the ideal fails and the NPC reveals stakes they downplayed).

### `skillcheckFailText`

The NPC stays focused on the cause. The personal cost remains private.

---

## Anti-Patterns Specific to Self-Sacrifice for an Ideal

- **The ideal is vague.** "Making the world better" is not a cause. The NPC should have a specific, concrete goal: building something, protecting something, preserving something.
- **The NPC is a martyr.** Self-sacrifice is about commitment, not suffering. The NPC is tired, not performing their suffering for an audience.
- **The resolution is bittersweet for no reason.** The tone arc destination is "fulfilled," not "tragic." If the cause succeeded, let the NPC feel it.
- **The cause is obviously foolish.** The player should understand why the NPC cares, even if they wouldn't make the same choice. An ideal that reads as delusional makes the NPC unsympathetic.
