# Quest System

## Overview

A **quest** is a multi-step task offered by an NPC: investigate a rumour, retrieve an artifact, bring a named bounty to justice, gather enough of a resource to rebuild. Quests are broken into one or more **phases**, each with its own objective and its own reward payment. Completing the final phase closes the quest at its source NPC.

Two systems shape quests beyond "go do the thing":

- **Disposition** — an NPC's personal feelings toward you, 0 to 100, which colours how they talk to you, what they're willing to offer, and your odds in any dialogue skill check.
- **Skill checks** — D&D-style d20 rolls embedded in conversations that open or shut persuasion, intimidation, investigation, and similar paths. Outcomes move disposition and can unlock alternative quest branches.

Both systems are per-NPC-per-player: the blacksmith and the tavern-keeper each keep their own book on you, and building rapport with one doesn't leak to the other.

---

## Detailed Explanation

### Objectives

Every phase of a quest has one **objective** that determines what you're asked to do and how progress is tracked.

#### Collect Resources

Gather a target count of a specific item type from anywhere in the world. Progress ticks up as you pick matching items up and ticks back down if you drop them. No fixed map marker: you're free to find the items wherever they exist. The phase is ready for turn-in the moment your inventory count meets the target, and reverts if your count drops below it afterward.

Collect Resources is the only objective type that **ignores the 80-block party-proximity check** (see the Party System wiki page). Any online party member is eligible for that phase's rewards regardless of where the gathering happened.

#### Kill Mobs

Defeat a target count of a specific mob type. Only champion mobs at the quest's hostile POI count: kills elsewhere, including ambient surface spawns of the same role, don't tick the counter. Credit goes to every party member who dealt damage to the mob within a short window before its death, which means environmental kills, pet kills, and AI-on-AI all count if you softened the target first. The phase auto-completes the instant the counter hits the target.

#### Kill Boss

Defeat a single named boss. Same damage-contribution rule as Kill Mobs, but there's no counter on display: it's a single binary kill. Boss phases always point at a fixed world location.

#### Fetch Item

Retrieve a specific item and keep it in your inventory. Unlike Collect Resources, a fetch is binary: one qualifying item is enough. Dropping the item reverts the phase to active until you pick it back up. After a successful fetch you return to the quest-giver to turn it in.

There is also a **peaceful fetch** variant for storylines that deliberately avoid combat — mechanically identical, but used in scenarios where the fetch target sits in a chest tucked away inside a settlement rather than guarded by hostile mobs.

#### Talk to NPC

Hold a conversation with a designated NPC. The objective ticks the moment you finish the prompted dialogue with the right target, then you return to the quest-giver to turn the phase in.

### Phase-to-phase flow

Every phase rolls its own XP and its own item reward. A three-phase quest pays three separate rewards, one per phase, rather than a single bundle at the end. After you finish a phase's objective, the game points you back at the source NPC for turn-in. The source NPC shows a blue question mark while they have a turn-in waiting.

Rewards are always level-and-difficulty scaled (see the Party System wiki for the multipliers), and items are freshly rolled for each eligible accepter so multiple party members never walk away with identical loot.

---

### Disposition

Every NPC stores a number between 0 and 100 representing their attitude toward you specifically. Other players have their own, separate values with the same NPC.

#### What moves disposition

| Action | Disposition |
|--------|-------------|
| Accept a quest | +3 |
| Turn in a phase | +5 |
| Complete a full quest | +10 |
| Pass a dialogue skill check | +3 |
| Fail a dialogue skill check | -2 |

The movement hits the NPC who gave or received the action and persists across sessions. Individual dialogue choices can also move disposition — insulting an NPC in free-roam dialogue, picking a compassionate response during a quest hand-off — but the quest-flow changes above are the ones you'll feel most reliably.

#### What disposition affects

**Dialogue tone.** Nine named brackets colour how the NPC talks to you:

| Range | Bracket |
|-------|---------|
| 0–10 | Hostile |
| 11–24 | Scornful |
| 25–39 | Unfriendly |
| 40–49 | Wary |
| 50–59 | Neutral |
| 60–69 | Cordial |
| 70–79 | Friendly |
| 80–89 | Trusted |
| 90–100 | Loyal |

Each bracket pulls from a different dialogue pool, so the same NPC responds icily at 10 disposition and warmly at 95. This is flavor only: tone by itself doesn't gate anything mechanical.

**Skill check odds.** Three **gameplay bands** decide which dice mode you get on any skill check rolled with this NPC:

| Disposition | Roll Mode | Effect |
|-------------|-----------|--------|
| 0–24 | Disadvantage | Roll two d20s, keep the lower |
| 25–74 | Normal | Roll one d20 |
| 75–100 | Advantage | Roll two d20s, keep the higher |

Disadvantage drags your effective roll down even if your stats are excellent; advantage lifts it even if your stats are weak. Keeping a friendly NPC friendly pays off the next time you need to persuade them.

**Quest and dialogue availability.** Some story beats — special reward tiers, alternative phase paths, secret questlines — only unlock once disposition with a specific NPC crosses a threshold. This gating is authored per-quest rather than applied globally.

---

### Skill Checks

A **skill check** is a dice roll mini-game inside a dialogue. It appears as a response option, usually labeled with the skill in brackets: *"[Persuasion] Convince him you're not a threat."* or *"[Intimidation] Make him understand how this goes."* Choosing one rolls the dice, adds your modifiers, and compares the total to a target Difficulty Class.

#### The roll

```
total = d20 + stat modifier + proficiency bonus (if trained)
```

**Stat modifier** is your relevant stat score divided by three, rounded down. All ability scores contribute; dumped stats still give +0 rather than a penalty.

| Stat score | Modifier |
|------------|----------|
| 0–2 | +0 |
| 3–5 | +1 |
| 6–8 | +2 |
| 9–11 | +3 |
| 12–14 | +4 |
| 15–17 | +5 |
| 18–20 | +6 |
| 21–23 | +7 |
| 24–26 | +8 |
| 27–29 | +9 |
| 30 | +10 |

**Proficiency bonus** adds on top of the modifier, but only on checks in skills you're trained in. It scales with character level:

| Level | Proficiency |
|-------|-------------|
| 1–8 | +2 |
| 9–16 | +3 |
| 17–24 | +4 |
| 25–32 | +5 |
| 33–40 | +6 |

**Crits.** A **natural 20** on the kept die is an automatic pass, full stop. A **natural 1** is an automatic fail. Neither can be overridden by modifiers.

#### Difficulty Class

Every skill check targets a DC that the roll needs to meet or beat. The DC is set by a **tier**:

| Tier | DC |
|------|-----|
| Trivial | 5 |
| Easy | 10 |
| Medium | 15 |
| Hard | 20 |
| Very Hard | 25 |
| Nearly Impossible | 30 |

Some checks are **authored** — the quest writer pinned an explicit tier because the story called for it. Others are **procedural** — no tier was authored, so the game rolls one at runtime weighted by the NPC's zone level. That means a generic persuasion check against a starting-zone farmer is forgiving while the same phrasing against an endgame warlord is daunting.

#### Roll modes

The dice roll displays on-screen with a short animation so you can see the outcome. If you're rolling under **advantage**, two dice appear and the higher one is kept. Under **disadvantage**, two dice appear and the lower one is kept. Under **normal** mode, a single die rolls.

Roll mode comes from the NPC's current disposition with you (see the disposition band table above). Advantage and disadvantage do not "cancel" into a flat roll: if disposition triggers advantage, you roll 2d20-keep-highest and nothing else overrides that.

#### Outcome

| Result | Effect on disposition | Other effect |
|--------|----------------------|--------------|
| Pass | +3 | Dialogue branches to the success line; quest flags you as having passed a check (reserved for future reward hooks) |
| Fail | -2 | Dialogue branches to the failure line |

A pass or fail does not end the conversation: whichever branch you take, the dialogue keeps going and you can still accept or decline the quest afterward, depending on how the author placed the check.

#### When skill checks fire

Skill checks are always **optional** — they appear as response choices the player can pick or ignore. Common placements:

- **During a quest offer**, as a persuasion or intimidation angle that opens an alternative phase path or a better reward tier.
- **During turn-in**, as a way to press for more (or smooth over a failed objective).
- **In free-roam dialogue** with NPCs you aren't questing for, purely to build disposition for later.

Declining a check is never a failure — you just move on to the normal dialogue options, no disposition change, no risk.

---

## Summary

- Quests are one or more phases; each phase has one objective and pays its own reward at turn-in.
- Six objective types: Collect Resources, Kill Mobs, Kill Boss, Fetch Item (and Peaceful Fetch variant), Talk to NPC.
- Collect Resources ignores the party-proximity check; all others check distance at phase completion.
- Disposition is per-NPC-per-player, 0 to 100, moved most reliably by quest accept / phase turn-in / check outcomes.
- Disposition has two faces: nine cosmetic brackets that colour dialogue tone, and three gameplay bands (0–24 / 25–74 / 75–100) that set disadvantage, normal, or advantage on all skill checks with that NPC.
- Skill checks roll d20 + stat modifier + proficiency bonus (if trained) against a DC from 5 (Trivial) to 30 (Nearly Impossible).
- Natural 20 auto-passes; natural 1 auto-fails.
- Authored skill checks pin a tier; procedural checks scale to the NPC's zone level.
- Passing a skill check gives +3 disposition; failing costs -2.
