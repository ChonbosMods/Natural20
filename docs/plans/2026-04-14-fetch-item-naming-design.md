# Fetch Item Naming & Reward Flavor Redesign

**Date:** 2026-04-14
**Branch:** `fix/fetch-naming-and-continue-buttons`
**Status:** Design approved, ready to plan

## Problem

Fetch item pool labels are authored as single narrative fragments that serve triple duty: objective UI, inline dialogue, and inventory item names. All three surfaces are currently broken.

Example entry from `src/main/resources/quests/pools/keepsake_items.json`:

```json
{ "id": "keepsake_toy", "label": "a child's toy they could never throw away", "labelPlural": "old toys" }
```

Failure modes observed on every surface:

- **Objective card / waypoint:** "Fetch: a child's toy they could never throw away" is too long, and the leading article reads wrong mid-sentence.
- **Dialogue:** article-prefixed labels cause double-article bugs ("I lost a a child's toy..."), and the trailing "they" clause breaks when the subject isn't clear from context.
- **Inventory item:** a full sentence fragment as an item stack name is obnoxious.

Rewards have the same disease: strings like `"here is some gold and a mother's closest prayer"` fuse a concrete reward with an emotional flourish, and the flourish pretends to be an item.

## Design

### Section 1: Pool schema

Split each pool entry into **bare noun** + **short epithet** (≤ 4 words, optional). Articles are stripped from the pool; the composer owns grammar.

**New schema for `keepsake_items.json` (and any future fetch pools):**

```json
{
  "id": "keepsake_tonic",
  "noun": "tonic",
  "nounPlural": "tonics",
  "epithet": "brewed every morning",
  "fetchItemType": "quest_vial"
}
```

- `noun`: bare noun phrase, no article, no relative clause. Title-case-friendly.
- `nounPlural`: plural form, still bare.
- `epithet`: optional flavor clause (≤ 4 words). `null` is fine — not every item needs one.
- `fetchItemType`: unchanged, maps to the in-world item.

**Two template variables resolve from one pool roll:**

- `{quest_item}` → bare noun (e.g. `"child's toy"`) for objective UI, inventory names, and tight grammar slots in prose.
- `{quest_item_full}` → bare noun + epithet when present (e.g. `"child's toy kept since childhood"`), else just the noun. Used where flavor is welcome.

**Articles are template-authored.** Template sentences write `"Find the {quest_item}"` or `"She carried a {quest_item_full}"`. The sentence decides `a` / `the` / possessive; the data file never does.

**Surface outcomes:**

| Surface | Form | Example |
|---|---|---|
| Objective card | `Fetch: {quest_item}` | "Fetch: child's toy" |
| Waypoint | `Recover the {quest_item}` | "Recover the child's toy" |
| Inventory item | title-cased `{quest_item}` | "Child's Toy" |
| Dialogue | template-authored prose around `{quest_item_full}` | "I lost a child's toy, kept since childhood..." |

### Section 2: Reward flavor

Rewards split into two structurally separate slots so flavor never pretends to be an item.

```json
// quest template
{
  "rewardGold": 25,
  "rewardItem": null,                          // or a concrete item ref
  "rewardFlavor": "something of hers to keep"  // ≤ 5 words, optional
}
```

Dialogue composes the turn-in as two beats, not one clause:

1. `"Take this: 25 gold."` — concrete reward line.
2. `"And this. Something of hers to keep."` — optional flavor beat, only when `rewardFlavor` is set.

**Rule:** if it isn't a real item stack in the player's inventory, it lives in dialogue only. No invented pseudo-items.

### Section 3: Pool is authoritative (audit — follow-up scope)

Constraint: fetch items come **only** from the referenced pool. Nothing invents item names at runtime.

Deferred to a follow-up pass after the schema lands. Enforcement will include:

1. Template references to items must be by `{ pool, field }`, not literal strings. Missing pool entries fail loudly.
2. All `{quest_item}` / `{quest_item_full}` resolutions must trace back to a rolled pool entry.
3. Audit pass over `TopicGenerator` and any dialogue composer to find paths that currently interpolate freeform nouns and remove them.

The audit is filed as a follow-up because it's a separate investigation scope and shouldn't block the schema change.

## Scope of this change

**In scope:**
- New pool schema on `keepsake_items.json` (and any other fetch pools found during implementation).
- `{quest_item}` vs `{quest_item_full}` resolver in the quest/dialogue code.
- Reward schema split (`rewardGold` / `rewardItem` / `rewardFlavor`).
- Migration of any existing v2 templates that reference fetch items or rewards.
- Update of all objective UI / waypoint / inventory item naming paths to use `{quest_item}` (bare).

**Out of scope (follow-up):**
- Audit for invented item names outside the pool (Section 3).
- Continue-button fix (separate design; the branch name covers both but they're independent changes).

## Open questions

None for the design itself. Implementation plan to come next.
