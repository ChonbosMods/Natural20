# Party System

## Overview

A **party** is a group of players who share quests. Every player is always in a party, even if it's just them: a solo player is in a "party of one." Growing a party is done by invitation through the Character Sheet: once another player accepts, they join your party and you can start tackling quests together.

The party system is built around one simple promise: **if you accept a quest while in a party, the whole party gets to work on it.** Progress, rewards, and consequences are shared across everyone who was in the party at the moment of acceptance. Leaving, kicking, or disbanding the party later never changes who owns a quest: whoever was there when the quest was accepted keeps the quest.

---

## Detailed Explanation

### What a party is

At its core, a party is a named list of players with a leader at the top. Every player has a party entry from the moment they first connect. Solo players occupy a size-one party by themselves, with themselves as leader. There is no separate "solo mode" vs "party mode" to toggle.

Parties persist across sessions: if your party has four members and everyone logs off overnight, the party is still four members the next day.

### Forming and growing a party

Any party member can invite any non-partied player. Invites are sent from the **Party tab** of the Character Sheet. The invitee sees a banner prompting them to accept or decline from their own Party tab.

Accepting an invite disbands your old size-one party and places you in the inviter's party. The inviter, or whoever is currently leader of their party, remains leader.

Players already in a party of two or more cannot be invited to a different party without leaving or being kicked first. Only one pending invite can be outstanding per recipient at a time: re-inviting replaces the previous invite rather than stacking.

### Leadership and succession

Each party has exactly one leader. The leader has two distinct privileges:

- **Kicking members.** Leader-only.
- **Being shown as the leader on the Party tab.** A star or crown icon marks them in the member list.

Every other party action (inviting, leaving, accepting invites) is open to all members equally. Logging out does not transfer leadership: the same person stays leader when they come back online.

If the leader explicitly leaves the party, leadership passes to the next member by **join order** (whoever joined earliest, second earliest, and so on).

If the leader goes offline for **seven consecutive days**, leadership automatically transfers on the next member's login. This "ghost leader rule" prevents a permanently-absent leader from locking everyone else out of kicking inactive players.

### Leaving and kicking

Any member can leave at any time. They revert to a fresh size-one party with themselves as leader. Leaving does not forfeit any quests already in progress: you keep every quest you were already a part of (see **Quest sharing** below).

Kicking is the leader's equivalent of a forced leave. The kicked player reverts to a solo party exactly as if they left voluntarily, keeping all their in-progress quests.

When the last non-leader member leaves a party, the party quietly collapses back to a size-one for the remaining leader. There is no explicit "disband" button.

### Party size

There is no cap on party size. A party of 12 is as valid as a party of 2. Monster difficulty scaling (see **Monster scaling** below) is the primary balancing lever for large groups, not a hard cap.

### Friendly fire and PVP

Party members cannot damage each other, period. Even on a server with PVP enabled, friendly fire is blocked between members of the same party: melee swings, projectiles, AoE bursts, reflected damage, and DoT splash all pass straight through. Joining a party is the cleanest way to make another player un-attackable to you regardless of server settings.

Players outside your party follow the server's normal PVP rules. On a PVP-enabled server, you can engage non-party players as usual; on a PVE server, no one is attackable. The party shield only protects the people listed on your party tab.

---

## Quest Sharing

### Who owns a quest

When one party member accepts a quest from an NPC, the entire party is locked in as the quest's **accepters**. This is a snapshot taken the moment the accept button is clicked: everyone in the party at that instant becomes an accepter, and nobody else.

This list never changes. If a new player joins the party afterward, they do not see the quest at all: not in their Quest Log, not on their world map, not in any dialogue. The quest is invisible to them.

If a current accepter leaves the party (voluntarily or via kick), the quest still belongs to them. They keep it in their log, and rewards still flow to them when the quest finishes.

### Shared progress

All accepters share one set of objective counters. If the quest is "kill 10 goblins," the counter ticks up whenever **any accepter** kills a goblin, regardless of whose area it happened in or whether other accepters were nearby. Phase transitions happen once, for the whole party.

Progress is not re-rolled per player. There is one quest record, one set of counts, one list of phases.

### Proximity at phase completion

When a phase's objective finishes, the game checks which party members were near the triggering player (the one who finished it) at that exact moment.

- **Within 80 blocks of the triggering player**: eligible for this phase's rewards.
- **More than 80 blocks away**: marked as having missed this phase and shown a **Quest Missed** banner for it.
- **Offline entirely**: silently misses this phase. No banner, no reward.

**Collect Resources objectives are exempt from the distance check.** Gathering happens over time and in many places, so any online accepter is eligible regardless of where they are. Only offline disqualifies you for a gather phase.

### Quest Missed: what it means

The Quest Missed banner means "you weren't present for this particular phase." It is a consequence of one phase only, not the whole quest. Missing a phase:

- **Loses you that phase's XP and that phase's item reward.** You get nothing from that turn-in.
- **Keeps you on the quest.** Your quest log still shows it. Your waypoint advances to the next phase's target like anyone else's. You can freely participate in later phases.

A missed player does not see the return-waypoint or turn-in dialogue for the phase they missed: the party must turn it in through an eligible member. Once the party advances to the next phase, the missed player is again a full participant.

### Turn-in

Any eligible (non-missed) accepter can trigger turn-in by talking to the source NPC. The first one to do so fires the turn-in for the whole party: everyone who was eligible for that phase receives their reward and the quest advances to the next phase.

**Rewards are split per-accepter.** Each eligible accepter gets:

- **XP**, scaled by their own level and the quest's difficulty.
- **A freshly rolled item** of the phase's tier and item level. Two party members never receive identical items: the loot system re-rolls for each recipient so every player walks away with their own unique piece.

Offline accepters at turn-in time get nothing. If you want your share of a phase's reward, be online and within 80 blocks when it completes.

### XP amounts

Per-phase XP is based on the accepter's current level multiplied by a difficulty factor:

| Difficulty | Multiplier |
|------------|-----------|
| Easy       | 0.5x      |
| Medium     | 1.0x      |
| Hard       | 2.0x      |

So a hard-quest phase pays roughly twice what a medium-quest phase of the same level pays, and four times what an easy-quest phase does. Each phase pays its own amount, so a three-phase hard quest pays roughly three times a one-phase hard quest.

### Pre-existing quests when joining a party

Anything you accepted before joining a new party stays yours and only yours. The new party's other members are not automatically added as accepters of your old quests. Similarly, the new party's pre-existing quests are invisible to you.

The only way to share a quest with new party members is to accept a new one together.

---

## Monster Scaling

### Why it exists

Large parties would trivialize every encounter if the game didn't respond. Monster scaling makes encounters tougher the more party members are nearby.

### How it works

When a mob group spawns (from a POI entry, an ambient roll, or a quest trigger), the game counts how many of the triggering player's party members are online and within 80 blocks of that player. That count becomes a **monster level bump**:

- Party of 1 (solo): no bump.
- Party of 2: +1 monster level.
- Party of 3: +2 monster levels.
- ...and so on, up to a **cap of +6**.

A higher monster level means higher mob HP and damage, via the standard monster-level curves. The bump is baked into the spawning mobs and is preserved across chunk reload: once the group exists, its difficulty is fixed for the lifetime of those mobs.

### What scaling doesn't affect

Monster scaling adjusts mob stats (HP, damage) only. It does not:

- Change the number of mobs in a group.
- Change the mob types that spawn.
- Change the item level of mob loot drops.

Loot rolls continue to scale off the zone's regular item level, independent of how many party members were nearby at spawn.

---

## Summary of party dynamics

- Every player is in a party; solo is a party of one.
- Invite and accept through the Character Sheet's Party tab.
- Leaders can kick; anyone can leave; ghost leaders auto-transfer after 7 days offline.
- Party members cannot damage each other, even on PVP-enabled servers; non-party players follow the server's PVP setting.
- The party at the moment of accept becomes the quest's accepters, locked in forever.
- All accepters share one quest; any one of them ticking the objective advances it for everyone.
- Be within 80 blocks of the phase-completer, or be online for a Collect Resources phase, to collect that phase's rewards.
- Missing a phase loses you its XP and item but keeps you on the quest for later phases.
- Each eligible accepter gets their own uniquely-rolled item at turn-in; no duplicates.
- XP scales with your level and the quest's difficulty.
- More nearby party members means tougher mobs, up to +6 monster levels.
