# Situation 09: Vengeance

**Polti classification:** Vengeance — a wrong has been committed and the aggrieved party wants payback, not justice.

**Tone arc:** bitter/cold → settled

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM

**KILL_BOSS variants:** When authoring a KILL_BOSS template for Vengeance, the NPC must have a specific named-villain relationship with the boss: a concrete wrong, a long-nursed hatred, a score that has a face. The boss is singular: do NOT use `{kill_count}` or `{enemy_type}` as the target in boss-quest text, and do not write the plural form as if describing the boss themselves. Use `{boss_name}` wherever the threat is named. `{enemy_type_plural}` is permitted only for the boss's gang/kin (the followers the player will cut through to reach them). `{group_difficulty}` is available as a quiet flavor variable ("legendary," "epic") but should not appear in most text.

---

## Emotional Frame

The NPC has been wronged. This is not fresh — it has been sitting with them, hardening. They are not asking for justice or fairness. They want retribution. The quest is built on the NPC's anger, and the moral ground is deliberately gray.

The key distinction: the NPC may not be entirely in the right. Their grievance is real, but their response might be disproportionate or misdirected. The player should feel useful but not necessarily righteous. This separates Vengeance from Pursuit (which is practical threat elimination) and Supplication (which is sympathetic need).

The restricted objective set (KILL_MOBS, FETCH_ITEM only) reinforces the tone. Vengeance is about taking, not negotiating. There is no TALK_TO_NPC — the NPC is past talking. There is no COLLECT_RESOURCES — this is not about rebuilding.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC has been thinking about this for a while. The anger is cold, not hot. They've rehearsed this. The grievance should be specific and personal — not "bad things happened" but "they did X and I haven't forgotten." The moral ambiguity should be present from the first line: the player should recognize the grievance as legitimate while sensing that the NPC's response might be more than what's warranted.

Must establish: what happened, who or what is responsible, and that the NPC wants it answered — not discussed, not resolved, answered.

### `acceptText`

Not warm. Satisfied. A grim partnership is forming. The NPC got what they wanted: someone willing to act. There is no "thank you for being kind." There is "good, let's get this done." The NPC respects willingness, not compassion.

### `declineText`

Hostile is appropriate and expected. Cold, dismissive, possibly contemptuous. Being told "no" confirms the NPC's belief that they are alone in this grievance. They are not hurt — they are disgusted. Range from icy silence to open scorn. The NPC might imply they'll handle it themselves, with the subtext that the player is a coward for not helping.

### `expositionTurnInText`

The first hit landed. The NPC is satisfied but not sated. If more phases follow, success may have revealed something that deepens the grievance or makes it more personal. The NPC is leaning in, not pulling back. There is no "maybe that's enough." The NPC's appetite for payback has been confirmed, not diminished.

### `conflict phases` (conflict1 through conflict4)

Escalation. The NPC is committed. The player should start to feel the moral weight — is this still proportionate? The NPC does not care about proportionality. The ask is direct and cold. No pleading, no justification. The NPC has moved past needing to explain why.

In longer chains, each successive conflict deepens the player's complicity. The NPC's appetite for payback is confirmed, not diminished, by success. Later phases may reveal that the grievance is expanding — what started as a specific wrong is becoming a broader campaign. The player is now an instrument of someone else's grudge. The text should make this feel heavy without moralizing about it.

### `conflictTurnInText` (any conflict turn-in)

Not gratitude — acknowledgment. The NPC respects what was done. Still cold. But there may be a crack: a moment where the NPC realizes this isn't making them feel the way they expected. Not regret — just a flicker of emptiness where satisfaction was supposed to be. This is optional but powerful when present. Later turn-ins can lean harder into this hollowness.

### `resolutionText`

The defining field. The NPC got what they wanted. The tone is "settled" — not triumphant, not joyful, not relieved. The anger has gone somewhere but it didn't become something positive. The NPC might feel hollow, quiet, or grimly at peace. They are not worse off, but they are not healed. The reward should feel like a transaction being closed, not a gift being given. The player should walk away thinking about the NPC's state of mind.

### `skillCheck.passText`

The real wound. Underneath the cold anger is something the NPC will not volunteer: the specific moment of loss, betrayal, or humiliation that started this. A passed check peels back the bitterness and shows the hurt. For one beat, the NPC is not vengeful — they are wounded. Best fit skills: INSIGHT (emotional truth under the anger), PERSUASION (NPC trusts you enough to stop performing toughness), PERCEPTION (player notices physical signs of grief the NPC is hiding), HISTORY (player recognizes the pattern — this isn't the first time this has happened to the NPC or their people), INVESTIGATION (player catches an inconsistency in the NPC's account that reveals the grievance is more personal than stated).

### `skillCheck.failText`

The armor stays up. The NPC gives you the mission, not the reason. The deflection should feel like a wall going up — deliberate, practiced. The NPC does not want your understanding. They want your sword.

---

## Anti-Patterns Specific to Vengeance

- **The NPC is clearly righteous.** If the grievance is unambiguous and the response proportionate, this is Pursuit or Supplication, not Vengeance. Vengeance requires moral gray. The player should help but wonder if they should have.
- **The resolution is triumphant.** "We showed them!" undercuts the situation. Vengeance's destination is "settled," not "victorious." The NPC should feel the absence of anger more than the presence of satisfaction.
- **The decline text is understanding.** A vengeful NPC does not say "I understand." They judge you for saying no. Let them.
- **The NPC explains their moral position.** Vengeance does not justify itself. The NPC states what happened and what they want done. They do not deliver a speech about why revenge is warranted. If the author feels the need to justify the NPC's position, the grievance isn't specific enough.
- **The accept text is grateful.** This is not a favor. It is a partnership of convenience. The NPC needed someone with the capacity to act. Gratitude implies the NPC owes you something emotional. They don't. They owe you payment.
