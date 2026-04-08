# Situation 16: Loss of Loved Ones

**Polti classification:** Loss of Loved Ones — grief, remembrance, and the search for closure.

**Tone arc:** mourning → bittersweet peace

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

Someone is gone and the NPC is dealing with it. The loss has already happened — the quest is about what comes after. Honoring, remembering, finding peace, tying up loose ends. The NPC is not asking for the loss to be undone. They're asking for help with the process of moving forward.

The key distinction from Necessity of Sacrificing Loved Ones (active painful choice): Loss is past tense. The NPC didn't choose this. They're living with it.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC shares their loss and what they need to find peace. They should sound like someone at a specific stage of grief — not raw shock (too recent) but not fully healed (no quest needed). The middle ground: they know what they need to do to take the next step and they need help doing it.

### `acceptText`

Quiet gratitude. The NPC is not asking for much emotionally — they're asking for practical help with an emotional process. The appreciation is real but understated.

### `declineText`

Gentle disappointment. The NPC's grief does not become the player's burden. They will continue to process on their own. The decline should feel like a door quietly closing, not a guilt trip.

### `expositionTurnInText`

The first step toward closure. The NPC processes the result with visible emotion but not breakdown. If more conflicts follow, each step uncovers another thread that needs tying — a message undelivered, a belonging unreturned, a truth unspoken.

### `conflict phases` (conflict1 through conflict4)

Each phase is a step in the grieving process made tangible. FETCH_ITEM can be a keepsake, a memento, something that belonged to the person. TALK_TO_NPC can be "tell {target_npc} what happened" or "my mother's friend should hear this from a person, not a letter." KILL_MOBS might address a threat connected to the loss — clearing the area where it happened, dealing with what caused it. Each phase should feel like the NPC releasing one more piece of the weight.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each piece of closure. The mourning doesn't disappear — it transforms. Each turn-in should show the NPC slightly more at peace, slightly more present, slightly more able to look forward instead of back.

### `resolutionText`

Bittersweet peace. The NPC has taken a step forward. They are not over the loss — they never will be — but they have done what they needed to do. The tone is quiet and grateful. The NPC should sound like someone who can sleep tonight. The reward should feel like a deeply personal thank-you for helping with something no amount of money could buy.

### `skillCheck.passText`

The NPC shares a memory of the person they lost — something specific, intimate, and revealing of the relationship. Or they admit a regret they've been carrying. Best fit skills: INSIGHT (the specific nature of the NPC's grief — what they miss most), PERSUASION (NPC trusts enough to share the memory they've been holding privately), PERCEPTION (player notices something the NPC is carrying — a token, a habit, a mark of the loss), HISTORY (the NPC reveals the relationship's history and what made it special), INVESTIGATION (player asks the right question and the NPC reveals the loss was more complicated than stated — guilt, unfinished business, words unsaid).

### `skillCheck.failText`

The NPC keeps the intimate details private. The grief is visible but the memories stay guarded.

---

## Anti-Patterns Specific to Loss of Loved Ones

- **The loss is recent enough for shock.** If the NPC is in acute crisis, this is Supplication or Madness. Loss of Loved Ones assumes enough time has passed for the NPC to know what they need.
- **The quest tries to undo the loss.** No resurrection, no "it turns out they're alive." The loss is permanent. The quest is about living with it.
- **The resolution is full closure.** Grief doesn't close. The NPC takes a step forward, not a final step. "Bittersweet peace" means the sorrow remains but it's joined by something gentler.
- **The NPC's grief is performative.** Real grief is quiet as often as it's loud. The NPC should feel like someone carrying weight, not someone performing for an audience.
