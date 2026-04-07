# Situation 21: Involuntary Crimes of Love

**Polti classification:** Involuntary Crimes of Love — accidental harm caused by good intentions or misunderstanding.

**Tone arc:** regretful/confused → resolved

**Available objectives:** TALK_TO_NPC, FETCH_ITEM

---

## Emotional Frame

The NPC did something that hurt someone they care about, but they didn't mean to — and they may not fully understand what went wrong. The damage is real but the intent was absent or misguided. The NPC is confused as much as guilty. They need help figuring out what happened as much as fixing it.

The key distinction from Remorse (NPC knows what they did wrong): here, the NPC is at least partly in the dark. They know something broke but they're not sure which part was their fault, or they thought they were doing the right thing and it backfired. The emotional register is bewilderment and regret rather than clear-eyed guilt.

The restricted objective set (TALK_TO_NPC, FETCH_ITEM only) reflects the social and investigative nature. Understanding and repairing accidental harm requires conversation and gesture, not force.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC describes the situation with visible confusion. They know something went wrong in a relationship but they're not sure what. They should sound like someone replaying events in their head and not finding the moment where it broke. The harm should be specific — not "they're upset with me" but a concrete consequence of something the NPC did or said without realizing the impact.

### `acceptText`

Grateful and relieved to have someone willing to help untangle this. The NPC may frame it as needing an outside perspective — "maybe you can see what I'm missing."

### `declineText`

Disappointed but not dramatic. The NPC is stuck in confusion and hoped for clarity. Without help, they'll keep replaying events and getting nowhere. The decline should feel like someone's internal loop continuing without resolution.

### `expositionTurnInText`

New information arrives and the NPC begins to understand. This is the moment of realization — "oh, THAT'S why they're upset." The NPC may feel foolish, or may realize the harm was worse than they thought. If more conflicts follow, understanding the cause is only the first step — now they need to address it.

### `conflict phases` (conflict1 through conflict4)

Each phase moves from understanding to repair. Early conflicts are about learning what went wrong. Later conflicts are about making it right. TALK_TO_NPC phases are conversations that clarify or mend. FETCH_ITEM phases retrieve something meaningful that aids reconciliation. The NPC's confusion gradually gives way to understanding and purposeful action.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes growing clarity. Early turn-ins show the pieces falling into place. Later turn-ins show the NPC transitioning from "I understand now" to "I know what I need to do." The confusion lifts and is replaced by quiet determination to repair the damage.

### `resolutionText`

Resolved. The NPC understands what happened and has taken steps to address it. The relationship may not be fully repaired, but the confusion is gone and the path forward is clear. The NPC should sound like someone who has learned something about themselves — not just the specific mistake, but something about how they affect others. The reward should feel earnest and personal.

### `skillcheckPassText`

The NPC reveals what they were actually trying to do — the good intention behind the harm. Understanding the intent adds complexity: the NPC wasn't careless, they were trying to help and got it wrong. Or they reveal a fear about the relationship that made them act the way they did. Best fit skills: INSIGHT (the good intention behind the bad outcome), PERSUASION (NPC admits they're afraid the relationship is permanently damaged), PERCEPTION (player notices the NPC has been trying to compensate in other ways), INVESTIGATION (player asks about the NPC's reasoning and the misunderstanding becomes clearer), HISTORY (the NPC reveals a previous misunderstanding that made them overcorrect into this one).

### `skillcheckFailText`

The NPC stays confused. The underlying intention and fear remain inarticulate.

---

## Anti-Patterns Specific to Involuntary Crimes of Love

- **The NPC is clearly at fault.** If the NPC knowingly did something hurtful, that's Remorse. Involuntary Crimes requires genuine misunderstanding or unintended consequences.
- **The harm is trivial.** The confusion and quest effort should be proportionate to real emotional damage, not a minor social faux pas.
- **The NPC never gains clarity.** The arc requires movement from confusion to understanding. A resolution where the NPC is still confused fails the situation.
- **The wronged party is absent.** The person who was hurt should be present in the quest — through `{settlement_npc}` or `{target_npc}` — not an abstract offscreen figure.
