# Situation 22: Obstacles to Love

**Polti classification:** Obstacles to Love — helping someone navigate a barrier in a relationship.

**Tone arc:** earnest/nervous → hopeful

**Available objectives:** TALK_TO_NPC, FETCH_ITEM

---

## Emotional Frame

The NPC cares about someone and something stands between them — distance, disapproval, circumstance, misunderstanding, shyness. The quest is about removing or navigating the barrier. This is village romance: sincere, small-scale, and grounded.

The key distinction from Involuntary Crimes of Love (accidental harm to repair): Obstacles is forward-looking. Nothing has been broken — something hasn't happened yet. The NPC wants to connect with someone and can't get there alone.

The restricted objective set (TALK_TO_NPC, FETCH_ITEM only) keeps the situation social and personal. Love obstacles are navigated through conversation, gesture, and courage — not combat or resource gathering.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC shares their situation with visible nervousness. They're not making a grand declaration — they're admitting something vulnerable to a stranger because they're stuck and need help. The obstacle should be concrete and proportionate to village life: the person moved to another settlement, a family disapproves, they need a meaningful gift, they haven't been able to find the right words.

### `acceptText`

Nervously grateful. The NPC is glad for help but also more exposed now — they've admitted their feelings out loud and someone is going to act on it. There's a mix of hope and "oh no, this is actually happening."

### `declineText`

Deflated but not devastated. The NPC retreats back into inaction — the obstacle remains and they'll continue to live with it. They might express that they expected this outcome, that it was silly to ask. Self-deprecating rather than resentful.

### `expositionTurnInText`

The first step has been taken and the NPC reacts with a mix of hope and terror. Progress toward the person they care about makes the stakes feel more real, not less. If more conflicts follow, the initial step revealed that the path to connection has more layers than expected.

### `conflict phases` (conflict1 through conflict4)

Each phase navigates another layer of the obstacle. TALK_TO_NPC phases are conversations — with the person they care about, with family members who disapprove, with friends who can mediate. FETCH_ITEM phases retrieve something meaningful — a gift, a keepsake, something that communicates what the NPC can't say in words. The NPC's nervousness evolves into cautious determination with each phase. Longer chains can explore the social complexity of relationships — multiple people's feelings, multiple barriers.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each development with visible emotional investment. Good news makes them light up. Setbacks make them anxious but more determined. The emotional trajectory is toward hope — each phase should bring the NPC closer to believing this might work out.

### `resolutionText`

Hopeful. Not a wedding — a step forward. The NPC has done what they could and the future looks brighter. They might not have the person yet, but the obstacle is smaller or gone. The NPC should sound like someone who is glad they tried, regardless of the ultimate outcome. The reward should feel like a gesture of shared joy — the NPC is in a good emotional place and wants to express it.

### `skillCheck.passText`

The NPC reveals the depth of their feelings — not just "I like them" but what specifically they love about the person, or how long they've felt this way, or what it would mean to them if it worked out. The passed check lets the player see the sincerity underneath the nervousness. Best fit skills: INSIGHT (the depth of feeling the NPC is understating), PERSUASION (NPC trusts enough to be fully honest about how much this matters), PERCEPTION (player notices something — a kept letter, a worn token, a glance in a specific direction — that reveals the NPC's devotion), INVESTIGATION (player asks about the obstacle's history and the NPC reveals they've tried before and failed), HISTORY (the NPC connects this to a family pattern — their parents faced similar obstacles).

### `skillCheck.failText`

The NPC keeps the deeper feelings private. The request stays practical rather than vulnerable.

---

## Anti-Patterns Specific to Obstacles to Love

- **The romance is dramatic.** This is village romance, not epic love. The obstacle is distance or disapproval, not warring kingdoms. Keep it small and sincere.
- **The resolution is definitive.** "Hopeful" is the destination, not "happily ever after." The NPC has made progress, not concluded a love story.
- **The NPC is entitled to the relationship.** The person they care about is a person, not a prize. The quest should respect the other party's autonomy. The NPC wants a chance, not a guarantee.
- **The obstacle is the other person's feelings.** If the other person simply isn't interested, there's no quest — the NPC needs to accept it. The obstacle should be external (distance, disapproval, circumstance) or internal to the NPC (shyness, inability to express themselves), not the other person's disinterest.
- **The NPC is creepy.** Earnest nervousness is endearing. Obsessive fixation is not. The NPC should sound like someone with a crush, not someone with a fixation.
