# Situation 03: Recovery

**Polti classification:** Recovery — seizing or reclaiming something that was taken or lost.

**Tone arc:** wronged/frustrated → vindicated

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

Something that belongs to the NPC or their community is gone — stolen, lost, destroyed, or scattered. The quest is about getting it back or replacing what was lost. The NPC's emotional state is frustration mixed with a sense of injustice. They had something, it was taken, and the world feels a little less right because of it.

The key distinction from Obtaining: Obtaining is about acquiring something new. Recovery is about restoring something that was already theirs. The emotional register is resentment and determination, not aspiration. The NPC doesn't want something better — they want what they had.

Recovery can range in scale from petty ("someone took my tools") to serious ("our winter stores are gone"). Both are valid. The scale determines the tone — petty recovery is grumbling and indignant, serious recovery carries real stakes.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC explains what was lost and why it matters. The emotional weight should match the loss — a sentimental heirloom and a season's worth of supplies both justify Recovery quests but hit differently. The NPC should sound like someone who has been wronged, not someone filing a report. The loss is personal.

### `acceptText`

Determination validated. The NPC feels less alone in their frustration. The tone is "finally, someone who gets it" — they've been stewing and now there's a path forward.

### `declineText`

Frustration directed inward, not at the player. The NPC was already frustrated and being told no adds to the pile. They might express resignation or mutter about handling it themselves. Light guilt-tripping is natural but the dominant emotion is exasperation with the situation, not anger at the player.

### `expositionTurnInText`

The first step toward recovery is complete. The NPC's frustration begins to ease into cautious optimism. If more conflicts follow, the NPC might learn that recovering what was lost is more complicated than expected — there's more missing than they thought, or getting it back requires dealing with an additional problem.

### `conflict phases` (conflict1 through conflict4)

Each phase moves closer to full restoration. The NPC's emotional state shifts from "wronged" toward "made whole." In longer chains, complications should relate to the recovery itself — the stolen goods were moved, the lost item led to a trail, replacing what was destroyed requires materials from multiple sources. The NPC becomes more invested and specific with each phase.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each piece of recovery with growing satisfaction. Not celebration — more like someone ticking items off a list and feeling the world return to order. If the recovery is complete, vindication is quiet and solid.

### `resolutionText`

Vindication. The NPC has what was theirs again, or has rebuilt what was lost. The world is back in balance. The tone is satisfaction and restored dignity, not triumph. The NPC should sound like someone who can finally move on. The reward is offered with the sense of "you helped me set things right."

### `skillCheck.passText`

The NPC reveals why the lost thing matters more than its practical value — a personal connection, a memory, a principle. Or the NPC admits the loss exposed a vulnerability they don't want others to know about. Best fit skills: INSIGHT (the emotional weight behind the material loss), INVESTIGATION (player pieces together how the loss happened and it's more embarrassing or concerning than the NPC let on), PERSUASION (NPC admits the loss has shaken their confidence), PERCEPTION (player notices the NPC has been compensating for the loss in visible ways), HISTORY (the lost item has history the NPC didn't mention).

### `skillCheck.failText`

The NPC keeps it practical. The loss is stated as a material problem, not a personal one.

---

## Anti-Patterns Specific to Recovery

- **The lost thing doesn't matter.** If the NPC wouldn't genuinely miss what was taken, this isn't Recovery — it's a fetch quest with no emotional stake. The loss must register.
- **The NPC is detached.** Recovery requires personal investment. If the NPC sounds like they're reporting a logistics problem, the situation is wrong.
- **The resolution doesn't restore.** The NPC must get something back — the original item, a replacement, or the knowledge of what happened. A resolution that says "well, it's gone forever, but thanks for trying" violates the arc.
- **The grievance escalates into vengeance.** If the NPC's frustration tips into wanting to punish whoever took their things, the situation is sliding into Vengeance. Recovery is about getting whole, not getting even.
