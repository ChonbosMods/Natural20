# Situation 17: Ambition

**Polti classification:** Ambition — the NPC wants to build, create, or achieve something driven by aspiration rather than necessity.

**Tone arc:** driven/excited → proud

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

The NPC has a vision. Not a need — a want. They want to create something, prove something, or reach a goal that matters to them. The quest is about making it happen. This is one of the lightest situations in the catalog alongside Obtaining, but the difference is energy: Obtaining is practical, Ambition is aspirational.

The key distinction from Obtaining (practical need) and Daring Enterprise (dangerous expedition): Ambition is about desire and personal growth, not survival or risk. The NPC isn't in danger and the goal isn't hazardous — it's just beyond what they can achieve alone. A cook who wants to create the perfect dish. A blacksmith who wants to build something they've never attempted. A villager who wants to improve the settlement.

Keep the ambition scaled to a village. A person wanting to be the best baker in the region is Ambition. A person wanting to conquer the world is not expressible through COLLECT_RESOURCES.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC shares their vision with enthusiasm. They should sound like someone who's been thinking about this and is excited to finally have someone to tell — and someone who might help. The tone is energetic and forward-looking. The NPC might be slightly grandiose for their station, and that's charming rather than off-putting.

### `acceptText`

Excited. The NPC has a partner in their vision. This is the most upbeat accept text in the catalog. The NPC's energy should be infectious — they see the goal getting closer.

### `declineText`

Deflated but not devastated. The vision stalls, not dies. The NPC might express that they'll keep working toward it on their own, just slower. Disappointment without despair. They might be slightly hurt that their vision didn't inspire action.

### `expositionTurnInText`

The vision is taking shape. The NPC's excitement grows — tangible progress validates the ambition. If more conflicts follow, the project has revealed new requirements or opportunities the NPC didn't anticipate, and they're energized rather than discouraged.

### `conflict phases` (conflict1 through conflict4)

Each phase builds toward the vision's realization. The NPC becomes more animated and specific with each phase. COLLECT_RESOURCES phases gather materials. FETCH_ITEM phases secure key components. TALK_TO_NPC phases seek expertise, approval, or partnership. KILL_MOBS phases clear obstacles to the project. Longer chains work when the ambition is layered — each phase adds a new dimension to the project.

### `conflictTurnInText` (any conflict turn-in)

The NPC reacts to each step with growing pride and excitement. They should comment on the progress — what it looks like, how it feels, what comes next. The closer to resolution, the more the NPC sounds like someone seeing their dream materialize.

### `resolutionText`

Proud. The ambition has been realized or meaningfully advanced. The NPC should sound like someone who is genuinely proud of what was accomplished — not just grateful for help, but fulfilled by the achievement itself. The reward should feel generous and freely given — the NPC is in a good mood and wants to share.

### `skillcheckPassText`

The NPC reveals why this specific ambition matters — a personal history, a promise they made, a need to prove something to themselves or someone else. Or they share a fear that the ambition isn't good enough, that they're reaching beyond their ability. Best fit skills: INSIGHT (personal meaning behind the ambition), PERSUASION (NPC admits self-doubt under the enthusiasm), PERCEPTION (player notices preparatory work the NPC has already done, revealing how long they've been dreaming about this), INVESTIGATION (player asks about the vision's origin and uncovers a personal story), HISTORY (the ambition connects to a tradition or legacy the NPC wants to continue or surpass).

### `skillcheckFailText`

The NPC stays on the surface. The enthusiasm is real but the deeper motivation remains private.

---

## Anti-Patterns Specific to Ambition

- **The ambition is necessary, not aspirational.** If the NPC needs this to survive, it's Supplication or Obtaining. Ambition requires that the NPC would be fine without achieving the goal — they just deeply want to.
- **The ambition is unrealistic for the setting.** A village blacksmith wanting to forge a perfect blade is Ambition. A village blacksmith wanting to arm an empire is absurd. Scale to the world.
- **The NPC is arrogant.** Ambition should be endearing, not off-putting. The NPC's vision might be slightly larger than their station warrants, but their sincerity makes it charming.
- **The resolution is modest.** Ambition's destination is "proud." If the NPC shrugs at the result, the ambition wasn't real. Let them feel the achievement.
