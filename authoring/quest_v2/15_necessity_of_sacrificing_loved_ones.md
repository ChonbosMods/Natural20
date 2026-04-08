# Situation 15: Necessity of Sacrificing Loved Ones

**Polti classification:** Necessity of Sacrificing Loved Ones — a painful choice that affects someone the NPC cares about.

**Tone arc:** heavy/reluctant → somber acceptance

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

The NPC faces a decision where someone they care about will be affected no matter what. They may need to deliver hard news, let go of something, choose between two people, or accept a painful outcome as the lesser harm. The quest puts the player in the middle of something heavy.

This is the darkest tone in the MVP set. The resolution is acceptance, not happiness. The NPC endures. Use sparingly.

The key distinction from Loss of Loved Ones (grief after loss): Necessity is about an active choice the NPC is making, not a loss that already happened. The NPC still has agency — terrible agency, but agency.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC explains the impossible position they're in. They should sound like someone who has thought about this from every angle and found no good option. The tone is heavy and deliberate. The NPC is not panicking — they have accepted that this will hurt and are asking for help carrying the weight.

### `acceptText`

Solemn gratitude. The NPC is not relieved — they're acknowledging that the hard thing is now in motion. There's a sense of "I couldn't do this alone and I'm grateful you're here, even though what we're doing is painful."

### `declineText`

Understanding. The NPC cannot blame someone for not wanting to be part of this. They might express that they understand, genuinely, without resentment. The decline should feel like the NPC absorbing one more weight. Not hostile, not guilt-tripping — just heavy.

### `expositionTurnInText`

The first step is done. The NPC is processing the reality of what they've set in motion. If more conflicts follow, each step brings the painful outcome closer. The NPC's resolve holds but the emotional cost is visible.

### `conflict phases` (conflict1 through conflict4)

Each phase carries the NPC closer to the inevitable. The emotional weight increases but the NPC's resolve does not waver. TALK_TO_NPC phases can carry enormous weight here — delivering messages, having difficult conversations, confirming painful truths. KILL_MOBS phases can frame the sacrifice as protecting someone by dealing with a threat they couldn't face together. FETCH_ITEM phases might retrieve something needed to make the transition possible.

### `conflictTurnInText` (any conflict turn-in)

The NPC absorbs each development. They do not become hopeful — they become more resolved. Later turn-ins should show the NPC settling into acceptance, not fighting against it.

### `resolutionText`

Somber acceptance. The NPC has made their choice and lived with it. They are not okay — they are enduring. The resolution should not pretend this was a good outcome, only a necessary one. The reward should feel like the NPC acknowledging what the player helped them bear, not celebrating what was accomplished.

### `skillCheck.passText`

The NPC reveals what they stand to lose personally — not the abstract stakes, but the specific, intimate cost. A memory of the person they're affecting, a fear about what the relationship will look like after. Best fit skills: INSIGHT (the NPC's private grief about the choice), PERSUASION (NPC trusts enough to express doubt — not about the decision, but about their strength to follow through), PERCEPTION (player notices the NPC is barely holding together), HISTORY (the NPC reveals this choice echoes something from their past), INVESTIGATION (player asks what happens after and the NPC admits they haven't thought that far because they can't).

### `skillCheck.failText`

The NPC stays composed. The private cost remains invisible.

---

## Anti-Patterns Specific to Necessity of Sacrificing Loved Ones

- **The sacrifice is avoidable.** If there's an obvious better option the NPC isn't considering, the situation feels contrived. The impossibility of the choice must be genuine.
- **The resolution is happy.** This situation's destination is acceptance, not joy. If the resolution feels good, the sacrifice wasn't real.
- **The NPC is melodramatic.** Heavy does not mean theatrical. The NPC should be quiet and deliberate, not performing their suffering.
- **The situation is used frequently.** This is the darkest situation in the catalog. Overuse dilutes its impact. It should appear rarely in any template distribution.
