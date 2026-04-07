# Situation 18: Mistaken Jealousy

**Polti classification:** Mistaken Jealousy — suspicion that turns out to be unfounded.

**Tone arc:** suspicious/anxious → sheepish/relieved

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

The NPC suspects someone of something — disloyalty, scheming, hiding something, working against the community. They feel justified in their suspicion and want the player to confirm it. The twist: they're wrong. The truth is innocent or mundane.

This is a comedy-of-errors situation, not a thriller. The emotional journey is the NPC's ego deflating as evidence accumulates that they were wrong. The resolution is sheepishness and relief — the NPC is embarrassed but glad the truth is harmless.

The restricted objective set (FETCH_ITEM, TALK_TO_NPC only) reinforces the social nature. Jealousy is investigated through conversation and evidence, not violence.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC presents their suspicion with conviction. They've been watching, noticing, building a case. They should sound sure of themselves — the player's first impression should be that the NPC might be right. The suspicion should be specific enough to investigate: "{settlement_npc} has been acting strange" plus concrete observations.

### `acceptText`

Validated. The NPC feels heard and justified. They're glad someone is taking their concern seriously. There's a note of righteous certainty — they expect to be proven right.

### `declineText`

Mildly offended. The NPC feels dismissed. They might suggest the player isn't taking the situation seriously enough, or mutter that they'll keep watching on their own. Light indignation, not fury.

### `expositionTurnInText`

The first piece of evidence comes back and it's... not what the NPC expected. The suspicion doesn't hold up, or the evidence points somewhere unexpected. The NPC should resist the disconfirmation initially — "that doesn't make sense" or "there must be more to it." The ego hasn't deflated yet.

### `conflict phases` (conflict1 through conflict4)

Each phase further undermines the NPC's theory. TALK_TO_NPC phases reveal innocent explanations. FETCH_ITEM phases retrieve evidence that clears the suspected party. The NPC's certainty erodes with each phase. They may try to reframe the evidence to fit their theory before eventually accepting they were wrong. Longer chains can draw out this comedic deflation — each phase the NPC is a little less sure, a little more uncomfortable.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes disconfirming evidence. Early turn-ins show denial or reinterpretation. Later turn-ins show the dawning realization. The shift from "I'm right about this" to "oh no, I'm wrong about this" should be gradual and humanly recognizable.

### `resolutionText`

Sheepish and relieved. The NPC has accepted they were wrong. The dominant emotion is embarrassment with an undercurrent of genuine relief — the person they suspected is innocent, the threat they imagined doesn't exist, and the world is less sinister than they feared. The NPC might ask the player not to mention this to anyone. The reward should feel like an apology offering as much as a payment.

### `skillcheckPassText`

The NPC reveals what's actually driving the suspicion — insecurity, fear of being left out, a past experience with betrayal that has nothing to do with the current situation. The jealousy has a root, and it's personal, not rational. Best fit skills: INSIGHT (the emotional root of the jealousy), PERSUASION (NPC admits they want to be wrong), PERCEPTION (player notices the NPC is more anxious than angry — this is fear, not conviction), INVESTIGATION (player probes the evidence and the NPC realizes it's thinner than they thought), HISTORY (the NPC reveals a past betrayal that made them hypervigilant).

### `skillcheckFailText`

The NPC stays in accusation mode. The personal root of the suspicion stays buried.

---

## Anti-Patterns Specific to Mistaken Jealousy

- **The NPC turns out to be right.** The suspicion MUST be unfounded. If the suspected party is actually guilty, this is Enigma or Pursuit, not Mistaken Jealousy.
- **The NPC is vindictive.** Jealousy here is anxious, not malicious. The NPC should be worried, not scheming for revenge.
- **The truth is dramatic.** The innocent explanation should be mundane — planning a surprise, helping someone privately, pursuing a harmless hobby. Not a second, more interesting mystery.
- **The NPC doesn't feel embarrassed.** The emotional payoff is the sheepishness. If the NPC takes the disconfirmation in stride without any ego deflation, the arc didn't land.
- **The decline text is heavy.** This is a light situation. The NPC's feelings are real but the stakes are social, not existential.
