# Situation 06: Disaster

**Polti classification:** Disaster — a community or individual has been struck by calamity and must recover.

**Tone arc:** shaken → stabilized

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

---

## Emotional Frame

Something bad has already happened. The disaster is not ongoing — the NPC is dealing with aftermath. Crops failed, creatures overran an area, supplies were destroyed, a storm wrecked infrastructure. The quest is about triage and recovery, not prevention.

The key distinction from Supplication (personal plea from someone overwhelmed): Disaster is communal. The NPC speaks on behalf of the settlement or a group, not just themselves. The emotional register is weary and practical — the NPC has already processed the shock and is now in problem-solving mode. They're tired, not panicked.

The disaster should feel like a bad season, not an apocalypse. Scale to village life. A settlement that lost its food stores is in trouble. A settlement facing the end of the world is in the wrong game.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC describes the aftermath. The disaster has happened — this is not a warning, it's a status report delivered in a human voice. The NPC is tired and practical. They know what they need and they're too worn out for pride. The tone is "here's where we are, here's what we need."

### `acceptText`

Weary gratitude. Not the sharp relief of Supplication — more like the grateful acknowledgment of someone who has been handling a crisis alone and finally got reinforcements. The NPC may express hope cautiously, as if they've learned not to hope too quickly.

### `declineText`

Heavy. The NPC doesn't have energy for guilt-tripping or hostility. They absorb the rejection with the same weariness they've been carrying. They might simply state what will happen without help — not dramatically, just factually. This should feel like watching someone's shoulders sag.

### `expositionTurnInText`

The first wave of relief. The most urgent need has been addressed. The NPC's weariness is still there but there's a foundation of stability forming. If more conflicts follow, the NPC is clear-eyed about what remains — they're not pretending it's over, but they can see a path now.

### `conflict phases` (conflict1 through conflict4)

Each phase addresses another layer of the disaster's aftermath. The NPC moves from triage (most urgent needs) to stabilization (longer-term needs). Longer chains work well for Disaster — a single calamity has many consequences. The NPC's emotional state should gradually shift from survival mode to rebuilding mode. Later phases feel less desperate and more constructive.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each step of recovery. The weariness lifts incrementally. By the later turn-ins, the NPC should sound like someone who is starting to believe things will be okay. Not optimistic — stabilized.

### `resolutionText`

Stabilized. The community will survive. The NPC is not celebrating — they're acknowledging that the worst is over and the future is manageable again. The tone is quiet, grounded, forward-looking. The reward should feel like what the community can spare, offered with an awareness that they're giving from limited resources.

### `skillCheck.passText`

The NPC reveals the full scope of the damage — worse than they publicly admitted. Or they share a personal cost of the disaster they've been hiding to maintain morale for others. Best fit skills: INSIGHT (the NPC admits how scared they were, or still are), PERCEPTION (player notices the disaster damage is more extensive than described), INVESTIGATION (player probes the timeline and the NPC reveals the disaster had warning signs they missed), PERSUASION (NPC drops the "we'll manage" front and admits how close to collapse things actually got), HISTORY (player recognizes this happened before and the NPC confirms it — they're afraid of the pattern), NATURE (player assesses the environmental impact and the NPC admits the recovery timeline is longer than they've told people).

### `skillCheck.failText`

The NPC maintains the practical front. The scope of the damage stays at the official version.

---

## Anti-Patterns Specific to Disaster

- **The disaster is ongoing.** If the threat is still active, this is Supplication or Deliverance. Disaster is about aftermath, not crisis in progress.
- **The disaster is apocalyptic.** Scale to village life. A lost harvest is a disaster. A world-ending cataclysm is not expressible through COLLECT_RESOURCES.
- **The NPC is panicking.** Disaster's emotional register is weary, not frantic. The NPC has already processed the shock. If they're still in shock, the disaster is too recent and the situation might be better framed as Supplication.
- **The resolution is celebratory.** The community survived but it cost them. "Stabilized" is the destination, not "victorious." There should be an awareness of what was lost even as the NPC acknowledges what was saved.
