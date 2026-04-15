# Phase 3 Batching Plan: Fetch Item Article Audit

Generated 2026-04-14 by Phase 3 Task 3.0 recon.

## Scope

This plan enumerates quest templates whose objectives include a fetch-phase
(`FETCH_ITEM` or `PEACEFUL_FETCH`) and which contain at least one `{quest_item}`
token in audit-relevant text fields. Phase 3.1..N subagents will consume one
batch each and audit article placement / determiner agreement around
`{quest_item}` substitutions.

**Source files (read-only):**

- `src/main/resources/quests/v2/index.json` : 238 total templates
- `src/main/resources/quests/mundane/index.json` : 21 total templates (all treated in-scope per Phase 3 plan)

**Fields scanned for `{quest_item}`:**

- `expositionText`
- `conflict1Text`, `conflict1TurnInText` : `conflict4Text`, `conflict4TurnInText`
- `resolutionText`
- `targetNpcOpener`, `targetNpcCloser`, `targetNpcOpener2`, `targetNpcCloser2`
- `skillCheck.passText`, `skillCheck.failText`

Templates with zero `{quest_item}` occurrences are excluded.

## Summary

- **In-scope templates:** 198 (177 v2 + 21 mundane)
- **Total `{quest_item}` occurrences:** 278 (248 v2 + 30 mundane)
- **Batch count:** 8 (7 v2 + 1 mundane)

## Batches

| batch id | file | situation(s) | template count | {quest_item} total | template ids |
|---|---|---|---|---|---|
| `v2_fetch_batch_01` | `src/main/resources/quests/v2/index.json` | deliverance, self_sacrifice_for_kindred, pursuit | 25 | 27 | deliverance_01, deliverance_02, deliverance_03, deliverance_04, deliverance_05, deliverance_07, deliverance_08, deliverance_09, deliverance_10, self_sacrifice_for_kindred_01, self_sacrifice_for_kindred_02, self_sacrifice_for_kindred_03, self_sacrifice_for_kindred_05, self_sacrifice_for_kindred_06, self_sacrifice_for_kindred_07, self_sacrifice_for_kindred_08, self_sacrifice_for_kindred_09, self_sacrifice_for_kindred_10, self_sacrifice_for_kindred_11, pursuit_01, pursuit_05, pursuit_06, pursuit_08, pursuit_09, pursuit_11 |
| `v2_fetch_batch_02` | `src/main/resources/quests/v2/index.json` | vengeance, daring_enterprise, ambition | 22 | 34 | vengeance_02, vengeance_03, vengeance_04, vengeance_05, vengeance_06, vengeance_08, vengeance_09, vengeance_10, daring_enterprise_01, daring_enterprise_02, daring_enterprise_03, daring_enterprise_05, daring_enterprise_07, daring_enterprise_08, daring_enterprise_09, daring_enterprise_11, ambition_01, ambition_03, ambition_04, ambition_07, ambition_09, ambition_11 |
| `v2_fetch_batch_03` | `src/main/resources/quests/v2/index.json` | obtaining, recovery, self_sacrifice_for_an_ideal, enigma | 28 | 36 | obtaining_01, obtaining_02, obtaining_03, obtaining_04, obtaining_05, obtaining_06, obtaining_07, obtaining_08, obtaining_09, obtaining_11, recovery_02, recovery_05, recovery_06, recovery_08, recovery_10, recovery_11, self_sacrifice_for_an_ideal_01, self_sacrifice_for_an_ideal_03, self_sacrifice_for_an_ideal_05, self_sacrifice_for_an_ideal_07, self_sacrifice_for_an_ideal_08, self_sacrifice_for_an_ideal_11, enigma_01, enigma_03, enigma_05, enigma_06, enigma_09, enigma_11 |
| `v2_fetch_batch_04` | `src/main/resources/quests/v2/index.json` | rivalry_of_kinsmen, mistaken_jealousy, erroneous_judgment | 29 | 38 | rivalry_of_kinsmen_01, rivalry_of_kinsmen_03, rivalry_of_kinsmen_04, rivalry_of_kinsmen_05, rivalry_of_kinsmen_06, rivalry_of_kinsmen_08, rivalry_of_kinsmen_10, mistaken_jealousy_01, mistaken_jealousy_02, mistaken_jealousy_03, mistaken_jealousy_04, mistaken_jealousy_05, mistaken_jealousy_06, mistaken_jealousy_07, mistaken_jealousy_08, mistaken_jealousy_09, mistaken_jealousy_10, mistaken_jealousy_11, erroneous_judgment_01, erroneous_judgment_02, erroneous_judgment_03, erroneous_judgment_04, erroneous_judgment_05, erroneous_judgment_06, erroneous_judgment_07, erroneous_judgment_08, erroneous_judgment_09, erroneous_judgment_10, erroneous_judgment_11 |
| `v2_fetch_batch_05` | `src/main/resources/quests/v2/index.json` | remorse, involuntary_crimes_of_love | 21 | 40 | remorse_01, remorse_02, remorse_03, remorse_04, remorse_05, remorse_06, remorse_07, remorse_08, remorse_09, remorse_10, remorse_11, involuntary_crimes_of_love_01, involuntary_crimes_of_love_02, involuntary_crimes_of_love_03, involuntary_crimes_of_love_04, involuntary_crimes_of_love_05, involuntary_crimes_of_love_06, involuntary_crimes_of_love_07, involuntary_crimes_of_love_08, involuntary_crimes_of_love_09, involuntary_crimes_of_love_10 |
| `v2_fetch_batch_06` | `src/main/resources/quests/v2/index.json` | obstacles_to_love, madness, necessity_of_sacrificing_loved_ones | 29 | 38 | obstacles_to_love_01, obstacles_to_love_02, obstacles_to_love_03, obstacles_to_love_04, obstacles_to_love_05, obstacles_to_love_06, obstacles_to_love_07, obstacles_to_love_08, obstacles_to_love_09, obstacles_to_love_10, madness_01, madness_02, madness_03, madness_04, madness_05, madness_06, madness_07, madness_08, madness_09, madness_10, madness_11, necessity_of_sacrificing_loved_ones_01, necessity_of_sacrificing_loved_ones_03, necessity_of_sacrificing_loved_ones_05, necessity_of_sacrificing_loved_ones_07, necessity_of_sacrificing_loved_ones_08, necessity_of_sacrificing_loved_ones_09, necessity_of_sacrificing_loved_ones_10, necessity_of_sacrificing_loved_ones_11 |
| `v2_fetch_batch_07` | `src/main/resources/quests/v2/index.json` | loss_of_loved_ones, conflict_with_fate, supplication, disaster | 23 | 35 | loss_of_loved_ones_01, loss_of_loved_ones_02, loss_of_loved_ones_03, loss_of_loved_ones_04, loss_of_loved_ones_05, loss_of_loved_ones_06, loss_of_loved_ones_07, loss_of_loved_ones_09, loss_of_loved_ones_10, loss_of_loved_ones_11, conflict_with_fate_03, conflict_with_fate_04, conflict_with_fate_06, conflict_with_fate_08, conflict_with_fate_10, supplication_05, supplication_07, supplication_10, supplication_11, disaster_05, disaster_07, disaster_09, disaster_11 |
| `mundane_fetch_batch_01` | `src/main/resources/quests/mundane/index.json` | mundane_borrowed, mundane_curiosity, mundane_errand, mundane_lost, mundane_pantry, mundane_pickup, mundane_restock | 21 | 30 | mundane_pantry_01, mundane_pantry_02, mundane_pantry_03, mundane_restock_01, mundane_restock_02, mundane_restock_03, mundane_errand_01, mundane_errand_02, mundane_errand_03, mundane_curiosity_01, mundane_curiosity_02, mundane_curiosity_03, mundane_lost_01, mundane_lost_02, mundane_lost_03, mundane_pickup_01, mundane_pickup_02, mundane_pickup_03, mundane_borrowed_01, mundane_borrowed_02, mundane_borrowed_03 |

## Batch sizing notes

- Target batch size: 20-30 templates.
- Templates from the same `situation` bucket are kept contiguous where possible.
- No single v2 situation exceeded 30 templates, so no `_a`/`_b` splits were required.
- The mundane file is a single batch of 21 templates: situations are small (3 each) and cohesive as one unit.

