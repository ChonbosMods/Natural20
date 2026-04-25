# Gear-Pool Filter + Category Weights

**Date:** 2026-04-25
**Branch:** TBD (likely new branch `feat/gear-pool-filter`)
**Status:** Design locked, implementation pending
**Amends:** [2026-04-17 mob loot pool redesign](2026-04-17-mob-loot-pool-redesign-design.md). Specifically reverses the "Category mix: fully random per-drop slot" decision and tunes `NATIVE_LIST_BIAS` from 0.12 to 0.05.

## Problem

Three observations on shipped behavior, plus one latent bug:

1. **Tools drop too rarely.** With uniform per-slot picks from the global gear pool, tools land in 6-10% of drops (and only 9 tool entries qualify at ilvl 45). Players don't see affix-tools often enough to feel like they're a real reward channel.
2. **Melee dominates at endgame.** At ilvl 35-45, melee weapons are ~45% of the global pool while armor shrinks to ~25%. Combined with #1, the drop feel is "another sword, again."
3. **Native-list bias (12%) floods identical drops.** When a mob's native `ItemDropList` has 1-2 entries (e.g., Goblin → `Iron_Sword`), 12% of that mob's drops are literally the same Iron Sword. Across 100 kills you stack ~12 identical swords.
4. **Latent leak: 60 gear items match no material token** in `Nat20ItemTierResolver.MATERIAL_ILVL`, so they slip through `allowsIlvl` (the `return true` fallback fires) and drop at every ilvl from 1-45. Examples: Tribal, Praetorian, Trork themed sets, Zombie limb clubs, endgame flavor weapons (Void, Spectral, Flame), Crystal Staffs.

## Goals

1. **Category weighting**: lift tools to ~19% of drops, bring melee down from 45% to ~28%.
2. **Native bias retune**: 12% → 5%.
3. **Plug ilvl-gating leaks**: 60 currently-uncapped items get explicit tiers via tokens or per-item overrides.
4. **Future-proof gear-pool filter**: data-driven (JSON), supports blocklist + allowlist, ready for items registered by other plugins.
5. **Apply to both pickers**: mob loot and chest loot share the category-weighted picker.

## Locked Decisions

### Category weights (global pool only)

```
melee_weapon  : 30
armor         : 30
ranged_weapon : 20
tool          : 20
```

Per-pick algorithm:

```
roll category from {30,30,20,20} weighted distribution
if bucket[category] empty: renormalize over non-empty buckets and re-roll
pick uniformly within bucket
```

Empty-bucket fallback should be rare (≥9 entries per category at every ilvl in the audited pool) but is defensive.

### Native-list bias

`NATIVE_LIST_BIAS = 0.05f` (down from 0.12). Native pick logic is otherwise unchanged: 5% of slots draw uniformly from the mob's filtered native pool; the remaining 95% go through the category-weighted global pick. Empty-native fallthrough still applies.

### Combined per-slot distribution

| Source | Mob drops | Chest drops |
|---|---|---|
| Native | 5% | n/a |
| Melee | 28.5% | 30% |
| Armor | 28.5% | 30% |
| Ranged | 19% | 20% |
| Tool | 19% | 20% |

Compared to current ilvl-45 endgame (45 / 25 / 24 / 6 + 12% native): tools rise from 6% to 19%, melee falls from 45% to 28.5%.

### Both pickers share the helper

`Nat20MobLootPool.pick()` and `Nat20ChestLootPicker.pickLoot()` route through one shared category-weighted picker. Chests have no native-bias carve-out (no role context).

### Data-driven gear filter

Move `MATERIAL_ILVL` (and add new structures) to a single JSON resource at:

`src/main/resources/loot/gear_filter.json`

Schema:

```json
{
  "blocklist": [
    "Armor_Trooper_NPC_Only",
    "Weapon_Longsword_Praetorian_NPC"
  ],
  "allowlist": {
    "SomeMod:Weapon_Plasma_Sword": {
      "ilvl": [22, 38],
      "category": "ranged_weapon"
    }
  },
  "tier_tokens": {
    "iron":       [8, 26],
    "mithril":    [22, 38],
    "tribal":     [3, 18],
    "praetorian": [22, 35]
  },
  "tier_item_overrides": {
    "Weapon_Sword_Cutlass":   [8, 22],
    "Tool_Shears_Basic":      [1, 14],
    "Weapon_Assault_Rifle":   [22, 45]
  }
}
```

**Reserved field (deferred)**: `"quantity": [min, max]` may attach to entries in either `allowlist` or `tier_item_overrides` — schema reserved for future stack-quantity affix drops (e.g., a stack of 20 `Weapon_Bomb_Crude` with one shared affix). Not yet wired through `Nat20LootPipeline`. See "Coming Soon" note in `docs/wiki/offensive-affixes.md`.

`tier_tokens` is the **full migration** of the current Java `MATERIAL_ILVL` table plus the new thematic-group tokens (Hybrid C scheme).

### Lookup precedence

For any itemId, the gear filter resolves in this order. First match wins:

1. **Blocklist** → reject (item is not gear, no tier).
2. **Per-item override** (`tier_item_overrides[itemId]`) → use that ilvl band; category from prefix inference.
3. **Allowlist** (`allowlist[itemId]`) → use declared ilvl + category. Allowlist is the **only** path that supplies an explicit category — for mod-registered items whose namespace doesn't match Nat20 prefixes (`Armor_` / `Tool_` / `Weapon_`). Every other path falls back to `Nat20ItemTierResolver.inferCategory()` for prefix-based category lookup.
4. **Token match** (longest substring of `itemId.toLowerCase()` in `tier_tokens`) → use that band; category from prefix inference.
5. **No match** → reject.

Step 5 is **stricter than today**: items that don't match anything are excluded, not allowed. Closes the 60-item leak. Adding a new item now requires explicit registration via tokens, overrides, or the allowlist.

### Hybrid C tier scheme: starting token assignments

These cover the leak audit's natural groupings. Numbers are starting points; tune in implementation by playtest.

| Token | ilvl band | Items hit | Rationale |
|---|---|---|---|
| `tribal`     | [3, 18]  | 10 | Goblin/early-game themed gear, primitive feel |
| `praetorian` | [22, 35] | 3  | Mid-tier Roman-themed faction set |
| `trork`      | [16, 28] | 5  | Mid-tier orc gear (Trork mob faction) |
| `kweebec`    | [16, 28] | 2  | Forest-zone armor |
| `trooper`    | [22, 38] | 3  | Faction armor set |
| `scarab`     | [22, 38] | 2  | Desert-themed weapons |
| `zombie`     | [3, 14]  | 6  | Zombie limb clubs (low-tier flavor) |
| `crystal`    | [22, 40] | 5  | Magic crystal staffs |
| `spectral`   | [28, 45] | 1  | Endgame flavor longsword |
| `void`       | [28, 45] | 2  | Endgame flavor weapons |
| `flame`      | [22, 38] | 2  | Mid-high flavor (excluding Crystal_Flame, which matches `crystal` first) |

### Per-item overrides

For true one-offs that don't fit any thematic family. Starting list:

| Item | ilvl | Why |
|---|---|---|
| `Weapon_Sword_Cutlass` | [8, 22] | Pirate sword, mid-tier flavor |
| `Tool_Shears_Basic` | [1, 14] | Basic-tier tool, no material token |
| `Weapon_Assault_Rifle` | [22, 45] | High-tier modern weapon |
| `Weapon_Spear_Leaf` | [3, 18] | Early-tier spear |
| `Weapon_Longsword_Katana` | [22, 38] | Mid-high flavor |
| `Weapon_Longsword_Flame` | [22, 38] | Mid-high flavor |
| `Weapon_Gun_Blunderbuss` | [16, 32] | Mid-tier firearm |
| `Weapon_Staff_Bo_Bamboo` / `Weapon_Staff_Cane` / `Weapon_Staff_Onion` / `Weapon_Staff_Wizard` | varies | One-off staffs |
| `Weapon_Shortbow_*` (Combat, Bomb, Pull, Ricochet, Vampire) | varies | Themed shortbow flavors |
### Initial blocklist seeds

These items ship blocklisted from day one (drop nowhere). Reasoning is recorded with each so future authors know whether to lift the block:

| Item | Why blocklisted |
|---|---|
| `Weapon_Longsword_Praetorian_NPC` | NPC-suffixed combat-only variant |
| `Weapon_Wand_Root` | Excluded from player loot pool by design |
| `Weapon_Spellbook_Demon` | Excluded from player loot pool by design |
| `Weapon_Spellbook_Fire` | Excluded from player loot pool by design |
| `Weapon_Spellbook_Grimoire_Brown` | Excluded from player loot pool by design |
| `Weapon_Spellbook_Grimoire_Purple` | Excluded from player loot pool by design |
| `Weapon_Spellbook_Rekindle_Embers` | Excluded from player loot pool by design |

Implementation phase will finalize the full table.

### Unused tokens removed

`silver`, `bark`, `flint` match zero items in current vanilla.json. Removed during migration.

## Algorithm

### Mob drop pick (per slot)

```
pick(rng):
    if !native.isEmpty() and rng.nextFloat() < 0.05:
        return native.random()                        # 5% native
    return globalCategoryWeightedPick(rng)            # 95% category-weighted
```

### Chest drop pick

```
pickLoot(ilvl, rng):
    return globalCategoryWeightedPick(rng)            # 100% category-weighted
```

### Shared helper

```
globalCategoryWeightedPick(rng):
    weights = {mw:30, armor:30, rw:20, tool:20}
    while true:
        bucket = rollCategory(rng, weights)
        if buckets[bucket].nonEmpty(): return buckets[bucket].random()
        weights = removeAndRenormalize(weights, bucket)
        if weights empty: return null
```

`Nat20MobLootPool.build()` precomputes the four bucket lists at construction time, filtered by ilvl + the new gear-filter (block / override / allow / token / reject).

### Gear filter resolution

```
resolveTier(itemId):
    if blocklist.contains(itemId): return null
    if tier_item_overrides.contains(itemId): return (override.band, prefix.category)
    if allowlist.contains(itemId): return (allow.band, allow.category)
    longest = longestTokenMatch(itemId.toLowerCase(), tier_tokens)
    if longest != null: return (longest.band, prefix.category)
    return null
```

## File Structure

| Action | File | Purpose |
|---|---|---|
| Create | `src/main/resources/loot/gear_filter.json` | Single source of truth for tier bands + lists |
| Create | `loot/mob/Nat20GearFilter.java` | Loads gear_filter.json once at startup; exposes `resolveTier(itemId)`, `isAllowed(itemId, ilvl)` |
| Modify | `loot/mob/Nat20ItemTierResolver.java` | Strip `MATERIAL_ILVL`; delegate to `Nat20GearFilter`. Keep `inferCategory()` (prefix-based) for non-allowlist items |
| Modify | `loot/mob/Nat20MobLootPool.java` | Bucket pool by category at build; weight pick; `NATIVE_LIST_BIAS` = 0.05f |
| Modify | `loot/chest/Nat20ChestLootPicker.java` | Use shared category-weighted picker |
| Create | `loot/CategoryWeightedPicker.java` | Static helper: weighted pick over `Map<Category, List<String>>` with empty-bucket renormalization |

## Edge Cases

1. **Empty category bucket at extreme ilvl**: renormalize over non-empty categories; re-roll. If all four empty, return null (caller treats as empty pool, same as today).
2. **Mod-registered item not in JSON**: rejected. Mods must add an `allowlist` entry to drop. Document this for plugin authors.
3. **JSON parse failure at startup**: log SEVERE and **fail closed** — `Nat20GearFilter.isAllowed()` returns false for every itemId until JSON parses cleanly. Result: zero Nat20 gear drops from mobs and chests until the config is fixed. Failing closed beats failing open here because the alternative (permissive mode) silently regresses the game to pre-redesign drops, which is hard to notice in playtest.
4. **Blocklist removes a mob's only native drop**: native pool empty post-filter, 5% roll falls through to global as today.
5. **Per-item override conflicts with token**: per-item wins (precedence order).
6. **Overlapping token substrings**: longest match wins (e.g., `silversteel` beats `silver`; `crystal` beats `flame` in `Weapon_Staff_Crystal_Flame`).
7. **Allowlist item ilvl out of band for mob**: filtered out at pool-build like any other item.

## Smoke Test Checklist

Post-implementation:

- [ ] Boot devserver. No SEVERE about gear_filter.json parse.
- [ ] `/nat20 spawngroup` an EPIC champion at ilvl 30. Kill 20. Tally drop categories. Expect ~28 / 28 / 19 / 19 split (allow ±5%).
- [ ] Kill a Goblin (native = `Iron_Sword`) 30 times. Expect ~5% Iron_Sword, not ~12%.
- [ ] Open dungeon chest at ilvl 30 × 10. Tally categories. Expect 30/30/20/20 (no native carve-out).
- [ ] Kill Trork mob at ilvl 30. No Trork armor drops (out of [16,28] band).
- [ ] Kill Trork mob at ilvl 22. Trork armor occasionally drops.
- [ ] Spawn ilvl-3 mob and kill 30. No Praetorian / Tribal / Void / Spectral drops.
- [ ] `/give` of vanilla items still works (gear_filter only affects loot generation, not item commands).

## Open / Deferred

- **Stack-quantity drops** (`"quantity"` field): schema reserved, plumbing deferred. Wiki "Coming Soon" note added to `docs/wiki/offensive-affixes.md`.
- **Hot-reload of gear_filter.json**: not in this pass. Edits need server restart.
- **Live admin command** (e.g., `/nat20 blocklist add <itemId>`): deferred. Edit JSON + restart.
- **Inter-mod tier inheritance** (e.g., `OtherMod:Iron_Sword` inheriting `iron` token): supported via substring match. Mods can opt out by adding to blocklist.
- **Per-ilvl-band category weight tuning** (e.g., more tools at low ilvl, more weapons at endgame): single constant for now. Revisit if playtest shows the flat 30/30/20/20 feels wrong at any band.
