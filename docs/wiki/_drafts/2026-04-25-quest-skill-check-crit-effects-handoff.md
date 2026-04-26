# Quest Skill-Check Crit Effects: Wiki Author Handoff

**Date:** 2026-04-25
**Status:** Design locked, implementation pending. Wiki update can be drafted alongside.
**Audience:** Wiki author. This is an artifact for the wiki author, not a published page.

This document is self-contained. You should not need the design doc to do your job.

---

## What changed, in plain prose

The natural 20 and natural 1 outcomes on the **quest-accept skill check** now have real consequences beyond the existing "auto-pass / auto-fail." A nat20 hands the player an extra item before the quest even starts. A nat1 floors the player's disposition with the NPC, locks them out of that specific quest forever, and stops the quest from being accepted at all.

This only applies to **the one skill check that gates a quest's accept/deny step on its first phase**. Free-roam dialogue checks, turn-in checks, and any other d20 rolls keep their existing pass/fail behavior with no special crit consequences.

A normal fail (rolled 2-19, total under the DC) still accepts the quest, exactly as it does today. Only a natural 1 blocks acceptance.

---

## What players see

### On a natural 20 at quest accept

Same skill-check XP as a normal pass. Plus an extra item rolled from the same loot pool the quest's first-phase reward will draw from when that phase completes. The item lands in the player's inventory immediately.

The dialogue line shown after the diceroll page closes ends with one extra sentence:

> [NPC Name] hands you [Item Name].

The quest is accepted as normal.

### On a natural 1 at quest accept

No skill-check XP (same as a normal fail). Disposition with that NPC drops to the top of the **Hostile** bracket regardless of whatever it was before. The normal `-2` disposition penalty for a failed check does not also apply on a nat1 — the floor replaces it. After the floor is set, disposition can be rebuilt through the usual gain rules.

The specific quest the player just rolled on is added to a personal blacklist for this player and this NPC. The NPC will not offer that exact quest to that player again, ever. Other players can still roll on it normally. When the NPC's quest regenerates on its normal cycle, the fresh quest id is not on the blacklist and that player can be offered the new quest.

The dialogue line shown after the diceroll page closes ends with one extra sentence:

> [NPC Name] is insulted and no longer wants your help.

Crucially, the quest is **not** accepted on a nat1. This is the only outcome of the four (pass, fail, nat20, nat1) where the player walks away without the quest.

---

## Suggested edits to `quest-system.md`

### Outcome table (currently inside Skill Checks → Outcome)

The current table reads:

| Result | Effect on disposition | Other effect |
|--------|----------------------|--------------|
| Pass | +3 | Dialogue branches to the success line; quest flags you as having passed a check (reserved for future reward hooks) |
| Fail | -2 | Dialogue branches to the failure line |

This table is fine for non-quest skill checks. For the quest-accept skill check specifically, expand it to cover all four outcomes. Consider adding a second table immediately after, or replacing the existing one with a four-row version that notes the nat20 / nat1 rows only fire on the quest-accept check:

| Result | Disposition | Other effect |
|--------|------------|--------------|
| Pass (rolled 2-19, meets DC) | +3 | Dialogue branches to the success line. Quest accepted. |
| Fail (rolled 2-19, below DC) | -2 | Dialogue branches to the failure line. Quest still accepted. |
| Natural 20 (quest-accept check only) | +3 | Dialogue branches to the success line. NPC hands the player an extra item rolled from the quest's first-phase reward pool. Quest accepted. |
| Natural 1 (quest-accept check only) | Floored to top of Hostile bracket | Dialogue branches to the failure line. Quest is added to a personal blacklist for this player and this NPC, and is **not** accepted. |

### Crits paragraph (currently inside Skill Checks → The roll)

The current paragraph reads:

> **Crits.** A **natural 20** on the kept die is an automatic pass, full stop. A **natural 1** is an automatic fail. Neither can be overridden by modifiers.

Add a follow-up paragraph noting the new quest-accept consequences:

> **Crits at quest accept.** When the skill check that gates a quest's accept/deny step rolls a natural 20, the NPC hands the player an extra item on top of accepting the quest, rolled from the same reward pool the quest's first phase will draw from. When the same check rolls a natural 1, the player's disposition with that NPC is floored to the top of the Hostile bracket, the specific quest is permanently blacklisted from that player by that NPC, and the quest is not accepted. The blacklist is per-player and per-quest: other players can still roll on the same quest, and the NPC's next regenerated quest is fresh and offerable again.

### When skill checks fire (currently lists three placements)

The list currently includes "During a quest offer." Expand that bullet to cross-reference the new consequences:

> **During a quest offer**, as a persuasion or intimidation angle that opens an alternative phase path or a better reward tier. This is the only check where natural 20 and natural 1 carry the special consequences described in the Crits at quest accept paragraph.

### Summary section

The current summary reads "Natural 20 auto-passes; natural 1 auto-fails." Extend that line:

> Natural 20 auto-passes; natural 1 auto-fails. On the quest-accept skill check only, a natural 20 also grants an extra item from the first-phase reward pool, and a natural 1 also floors disposition to Hostile and permanently blacklists that quest from that player.

---

## What does not change

- The roll formula, modifier table, proficiency table, DC tier table, advantage / disadvantage rules, and procedural-vs-authored DC distinction. All unchanged.
- The Disposition section (gameplay bands, dialogue tone brackets, +/- table for normal play). The hostile floor on nat1 is a one-shot direct set; it does not change how disposition is normally gained or lost afterward.
- All other quest mechanics: phase rewards, party proximity, objective types, turn-in flow.
- Free-roam and turn-in skill checks keep nat20 = auto-pass, nat1 = auto-fail with no other consequences.

---

## Open question for the wiki

The current page describes skill checks generally, then notes their three common placements (quest offer / turn-in / free-roam). The new consequences are tied to one specific placement (quest offer / accept). The wiki author may prefer to:

- **Option A:** keep the new consequences inside the existing Skill Checks section and gate them with the "only on the quest-accept check" qualifier (the layout suggested above).
- **Option B:** move the new consequences into the existing Phase-to-phase flow section (as part of describing how a quest is offered and accepted) and leave the Skill Checks section general.

Either is fine; the design has no preference. Option A keeps all skill-check rules in one place; Option B keeps the quest-accept flow narrative tighter.
