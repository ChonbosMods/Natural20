# Situation 20: Remorse

**Polti classification:** Remorse — the NPC wronged someone and wants to make it right.

**Tone arc:** guilty → lightened

**Available objectives:** TALK_TO_NPC, FETCH_ITEM

---

## Emotional Frame

The NPC did something wrong and it's eating at them. They want to make amends but can't bring themselves to face the person directly, or they need something to make the gesture meaningful. The quest is the NPC's attempt at redemption through action.

The key distinction from Involuntary Crimes of Love (accidental harm, confused about what went wrong): in Remorse, the NPC knows exactly what they did. They're not confused — they're ashamed. The guilt is specific and acknowledged.

The NPC's remorse should feel genuine, not performative. They're not asking the player to witness their guilt — they're asking for practical help bridging a gap they created.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC admits what they did. This costs them. They should sound like someone forcing themselves to say something they've been avoiding. The admission should be specific — not "I made a mistake" but what the mistake was, who it hurt, and why the NPC can't fix it alone. The NPC should not make excuses.

### `acceptText`

Relief mixed with lingering shame. Someone is willing to help them do the right thing. The NPC is grateful but not absolved — the help doesn't erase what they did, it just makes the amends possible.

### `declineText`

Deflated acceptance. The NPC expected rejection — they know what they did doesn't entitle them to help. The decline reinforces their guilt rather than triggering anger. They might say they understand, and mean it.

### `expositionTurnInText`

The first step toward amends is complete. The NPC processes it with cautious hope — maybe this can be repaired. If more conflicts follow, the amends require more than one gesture, or the first step revealed that the harm was deeper than the NPC realized.

### `conflict phases` (conflict1 through conflict4)

Each phase moves the NPC closer to making things right. TALK_TO_NPC phases are apologies, explanations, or reconnections. FETCH_ITEM phases retrieve something needed for the gesture — something that belonged to the wronged party, something the NPC can offer as proof of sincerity. Each phase should feel like the NPC earning back ground they lost. Longer chains can explore the complexity of genuine amends — it's rarely as simple as saying sorry.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each step with gradually lightening guilt. They're not absolved yet, but the weight is shifting. Later turn-ins should show the NPC regaining a sense of agency — they're no longer just someone who did wrong, they're someone who is doing right.

### `resolutionText`

Lightened. Not absolved — the NPC doesn't get to decide if they're forgiven. But they've done what they could, and the act of making amends has lifted something. The NPC should sound like someone who can look the wronged person in the eye again, even if the relationship is permanently changed. The reward should feel like the NPC paying forward — giving because they've been reminded of what generosity means.

### `skillcheckPassText`

The NPC reveals the full weight of what they did — the part they didn't mention, the consequence they're most ashamed of, or the moment they realized they'd crossed a line. Best fit skills: INSIGHT (the NPC reveals deeper shame than shown), PERSUASION (NPC admits the worst part of what they did), PERCEPTION (player notices signs of sleeplessness, distraction, or self-punishment), INVESTIGATION (player asks about the timeline and the NPC reveals they've been carrying this longer than they let on), HISTORY (the NPC reveals a pattern — this isn't the first time they've hurt someone this way, and that's what scares them).

### `skillcheckFailText`

The NPC gives the surface version. The deeper shame stays hidden.

---

## Anti-Patterns Specific to Remorse

- **The NPC makes excuses.** Remorse requires accountability. If the NPC spends exposition justifying their actions, the guilt isn't real.
- **The NPC is forgiven immediately.** Forgiveness is not owed and should not be guaranteed. The resolution is about the NPC's effort, not the wronged party's response.
- **The wrong is trivial.** If the NPC feels this level of guilt over something minor, they seem fragile rather than remorseful. The wrong should be real enough to warrant the quest's emotional weight.
- **The quest punishes the NPC.** The arc is guilt → lightened, not guilt → more guilt. The NPC's effort should yield emotional progress, even if full forgiveness doesn't come.
