# Situation 07: Obtaining

**Polti classification:** Obtaining — acquiring something through effort, negotiation, or persistence.

**Tone arc:** practical/hopeful → satisfied

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

The NPC needs something. It's straightforward — they have a clear need and a clear ask. This is the least dramatic situation in the catalog, and that's by design. Obtaining is the village favor: help me get this thing I need for my work, my family, my project.

The key distinction from Recovery (restoring what was lost) and Ambition (aspirational goals): Obtaining is practical and present-tense. The NPC isn't mourning a loss or dreaming big. They need a thing, they can't get it alone, and they're asking for help. The emotional register is everyday — a neighbor asking a neighbor.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC states what they need and why. No drama. The need should feel reasonable — the NPC isn't asking for the impossible, just for help with something that requires more hands, more reach, or more courage than they have. The tone is conversational and direct.

### `acceptText`

Simple gratitude. Warm but not intense. The NPC appreciates the help and is ready to get the thing done.

### `declineText`

Mild disappointment. The NPC isn't in danger — they'll manage, it'll just take longer or be harder. No guilt, no hostility. A shrug and a "well, I'll figure something out." The lightest decline in the catalog alongside Enigma.

### `expositionTurnInText`

The first piece is handled. The NPC is pleased — things are moving. If more conflicts follow, the NPC might realize the need was slightly more complex than they initially described. Not because they were hiding anything — they just didn't know the full scope until the process started.

### `conflict phases` (conflict1 through conflict4)

Each phase brings the NPC closer to having what they need. The emotional register stays practical throughout. Complications should be logistical, not dramatic — a material turned out to be harder to find, a supplier needs convincing, there's one more step the NPC didn't anticipate. Obtaining rarely needs more than two conflict phases. Longer chains risk making a simple favor feel padded.

### `conflictTurnInText` (any conflict turn-in)

Practical satisfaction. The NPC is getting what they need, step by step. The appreciation is genuine but proportionate — they're not overwhelmed with gratitude for a favor.

### `resolutionText`

Satisfied. The NPC has what they needed and life can proceed. The tone is warm and even — a completed transaction between people who helped each other out. The reward should feel fair and appropriate, not grand.

### `skillCheck.passText`

The NPC reveals why this particular need matters more than it seems — a personal reason, a time pressure, or a consequence of not having it that they downplayed. Best fit skills: INSIGHT (the need is emotionally loaded in a way the NPC didn't let on), PERSUASION (NPC admits they've been trying to get this for a while and are more frustrated than they showed), PERCEPTION (player notices the NPC's current workaround for not having the thing and it's clearly inadequate), INVESTIGATION (player asks why they can't get it themselves and the answer reveals a complication).

### `skillCheck.failText`

The NPC keeps it simple. The need is what it is.

---

## Anti-Patterns Specific to Obtaining

- **The need is secretly urgent.** If the NPC actually faces consequences for not getting this, the situation is Supplication or Disaster, not Obtaining. Obtaining's stakes are low by design.
- **The favor becomes an adventure.** If the complications escalate into genuine danger, the situation has drifted into Daring Enterprise. Obtaining stays grounded.
- **The NPC is passionate about the need.** That's Ambition. Obtaining is practical, not aspirational. The NPC needs iron for repairs, not iron for their masterwork.
- **The resolution is emotional.** A simple "thanks, this is exactly what I needed" is the right register. Grand emotional payoffs are for situations with grand emotional setups.
