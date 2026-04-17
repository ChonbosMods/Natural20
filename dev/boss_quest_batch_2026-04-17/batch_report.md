# Phase 2 Batch Report — Boss-Focused Dramatic Templates

**Date:** 2026-04-17
**Target:** ~57 KILL_BOSS dramatic templates across 9 anchor/secondary situations from Polti's 22.
**Delivered:** 57 templates merged to `src/main/resources/quests/v2/index.json` (238 → 295 total). 2 additional templates stashed in `deferred_humanoid_only/` pending a future `bossCategory` schema filter.

---

## Totals

| Metric | Count |
|---|---|
| Templates authored | 59 |
| Merged to v2/index.json | 57 |
| Stashed (humanoid-boss-only, deferred post-MVP) | 2 |
| v2 catalog total after merge | 295 |

## Per-situation delivery

| Situation | Plan target | Delivered to v2 | Stashed | IDs added |
|---|---:|---:|---:|---|
| vengeance | 5 | 5 | 0 | 11–15 |
| remorse | 5 | 4 | 1 | 12, 13, 15, 16 (14 stashed) |
| loss_of_loved_ones | 8 | 13 | 0 | 12–24 |
| deliverance | 8 | 8 | 0 | 12–19 |
| self_sacrifice_for_kindred | 5 | 5 | 0 | 12–16 |
| pursuit | 8 | 7 | 1 | 12–16, 18, 19 (17 stashed) |
| supplication | 5 | 5 | 0 | 12–16 |
| disaster | 5 | 5 | 0 | 12–16 |
| conflict_with_fate | 5 | 5 | 0 | 11–15 |
| **Total** | **~57** | **57** | **2** | |

`loss_of_loved_ones` exceeded its plan target (8) because the user directed Agent 2 to produce +10 in Batch 2 rather than +5 to give grief its proportional weight in the catalog.

## Per-agent breakdown

Six tonal-cluster agents, each dispatched twice (Batch 1 review sample + continuation):

| Agent | Cluster | Batch 1 | Batch 2 authored | Batch 2 stashed | Total merged |
|---|---|---:|---:|---:|---:|
| 1 | Cold Revenge (vengeance + remorse) | 3 | 7 | 1 (`remorse_14`) | 9 |
| 2 | Hot Grief (loss_of_loved_ones) | 3 | 10 | 0 | 13 |
| 3 | Protective (deliverance + self_sacrifice_for_kindred) | 3 | 10 | 0 | 13 |
| 4 | Proactive (pursuit) | 3 | 5 | 1 (`pursuit_17`) | 7 |
| 5 | Desperate (supplication + disaster) | 3 | 7 | 0 | 10 |
| 6 | Mysterious (conflict_with_fate) | 3 | 2 | 0 | 5 |
| **Total** | | **18** | **41** | **2** | **57** |

## Chain-shape distribution across 57 merged templates

| Shape | Count |
|---|---:|
| Single-phase KILL_BOSS | 18 |
| COLLECT_RESOURCES → KILL_BOSS | 16 |
| FETCH_ITEM → KILL_BOSS | 11 |
| TALK_TO_NPC → KILL_BOSS | 12 |

All 12 TALK setups include `targetNpcOpener` and `targetNpcCloser`. Setup-phase distribution exceeds the plan's "roughly mixed" guidance in every cluster.

## Stashed templates

### `deferred_humanoid_only/remorse_14.json` — "What I Sold"

Complicity arc where the NPC sold an item to the boss that is now being used against others. Requires a boss capable of commerce (taking coin, accepting a sale). No pool-agnostic rewrite preserves the confession arc.

### `deferred_humanoid_only/pursuit_17.json` — "I Know The Type"

Reformed-bandit NPC testifying that `{boss_name}` has the same shape as the criminal crew they used to run with. Entire voice is structural to humanoid-boss framing ("cut from the same cloth", "that kind doesn't stop"). No pool-agnostic rewrite survives.

**Un-stash condition:** either a `bossCategory: "humanoid"` field on objectives, a per-template `bossPool` override, or a `bossAffinity` filter on the KILL_BOSS objective. See `deferred_humanoid_only/README.md`.

## Validation outcomes

All 57 merged templates passed automated validation after three review rounds:

### Review round 1 (Batch 1)
- **Issue discovered:** stale authoring docs referenced `rewardText` and `{quest_reward}` while runtime uses `rewardFlavor` and `{reward_item}`/`{reward_flavor}`. Docs corrected across 7 files before Batch 2 dispatch.
- **Issue discovered:** Agents authored awkward `"Take {reward_item}, it's {reward_flavor}"` patterns that rendered the flavor as a description of the item. Worst case: `disaster_12` rendered the same phrase twice.

### Review round 2 (post-rule-tightening)
- **Structural bug discovered by user:** 11 of 18 Batch 1 templates had `{boss_name}` leaking into `expositionText` on two-phase chains where `objectives[0]` is COLLECT / FETCH / TALK. Exposition must belong to the setup objective; boss reveals in `conflict1Text`. Rule A was codified and all 6 agents redid their 3 templates each with the sharper spec. Rule B dropped `rewardFlavor` entirely from this batch.

### Review round 3 (prose polish)
- Variable-constraint leakage on `{quest_item}` pool-agnosticism: 7 templates rewritten to drop assumptions about item material / size / function.
- Narrative-mechanical coherence: `vengeance_12`'s implied pendant return fixed by removing the physical-return prose (no FETCH phase in chain).
- Gender assumption on questgiver in `targetNpcOpener`/`Closer`: `loss_of_loved_ones_13` rewritten to they/them.
- `{group_difficulty}` framing in resolutionText tightened where awkward.
- Ambiguous idioms replaced (`ran with me` → `stood with me`).

### Final automated audit
Across all 57 merged templates:
- ✅ 0 `rewardFlavor` fields present
- ✅ 0 `{reward_flavor}` references in any text
- ✅ 0 `{boss_name}` appearances in `expositionText` / `acceptText` / `declineText` / `expositionTurnInText` on two-phase templates
- ✅ 0 `{kill_count}` in KILL_BOSS-bound fields
- ✅ 0 colons or em-dashes in any dialogue string
- ✅ 0 R14 (corrective reframing) pattern hits via regex scan
- ✅ 100% of `resolutionText` fields reference `{reward_item}`
- ✅ All single-phase templates populate `expositionTurnInText`
- ✅ All two-phase templates include `conflict1Text` and `conflict1TurnInText`
- ✅ All single-phase templates omit `conflict1Text` / `conflict1TurnInText`

## External review sample

`REVIEW_BATCH2_EXTERNAL.md` — 14-template curated review set from Batch 2, covering all 6 clusters, all 4 chain shapes, and the high-risk surfaces (pronoun handling in TALK templates, `{quest_item}` pool-agnosticism in heavy-prose templates, chain-integrity on layered setups).

## References

- Design: `docs/plans/2026-04-17-kill-boss-objective-handoff.md`
- Phase 1 plumbing (shipped in same session): `ObjectiveType.KILL_BOSS` enum value added, `QuestGenerator` routes KILL_BOSS via `createPOIObjective` + `applyBossPreRoll`, `forceBossDirection` retired entirely from the codebase, 30 `mundane_bounty_*` templates migrated to `"type": "KILL_BOSS"`, `quest_template_schema.json` + `quest_situations.md` + `quest_variable_palette.md` + 9 per-situation authoring docs updated.
