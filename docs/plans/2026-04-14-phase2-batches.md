# Phase 2 Batching Plan (2026-04-14)

Phase 2 is a per-template authoring pass to condense `rewardFlavor` to ≤5 words and weave `{reward_flavor}` into resolution text. This plan groups 259 templates (238 in v2, 21 in mundane) into batches sized for single-subagent handling (20–30 templates per batch). Situations ≤20 templates are merged with adjacent small situations; situations >30 are split by template id.

## Situation Summary

| File | Situation | Count |
|------|-----------|-------|
| v2 | ambition | 11 |
| v2 | conflict_with_fate | 11 |
| v2 | daring_enterprise | 11 |
| v2 | deliverance | 11 |
| v2 | disaster | 11 |
| v2 | enigma | 11 |
| v2 | erroneous_judgment | 11 |
| v2 | involuntary_crimes_of_love | 10 |
| v2 | loss_of_loved_ones | 11 |
| v2 | madness | 11 |
| v2 | mistaken_jealousy | 11 |
| v2 | necessity_of_sacrificing_loved_ones | 11 |
| v2 | obstacles_to_love | 10 |
| v2 | obtaining | 11 |
| v2 | pursuit | 11 |
| v2 | recovery | 11 |
| v2 | remorse | 11 |
| v2 | rivalry_of_kinsmen | 11 |
| v2 | self_sacrifice_for_an_ideal | 11 |
| v2 | self_sacrifice_for_kindred | 11 |
| v2 | supplication | 11 |
| v2 | vengeance | 10 |
| mundane | mundane_borrowed | 3 |
| mundane | mundane_curiosity | 3 |
| mundane | mundane_errand | 3 |
| mundane | mundane_lost | 3 |
| mundane | mundane_pantry | 3 |
| mundane | mundane_pickup | 3 |
| mundane | mundane_restock | 3 |

**File totals:** v2 = 238 templates (22 situations), mundane = 21 templates (7 situations).

## Batch Plan

| Batch ID | File | Situation(s) | Count | Template IDs |
|----------|------|-------------|-------|--------------|
| v2_batch_01 | v2 | ambition | 11 | ambition_01–11 |
| v2_batch_02 | v2 | conflict_with_fate | 11 | conflict_with_fate_01–11 |
| v2_batch_03 | v2 | daring_enterprise | 11 | daring_enterprise_01–11 |
| v2_batch_04 | v2 | deliverance | 11 | deliverance_01–11 |
| v2_batch_05 | v2 | disaster | 11 | disaster_01–11 |
| v2_batch_06 | v2 | enigma | 11 | enigma_01–11 |
| v2_batch_07 | v2 | erroneous_judgment | 11 | erroneous_judgment_01–11 |
| v2_batch_08 | v2 | involuntary_crimes_of_love | 10 | involuntary_crimes_of_love_01–10 |
| v2_batch_09 | v2 | loss_of_loved_ones | 11 | loss_of_loved_ones_01–11 |
| v2_batch_10 | v2 | madness | 11 | madness_01–11 |
| v2_batch_11 | v2 | mistaken_jealousy | 11 | mistaken_jealousy_01–11 |
| v2_batch_12 | v2 | necessity_of_sacrificing_loved_ones | 11 | necessity_of_sacrificing_loved_ones_01–11 |
| v2_batch_13 | v2 | obstacles_to_love | 10 | obstacles_to_love_01–10 |
| v2_batch_14 | v2 | obtaining | 11 | obtaining_01–11 |
| v2_batch_15 | v2 | pursuit | 11 | pursuit_01–11 |
| v2_batch_16 | v2 | recovery | 11 | recovery_01–11 |
| v2_batch_17 | v2 | remorse | 11 | remorse_01–11 |
| v2_batch_18 | v2 | rivalry_of_kinsmen | 11 | rivalry_of_kinsmen_01–11 |
| v2_batch_19 | v2 | self_sacrifice_for_an_ideal | 11 | self_sacrifice_for_an_ideal_01–11 |
| v2_batch_20 | v2 | self_sacrifice_for_kindred | 11 | self_sacrifice_for_kindred_01–11 |
| v2_batch_21 | v2 | supplication | 11 | supplication_01–11 |
| v2_batch_22 | v2 | vengeance | 10 | vengeance_01–10 |
| mundane_batch_01 | mundane | mundane_borrowed, mundane_curiosity, mundane_errand, mundane_lost, mundane_pantry, mundane_pickup, mundane_restock | 21 | All 21 mundane templates |

## Dispatch Order (Smallest → Largest for Early Confidence)

1. **mundane_batch_01** (21 templates, 7 situations merged) — all mundane questions in one pass.
2. **v2_batch_08** (10 templates: involuntary_crimes_of_love_01–10)
3. **v2_batch_13** (10 templates: obstacles_to_love_01–10)
4. **v2_batch_22** (10 templates: vengeance_01–10)
5. **v2_batch_01** through **v2_batch_21** (11 templates each, alphabetically).

## Template Details by Batch

### mundane_batch_01

**Merged situations:** mundane_borrowed, mundane_curiosity, mundane_errand, mundane_lost, mundane_pantry, mundane_pickup, mundane_restock.

**Template IDs (21 total):**
- mundane_borrowed_01, mundane_borrowed_02, mundane_borrowed_03
- mundane_curiosity_01, mundane_curiosity_02, mundane_curiosity_03
- mundane_errand_01, mundane_errand_02, mundane_errand_03
- mundane_lost_01, mundane_lost_02, mundane_lost_03
- mundane_pantry_01, mundane_pantry_02, mundane_pantry_03
- mundane_pickup_01, mundane_pickup_02, mundane_pickup_03
- mundane_restock_01, mundane_restock_02, mundane_restock_03

### v2 Batches (11-template situations)

**v2_batch_01 (ambition):** ambition_01–11
**v2_batch_02 (conflict_with_fate):** conflict_with_fate_01–11
**v2_batch_03 (daring_enterprise):** daring_enterprise_01–11
**v2_batch_04 (deliverance):** deliverance_01–11
**v2_batch_05 (disaster):** disaster_01–11
**v2_batch_06 (enigma):** enigma_01–11
**v2_batch_07 (erroneous_judgment):** erroneous_judgment_01–11
**v2_batch_09 (loss_of_loved_ones):** loss_of_loved_ones_01–11
**v2_batch_10 (madness):** madness_01–11
**v2_batch_11 (mistaken_jealousy):** mistaken_jealousy_01–11
**v2_batch_12 (necessity_of_sacrificing_loved_ones):** necessity_of_sacrificing_loved_ones_01–11
**v2_batch_14 (obtaining):** obtaining_01–11
**v2_batch_15 (pursuit):** pursuit_01–11
**v2_batch_16 (recovery):** recovery_01–11
**v2_batch_17 (remorse):** remorse_01–11
**v2_batch_18 (rivalry_of_kinsmen):** rivalry_of_kinsmen_01–11
**v2_batch_19 (self_sacrifice_for_an_ideal):** self_sacrifice_for_an_ideal_01–11
**v2_batch_20 (self_sacrifice_for_kindred):** self_sacrifice_for_kindred_01–11
**v2_batch_21 (supplication):** supplication_01–11

### v2 Batches (10-template situations)

**v2_batch_08 (involuntary_crimes_of_love):** involuntary_crimes_of_love_01–10
**v2_batch_13 (obstacles_to_love):** obstacles_to_love_01–10
**v2_batch_22 (vengeance):** vengeance_01–10

---

**Total batches:** 23  
**Coverage:** 259 templates (238 v2 + 21 mundane)  
**Strategy notes:** All 22 v2 situations fit exactly 10–11 templates each (no merging or splitting needed). All 7 mundane situations are merged into one batch (21 total, coherent as "everyday life tasks").
