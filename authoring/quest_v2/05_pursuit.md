# Situation 05: Pursuit

**Polti classification:** Pursuit — hunting down a threat to eliminate it at the source.

**Tone arc:** determined/angry → satisfied

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

Something has been causing problems and the NPC has had enough. This is not a reactive plea for help — the NPC has decided it's time to act. They've endured a pattern of problems and reached a last straw. The quest is about ending a threat at its source, not managing symptoms.

The key distinction from Supplication (reactive, desperate) and Vengeance (personal grudge, morally gray): Pursuit is proactive and practical. The NPC isn't overwhelmed and isn't seeking payback. They're fed up and want the problem solved permanently. The moral ground is clear — the threat is real and dealing with it is reasonable.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC lays out the pattern — not a single incident, but a series that has reached a tipping point. The tone is determined, not panicked. The NPC has been patient and patience has run out. The ask should feel like a practical decision, not an emotional outburst.

### `acceptText`

Approval. The NPC respects the player's willingness to act. This is less emotional than Supplication's relief and less cold than Vengeance's satisfaction. The NPC is ready to see this handled and is glad to have found someone capable.

### `declineText`

Mild contempt or resignation. The NPC expected action and got passivity. They're not hurt — they're frustrated that the problem persists. They might express that they'll find someone else or that the problem will get worse. Less hostile than Vengeance's decline, more pointed than Enigma's shrug.

### `expositionTurnInText`

The first strike was effective. The NPC's determination is validated — they were right to act. If more conflicts follow, the scope of the threat was larger than expected, or dealing with the first part revealed the source.

### `conflict phases` (conflict1 through conflict4)

Each phase tracks closer to the source of the problem. The NPC becomes more focused and specific with each phase. Pursuit naturally supports longer chains — tracking a threat to its source can involve gathering information (TALK_TO_NPC), finding evidence (FETCH_ITEM), and eliminating the threat (KILL_MOBS) in sequence. Each conflict should feel like progress toward a definitive end.

### `conflictTurnInText` (any conflict turn-in)

Growing satisfaction. The NPC sees the problem being systematically addressed. The tone is practical approval — "good, now the next part." The closer to resolution, the more the NPC's tension releases.

### `resolutionText`

Clean satisfaction. The threat is handled. The NPC sounds like someone who can finally stop worrying about this and get back to their life. Not triumphant — just done. The problem existed, it was dealt with, life continues. The reward is practical and proportionate.

### `skillCheck.passText`

The NPC reveals the specific incident that was the last straw, or shares tactical knowledge about the threat they've gathered from experience. Best fit skills: NATURE (player demonstrates knowledge of the threat's behavior or patterns), PERCEPTION (player notices signs of the threat the NPC has been tracking), INVESTIGATION (player asks about the pattern and the NPC reveals more detail), INSIGHT (the NPC admits this is about more than just the threat — it's about feeling safe again), HISTORY (player recognizes this type of threat and how it was dealt with before).

### `skillCheck.failText`

The NPC stays tactical. The personal dimension or the deeper intel stays private.

---

## Anti-Patterns Specific to Pursuit

- **The threat is vague.** Pursuit requires a specific, identified problem. "Something's been causing trouble" is Enigma. "The {enemy_type_plural} have been raiding the stores every week" is Pursuit.
- **The NPC is desperate.** That's Supplication. Pursuit's NPC has the emotional bandwidth to be strategic, not just reactive.
- **The NPC wants punishment, not prevention.** That's Vengeance. Pursuit is about ending a problem, not settling a score. The NPC should care about the future, not the past.
- **The resolution leaves the threat active.** Pursuit must end with the problem solved. "We drove them back for now" undercuts the arc — the NPC wanted permanent resolution.
