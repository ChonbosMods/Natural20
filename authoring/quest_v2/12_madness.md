# Situation 12: Madness

**Polti classification:** Madness — someone is acting irrationally and needs help, not judgment.

**Tone arc:** worried → relieved or bittersweet

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

---

## Emotional Frame

Someone the NPC cares about is behaving in ways that don't make sense — hoarding things, avoiding everyone, fixated on something irrational, making decisions that alarm the people around them. The NPC is worried, not angry. They want to help, not fix.

The key distinction from Enigma (which is about curiosity): Madness is about compassion. The NPC isn't puzzled by an interesting question — they're frightened for someone they care about. The emotional register is tender and anxious.

The restricted objective set (FETCH_ITEM, TALK_TO_NPC only) reflects the nature of the situation. You cannot kill or gather your way through someone's crisis. The quest is about reaching someone and understanding what they need.

**Handle with care.** The "madness" must be sympathetic — stress, grief, fear, obsession, exhaustion. Not a punchline, not a spectacle. The person in crisis is a whole person having a hard time. The quest is about helping, not diagnosing.

---

## Per-Field Emotional Guidance

### `expositionText`

The NPC describes the behavior they've been observing. They are concerned, not judgmental. They should sound like someone who has been watching a person they care about change and doesn't know what to do. The description should be specific — what the person is doing, not a clinical label for it.

### `acceptText`

Grateful and anxious. The NPC is relieved someone is willing to help but isn't sure what help looks like. They may express uncertainty about the right approach — "I don't know what they need, but they won't talk to me."

### `declineText`

Quiet worry. The NPC absorbs the refusal without anger. They're still going to worry about the person whether help comes or not. The decline should feel like watching someone carry a weight they can't put down.

### `expositionTurnInText`

The first step revealed something — maybe the person's behavior has a cause that wasn't visible from outside, maybe reaching them was harder or easier than expected. The NPC processes the new understanding with relief or renewed concern.

### `conflict phases` (conflict1 through conflict4)

Each phase moves closer to understanding or helping the person in crisis. TALK_TO_NPC phases are conversations that peel back layers. FETCH_ITEM phases retrieve something the person needs to feel safe, to heal, or to reconnect. The NPC's anxiety evolves — from general worry to specific concern to actionable understanding. Longer chains can explore the complexity of reaching someone who doesn't want to be reached.

### `conflictTurnInText` (any conflict turn-in)

The NPC processes each new piece of understanding. Their worry shifts form — from "what's wrong" to "how do we help." Later turn-ins should show the NPC preparing to reconnect with the person on new terms.

### `resolutionText`

Relieved or bittersweet. The person in crisis is better, or at least understood. The NPC may not have a neat answer — "they're going to be okay" or "they needed space, and now I understand why." The resolution should honor the complexity of the situation. The reward should feel like a deeply personal thank-you.

### `skillCheck.passText`

The NPC reveals their own fear — that they somehow caused the person's crisis, or that they should have noticed sooner, or that they're afraid of losing the relationship. The passed check shows the NPC's vulnerability about their own role. Best fit skills: INSIGHT (NPC's self-blame or deeper fear), PERSUASION (NPC trusts enough to admit feeling helpless), PERCEPTION (player notices the NPC is more affected than they're showing), INVESTIGATION (player asks about the timeline and the NPC realizes they missed early signs), NATURE (if the behavior relates to seasonal patterns or environmental stress the NPC hadn't considered).

### `skillCheck.failText`

The NPC stays focused on the other person. Their own emotional stake remains private.

---

## Anti-Patterns Specific to Madness

- **The person's behavior is played for laughs.** Mental distress is not comedy. The person in crisis should be portrayed with dignity and the quest should treat their experience seriously.
- **The quest "cures" them.** Fetching an item or having a conversation doesn't cure a person's crisis. The resolution is about connection, understanding, or providing what someone needs — not magically fixing them.
- **The NPC is judgmental.** "They've gone crazy" is the wrong register. "I'm worried about them" is right. The NPC should model compassion.
- **The crisis is dangerous.** If the person's behavior threatens others, the situation is something else. Madness is about someone who is hurting, not someone who is hurting others.
- **The cause is supernatural.** The person's behavior should have human causes — loss, fear, isolation, exhaustion. Not curses, possession, or magical influence.
