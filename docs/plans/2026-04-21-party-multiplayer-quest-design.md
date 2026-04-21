# Natural 20 — Party & Multiplayer Quest Design

**Status:** Locked. Ready for implementation planning.
**Date:** 2026-04-21
**Scope:** Party system, multi-player quest ownership, /sheet UI additions, banner integrations, party-based monster scaling, server-side quest store architecture.
**Non-goals:** Party chat, persistent social graph, loot distribution rules beyond what's specified, XP-split math (Diablo-style full-XP-each is already the rule), quest cancellation.

---

## 1. Goals

- Enable cooperative questing without forcing sharing on anyone.
- Keep NPC social systems (disposition, smalltalk) strictly per-player.
- Add the minimum viable Party abstraction. No persistent social features beyond what quest ownership and scaling require.
- Make party difficulty scale via monster-level adjustment rather than arbitrary reward gates.
- Integrate with existing /sheet UI and the existing banner system.

## 2. Core model

Two orthogonal concepts govern multiplayer questing.

**Claimant.** Who owns a QuestInstance. Always a party (including the default party-of-1 every player inhabits). All phase progress is tracked at the claimant level. Every member of the claimant shares one set of objective counters.

**Accepters.** A snapshot of the claimant's membership frozen at accept time and stored on the QuestInstance. Accepters never mutates for any reason: not on leave, kick, disband, or rejoin. This is the bedrock rule that keeps social churn from corrupting quest state.

Every visibility, progress, and quest-reward question in this system reduces to: *"is this player on the accepters list for this quest?"*

All four objective types (KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC) share progress across the claimant. Phase scope is uniform. There is no per-phase world-scarcity flag, because the claimant owns the full quest from accept through completion.

## 3. Party system

### Every player is always in a party

On first connect, every player is automatically placed into a size-1 party with themselves as leader. There is no solo-vs-party branch in the data model. A "solo" player is a party of 1. All quest-ownership, reward, and scaling logic uses the same party-aware code paths whether party size is 1 or N.

### Formation and growth

A party grows when its leader (or any current member) sends an invite and the invitee accepts. On accept, the invitee's prior size-1 party is disposed and they join the inviter's party. Any quests they had personally accepted before joining remain theirs (accepters of those quests was [self] and is immutable). They are not added to accepters of the new party's pre-existing quests, and the new party's other members are not added to accepters of the joiner's pre-existing quests.

### Leadership

The leader has exactly two privileges:

- Kicking members
- Being displayed as leader in the /sheet Party view

Every other party action is open to all members.

### Leader persistence across logout

Leader stays leader when they log out. Leadership does not transfer on disconnect alone.

### Leader succession

On explicit leader leave, leadership transfers to the next member by join order. No manual transfer action in MVP.

### Ghost leader rule

If the current leader has been offline continuously for **N days** (default: 7, tuneable), leadership automatically transfers to the next-by-join-order online member on their next login. This prevents a permanently-absent leader from locking the party's ability to kick ghost accounts. The rule only fires for ghost leaders: the "no auto-transfer on disconnect" rule still holds for live play.

### Invitation

Any member can invite any non-partied player. One pending invite per invitee at a time: re-inviting replaces the prior pending invite rather than stacking. Invites to already-partied players (party size > 1) are refused server-authoritatively; the invite button can gray out client-side as a UX hint but the server is the source of truth.

### Leaving

Any member can leave at any time. They revert to a fresh size-1 party with themselves as leader. They remain on every accepters list they were on: in-flight quest participation is unaffected.

### Kicking

Leader-only. Quest-state effect is identical to voluntary leave: kicked player stays on accepters for in-flight quests and reverts to a size-1 party.

### Disband

No explicit disband action. The non-default party disposes automatically when the last non-leader member leaves and the leader's remaining state reverts to the default size-1 party.

### Persistence

Party state persists across server restarts. Offline members remain in the party until they explicitly leave or are kicked. Ghost members (UUIDs that never return) are an acceptable state: any member (leader, per §3 leadership) can kick them, and the ghost leader rule above keeps this unblocked.

### Size

No cap. Parties can grow to any size. Monster-level scaling (§6) is the primary balancing mechanism for large parties. Social and UI friction is expected to produce organic natural limits rather than hard caps.

## 4. Quest ownership and progression

### Accept

When a party member accepts a quest from an NPC, the quest is claimed by that party. Accepters is snapshotted from the current party member list at that instant. The current NPC-side rule that `preGeneratedQuest` is consumed on accept (blocking other players from receiving the same instance) is preserved. The party is the first-and-only claimant.

### Progress

All objectives tick on any accepter's qualifying action, regardless of phase or objective type. Phase transitions advance globally for the claimant.

### No cancellation

Natural 20 quests cannot be cancelled or abandoned by design. Completion is the only exit path. This removes the abandon-griefing surface entirely and is listed in known shortcomings (§15).

### Turn-in

Any accepter can trigger turn-in by interacting with the turn-in NPC. Turn-in completes the quest for the full accepters list in one event:

- All accepters receive the full quest-reward bundle (XP, items, affix loot rolls, whatever the template declares).
- All accepters' quest logs mark the quest Completed and the record moves to that player's `Nat20PlayerData.completedQuests`.
- All accepters see a completion banner on next online session if they were offline at turn-in.

Distance from the turn-in NPC does not affect eligibility for the quest-reward bundle. Everyone on accepters gets the bundle. Offline accepters receive their reward bundle on next login, delivered automatically with no mail system dependency.

## 5. Reward model

Two distinct reward layers, each with its own distribution rule.

### Quest-reward bundle (turn-in)

The reward package declared by the quest template: base XP, named items, affix loot rolls. Distributed at turn-in to **all accepters regardless of proximity or online status**. This is the fixed reward for completing the narrative arc of the quest.

### In-quest incidental rewards (per-objective)

Combat XP from killed mobs, dropped loot, and any per-objective rewards that exist outside the quest bundle. Distributed via a **proximity check at the moment the objective event fires**, using the rally-affix-equivalent radius.

The rule: when an objective event happens (a kill, a resource gather, an item fetch, a dialogue exchange), any party accepter within rally-affix-equivalent radius of the triggering player receives the incidental reward for that event. Accepters outside the radius or offline receive nothing from that particular event.

This layer behaves like standard combat-reward distribution. There is no special quest-reward handling for mid-quest events. The proximity rule is inherited from existing nat20 combat and loot code paths.

### What this replaces

No prior "Completed (absent)" or credit-only classification exists in the new model. There is no difference in *quest completion status* based on proximity: every accepter completes the quest on turn-in. Proximity only affects the drip of in-quest XP and drops during phase progression.

## 6. Monster level scaling

Party size increases the monster level (mlvl) of nat20 mob groups. mlvl scaling considers all online party members in proximity of an active quest tied to the spawn.

### Formula (starting point, curve TBD)

`mlvl_effective = mlvl_base + f(nearby_party_members_count - 1)`

Where `nearby_party_members_count` is the count of online party members within some radius of the spawn trigger, inclusive of the triggering player. Solo (party of 1) = no change. The exact shape of `f` is a TODO. Linear `f(n) = n` is the prototype; a diminishing-returns curve or soft cap is expected after playtesting large parties.

### Why nearby-count and not total-party-count

Scaling off total party size punishes solo exploration when partymates are elsewhere on the map. Nearby-count ties difficulty to who's actually present for the encounter, mirrors the in-quest incidental-reward proximity rule, and stays consistent with rally-style aura logic used elsewhere in the codebase.

### Application surface

Applies to all nat20 mob groups spawned via the existing `mob_themes.json` zone-scale theming system (`Nat20MobGroupSpawner`, POI triggers, ambient spawns). Scaling is resolved **at spawn time**: once a group is placed in the world, its mlvl is fixed for the lifetime of those mobs. No dynamic rescaling as party members arrive or leave during an encounter.

### Interaction with HP/DMG scaling

Combines with the existing mlvl-anchored HP/DMG scaling (HP=40, DMG=12 at mlvl 1) naturally. A party of 3 triggering a mlvl-5 group spawn in-zone sees mlvl-7 mobs (assuming linear `f`) with the corresponding HP/DMG curves. No separate HP/DMG multiplier is introduced: the existing mlvl curves do the work.

### Runaway scaling caveat

With no party cap, unbounded mlvl addition can spiral at large party sizes. The curve `f` must eventually diminish or soft-cap. An auxiliary lever: increase champion and boss spawn rates in lieu of further mlvl addition at high party sizes. Exact tuning is a playtest-pass concern and tracked as TODO.

## 7. Mid-join visibility

New members joining mid-adventure do not see any quest accepted before they joined. They are not on that quest's accepters list, and the quest is invisible to them in the Quest Log and in world markers.

The Party view on /sheet shows a summary of party-owned active quests (name + current phase) regardless of accepter status. This is a meta-view of what the party is doing, not a quest-log substitute. Detailed quest info and world markers remain gated by accepters.

Returning former members who were on the original accepters see the quest normally because they remain on accepters. This follows automatically from the accepters-is-frozen rule and needs no special-case code.

## 8. NPC interaction

**Disposition and smalltalk remain per-player.** Party formation never merges social state with NPCs. Each member builds their own rapport with any given NPC.

**Quest-offer dialogue** can be triggered by any member. Acceptance binds the whole party.

**Turn-in dialogue** plays for the speaking member only. Other accepters see their completion and reward delivery silently on their quest log.

**Dialogue v3 tonal posture** is driven by the speaker's individual disposition and valence, unaffected by party context.

## 9. /sheet UI additions

### New Party subview

A Party tab in /sheet, peer to Character Sheet and Quest Log. Displays:

- Current party members: name, level, online/offline, leader marker.
- Invite button (all members).
- Kick button per non-self, non-leader member (leader only; hidden for non-leaders).
- Leave button (all members).
- Pending invites: sent invites visible to all members, received invites visible only to self.
- Summary of party-owned active quests (name + current phase only).

### Quest Log updates

- "Accepted by: [PlayerName]" line on every quest, where `PlayerName` is the account name of the player who clicked accept. Historical and immutable: does not update on leader change or accepter churn.
- Party-owned quests are visually marked (icon or small label distinguishing them from default-size-1 solo quests).
- Non-visible quests (mid-join or non-accepter case) simply do not appear in the log.
- Party-view quest summary visibility for non-accepter members: show summary in the Party tab, but do not show detail or markers in the Quest Log.

## 10. UI banners

All banners route through the existing banner system (already wired in codebase).

### Party Invite banner

Triggers when a player receives a party invite. Displays inviter name and accept/decline actions (or a prompt to open /sheet to respond, depending on final banner capability). Dismissible. Queues if the player is offline; shown on next login.

### Quest Phase Transition banner

Triggers to all currently-online accepters when a quest advances a phase. Displays quest name, new phase name, and objective summary. Purely informational, non-interactive. Offline accepters do not receive a retroactive banner: the new phase is simply highlighted in their quest log on next login.

### Quest Completion banner

When turn-in fires, all online accepters get a completion banner showing the quest name and a summary of rewards received. Offline accepters see it on next login. Reward delivery itself is silent on top of the banner. A second banner variant is planned for accepters who were **not present (outside proximity or offline) when the turn-in fired**, distinguishing "you completed this with your party" from "your party completed this while you were away, and here are your rewards." Exact copy TBD.

## 11. Storage architecture

### Decision: Nat20PartyQuestStore

A server-global, quest-keyed store owns all active QuestInstances. Every query, mutation, and persistence operation goes through it.

```
Nat20PartyQuestStore
  primary:   Map<QuestInstanceId, QuestInstance>   // authoritative
  index:     Map<PlayerUuid, Set<QuestInstanceId>> // always consistent with primary
  persist:   aggregate JSON at devserver/nat20/party_quests.json
```

`QuestInstance` gains `List<UUID> accepters` and `QuestInstanceId id` (generated at accept time) as first-class fields.

### Query path

`QuestStateManager.getActiveQuests(player)` returns all QuestInstances where `accepters.contains(player.uuid)`, resolved via the secondary index (O(1) lookup + O(k) resolve where k is that player's active quest count). The method is a pure read into the store; it no longer touches `Nat20PlayerData.questFlags`.

### Mutation path

All mutations flow through store methods (`advancePhase`, `incrementObjective`, `turnIn`, etc.). These are the only code paths that may modify a QuestInstance. Returned QuestInstance objects are treated as read-only snapshots by callers.

### Persistence

Aggregate JSON, written on each mutation. Quest volume is expected to stay small (hundreds, not tens of thousands) so aggregate writes are cheap enough for MVP. Lifecycle mirrors `Nat20MobGroupRegistry`: load on plugin start, save on change, reconcile on world load.

### Migration

One-shot migration runs on first PlayerConnect after the feature ships. If `Nat20PlayerData.questFlags` contains legacy QuestInstance entries, each is re-created in the store with `accepters=[thatPlayer]` and a fresh `QuestInstanceId`, then the player's legacy field is cleared. Idempotent: re-running on an already-migrated player is a no-op.

### Completion

Completion remains per-player. On turn-in, the store iterates accepters, writes a `CompletedQuestRecord` to each player's `Nat20PlayerData.completedQuests`, and removes the QuestInstance from the store. Completed history is never consulted by the store.

### Why not PartyClaim (quest-on-the-party-object)

Rejected. The bedrock rule "accepters is frozen and outlives the live party" is incompatible with party-object ownership:

- A player who leaves the party still needs to see the quest. If the quest lives on the party object they cannot.
- If the party disbands entirely (last member leaves), the quest has to migrate somewhere or be orphaned.
- Two accepters in different future parties cannot share one record if the record is tied to a live party.

A quest-keyed store decouples quest persistence from party membership churn, which is exactly what §2 demands.

## 12. Abuse surface (handled)

| Vector | Handling |
|---|---|
| Invite spam | One pending invite per invitee; re-invite overwrites |
| Kick-to-steal-credit | Blocked by accepters-is-frozen: kicked player keeps all quest standings |
| Quest-state griefing via abandon | N/A: quests cannot be cancelled |
| XP leeching by mid-quest passive party members | Blocked by in-quest proximity rule: distant AFK members get no incidental XP |
| Quest-bundle free-loading | Accepted design tradeoff: turn-in gives everyone the bundle by design; commitment is made at accept time, not by proximity at turn-in |
| Ghost leader (permanent logout) | Ghost leader rule (§3) auto-transfers leadership after N days offline |
| Spoofed-accept-then-disconnect | Gets the quest-reward bundle on return (by design); no incidental XP from mid-quest events while offline |
| Runaway mlvl in mega-parties | Mitigated by `f` diminishing-returns curve (§6) and increasing champion/boss spawn rates in lieu of mlvl at high party levels |

## 13. Decisions locked

- Max party size: **no cap**
- Every player is always in a party: **yes, default size-1 on first connect**
- Leader definition: **first inviter when a party grows past 1**
- Leader persistence across logout: **yes, stays leader**
- Leader succession on explicit leave: **next by join order, automatic**
- Ghost leader auto-transfer: **after N days offline (default 7, tuneable)**
- Accepters: **frozen at accept time, immutable for any reason**
- Pre-existing quests on join: **stay with individual accepter, not re-shared to new party**
- Mid-join quest visibility: **invisible unless already on accepters**
- Turn-in: **any accepter can trigger; all accepters get full quest-reward bundle regardless of proximity or online status**
- In-quest incidental reward distribution: **proximity-gated using rally-affix-equivalent radius**
- Quest cancellation: **not supported (known shortcoming)**
- All objective types share progress across claimant: **yes, no phase-scope flag**
- "Accepted by" line: **historical, immutable, account name of the player who clicked accept**
- Storage: **Nat20PartyQuestStore (quest-keyed), per-player completedQuests**
- Quest completion recording: **per-player `Nat20PlayerData.completedQuests`**
- Monster level scaling: **`mlvl += f(nearby_party_members_count - 1)`, applied at spawn time, curve `f` TBD**

## 14. Open items

- **mlvl curve `f`.** Design the curve. Linear to start; diminishing-returns shape after playtest.
- **Ghost leader N.** Default 7 days. Confirm via playtest.
- **Rally-affix-equivalent radius.** Reuse the existing combat-XP/loot distribution radius if it exists; otherwise introduce a single constant shared between rally, quest incidentals, and monster-scaling proximity.
- **"Absent at turn-in" completion banner.** Copy and visual TBD.
- **Spawn-time triggers.** Enumerate every path through `Nat20MobGroupSpawner` (POI entry, ambient roll, quest group) and confirm each passes the nearby-count snapshot through the mlvl formula.

## 15. Known shortcomings

- **No quest cancellation.** A player who accepts a quest arc they no longer want has no clean exit. The quest sits in the log until completed. Deliberate tradeoff for griefing resistance; revisit only if it causes sustained player frustration.
- **Quest-bundle free-loading.** Any accepter who contributes zero to progress still collects the full bundle at turn-in. Deliberate: commitment is at accept time.
- **Ghost-leader rule takes N days to fire.** A malicious leader who logs in briefly every few days can indefinitely prevent kicks. Out of scope for MVP.
