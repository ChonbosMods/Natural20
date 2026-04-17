# Situation 02: Deliverance

**Polti classification:** Deliverance — a rescuer arrives to save the helpless from a threatening force.

**Tone arc:** urgent → relieved

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, TALK_TO_NPC

**KILL_BOSS variants:** When authoring a KILL_BOSS template for Deliverance, the boss is the specific named threat holding or endangering the loved one. The NPC knows who has them (or is menacing them) and can name the threat: not "something took them," but "{boss_name} took them." Protective rage, not cold vengeance. The boss is singular: do NOT use `{kill_count}` or `{enemy_type_plural}` as the target. Use `{boss_name}` wherever the threat is named. `{enemy_type_plural}` is permitted only for the boss's gang. `{group_difficulty}` is available as a quiet flavor variable.

---

## Emotional Frame

Someone is in danger and cannot extract themselves. The quest giver is not the endangered party — they are the worried friend, relative, neighbor, or authority figure who knows someone needs help and cannot provide it alone. The emotional center is on the *endangered party*, not the quest giver.

The key distinction from Supplication: in Supplication, the NPC is asking for help with their own problem. In Deliverance, the NPC is asking for help with someone else's problem. The NPC's desperation is secondhand — fueled by care, not self-preservation. This gives Deliverance a different flavor of urgency: protective rather than survival-driven.

Use `{settlement_npc}` or `{target_npc}` to ground the endangered party. The endangered person should feel real — someone the NPC clearly cares about — not an abstraction.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC explains that someone they care about is in trouble. The urgency is about time and vulnerability — the endangered person cannot wait. The NPC should communicate both what the threat is and why the endangered person cannot handle it. The NPC's own frustration at being unable to help should come through — they would go themselves if they could.

### `acceptText`

Urgent gratitude. Less about the NPC's own relief (as in Supplication) and more about hope for the person in danger. The NPC is trusting the player with someone they care about. This trust should feel weighty.

### `declineText`

More anguished than hostile. The NPC isn't angry at the player — they're terrified for the person in danger. Guilt-tripping is natural but comes from a place of fear, not bitterness. The NPC might state plainly what will happen if no one helps. This should feel like witnessing someone's helplessness.

### `expositionTurnInText`

The immediate threat has been addressed or the first step toward rescue is complete. The NPC's fear begins to ease but doesn't disappear until the endangered person is confirmed safe. If more conflicts follow, the NPC may learn that the situation was more complicated than they knew.

### `conflict phases` (conflict1 through conflict4)

Each phase moves closer to securing the endangered person's safety. The NPC's emotional state tracks with proximity to rescue — earlier phases are tense and worried, later phases are hopeful and focused. In longer chains, complications should involve the rescue itself (the path is blocked, additional threats appeared, the person needs something specific to recover) rather than unrelated new problems.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes progress toward rescue. Relief builds incrementally. If the endangered person has been confirmed safe, the NPC's emotional shift is dramatic — from tightly held fear to open relief. If safety isn't confirmed yet, the NPC is grateful but still tense.

### `resolutionText`

Relief and deep gratitude. The NPC should express what the endangered person means to them — not in a speech, but in the way the gratitude comes out. This is someone who almost lost someone they care about. The reward should feel secondary to the emotional payoff.

### `skillCheck.passText`

The NPC reveals the depth of the relationship with the endangered person, or shares a detail about the situation they were too frightened to mention. The passed check shows the player the emotional stakes behind the practical request. Best fit skills: INSIGHT (NPC reveals how terrified they really are), PERCEPTION (player notices signs the NPC has been in distress — haven't slept, hands shaking), PERSUASION (NPC trusts enough to admit they feel responsible for the situation), NATURE (player assesses the threat and gives the NPC realistic hope), INVESTIGATION (player asks about the timeline and realizes urgency is greater than stated).

### `skillCheck.failText`

The NPC stays focused on the practical. The emotional depth remains hidden behind the urgency of the request.

---

## Anti-Patterns Specific to Deliverance

- **The endangered person is abstract.** "People are in danger" is too vague. The NPC should be worried about a specific person grounded in a template variable. Deliverance is personal, not civic.
- **The NPC is the endangered party.** That's Supplication. Deliverance requires the NPC to be acting on someone else's behalf.
- **The resolution doesn't confirm safety.** The player needs to know the endangered person is okay. A resolution that leaves their fate ambiguous undercuts the entire arc.
- **The urgency is performative.** If the situation doesn't actually require immediate action, use Obtaining or Enigma instead. Deliverance earns its urgency through genuine vulnerability.
