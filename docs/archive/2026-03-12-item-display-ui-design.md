# Item Display UI Design

> **SUPERSEDED:** The dual-path approach (vanilla enhancement + fixed detail panel) was replaced by vanilla tooltip injection via dynamic item registration + I18n reflection. See `2026-03-14-vanilla-tooltip-injection-design.md`.

## Overview

The item display system uses two complementary paths that activate simultaneously for any item carrying Nat20 loot metadata. Path 1 provides passive rarity signaling via Hytale's built-in systems. Path 2 provides a rich detail panel on hover via custom UI.

## Architecture

### Path 1: Vanilla Enhancement (Passive Rarity Signal)

Runs once at item creation time. When the loot pipeline generates a Nat20 item, it performs three operations:

1. **Sets `ItemQuality`** to `nat20_<rarity>` (e.g., `nat20_rare`). This gives rarity-colored slot borders and tooltip background textures rendered client-side for free. Every Nat20 item in every inventory, chest, trade screen, and hotbar instantly communicates its tier.
2. **Calls `setName()`** with the rarity-colored generated name (e.g., "Violent Iron Sword of Vitality").
3. **Calls `setDescription()`** with a condensed plain-text affix summary for the vanilla tooltip.

`setDescription()` format:
```
+8.3 Attack Damage (STR)
4.2% Life Steal (CON)
Sockets: [Ruby of Might] [Empty] [Empty]
Requires: STR 14
```

This is functional but visually flat. The detail panel (Path 2) is where the full presentation lives.

Path 1 is fire-and-forget: the item carries its own display metadata from creation onward. No runtime cost, no player context needed.

### Path 2: Fixed Detail Panel (Rich Hover Display)

Runs on demand when a player hovers over any Nat20 item in any inventory-like context. Triggered by `SlotMouseEntered` / `SlotMouseExited` events.

The panel is fixed-position (not cursor-following: the SDK provides no cursor position data server-side), displayed adjacent to the inventory UI at 1/5 screen width.

The server produces a structured `Nat20ItemDisplayData` payload via `Nat20ItemRenderer.resolve()`. The .ui template binds to individual fields and handles all visual presentation: colors, alignment, conditional styling. The server never pre-formats display strings.

### Decoupling

The two paths are fully decoupled:

- Path 1 is write-once metadata baked at item creation.
- Path 2 is live-computed at view time using the viewing player's current stats.
- A non-Nat20 item (vanilla or another mod's) triggers neither path and gets the normal tooltip untouched.

## Structured Data Contract

### `Nat20ItemDisplayData`

The top-level payload produced by `Nat20ItemRenderer.resolve()`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Generated item name ("Violent Iron Sword") |
| `rarity` | String | Rarity ID ("Rare") |
| `rarityColor` | String | Hex color ("#5555FF") |
| `tooltipTexture` | String | Background texture path from `Nat20RarityDef` |
| `slotTexture` | String | Slot border texture path from `Nat20RarityDef` |
| `affixes` | List\<AffixLine\> | One per rolled affix |
| `sockets` | List\<SocketLine\> | One per socket slot |
| `requirement` | RequirementLine | Nullable, rarity stat gate |

### `AffixLine`

One per rolled affix. The `type` field drives template branching:

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Affix display name ("Violent", "Vampiric"). Not shown in hover panel (identity lives in generated item name), but carried for guide book and future contexts |
| `value` | String | Formatted number ("+8.3", "4.2") |
| `unit` | String | "%" or "" (multiplicative vs additive) |
| `statName` | String | Target stat ("Attack Damage", "Life Steal") |
| `scalingStat` | String | Nullable. Which D&D stat scales it ("STR") |
| `type` | String | "STAT" / "EFFECT" / "ABILITY" |
| `requirementMet` | boolean | Does the viewing player meet this affix's stat gate? |
| `requirementText` | String | Nullable. "STR 14" if gated |
| `description` | String | Nullable. EFFECT/ABILITY only. Mechanical detail for guide book |
| `cooldown` | String | Nullable. Formatted ("2.0s") |
| `procChance` | String | Nullable. Formatted ("30%") |

For STAT affixes, `description`, `cooldown`, and `procChance` are null. These fields exist in the struct for consumption by other contexts (guide book, future detailed views) but the hover panel does not render them.

### `SocketLine`

One per socket slot:

| Field | Type | Description |
|-------|------|-------------|
| `index` | int | 0-based socket position |
| `filled` | boolean | Whether a gem is socketed |
| `gemName` | String | Nullable. "Ruby of Might" |
| `purity` | String | Nullable. "Flawed" / "Standard" / "Pristine" |
| `gemColor` | String | Nullable. Hex color |
| `bonusValue` | String | Nullable. "+2.1" |
| `bonusStat` | String | Nullable. "Attack Damage" |

The `index` field supports future socketing/crafting UI that targets specific sockets.

### `RequirementLine`

| Field | Type | Description |
|-------|------|-------------|
| `text` | String | "STR 14" |
| `met` | boolean | Whether the viewing player meets the requirement |

## Panel Template Layout

### Single Panel (1/5 screen width)

```
┌────────────────────────┐
│  Violent Iron Sword    │  ← rarityColor
│      ── Rare ──        │  ← dimmer, centered
│                        │
│  +8.3 Attack Dmg  STR  │  ← STAT: value+stat left, scalingStat right
│  +2.1 Atk Speed   DEX  │
│                        │
│  4.2% Life Steal  CON  │  ← EFFECT: just the effect value
│  30% Chain Ltng   INT  │
│    ⚠ Requires INT 14   │  ← unmet gate, red, indented under
│                        │
│  Sockets [1/3]:        │
│  ◆ Ruby of Might       │  ← gemColor, purity
│    +2.1 Attack Dmg     │  ← bonus indented
│  ○ Empty               │
│  ○ Empty               │
│                        │
│  Requires: STR 14  ✓   │  ← green/red based on met
└────────────────────────┘
```

Design decisions:
- **Affix lines show the effect, not the affix name.** No "Vampiric:" prefix. Just "4.2% Life Steal". The affix identity lives in the item's generated name at the top.
- **EFFECT/ABILITY lines need a visual cue to distinguish proc chance from flat value.** "30% Chain Ltng" is ambiguous: is that 30% more lightning or 30% chance to trigger? The `type` field is already in the struct, so the template can branch on it. EFFECT lines should render a distinct icon prefix or subtle formatting difference (e.g., a trigger icon, dimmer value color, or "chance" suffix) so players intuit the difference from STAT lines without needing to read a guide. Exact visual treatment is a template-level decision.
- **Per-affix requirement warnings** appear indented below the affix line only when unmet. Met requirements produce no extra line.
- **EFFECT/ABILITY description, cooldown, and procChance are not rendered** in the panel. Complex mechanics belong in the guide book. The panel is a quick mechanical readout.
- **The two paths intentionally present the same data differently.** `setDescription()` includes affix names ("Attack Damage (STR)") because the vanilla tooltip is text-only and names add context without visual formatting. The panel omits affix names because its rich formatting makes them redundant. This is a feature: each path is optimized for its medium.

### Template Implementation

The .ui file defines a maximum capacity of pre-baked element slots (6 affix groups, 3 socket groups), all initially `Visible: false`. On hover, the server sets field values and toggles visibility for exactly the slots needed. Unused slots stay hidden.

Each affix group contains both the single-line layout (for STAT) and the two-line layout (for EFFECT/ABILITY with unmet requirements), with visibility toggled by type. Since EFFECT/ABILITY description lines are not rendered in the panel, the two-line case only activates for the unmet requirement warning.

Fixed element count avoids `appendInline()` + `clear()` on every hover (which adds latency). The tradeoff is a max affix cap baked into the template, but Legendary caps at 5 affixes, so 6 slots handles any item with headroom.

### Dual Panel Trees

The template contains two complete panel element trees: one rooted at `#Left`, one at `#Right`, with identical internal structure. The renderer writes to either via a `panelPrefix` parameter.

**Open investigation:** whether Hytale's .ui format supports reusable sub-template composition (define the panel layout once, instantiate twice). If yes, define once and import twice. If no, duplicate the tree and keep them in sync manually. Either way, the renderer code is identical.

## Comparison Mode

### When It Activates

Comparison mode activates when both conditions are met:

1. The player hovers an equippable Nat20 item (weapon/armor/tool).
2. The player currently has a Nat20 item equipped in the corresponding equipment slot.

If the equipped slot is empty or contains a non-Nat20 (vanilla) item, only the right panel appears (single mode).

### Layout

```
┌─ EQUIPPED ─────────────┐  ┌─ HOVERED ──────────────┐
│  Iron Sword            │  │  Violent Iron Sword    │
│    ── Common ──        │  │      ── Rare ──        │
│                        │  │                        │
│  +3.0 Attack Dmg  STR  │  │  +8.3 Attack Dmg ▲STR │
│                        │  │  +2.1 Atk Speed   DEX  │
│                        │  │                        │
│                        │  │  4.2% Life Steal  CON  │
│                        │  │                        │
│  Sockets [0/0]         │  │  Sockets [1/3]:        │
│                        │  │  ◆ Ruby of Might       │
│                        │  │    +2.1 Attack Dmg     │
│                        │  │  ○ Empty               │
│                        │  │  ○ Empty               │
│                        │  │                        │
│                        │  │  Requires: STR 14  ✓   │
└────────────────────────┘  └────────────────────────┘
```

Total width: 2/5 screen. Inventory occupies the center.

**Line width constraint:** At 1/5 screen width, affix lines in comparison mode must fit: value + stat name + delta indicator + scaling stat. Longest realistic case: `+12.5 Knockback Res ▲CON` (~27 characters plus icons). The template's panel width constant must accommodate this without wrapping, or stat names must use abbreviated forms for long names (e.g., "Knockback Res" → "KB Resist"). If a line wraps, the panel height becomes unpredictable. Define a stat name display length budget during template implementation and truncate to fit.

### Delta Indicators

Delta indicators appear on the hovered (right) panel only, next to affix lines that share a `statName` with an affix on the equipped item:

- `▲` green: hovered item is better for that stat
- `▼` red: hovered item is worse for that stat

Affixes present on only one side get no indicator: they're net new or net lost, which is self-evident from the side-by-side.

Deltas use **effective values** (post-scaling for the viewing player's current stats), not raw values. A STR-scaled affix on a STR-dumped character correctly shows as worse than a lower-raw-value DEX-scaled affix on a high-DEX character.

### Equipment Slot Mapping

The hovered item's category (from `Nat20LootEntryRegistry` or auto-detected from `Item.weapon`/`.armor`/`.tool`) maps to the equipment slot to compare against. A hovered sword checks the weapon slot. A hovered chestplate checks the chest armor slot.

## Context Compatibility

The `Nat20_ItemPanel.ui` component works identically across every inventory-like context. The panel is context-agnostic: it receives data and renders it. The parent layout decides positioning and single/comparison mode.

| Context | Modes | Behavior |
|---------|-------|----------|
| Player inventory | Single + comparison | Panels flank the inventory |
| Chest/container | Single + comparison | Player hovers loot, compares against equipped |
| Merchant trade UI | Single + comparison | Player browses stock, compares against equipped |
| Future crafting/socketing UI | Single | Shows item being modified with live socket state |

The component does not know which context it's in. It does not position itself. It does not decide single vs. comparison. It just renders the `Nat20ItemDisplayData` payload it receives.

## Data Flow

### Flow 1: Item Creation (Write-Once)

```
Trigger (mob death / chest open / merchant refresh)
  → Nat20LootPipeline.generate()
    → Select rarity (weighted random, clamped by context tier)
    → Roll affixes (loot rules per rarity, filtered by category)
    → Allocate sockets
    → Generate name ("[Prefix] Base Item [of Suffix]")
    → Build Nat20LootData
  → Persist to ItemStack.metadata via KeyedCodec
  → Set ItemQuality to nat20_<rarity>
  → setName() with rarity-colored generated name
  → setDescription() with plain-text affix summary
  → Done. Item is self-describing.
```

No player context needed. The item carries its identity in metadata.

### Flow 2: Hover Display (On-Demand)

```
SlotMouseEntered fires
  → Read hovered ItemStack.metadata → Nat20LootData
  → No Nat20 data? → exit, vanilla tooltip handles it
  → Nat20ItemRenderer.resolve(stack, viewingPlayerStats)
    → Look up rarity def → colors, textures
    → For each rolled affix:
      → Interpolate base value from stored lootLevel
      → Apply stat scaling from viewing player's ability scores
      → Build AffixLine
    → For each socket:
      → Build SocketLine
    → Build RequirementLine
    → Return Nat20ItemDisplayData
  → Determine equipment category of hovered item
  → Check player's corresponding equipment slot
  → Equipped has Nat20 data?
    → Yes: resolve() equipped item → comparison mode
    → No: single mode
  → Nat20ItemPanelRenderer.render(cmd, data, "#Right", deltas)
  → If comparison: Nat20ItemPanelRenderer.render(cmd, equippedData, "#Left", null)
  → Toggle panel visibility
  → SlotMouseExited → hide both panels
```

**Key properties:**
- **Stateless between hover events.** No cached panel state, no "currently displayed item" tracking. Each `SlotMouseEntered` is a fresh resolve + render. Performance cost is negligible: ~10 registry lookups and ~20 float multiplications per resolve, potentially doubled in comparison mode. If rapid slot dragging becomes a concern, a single-tick debounce (skip resolve if another `SlotMouseEntered` fires within the same server tick) handles it without adding persistent state.
- **`resolve()` always uses the viewing player's stats.** The item's stored data (rarity, affix IDs, loot level) is its identity. The player's current ability scores are the lens applied at view time. Two players hovering the same merchant sword see different effective values, different requirement flags, different scaling bonuses. The stored `lootLevel` is the item's identity; the player's stats are the viewer's lens.

## Player-Facing View of `resolve()`

A player who levels up STR sees their Violent affix's effective value increase the next time they hover, without any item update needed. The item hasn't changed: the player's lens has.

## Code Changes

### `Nat20ItemDisplayData` (Replace)

Current record uses `List<Message>` for pre-baked formatted strings. Replace with the structured records defined in the data contract section above. The renderer no longer owns color or formatting decisions.

### `Nat20ItemRenderer` (Refactor)

Method signature stays: `resolve(ItemStack, PlayerStats) → Nat20ItemDisplayData`.

Internals change: instead of building `Message` objects with baked-in colors, build structured `AffixLine`, `SocketLine`, `RequirementLine` records. Delete `extractDisplayWord()` helper and all `Message.raw().color()` calls.

Retained: interpolation math, stat scaling computation, requirement checking logic. These are data concerns.

### New: `Nat20ItemPanelRenderer`

Maps `Nat20ItemDisplayData` to `UICommandBuilder` calls:

```java
void render(UICommandBuilder cmd, Nat20ItemDisplayData data,
            String prefix, ComparisonDeltas deltas)
```

Where `prefix` is `"#Left"` or `"#Right"`, and `deltas` is nullable (null in single mode, populated in comparison mode with per-statName ▲/▼ indicators).

### New: `Nat20_ItemPanel.ui`

Template with two panel trees (`#Left`, `#Right`), each containing:
- 6 affix groups (pre-baked, toggled by visibility)
- 3 socket groups (pre-baked, toggled by visibility)
- Name, rarity label, requirement line
- All initially `Visible: false`

Located at `src/main/resources/Common/UI/Custom/Pages/Nat20_ItemPanel.ui`.

### New: ItemQuality JSON Files

One per rarity tier at `src/main/resources/Server/Item/Qualities/`:

```
nat20_common.json
nat20_uncommon.json
nat20_rare.json
nat20_unique.json
nat20_legendary.json
```

Each defines slot border texture/color and tooltip background texture matching `Nat20RarityDef`.

## Extensibility

### Surviving SDK Updates

The system touches three SDK surface areas:

| Dependency | Risk | Degradation |
|-----------|------|-------------|
| `ItemStack.metadata` (BSON) | Low: official extensibility point | `nat20_version` field enables schema migration |
| `ItemQuality` registration | Low: content registration system | Fix is updating JSON paths, not logic |
| `SlotMouseEntered/Exited` | Medium: could change in future SDK | Path 1 still works: players keep borders + text tooltip |

If `SlotMouseEntered/Exited` breaks, the system degrades gracefully to Path 1 only.

### Coexisting With Other Mods

- Metadata keys namespaced (`Nat20Loot`): no collision.
- ItemQuality IDs prefixed (`nat20_common`): no collision.
- Stat modifier keys namespaced (`nat20:affix:violent:weapon`): coexists with vanilla and other mods.
- Hover panel checks for Nat20 metadata first: if absent, does nothing. No interference with other mods' tooltips.

### Future Extension Points

Not built now, but kept possible by design:

- **JSON override directories:** registries load classpath defaults then apply overrides from plugin data directory. Third-party mods can drop JSONs to add content.
- **`Nat20ItemDisplayData` is a plain record:** another mod could call `Nat20ItemRenderer.resolve()` and build its own renderer.
- **No public API, no event bus, no plugin interfaces.** These come later if demand exists.

## Open Questions

### Hotbar Hover Behavior

The design assumes `SlotMouseEntered` fires on inventory-like UIs (player inventory, chests, merchant trade). Whether hotbar slots also fire `SlotMouseEntered` when hovered is unverified. If they don't, Path 1 still covers hotbar items (slot border + vanilla tooltip). If they do, the panel needs a positioning strategy for hotbar context where there's no open inventory UI to flank. Flag for SDK investigation during implementation rather than designing around it now.
