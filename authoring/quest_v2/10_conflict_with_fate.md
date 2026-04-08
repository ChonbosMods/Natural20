# Situation 10: Conflict with Fate

**Polti classification:** Conflict with Fate — forces beyond comprehension are plaguing the land and people push back against what they cannot understand.

**Tone arc:** bewildered/fatalistic → cautiously hopeful

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES

---

## Emotional Frame

Things are going wrong and nobody knows why. Creatures appear where they shouldn't. Resources are depleted for no clear reason. The land feels hostile in a way that doesn't have a simple explanation. The NPC doesn't understand the cause but knows what needs doing — kill what's threatening them, gather what they need to endure, find what might help.

The key distinction from Disaster (specific aftermath to recover from) and Pursuit (identified threat to eliminate): Conflict with Fate has no identifiable root cause. The NPC is fighting symptoms, not a source. There's an element of helplessness against something larger — but the response is practical, not mystical.

The restricted objective set (no TALK_TO_NPC) reinforces the tone. There's no one to ask, no one who has answers. The NPC and the player act against forces they don't fully understand.

Avoid cosmic or supernatural framing. The bewilderment should feel like a farmer facing a bad year that doesn't make sense, not a prophet foretelling doom. Keep it grounded in practical consequences.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC describes what's been happening — not with understanding, but with cataloging. A list of things going wrong that don't add up. The tone is bewilderment mixed with weariness. The NPC may have tried to make sense of it and given up. They've settled on dealing with what they can.

### `acceptText`

Cautious appreciation. The NPC isn't sure help will work — nothing else has — but they're willing to try. There's a note of fatalism: "maybe this will make a difference, maybe it won't, but at least we're doing something."

### `declineText`

Resignation. Not anger — the NPC didn't expect help to come. Being told no confirms what they already suspected: they're on their own against something they don't understand. The decline should feel lonely, not hostile.

### `expositionTurnInText`

A small win against an unclear enemy. The NPC is cautiously encouraged. The tone shifts from fatalism to "maybe we can get through this." If more conflicts follow, the situation's strangeness persists but the NPC has found a way to push back, even if they don't understand what they're pushing against.

### `conflict phases` (conflict1 through conflict4)

Each phase is another act of practical resistance against incomprehensible circumstances. The NPC doesn't gain understanding — they gain ground. Each conflict should feel like taking one step forward against a tide. Longer chains work well for this situation because the sustained effort against an unclear threat creates a specific kind of quiet heroism.

### `conflictTurnInText` (any conflict turn-in)

Measured hope building. The NPC starts to believe that sustained effort might be enough even without understanding. Later turn-ins should show the NPC's fatalism giving way to cautious determination.

### `resolutionText`

Cautiously hopeful. The immediate crisis has been weathered. The NPC does not claim to understand what happened or why — but they're still standing. The tone is "we made it through this one." Not triumphant, not fully relieved. The NPC is aware that what they don't understand might return. But for now, things are better. The reward should feel like an offering of thanks from someone who wasn't sure thanks would ever be warranted.

### `skillCheck.passText`

The NPC shares their private theory — not an explanation, but a pattern they've noticed that they haven't told anyone because it sounds strange. Or they reveal how deeply the uncertainty has affected them emotionally. Best fit skills: NATURE (player recognizes a natural pattern behind the seemingly inexplicable events), PERCEPTION (player notices environmental details that give shape to the undefined threat), INSIGHT (NPC reveals the psychological toll of fighting something they don't understand), HISTORY (player recognizes this pattern from regional lore or past events), INVESTIGATION (player asks the right questions and the NPC's scattered observations start to form a picture).

### `skillCheck.failText`

The NPC stays in practical mode. The confusion and the fear remain private.

---

## Anti-Patterns Specific to Conflict with Fate

- **The cause is revealed.** Conflict with Fate's power is in the not-knowing. If the resolution explains why everything happened, this is Enigma with combat. The NPC should end cautiously hopeful, not enlightened.
- **The framing is supernatural.** "Dark forces" and "ancient curses" are too specific. The NPC doesn't know what's happening. "Things have been wrong and I can't explain it" is the register.
- **The NPC is prophetic.** No prophecies, no omens, no mystical language. The NPC is a practical person dealing with practical problems that happen to be inexplicable.
- **The resolution is definitive.** "It's over" is too clean. "It seems better now" is the right landing. The uncertainty persists even as the immediate problems are solved.
