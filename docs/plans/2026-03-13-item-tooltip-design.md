# Item Tooltip Design

> **SUPERSEDED:** This custom panel approach was replaced by vanilla tooltip injection via dynamic item registration + I18n reflection. See `2026-03-14-vanilla-tooltip-injection-design.md`.

## Goal

When a player hovers any item with `Nat20LootData` metadata, they see a rich tooltip showing: generated name (rarity-colored), rarity label, STAT affix lines with values, EFFECT affix descriptions with cooldown/proc info, socket status, stat requirements, and flavor text. When hovering a non-equipped item, inline comparison deltas show how it compares to the currently equipped item in that slot.

Rarity outlines on item slots are deferred to a future task.

## What Exists Today

- `Nat20LootData`: structured metadata on every generated item (rarity, rolled affixes, sockets, gems, name, description)
- `Nat20ItemRenderer.resolve()`: takes an ItemStack + optional PlayerStats, produces `Nat20ItemDisplayData` with resolved affix lines, socket lines, and requirement
- `Nat20RarityDef`: data-driven rarity definitions with colors and textures, loaded from JSON
- `Nat20EquipmentListener`: tracks equipped items per-player per-slot in `equippedCache` (stores `Nat20LootData`)
- `ComparisonDeltas`: record with `Map<String, Delta>` and `Delta(symbol, color)`, exists but not wired up
- `AffixLine` record: has `type` field distinguishing STAT vs EFFECT, plus `description`, `cooldown`, `procChance` for effects
- Rarity tiers: Common, Uncommon, Rare, Epic, Legendary

## What Doesn't Exist

- Any code that renders display data as visible UI output
- Rich tooltip content on generated items
- Comparison delta computation logic
- Knowledge of whether/how Hytale's vanilla tooltip system can be hooked
- `description` field on `Nat20ItemDisplayData` (needs to be added)

## Phase 1: Tooltip Integration Investigation

Research spike to determine how to get tooltip content displayed. Test these approaches in order, stopping at the first that works.

### Approach A: Item-level tooltip property

Investigate whether `ItemStack` supports a tooltip property (e.g., via metadata or a setter) that the vanilla inventory's `InfoDisplay: Tooltip` reads automatically. If so, stamp a rich `Message` onto items when generated or when entering a player's inventory. Every UI showing the item gets the tooltip for free.

Key things to check from the UI skills:
- `TooltipTextSpans` accepts `Message` objects on UI elements, requires `TextTooltipStyle: $C.@DefaultTextTooltipStyle`
- `TooltipText` accepts plain String
- Whether these are element-level only or can be associated with an ItemStack
- Whether `InfoDisplay: Tooltip` on ItemGrid drives tooltip rendering from item data

### Approach B: PacketAdapter interception

If items can't carry their own tooltip data, investigate intercepting outbound inventory display packets via `PacketAdapters.registerOutbound()`. The adapter would detect items with `Nat20LootData` and inject `TooltipTextSpans` onto their slot elements. Works across all UIs but more complex, and the packet granularity is unknown.

### Approach C: Custom page (last resort)

Build a custom `InteractiveCustomUIPage` with our own ItemSlot elements where we control `TooltipTextSpans` directly via `UICommandBuilder`. Only works on our page, not the vanilla inventory. Requires a trigger mechanism (likely a command via the existing `Nat20Command` system).

### Investigation also answers (document for future outline work)

- Does `Nat20LootPipeline` currently stamp a Hytale-native quality value onto ItemStacks?
- Does `RenderItemQualityBackground: true` produce visible slot backgrounds?
- Can native quality backgrounds and custom `OutlineColor`/`OutlineSize` coexist?

## Phase 2: Data Model Change

Add `description` field to `Nat20ItemDisplayData`:

```java
public record Nat20ItemDisplayData(
    String name,
    String rarity,
    String rarityColor,
    String tooltipTexture,
    String slotTexture,
    List<AffixLine> affixes,
    List<SocketLine> sockets,
    @Nullable RequirementLine requirement,
    @Nullable String description          // NEW
) {}
```

Update `Nat20ItemRenderer.resolve()` to populate it from `Nat20LootData.getDescription()`.

## Phase 3: Tooltip Renderer

Stateless class that converts `Nat20ItemDisplayData` into tooltip content.

### Signature

```java
Message buildTooltipMessage(Nat20ItemDisplayData displayData, @Nullable ComparisonDeltas deltas)
```

### Tooltip sections (top to bottom)

1. **Item name**: bold, colored with `displayData.rarityColor()`
2. **Rarity label**: `displayData.rarity()` in same color, not bold
3. **STAT affix lines**: for each `AffixLine` where type is `STAT`:
   - `"{value}{unit} {statName}"` (e.g., "+12 Physical Damage", "+3% Critical Chance")
   - Scaling stat indicator if `scalingStat` is non-null
   - Requirement text in red if `requirementMet` is false
   - Inline comparison delta from `deltas.getForStat(statName)` if present, colored per `Delta`
4. **EFFECT affix lines**: for each `AffixLine` where type is `EFFECT`:
   - `description` text (e.g., "Deals lightning damage on hit")
   - Cooldown and proc chance as secondary details if non-null
   - Visually distinct from STAT lines (different color or formatting)
5. **Socket lines**: filled sockets show gem name, purity, bonus colored by `gemColor`. Empty sockets show "[Empty Socket]" muted
6. **Requirement line**: `displayData.requirement().text()` colored green if met, red if not
7. **Description / flavor text**: italic, muted color

Composed using `Message.raw()`, `.bold()`, `.italic()`, `.color()`, `.insert()` with `\n` separators.

### Limitations of Message-based tooltips

No visual separators (horizontal rules), indentation, column alignment, or themed backgrounds. If this proves too limiting, escalate to a custom `.ui` tooltip panel (the `tooltipTexture` and `tooltipArrowTexture` fields on `Nat20RarityDef` were designed for that). But start with Message-based: it's far less code and works with any tooltip integration approach.

## Phase 4: Tooltip Wiring

Wire the tooltip renderer to the integration path determined by Phase 1.

- **If Approach A worked**: stamp `buildTooltipMessage()` output onto items at generation time or when entering inventory
- **If Approach B worked**: PacketAdapter calls `Nat20ItemRenderer.resolve()` + `buildTooltipMessage()` for each Nat20 item in outbound inventory packets
- **If Approach C**: custom page sets `TooltipTextSpans` on its own ItemSlot elements via `UICommandBuilder`

For Approaches A and B, tooltips appear on the vanilla inventory automatically. For Approach C, a command (e.g., `/nat20 equip`) opens the custom page.

Note: for Approaches A and B, comparison deltas are harder to integrate since the tooltip is stamped per-item rather than per-hover. Comparison may require Approach C regardless, or may need to be deferred. The investigation will clarify this.

## Phase 5: Comparison Deltas

### When comparison triggers

The player hovers an item that:
- Has `Nat20LootData`
- Has a resolvable `EquipmentCategory`
- The player has a Nat20 item equipped in the corresponding slot

### Computing deltas

Collect all stat names across both items' STAT affix lines. For each stat:
- Present on both: diff numeric values, produce `+N` or `-N`
- Present only on hovered item: pure gain
- Present only on equipped item: pure loss
- Zero diff: omit entirely

EFFECT affixes are not compared numerically. No delta for effects.

Delta colors:
- Green + `+` prefix: improvement
- Red + `-` prefix: downgrade
- Omit unchanged values entirely

### Cache expansion

`Nat20EquipmentListener.equippedCache` stores `Map<String, Nat20LootData>` per player. To resolve display data for comparison, we need both loot data and the item context.

Change: store `Nat20ItemDisplayData` alongside `Nat20LootData` in the cache. Resolve at equip time via `Nat20ItemRenderer.resolve()` so it's available instantly on hover. The listener already has access to the ItemStack when the equipment change fires.

The existing `Nat20LootData` in the cache is still needed for `Nat20ModifierManager`. The cache entry becomes a pair: `(Nat20LootData, Nat20ItemDisplayData)`.

### Mismatched affixes

Items with entirely different affixes are the common case. Every affix on the hovered item that the equipped item lacks appears as a gain. Every affix on the equipped item that the hovered item lacks appears as a loss. This is expected and correct: the side-by-side makes it self-evident.

## Deferred: Rarity Outlines

Documented for future implementation.

### Concept

Item slots displaying Nat20 items get a visual rarity indicator via native quality backgrounds, custom `OutlineColor`/`OutlineSize`, or `slotTexture` per-rarity backgrounds.

### Prerequisites before implementing

- Determine whether `Nat20LootPipeline` stamps Hytale-native quality values on items (Phase 1 investigation answers this)
- Test `RenderItemQualityBackground: true` behavior
- Test coexistence of quality backgrounds and custom outlines
- Pull colors from `Nat20RarityDef.color()`, never hardcode
- Consider `slotTexture` field: it was designed for this purpose
