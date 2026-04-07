# Situation 11: Rivalry of Kinsmen

**Polti classification:** Rivalry of Kinsmen — tension between people who should be allies: neighbors, partners, old friends, family.

**Tone arc:** exasperated/hurt → resigned or reconciled

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

Two people who should get along don't. The NPC is caught in the middle or is one of the parties. The conflict is personal and petty enough for village life — a property dispute, a broken promise, a difference in values that festered — but it feels enormous to the people involved.

The key distinction from Vengeance (which is about payback for a wrong): Rivalry is ongoing friction, not a grievance seeking resolution through force. Both parties have a point. Neither is fully right. The player navigates the space between them.

The resolution doesn't have to be happy. Sometimes people agree to disagree. Sometimes one side concedes. Sometimes the rivalry just gets managed, not solved. All are valid endings.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC describes the tension. They're frustrated and probably biased — they see their side clearly and the other side less so. The conflict should feel real and specific, not abstract. "They moved the fence line" or "they promised to help with the harvest and didn't show" — concrete, petty, human.

### `acceptText`

Relief at having someone willing to mediate or investigate. The NPC may not frame it as mediation — they probably think they're right and want the player to confirm it.

### `declineText`

Frustration. The NPC is stuck in this conflict and was hoping for an outside perspective or practical help. Being told no leaves them where they were — still stuck, still frustrated. Not hostile, but clearly disappointed.

### `expositionTurnInText`

The first step toward addressing the rivalry. The NPC processes new information — maybe from talking to the other party, maybe from investigating the source of the disagreement. The NPC may resist hearing the other side.

### `conflict phases` (conflict1 through conflict4)

Each phase navigates the social terrain of the dispute. TALK_TO_NPC phases work naturally as mediation steps. FETCH_ITEM or COLLECT_RESOURCES might represent gathering evidence or fulfilling a compromised agreement. KILL_MOBS might address an external threat that both parties need to face together. Longer chains can explore both sides of the dispute, with each phase adding nuance.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each development. They may soften, harden, or reconsider depending on what the player found. The emotional trajectory toward the resolution should be visible — the NPC is slowly moving from entrenched to something more open.

### `resolutionText`

Resigned or reconciled. The NPC has accepted the outcome, whatever it is. If reconciled: cautious warmth, acknowledgment that the other party had a point. If resigned: acceptance that the relationship has changed but life goes on. Neither extreme is triumphant. The reward should feel personal — the NPC appreciating help with something that mattered to them socially, not materially.

### `skillcheckPassText`

The NPC admits their own role in the conflict — something they contributed that they haven't acknowledged. Or they reveal what they're actually afraid of losing (the relationship, not the fence line). Best fit skills: INSIGHT (the NPC's real fear underneath the surface complaint), PERSUASION (NPC trusts enough to admit fault), PERCEPTION (player notices signs the NPC misses the other person), INVESTIGATION (player asks about the history and the NPC reveals the rivalry started over something different than claimed), HISTORY (the dispute echoes a previous one the NPC went through).

### `skillcheckFailText`

The NPC stays entrenched in their version. No self-reflection surfaces.

---

## Anti-Patterns Specific to Rivalry of Kinsmen

- **One side is clearly wrong.** Rivalry requires both parties to have a point. If one side is obviously the villain, this is Vengeance or Erroneous Judgment.
- **The conflict is violent.** Rivalry is social friction, not warfare. If people are in physical danger from each other, the situation has escalated beyond Rivalry.
- **The resolution picks a winner.** Rivalry should resolve through understanding or acceptance, not through one side defeating the other.
- **The conflict is abstract.** "There's tension in the settlement" is too vague. The NPC should describe a specific, concrete disagreement between specific people.
