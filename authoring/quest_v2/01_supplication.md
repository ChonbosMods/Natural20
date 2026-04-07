# Situation 01: Supplication

**Polti classification:** Supplication — a person in need begs for help from someone who has the power to give it.

**Tone arc:** desperate → grateful

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

The NPC has a problem they cannot solve alone. They have tried what they can. Asking a stranger is a last resort — an admission that their own resources, skills, or courage are insufficient. The quest is built on the NPC's vulnerability.

The key distinction from other situations: the NPC is *reactive*, not proactive. They didn't go looking for trouble. Trouble found them and they're overwhelmed. This separates Supplication from Pursuit (proactive hunting) and Daring Enterprise (voluntary risk).

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC lays out their problem. They are past the point of pride — they are asking because they have no other option. The emotional register is not panicked but weary. They've been dealing with this and they're running out of capacity. The ask should feel like something the NPC has been working up the courage to say.

Must establish: what the problem is, that the NPC has already tried to handle it, and that they genuinely need outside help. Avoid melodrama — exhaustion reads as more authentic than hysteria.

### `acceptText`

Relief. Not effusive gratitude — that comes at resolution. This is the moment someone says "yes" after the NPC expected to hear "no." The NPC exhales. Brief and warm. Possibly a little shaky or disbelieving.

### `declineText`

Guilt-tripping is natural and expected here. The NPC is desperate and just got turned down. They are not angry — they are deflated. The decline text should make the player feel the weight of walking away. Range from quiet resignation to openly stating how bad things are going to get without help. The NPC should not become hostile — they don't have the energy for it.

### `expositionTurnInText`

The first objective is done. The NPC shifts from desperate to cautiously hopeful. The immediate pressure has eased but the situation isn't fully resolved. If another conflict phase follows, this is where the NPC reveals a complication or a deeper layer of the problem they didn't mention before — not because they were hiding it, but because the first crisis was too urgent to think past.

### `conflict phases` (conflict1 through conflict4)

The NPC is stabilizing. They trust the player now — the ask is more direct, less pleading. Each successive conflict should feel like a natural extension of the situation, not a disconnected new problem. The NPC is no longer in freefall; they're triaging. With each phase, the desperation recedes and is replaced by cautious hope and growing trust.

In longer chains, later conflicts should reveal that the NPC's situation was more layered than initially apparent — not because they were hiding it, but because the first crisis was too urgent to think past. Each conflict deepens the player's understanding of what the NPC is going through.

### `conflictTurnInText` (any conflict turn-in)

Gratitude building. The NPC can see progress. If another conflict follows, the NPC might be almost apologetic about needing to ask for more. If this is the last conflict phase, relief is tangible and sets up the emotional landing of the resolution.

### `resolutionText`

Full gratitude. The NPC acknowledges what was at stake and what the player prevented. This should not be a simple "thanks" — the NPC should articulate, briefly, what their life looks like now that the crisis is over. The reward reference should feel humble and sincere — they're giving what they can, and they know it's not enough.

### `skillcheckPassText`

The NPC reveals the personal stakes they were holding back. Not just "bandits are a problem" but the fear underneath — for their family, their livelihood, their future. A passed check lets the player see the person behind the request. Best fit skills: INSIGHT (emotional truth), PERCEPTION (physical signs of how bad things are), PERSUASION (NPC trusts you enough to stop performing composure), INVESTIGATION (player probes the timeline and realizes it's worse than stated), HISTORY (player recognizes this has happened before and the NPC has been through it already).

### `skillcheckFailText`

The NPC stays on the surface. The practical problem is stated; the personal cost is not. The deflection should feel like the NPC choosing to keep it together, not a game punishing the player.

---

## Anti-Patterns Specific to Supplication

- **The NPC is merely inconvenienced.** Supplication requires genuine need. "I could really use some extra iron" is Obtaining, not Supplication. If the NPC could reasonably handle this themselves with mild effort, the situation is wrong.
- **The NPC is melodramatic.** Desperation reads as weary, not theatrical. Avoid "we're all going to die" unless the situation genuinely warrants it. Most Supplication quests are about livelihood and safety, not apocalypse.
- **The resolution is transactional.** "Here's your reward, thanks" undercuts the arc. The NPC should sound like someone whose life has materially improved because of what the player did.
- **The decline text is polite.** A desperate person who gets told "no" does not say "I understand, perhaps another time." They show the strain. Let them.
