# Tooltip Integration Investigation Findings

> **UPDATE (2026-03-15):** A fourth approach was discovered post-investigation: dynamic per-instance item registration via `Item` copy constructor + I18n reflection (inspired by WeaponStatsViewer). This is the production approach. See `2026-03-14-vanilla-tooltip-injection-design.md`.

## Approach A: Item-level tooltip property

### ItemStack API surface

`ItemStack` (decompiled from `com.hypixel.hytale.server.core.inventory.ItemStack`) has **no tooltip methods**. The full public API:

- Constructors: `(String itemId, int quantity)`, `(String, int, BsonDocument)`, `(String, int, double durability, double maxDurability, BsonDocument)`
- Getters: `getItemId()`, `getQuantity()`, `getMetadata()`, `getDurability()`, `getMaxDurability()`, `isUnbreakable()`, `isBroken()`, `isEmpty()`, `getItem()`, `getBlockKey()`
- Immutable mutators: `withMetadata(KeyedCodec<T>, T)`, `withMetadata(String, BsonDocument)`, `withDurability()`, `withQuantity()`, `withState()`
- No `setTooltip()`, `setDescription()`, `setName()`, `setTooltipTextSpans()`, or any display-related method

The `withMetadata()` family stores opaque data in a `BsonDocument`. This data **is** sent to the client (the `ItemWithAllMetadata` packet includes a `metadata` field as serialized String). However, the client's native tooltip renderer (`ItemTooltip.ui`) does not read plugin metadata: it reads the item's `translationKey`, `descriptionTranslationKey`, quality, durability, and stat values from the `Item` asset definition, not from metadata.

### ItemGridSlot API surface

`ItemGridSlot` (from `com.hypixel.hytale.server.core.ui.ItemGridSlot`) has these relevant methods:

- `setItemStack(ItemStack)`: assigns the item to display
- `setName(String)`: overrides the display name (plain String only)
- `setDescription(String)`: overrides the description (plain String only)
- `setBackground(Value<PatchStyle>)`: per-slot background
- `setOverlay(Value<PatchStyle>)`: per-slot overlay
- `setSkipItemQualityBackground(boolean)`: suppress quality border
- No `setTooltipTextSpans(Message)` or rich text setter

**Verdict: `ItemGridSlot.setName()` and `setDescription()` can put plain text into the native tooltip, but there is no `Message`-based rich text alternative.** The native `ItemTooltip.ui` template has `#Name` (Label with bold style), `#Quality` (Label), `#Description #Label` (gray text), and `#Stats` container with specific stat labels (`#StatHealth`, `#StatDefense`, `#StatAttack`, `#StatMana`, `#StatStamina`). These are hardcoded element IDs that the client populates from the item and quality definitions: plugins cannot inject arbitrary styled text into them.

### Native tooltip flow

When a player hovers an item slot in the inventory, the client:
1. Reads the `ItemWithAllMetadata` from the inventory packet
2. Looks up the `Item` asset by `itemId` to get `translationKey`, `descriptionTranslationKey`, stats, quality index
3. Looks up the `ItemQuality` asset by the item's `qualityIndex` to get tooltip texture, text color, quality label
4. Populates `ItemTooltip.ui` elements (`#Name`, `#Quality`, `#Description`, `#Stats`, `#Durability`, `#Cursed`)

If `ItemGridSlot.setName()` or `setDescription()` were called (custom page grids), those override the translated name/description. But these only accept String, not Message.

### ItemQuality quality index

`Item.getQualityIndex()` returns an `int` that maps to an `ItemQuality` entry from the asset store. The nat20 quality JSONs define `QualityValue` (101-105) along with tooltip textures, slot textures, and text colors. When the item variant (e.g., `nat20:iron_sword_rare`) has a quality index matching `nat20_rare`, the client will:
- Show the quality's `ItemTooltipTexture` as the tooltip background (instead of `ItemTooltipDefault.png`)
- Color the `#Name` label with the quality's `TextColor`
- Show the quality label text (from `LocalizationKey`) in the `#Quality` element if `VisibleQualityLabel` is true
- Show the quality's `SlotTexture` as the slot background when `RenderItemQualityBackground: true`

**This means Nat20's quality backgrounds and tooltip textures already work for variant items, without any code needed.** The item variant JSON just needs a `QualityIndex` pointing to the correct quality entry.

### Conclusion for Approach A

**Cannot achieve rich (colored, multi-line, styled) tooltip content through item-level properties.** The only overrides available are `ItemGridSlot.setName(String)` and `setDescription(String)`, which accept plain text only. The native tooltip template is rigid: it has hardcoded stat elements that can't be extended with custom affix lines, socket displays, or comparison deltas. The native quality system (tooltip background, text color, quality label) works automatically for variant items, but that only provides cosmetic framing: the actual affix/socket content cannot be injected.

## Approach B: PacketAdapter interception

### API availability: CONFIRMED

`PacketAdapters` exists at `com.hypixel.hytale.server.core.io.adapter.PacketAdapters` with these methods:

```java
// Read-only watchers (BiConsumer, cannot modify/block packets)
PacketFilter registerInbound(PacketWatcher)
PacketFilter registerOutbound(PacketWatcher)
PacketFilter registerInbound(PlayerPacketWatcher)
PacketFilter registerOutbound(PlayerPacketWatcher)

// Filter/modify (BiPredicate, return false to block)
void registerInbound(PacketFilter)
void registerOutbound(PacketFilter)
PacketFilter registerInbound(PlayerPacketFilter)
PacketFilter registerOutbound(PlayerPacketFilter)

// Deregistration
void deregisterInbound(PacketFilter)
void deregisterOutbound(PacketFilter)
```

Interfaces:
- `PacketFilter extends BiPredicate<PacketHandler, Packet>`: return `false` to block the packet
- `PacketWatcher extends BiConsumer<PacketHandler, Packet>`: observe only
- `PlayerPacketFilter extends BiPredicate<PlayerRef, Packet>`: player-scoped filter
- `PlayerPacketWatcher extends BiConsumer<PlayerRef, Packet>`: player-scoped observer

### Inventory packet structure

`UpdatePlayerInventory` is a `ToClientPacket` with fields:
- `storage`, `armor`, `hotbar`, `utility`, `builderMaterial`, `tools`, `backpack`: each an `InventorySection`
- `sortType`: sort type enum

Each `InventorySection` contains:
- `items: Map<Integer, ItemWithAllMetadata>`: slot index to item data
- `capacity: short`

`ItemWithAllMetadata` contains: `itemId`, `quantity`, `durability`, `maxDurability`, `overrideDroppedItemAnimation`, `metadata` (String).

### The problem with packet interception for tooltips

The tooltip is not part of the `UpdatePlayerInventory` packet. The packet only sends item identity (ID, quantity, metadata). The tooltip is rendered **entirely client-side** by looking up the `Item` and `ItemQuality` assets. There is no `tooltipText` field in the packet.

To modify the tooltip via packet interception, we would need to:
1. Intercept `UpdatePlayerInventory` outbound
2. Change the `itemId` in `ItemWithAllMetadata` to a different item whose asset definition has the desired description
3. This would require creating a unique Item asset per loot combination (impractical)

**Or** we could intercept and add custom metadata to the packet's `metadata` string, but the client doesn't read custom metadata keys for tooltip display.

### What packet interception CAN do

- Swap `itemId` to a variant item ID (e.g., change `Hytale:IronSword` to `nat20:iron_sword_rare`) so the client renders with the correct quality background and tooltip texture. This is already handled by the variant item system.
- Observe inventory changes for server-side logic.
- Block certain inventory packets.

### Conclusion for Approach B

**PacketAdapter cannot inject rich tooltip content.** The tooltip is rendered client-side from asset definitions, not from packet data. Packet interception is useful for other purposes (e.g., variant ID swapping, HUD multiplexing) but cannot solve the tooltip problem. The only way to control tooltip content from the server is through the custom page UI system.

## Approach C: Custom page

### Confirmed viability

Already proven working in the codebase: `Nat20DialoguePage` and `Nat20DiceRollPage` both use `InteractiveCustomUIPage` with custom `.ui` templates, `UICommandBuilder`, and `UIEventBuilder` to create fully custom interactive pages.

### Design for an item inspection page

A custom `Nat20ItemInspectPage` would:
1. Open as a `CustomUIPage` with a custom `.ui` template
2. The template includes `ItemIcon` elements (3D rendered item display), `Label` elements for stats/affixes, and styled groups for the tooltip body
3. Server populates using `cmd.set("#Element.TextSpans", message)` with the existing `Nat20TooltipBuilder.build()` output
4. Can include full rich text: colored rarity names, stat lines with scaling colors, socket displays, requirement warnings, comparison deltas
5. Can render item quality backgrounds via `ShowQualityBackground: true` on `ItemSlot` elements
6. Can include interactive elements (close button, comparison toggle, socket management)

### Two sub-approaches for trigger

**C1: Explicit command / keybind.** Player holds item and uses `/nat20 inspect` or a keybind. Opens the full inspection page. Works for detailed inspection but not for quick hover previews.

**C2: Custom inventory page replacement.** Replace the entire inventory page with a custom one that has item grids and handles hover events (`SlotMouseEntered`, `SlotMouseExited`) to show a side panel with rich tooltip content. This is the most seamless approach but requires reimplementing significant inventory UI.

**C3: HUD overlay on hover event.** Keep the native inventory but register a `SlotMouseEntered` event on a custom overlay. When the player hovers an item in their inventory, the server receives the event and pushes a `CustomUIHud` update with the tooltip content. Problems: (1) the native inventory page doesn't forward hover events to plugins, (2) a CustomUIHud can't render on top of a page, (3) there's no API to listen for "player hovered slot X in their inventory."

### ItemGridSlot in custom pages

For custom pages with `ItemGrid`, each slot can be set up with:
- `setItemStack(stack)`: item display
- `setName("Violent Iron Sword")`: plain text name override
- `setDescription("...")`: plain text description override
- `setBackground(patchStyle)`: custom slot background (for rarity borders)
- Custom quality backgrounds via `setSkipItemQualityBackground(false)` if the item's quality index is set

But the tooltip for these slots still renders through the native `ItemTooltip.ui` template, which only shows `#Name`, `#Quality`, `#Description`, and hardcoded stat elements. No custom rich content.

### TooltipTextSpans on wrapper elements

A better approach for custom pages: don't rely on ItemGrid's native tooltip at all. Instead:
- Use `ItemSlot` or `ItemIcon` elements inside `Group` wrappers
- Set `TooltipTextSpans` on the wrapping Group: `cmd.set("#ItemSlot0.TooltipTextSpans", richMessage)`
- Set `TextTooltipStyle` in the template: `TextTooltipStyle: $C.@DefaultTextTooltipStyle`
- This gives full `Message`-based rich text in the tooltip popup, with colors, bold, italic

This approach works for custom pages and HUDs but **not** for the native inventory page, which is client-controlled.

### Conclusion for Approach C

**This is the only viable approach for rich tooltip content.** Two practical paths:

1. **Inspection page** (simpler): `/nat20 inspect` command or keybind opens a dedicated page showing the full item card with all affix details, sockets, requirements, and comparison. Easy to implement with existing infrastructure.

2. **Custom equipment/inventory overlay** (more complex): A custom page that supplements or replaces the native inventory, with its own item display elements that have `TooltipTextSpans` set to the `Nat20TooltipBuilder` output. More seamless UX but much more work.

## Native quality backgrounds

### What `RenderItemQualityBackground: true` shows

This is an `ItemGrid`-level property (see `Hotbar.ui` line 64). When enabled, each item slot in the grid renders the `SlotTexture` from the item's `ItemQuality` definition as the slot background. The nat20 quality JSONs already define these:

- `nat20_common`: `"SlotTexture": "UI/ItemQualities/Slots/SlotCommon.png"`
- `nat20_uncommon`: `"SlotTexture": "UI/ItemQualities/Slots/SlotUncommon.png"`
- `nat20_rare`: `"SlotTexture": "UI/ItemQualities/Slots/SlotRare.png"`
- `nat20_epic`: `"SlotTexture": "UI/ItemQualities/Slots/SlotEpic.png"`
- `nat20_legendary`: `"SlotTexture": "UI/ItemQualities/Slots/SlotLegendary.png"`

The hotbar has `RenderItemQualityBackground: true`, so nat20 variant items automatically show their rarity-colored slot background in the hotbar. The inventory `ItemGrid` in `StoragePanel.ui` does **not** set this property, so it defaults to the standard slot background.

### Current gap: Nat20LootPipeline does not stamp quality

`Nat20LootPipeline.generate()` creates variant item IDs (e.g., `nat20:iron_sword_rare`) but the `LootCommand` creates the `ItemStack` with the **base** item ID (`Hytale:IronSword`), not the variant. The variant ID is stored only in `Nat20LootData.variantItemId` metadata.

For quality backgrounds to appear on native inventory/hotbar grids, the `ItemStack` must be created with the variant item ID (which has the correct `QualityIndex` in its item definition JSON). This is a loot system fix, not a tooltip fix: change `new ItemStack(itemId, 1)` to `new ItemStack(lootData.getVariantItemId(), 1)` (or the base ID if variant is null).

### Tooltip texture from quality

The `ItemQuality` also defines `ItemTooltipTexture` and `ItemTooltipArrowTexture`. These replace the default tooltip background in `ItemTooltip.ui` when hovering. The nat20 qualities define custom tooltip textures per rarity. These render automatically when the item uses the correct variant ID with the matching quality index.

## Recommendation

### Short-term (implement now): Approach C1 + quality fix

1. **Fix `LootCommand` (and any other item creation path) to use the variant item ID** when creating the `ItemStack`. This gives us quality backgrounds in the hotbar and native tooltip framing (rarity-colored name, quality label, tooltip texture) for free, with zero custom UI work.

2. **Build an `/nat20 inspect` command that opens a `Nat20ItemInspectPage`** (custom page) showing the full rich tooltip built by `Nat20TooltipBuilder`. This gives the detailed view: colored affixes, sockets, comparison deltas, stat scaling info. Triggered by command or eventually by a keybind.

3. **Set `ItemGridSlot.setDescription()` with the plain-text description** (from `Nat20LootData.getDescription()`) when putting items into custom page grids. This provides a basic affix summary in the native tooltip even without opening the inspect page.

### Medium-term (future): Approach C2

Build a custom equipment page that replaces or augments the native inventory, with `ItemSlot` elements wrapped in `Group` elements that have `TooltipTextSpans` set to the `Nat20TooltipBuilder` output. This would provide seamless rich tooltips on hover within the custom page, without requiring a separate inspect command. The main cost is reimplementing enough inventory UI to be usable alongside the native inventory.

### What won't work

- **Approach A**: No API for rich tooltip on ItemStack or ItemGridSlot. Dead end.
- **Approach B**: PacketAdapter cannot inject tooltip content. The tooltip is rendered client-side from assets. Dead end for this specific use case (though useful for other features like HUD multiplexing).
