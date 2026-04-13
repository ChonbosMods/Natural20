# Affix Name Audit: Batch Plan

Reference prompt: `~/Downloads/nat20_affix_naming_improvement_v3.md`
Worktree: `.worktrees/feat-affix-naming-batch`
Name pool dir: `src/main/resources/loot/names/`

## Batch Overview

| Batch | Focus | Families | Count | Status |
|-------|-------|----------|-------|--------|
| 0 | Global Census | ALL 33 | 33 | **DONE** |
| 1 | Elemental Damage (Weapons) | flat + dot families | 8 | **DONE** (149 names replaced) |
| 2 | Elemental Weakness (Weapons) | weakness families | 4 | **DONE** (~134 names replaced) |
| 3 | Debuff/Utility (Weapons) | crush, backstab, fear, hex, mockery, rally | 6 | TODO |
| 4 | Sustain (Weapons) + Elemental Resist (Armor) | leech + elem res | 6 | TODO |
| 5 | Physical Defense (Armor) | phys res, thorns, guardbreak, flinch, resilience | 5 | TODO |
| 6 | Mobility/Utility (Armor) | evasion, waterbreath, gallant, lightfoot | 4 | TODO |

Total: 33 families across 6 correction batches + 1 census batch.

---

## Batch 0: Global Census (read-only, do first)

**Goal:** Read all 33 files. Produce cross-family reference data that every subsequent batch needs.

**Output file:** `docs/affix-name-audit-census.md`

**What to produce:**
1. Compound SUFFIX stem usage counts (-Wrought, -Forged, -Born, -Crowned, etc.)
   - Which families use each stem, how many times
   - Which 2 uses to KEEP for any overused stem
2. Compound PREFIX stem usage counts (Null-, Pox-, Entropy-, Vigil-, etc.)
   - Same: which families, which single use to keep
3. Banned stem scan (Sovereign, Primordial, etc.): list every occurrence
4. Near-duplicate names across families
5. Cross-family mechanic confusion flags (fire words outside fire families, etc.)

**No corrections in this batch.** Just the census data.

---

## Batch 1: Elemental Damage Weapons (8 families)

Files:
- `wpn_fire_flat.json`
- `wpn_fire_dot.json`
- `wpn_frost_flat.json`
- `wpn_frost_dot.json`
- `wpn_poison_flat.json`
- `wpn_poison_dot.json`
- `wpn_void_flat.json`
- `wpn_void_dot.json`

Key rules to watch:
- Elemental words must stay in their element's lane
- flat vs. dot pairs in same element must not overlap in feel
- Compound stems: reference census, replace overused ones
- Tier escalation within each family

---

## Batch 2: Elemental Weakness Weapons (4 families)

Files:
- `wpn_fire_weak.json`
- `wpn_frost_weak.json`
- `wpn_poison_weak.json`
- `wpn_void_weak.json`

Key rules to watch:
- **Weakness Family Guidance**: describe vulnerability state, NOT defense-stripping process
- No body-state words (naked, soft, blind, raw)
- No mechanic confusion with the element's damage families (batch 1)

---

## Batch 3: Debuff/Utility Weapons (6 families)

Files:
- `wpn_crush.json`
- `wpn_backstab.json`
- `wpn_fear.json`
- `wpn_hex.json`
- `wpn_mockery.json`
- `wpn_rally.json`

Key rules to watch:
- "Bane" implies destroyer-of-X: wrong for families that CAUSE X
- Fear: names should evoke dread, not physical toughness
- Rally: buff names, should sound empowering
- Mockery: CHA-based, witty/cutting tone
- No tooltip language ("Quick Dodge", "Shaking Off")

---

## Batch 4: Sustain Weapons + Elemental Resist Armor (6 families)

Files:
- `wpn_lifeleech.json`
- `wpn_manaleech.json`
- `arm_fire_res.json`
- `arm_frost_res.json`
- `arm_void_res.json`
- `arm_poison_res.json`

Key rules to watch:
- Leech suffixes: must sound like a BENEFIT, not a curse on the wielder
- Suffix Tone Rule: no "Minor Draining", "Pale Leeching", etc.
- Elemental resist armor: element words stay in lane
- No tooltip language for resist names

---

## Batch 5: Physical Defense Armor (5 families)

Files:
- `arm_phys_res.json`
- `arm_thorns.json`
- `arm_guardbreak.json`
- `arm_flinch.json`
- `arm_resilience.json`

Key rules to watch:
- Physical toughness words ONLY in phys res / flinch / guard break
- Thorns: should evoke retaliation, not defense
- Resilience: broad "toughness" concept, distinct from flinch/guardbreak
- Suffix tone: these are suffixes, must sound like benefits

---

## Batch 6: Mobility/Utility Armor (4 families)

Files:
- `arm_evasion.json`
- `arm_waterbreath.json`
- `arm_gallant.json`
- `arm_lightfoot.json`

Key rules to watch:
- Creature Name Test: creatures must be KNOWN for the trait
- Evasion: agility/dodge creatures
- Water breathing: aquatic creatures
- Gallant: courage/bravery
- Light foot: stealth/silence
- No tooltip language ("Quick Dodge", "Brook Wading")

---

## Session Start Prompt

Paste this at the start of each correction batch session:

```
I'm auditing affix name pools for Natural 20.
Worktree: .worktrees/feat-affix-naming-batch
Rules: ~/Downloads/nat20_affix_naming_improvement_v3.md
Census: .worktrees/feat-affix-naming-batch/docs/affix-name-audit-census.md
Batch plan: .worktrees/feat-affix-naming-batch/docs/affix-name-audit-batches.md

Run batch N.
```

Replace N with the batch number. I'll read the rules, census, and batch plan, then process the listed families.

---

## After All Batches

- Re-read census to verify no compound stem > 2 uses remain
- Spot-check 10 random names with base items
- Verify no banned stems survived
- Update batch statuses to DONE
