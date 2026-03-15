# Custom Inventory Page Implementation Plan

> **SUPERSEDED:** This approach was abandoned because Hytale's CustomUIPage lacks mid-flight drag events. See `2026-03-14-vanilla-tooltip-injection-design.md` for the replacement approach (dynamic item registration + I18n reflection).

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the vanilla inventory (Tab key) with a custom inventory page that replicates the vanilla layout exactly, adds Nat20 rich tooltips on all items, and replaces the 3D character preview with a live stat sheet.

**Architecture:** Intercept the `ClientOpenWindow` packet (Tab key) via `PlayerPacketFilter`, block it, and open a custom `InteractiveCustomUIPage` using a `.ui` template that mirrors the vanilla 3-panel inventory layout. The left panel uses `ItemGrid` elements bound to real inventory sections (armor, utility) surrounding a stat sheet panel. The center panel uses a 9-column `ItemGrid` bound to the storage inventory section. Equipment changes trigger `sendUpdate()` to refresh stat values and tooltips in real-time.

**Tech Stack:** Hytale server plugin (Java 25, ScaffoldIt 0.2.x), custom `.ui` templates, `InteractiveCustomUIPage`, `ItemGrid` with `InventorySectionId`, `TooltipTextSpans` for rich tooltips, `PacketAdapters` for Tab interception.

**Relevant skills:** hytale-ui, hytale-ui-templates, hytale-ui-elements, hytale-ui-items

---

## Reference: Vanilla Inventory Layout Constants

From `InGame/Common.ui`:
- Slot size: **74×74px**, icon size: **64×64px**, spacing: **2px**, grid padding: **2px**
- Slots per row: **9**
- Center panel width: **715px**, side panel width: **410px**
- Horizontal panel spacing: **38px**, vertical: **8px**
- Total width: **1625px** (410 + 38 + 715 + 38 + 410)
- Slot background: `"Slot.png"` (from vanilla `Pages/Inventory/`)
- Container background: `"ContainerPatch.png"` (border: 23), header: `"ContainerHeaderNoRunes.png"` (hBorder: 35)
- Title style: 15px, bold, uppercase, Secondary font, color `#b4c8c9`
- Stat label color: `#878e9c`, stat value color: `#b4c8c9`
- Dark overlay: `#000000(0.55)`

## Reference: Elements That Work in Custom Pages

| Element | Status | Notes |
|---------|--------|-------|
| `SceneBlur` | YES | No config, standalone |
| `ItemGrid` | YES | Full drag/drop, `InventorySectionId` binds to real inventory |
| `TabNavigation` | YES | Full styling support |
| `ActionButton` | YES | Works as regular button |
| `ItemIcon` | YES | Set via `.ItemId` |
| `ItemSlot` | YES | With quality background |
| `CharacterPreviewComponent` | **NO** | SDK returns null |
| `ItemPreviewComponent` | **NO** | SDK returns null |

---

### Task 1: Create the .ui Template (Vanilla-Faithful Layout)

**Files:**
- Create: `src/main/resources/Common/UI/Custom/Pages/Nat20_Inventory.ui`
- Delete: `src/main/resources/Common/UI/Custom/Pages/Nat20_Equipment.ui` (replaced by this)

**Context:** This template replicates the vanilla `InventoryPage.ui` 3-panel layout as closely as possible. We cannot import vanilla's `$InGame` or `$Container` variables since those are client-side paths, so we inline the values. We cannot use `CharacterPreviewComponent` or `ItemPreviewComponent` (SDK returns null for custom pages). Everything else is replicated faithfully.

**Step 1: Create the template file**

The template structure mirrors `InventoryPage.ui` exactly:
- `SceneBlur` + dark overlay
- Root group (1625px wide, LayoutMode: Left)
- Left: Character panel (410px) with equipment `ItemGrid` slots, stat sheet center, stats panel bottom
- Center: Storage panel (715px) with 9-col `ItemGrid`, title, autosort placeholder
- Right: Item info panel (410px) with item name, affix details, flavor text
- `$C.@BackButton` for ESC close

```
$C = "../Common.ui";

// ─── Vanilla inventory constants (from InGame/Common.ui) ───
@SlotSize = 74;
@SlotSpacing = 2;
@SlotIconSize = 64;
@SlotsPerRow = 9;
@GridPadding = 2;
@SidePanelWidth = 410;
@CenterPanelWidth = 715;
@HSpacing = 38;
@TotalWidth = @CenterPanelWidth + (@SidePanelWidth * 2) + (@HSpacing * 2);
@TitleHeight = 38;
@PanelBg = (TexturePath: "Nat20_ContainerPatch.png", Border: 23);
@HeaderBg = (TexturePath: "Nat20_ContainerHeader.png", HorizontalBorder: 35, VerticalBorder: 0);
@TitleStyle = (FontSize: 15, VerticalAlignment: Center, RenderUppercase: true, TextColor: #b4c8c9, FontName: "Secondary", RenderBold: true, LetterSpacing: 0);
@StatLabelStyle = (TextColor: #878e9c, FontSize: 11, RenderBold: false);
@StatValueStyle = (TextColor: #b4c8c9, FontSize: 14, RenderBold: true);
@StatBoostedStyle = (TextColor: #44dd66, FontSize: 14, RenderBold: true);
@MutedStyle = (TextColor: #878e9c, FontSize: 13);

// ─── Background ───
SceneBlur {}
Group { Background: #000000(0.55); }

// ─── Root layout ───
Group #InvRoot {
  Anchor: (Width: @TotalWidth, Top: 0, Bottom: 20);
  LayoutMode: Left;

  // ═══════════════════════════════════════════
  // LEFT PANEL: Character (410px)
  // ═══════════════════════════════════════════
  Group #CharPanel {
    Anchor: (Width: @SidePanelWidth, Height: 720, Top: 180);

    // Header
    Group {
      Anchor: (Height: @TitleHeight, Top: 0);
      Background: @HeaderBg;
      Padding: (Top: 7);

      Label #CharTitle {
        Style: (...@TitleStyle, HorizontalAlignment: Center);
        Padding: (Horizontal: 19);
        Text: "CHARACTER";
      }
    }

    // Body
    Group {
      Anchor: (Top: @TitleHeight);
      Background: @PanelBg;
      Padding: (Full: 17);

      Group {
        Anchor: (Height: 490, Top: -8);

        // Armor ItemGrid (left column, 1×4)
        ItemGrid #ArmorGrid {
          Anchor: (Width: @SlotSize + 4, Height: (@SlotSize * 4) + (@SlotSpacing * 3) + (3 * @GridPadding), Left: 0, Top: 50);
          SlotsPerRow: 1;
          SlotSize: @SlotSize;
          SlotIconSize: @SlotIconSize;
          SlotSpacing: @SlotSpacing;
          Background: #202c3b(0);
          Padding: @GridPadding;
          InventorySectionId: 1;
          ShowScrollbar: false;
          RenderItemQualityBackground: true;
          DisplayItemQuantity: false;
          InfoDisplay: Tooltip;
          TextTooltipStyle: $C.@DefaultTextTooltipStyle;
        }

        // ─── Center: Nat20 Stat Sheet (replaces 3D character) ───
        Group #StatSheet {
          Anchor: (Left: @SlotSize + 20, Right: 20, Top: 30, Bottom: 10);
          Background: (Color: #141e2b(0.85));
          Padding: (Full: 12);
          LayoutMode: Top;

          // Section title
          Label {
            Text: "ABILITY SCORES";
            Style: (TextColor: #878e9c, FontSize: 12, RenderBold: true, RenderUppercase: true);
            Anchor: (Height: 22);
          }

          // STR row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "STR"; Style: (TextColor: #FF4444, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseSTR { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusSTR { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // DEX row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "DEX"; Style: (TextColor: #44DD66, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseDEX { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusDEX { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // CON row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "CON"; Style: (TextColor: #DD8833, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseCON { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusCON { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // INT row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "INT"; Style: (TextColor: #4488FF, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseINT { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusINT { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // WIS row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "WIS"; Style: (TextColor: #CCCCDD, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseWIS { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusWIS { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // CHA row
          Group { LayoutMode: Left; Anchor: (Height: 28);
            Label { Text: "CHA"; Style: (TextColor: #BB66FF, FontSize: 13, RenderBold: true); Anchor: (Width: 40); }
            Label #StatBaseCHA { Text: "10"; Style: @StatValueStyle; Anchor: (Width: 30); }
            Label #StatBonusCHA { Text: "(+0)"; Style: @StatLabelStyle; FlexWeight: 1; }
          }

          // Separator
          Group { Anchor: (Height: 8); Background: #c1c9d3(0.07); Anchor: (Height: 2); }

          // Derived combat stats
          Label {
            Text: "COMBAT STATS";
            Style: (TextColor: #878e9c, FontSize: 12, RenderBold: true, RenderUppercase: true);
            Anchor: (Height: 22);
          }

          Group { LayoutMode: Left; Anchor: (Height: 24);
            Label { Text: "AC"; Style: @StatLabelStyle; Anchor: (Width: 70); }
            Label #DerivedAC { Text: "10"; Style: @StatValueStyle; FlexWeight: 1; }
          }

          Group { LayoutMode: Left; Anchor: (Height: 24);
            Label { Text: "Attack"; Style: @StatLabelStyle; Anchor: (Width: 70); }
            Label #DerivedAttack { Text: "+0"; Style: @StatValueStyle; FlexWeight: 1; }
          }

          Group { LayoutMode: Left; Anchor: (Height: 24);
            Label { Text: "Damage"; Style: @StatLabelStyle; Anchor: (Width: 70); }
            Label #DerivedDamage { Text: "1-4"; Style: @StatValueStyle; FlexWeight: 1; }
          }
        }

        // Utility slot (below center)
        ItemGrid #UtilityGrid {
          Anchor: (Width: @SlotSize + @GridPadding, Height: @SlotSize + @SlotSpacing + @GridPadding, Left: 152, Bottom: 0);
          SlotsPerRow: 1;
          SlotSize: @SlotSize;
          SlotIconSize: @SlotIconSize;
          SlotSpacing: @SlotSpacing;
          Background: #000000(0);
          Padding: @GridPadding;
          InventorySectionId: 2;
          ShowScrollbar: false;
          RenderItemQualityBackground: true;
          DisplayItemQuantity: false;
          InfoDisplay: Tooltip;
          TextTooltipStyle: $C.@DefaultTextTooltipStyle;
        }
      }

      // ─── Bottom: Hytale stats (Health, Stamina, Mana, Defense) ───
      Group #HytaleStats {
        Background: (Color: #141e2b(0.6));
        Anchor: (Height: 135, Top: 35);
        Padding: (Horizontal: 10);
        LayoutMode: Top;

        Label {
          Text: "STATS";
          Anchor: (Top: 8);
          Style: (TextColor: #878e9c, FontSize: 14, RenderBold: true, RenderUppercase: true);
        }

        Group {
          LayoutMode: Top;
          FlexWeight: 1;
          Anchor: (Top: -5);

          Group {
            LayoutMode: Left;
            Anchor: (Height: 50, Top: 5);

            Group { Anchor: (Left: 10); LayoutMode: Middle; FlexWeight: 1;
              Label { Text: "Health"; Style: @StatLabelStyle; }
              Label #StatsHealth { Text: "0"; Style: @StatValueStyle; }
            }

            Group { Anchor: (Left: 10); LayoutMode: Middle; FlexWeight: 1;
              Label { Text: "Stamina"; Style: @StatLabelStyle; }
              Label #StatsStamina { Text: "0"; Style: @StatValueStyle; }
            }
          }

          Group { Anchor: (Height: 2); Background: #c1c9d3(0.07); }

          Group {
            LayoutMode: Left;
            Anchor: (Height: 50);

            Group { Anchor: (Left: 10); LayoutMode: Middle; FlexWeight: 1;
              Label { Text: "Mana"; Style: @StatLabelStyle; }
              Label #StatsMana { Text: "0"; Style: @StatValueStyle; }
            }

            Group { Anchor: (Left: 10); LayoutMode: Middle; FlexWeight: 1;
              Label { Text: "Defense"; Style: @StatLabelStyle; }
              Label #StatsDefense { Text: "0"; Style: @StatValueStyle; }
            }
          }
        }
      }
    }
  }

  // ═══════════════════════════════════════════
  // CENTER PANEL: Inventory Grid (715px)
  // ═══════════════════════════════════════════
  Group #StoragePanel {
    Anchor: (Horizontal: @HSpacing, Width: @CenterPanelWidth);
    LayoutMode: Bottom;

    Group {
      // Header
      Group {
        Anchor: (Height: @TitleHeight, Top: 0);
        Background: @HeaderBg;
        Padding: (Top: 7);
        LayoutMode: Left;

        Label #StorageTitle {
          Style: (...@TitleStyle, HorizontalAlignment: Start);
          Padding: (Horizontal: 19);
          Text: "INVENTORY";
          FlexWeight: 1;
        }
      }

      // Content
      Group {
        Anchor: (Top: @TitleHeight);
        Background: @PanelBg;
        Padding: (Full: 17, Top: 8);
        LayoutMode: Top;

        ItemGrid #InventoryGrid {
          Anchor: (Width: (@SlotSize * 9) + (@SlotSpacing * 8) + @GridPadding);
          SlotsPerRow: @SlotsPerRow;
          SlotSize: @SlotSize;
          SlotIconSize: @SlotIconSize;
          SlotSpacing: @SlotSpacing;
          Background: #202c3b(0);
          Padding: (Left: @GridPadding, Top: @GridPadding, Right: 0, Bottom: @GridPadding);
          InventorySectionId: 0;
          AreItemsDraggable: true;
          RenderItemQualityBackground: true;
          DisplayItemQuantity: true;
          ShowScrollbar: true;
          ScrollbarStyle: $C.@DefaultScrollbarStyle;
          InfoDisplay: Tooltip;
          TextTooltipStyle: $C.@DefaultTextTooltipStyle;
        }
      }
    }
  }

  // ═══════════════════════════════════════════
  // RIGHT PANEL: Item Info (410px)
  // ═══════════════════════════════════════════
  Group #InfoPanel {
    Anchor: (Width: @SidePanelWidth, Height: 500, Bottom: 163);

    // Header
    Group {
      Anchor: (Height: @TitleHeight, Top: 0);
      Background: @HeaderBg;
      Padding: (Top: 7);

      Label {
        Style: (...@TitleStyle, HorizontalAlignment: Center);
        Padding: (Horizontal: 19);
        Text: "ITEM INFO";
      }
    }

    // Content
    Group {
      Anchor: (Top: @TitleHeight);
      Background: @PanelBg;
      Padding: (Full: 17);
      LayoutMode: Top;

      // Item icon display (replaces 3D ItemPreviewComponent)
      Group #InfoItemDisplay {
        Anchor: (Height: 120);
        LayoutMode: Center;

        ItemIcon #InfoItemIcon {
          Anchor: (Width: 96, Height: 96);
        }
      }

      // Item name
      Label #InfoItemName {
        Anchor: (Height: 30);
        Style: (Alignment: Center, RenderBold: true, RenderUppercase: true, TextColor: #b4c8c9, FontSize: 15);
      }

      // Separator
      Group { Anchor: (Height: 2); Background: #c1c9d3(0.07); }

      // Rich tooltip area (scrollable)
      Group #InfoTooltipArea {
        LayoutMode: Top;
        FlexWeight: 1;
        Padding: (Top: 8);

        Label #InfoTooltipLabel {
          Style: (TextColor: #878e9c, FontSize: 13, Wrap: true);
          Text: "Hover over an item to see details.";
        }
      }
    }
  }
}

$C.@BackButton {}
```

**Step 2: Verify compilation**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (template is resource-only, no Java changes yet)

**Step 3: Commit**

```bash
git add src/main/resources/Common/UI/Custom/Pages/Nat20_Inventory.ui
git commit -m "feat: add Nat20_Inventory.ui template replicating vanilla 3-panel layout"
```

---

### Task 2: Create the Nat20InventoryPage Java Class

**Files:**
- Create: `src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java`

**Context:** This replaces the existing `Nat20EquipmentPage`. It extends `InteractiveCustomUIPage` to handle typed events from ItemGrid interactions (slot hover for item info panel, equipment drag events for stat updates). On `build()`, it loads the template, populates D&D ability scores and Hytale stats, and sets `TooltipTextSpans` on equipment items. The `handleDataEvent` processes slot hover events to update the right-side item info panel.

**Step 1: Create the page class**

```java
package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.display.Nat20TooltipBuilder;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.ui.builder.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public class Nat20InventoryPage extends InteractiveCustomUIPage<Nat20InventoryPage.InventoryEventData> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String TEMPLATE = "Pages/Nat20_Inventory.ui";

    public static final BuilderCodec<InventoryEventData> EVENT_CODEC =
        BuilderCodec.builder(InventoryEventData.class, InventoryEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                InventoryEventData::setAction, InventoryEventData::getAction)
            .addField(new KeyedCodec<>("SlotIndex", Codec.STRING),
                InventoryEventData::setSlotIndex, InventoryEventData::getSlotIndex)
            .addField(new KeyedCodec<>("GridId", Codec.STRING),
                InventoryEventData::setGridId, InventoryEventData::getGridId)
            .build();

    public Nat20InventoryPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(TEMPLATE);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Populate D&D ability scores
        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData != null) {
            populateAbilityScores(cmd, playerData);
        }

        // Populate Hytale stats
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            populateHytaleStats(cmd, statMap);
        }

        // Populate derived combat stats
        PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;
        populateDerivedStats(cmd, playerStats, statMap);

        // Bind hover events on inventory grid for item info panel
        events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#InventoryGrid",
            EventData.of("Action", "hover").append("GridId", "inventory"), false);
        events.addEventBinding(CustomUIEventBindingType.SlotMouseExited, "#InventoryGrid",
            EventData.of("Action", "unhover").append("GridId", "inventory"), false);

        // Bind hover events on armor grid
        events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#ArmorGrid",
            EventData.of("Action", "hover").append("GridId", "armor"), false);
        events.addEventBinding(CustomUIEventBindingType.SlotMouseExited, "#ArmorGrid",
            EventData.of("Action", "unhover").append("GridId", "armor"), false);
    }

    private void populateAbilityScores(UICommandBuilder cmd, Nat20PlayerData playerData) {
        int[] stats = playerData.getStats();
        for (Stat stat : Stat.values()) {
            int base = stats[stat.index()];
            // Equipment D&D bonus: future feature, hardcode 0 for now
            int equipBonus = 0;
            int total = base + equipBonus;

            cmd.set("#StatBase" + stat.name() + ".Text", String.valueOf(total));

            String bonusText = equipBonus >= 0 ? "(+" + equipBonus + ")" : "(" + equipBonus + ")";
            cmd.set("#StatBonus" + stat.name() + ".Text", bonusText);

            // Color: green if boosted, white if neutral, red if debuffed
            if (equipBonus > 0) {
                cmd.set("#StatBonus" + stat.name() + ".Style.TextColor", "#44dd66");
            } else if (equipBonus < 0) {
                cmd.set("#StatBonus" + stat.name() + ".Style.TextColor", "#cc3333");
            }
        }
    }

    private void populateHytaleStats(UICommandBuilder cmd, EntityStatMap statMap) {
        setStatLabel(cmd, statMap, "Health", "#StatsHealth");
        setStatLabel(cmd, statMap, "Stamina", "#StatsStamina");
        setStatLabel(cmd, statMap, "Mana", "#StatsMana");
        setStatLabel(cmd, statMap, "Defense", "#StatsDefense");
    }

    private void setStatLabel(UICommandBuilder cmd, EntityStatMap statMap,
                               String statName, String selector) {
        try {
            int index = EntityStatType.getAssetMap().getIndex(statName);
            if (index >= 0) {
                float value = statMap.getValue(index);
                cmd.set(selector + ".Text", String.valueOf(Math.round(value)));
            }
        } catch (Exception e) {
            // Stat type not found, leave default
        }
    }

    private void populateDerivedStats(UICommandBuilder cmd, @Nullable PlayerStats playerStats,
                                       @Nullable EntityStatMap statMap) {
        if (playerStats == null) return;

        // AC = 10 + DEX modifier + armor (from EntityStatMap Defense)
        int dexMod = playerStats.getModifier(Stat.DEX);
        int defense = 0;
        if (statMap != null) {
            try {
                int idx = EntityStatType.getAssetMap().getIndex("Defense");
                if (idx >= 0) defense = Math.round(statMap.getValue(idx));
            } catch (Exception ignored) {}
        }
        cmd.set("#DerivedAC.Text", String.valueOf(10 + dexMod + defense));

        // Attack bonus = STR modifier + proficiency bonus
        int strMod = playerStats.getModifier(Stat.STR);
        int profBonus = playerStats.getProficiencyBonus();
        String atkText = (strMod + profBonus >= 0 ? "+" : "") + (strMod + profBonus);
        cmd.set("#DerivedAttack.Text", atkText);

        // Damage: placeholder until weapon parsing
        cmd.set("#DerivedDamage.Text", "—");
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                InventoryEventData data) {
        String action = data.getAction();
        if ("hover".equals(action)) {
            handleSlotHover(ref, store, data);
        } else if ("unhover".equals(action)) {
            clearItemInfo();
        }
    }

    private void handleSlotHover(Ref<EntityStore> ref, Store<EntityStore> store,
                                  InventoryEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        int slotIndex;
        try {
            slotIndex = Integer.parseInt(data.getSlotIndex());
        } catch (NumberFormatException e) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemContainer container;
        String gridId = data.getGridId();
        if ("armor".equals(gridId)) {
            container = inventory.getArmor();
        } else {
            container = inventory.getStorage();
        }

        if (slotIndex < 0 || slotIndex >= container.getCapacity()) return;
        ItemStack stack = container.getItemStack((short) slotIndex);
        if (stack == null || stack.isEmpty()) {
            clearItemInfo();
            return;
        }

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#InfoItemIcon.ItemId", stack.getItemId());
        cmd.set("#InfoItemIcon.Visible", true);

        // Check for Nat20 loot data
        Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

        if (lootData != null) {
            Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
            Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);
            if (displayData != null) {
                cmd.set("#InfoItemName.Text", displayData.name());
                cmd.set("#InfoItemName.Style.TextColor", displayData.rarityColor());

                Message tooltip = Nat20TooltipBuilder.build(displayData, null);
                cmd.set("#InfoTooltipLabel.TextSpans", tooltip);
            } else {
                cmd.set("#InfoItemName.Text", stack.getItemId());
                cmd.set("#InfoItemName.Style.TextColor", "#b4c8c9");
                cmd.set("#InfoTooltipLabel.Text", "");
            }
        } else {
            cmd.set("#InfoItemName.Text", stack.getItemId());
            cmd.set("#InfoItemName.Style.TextColor", "#b4c8c9");
            cmd.set("#InfoTooltipLabel.Text", "");
        }

        sendUpdate(cmd);
    }

    private void clearItemInfo() {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#InfoItemIcon.Visible", false);
        cmd.set("#InfoItemName.Text", "");
        cmd.set("#InfoTooltipLabel.Text", "Hover over an item to see details.");
        sendUpdate(cmd);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        // No cleanup needed
    }

    // ─── Event data class ───
    public static class InventoryEventData {
        private String action = "";
        private String slotIndex = "";
        private String gridId = "";

        public InventoryEventData() {}
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getSlotIndex() { return slotIndex; }
        public void setSlotIndex(String slotIndex) { this.slotIndex = slotIndex; }
        public String getGridId() { return gridId; }
        public void setGridId(String gridId) { this.gridId = gridId; }
    }
}
```

**Step 2: Verify compilation**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java
git commit -m "feat: add Nat20InventoryPage with 3-panel layout, stat sheet, and item info"
```

---

### Task 3: Wire Interceptor to New Page

**Files:**
- Modify: `src/main/java/com/chonbosmods/ui/Nat20InventoryInterceptor.java`

**Context:** Update the interceptor to open `Nat20InventoryPage` instead of `Nat20EquipmentPage`.

**Step 1: Update the interceptor**

Change the import and page construction in `Nat20InventoryInterceptor.onPacket()`:
- Replace `Nat20EquipmentPage page = new Nat20EquipmentPage(playerRef)` with `Nat20InventoryPage page = new Nat20InventoryPage(playerRef)`

**Step 2: Verify compilation**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: In-game smoke test**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew devServer`

Test checklist:
- [ ] Press Tab: custom inventory opens with 3-panel layout
- [ ] Left panel: armor slots visible, stat sheet shows ability scores
- [ ] Center panel: 9-column inventory grid renders with real items
- [ ] Right panel: "Hover over an item to see details." placeholder shows
- [ ] ESC closes the page
- [ ] Tab again reopens
- [ ] Items can be dragged between inventory slots

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/ui/Nat20InventoryInterceptor.java
git commit -m "feat: wire Tab interceptor to new Nat20InventoryPage"
```

---

### Task 4: Add Rich Tooltips to ItemGrid Slots

**Files:**
- Modify: `src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java`

**Context:** The `ItemGrid` elements with `InfoDisplay: Tooltip` and `TextTooltipStyle: $C.@DefaultTextTooltipStyle` will show native Hytale item tooltips. To add Nat20 rich tooltips, we need to iterate each slot's items after `build()` and set `TooltipTextSpans` per slot via `cmd.set("#InventoryGrid[index].TooltipTextSpans", message)`. This is the core feature: every Nat20 item shows its full affix/socket/rarity tooltip on hover.

**Step 1: Add tooltip population method**

After the `build()` method loads the template and binds events, iterate the player's storage inventory and set `TooltipTextSpans` on each slot that has a Nat20 item:

```java
private void populateInventoryTooltips(UICommandBuilder cmd, Player player,
                                        @Nullable PlayerStats playerStats) {
    Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
    ItemContainer storage = player.getInventory().getStorage();

    for (short i = 0; i < storage.getCapacity(); i++) {
        ItemStack stack = storage.getItemStack(i);
        if (stack == null || stack.isEmpty()) continue;

        Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) continue;

        Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);
        if (displayData == null) continue;

        Message tooltip = Nat20TooltipBuilder.build(displayData, null);
        cmd.set("#InventoryGrid[" + i + "].TooltipTextSpans", tooltip);
    }
}
```

Call this from `build()` after the template is loaded. Do the same for `#ArmorGrid` slots.

**Step 2: In-game test**

- [ ] Hover over a Nat20 item in inventory: rich tooltip shows (name, rarity, affixes, sockets)
- [ ] Hover over a vanilla item: default tooltip shows
- [ ] Hover over armor slot with Nat20 item: rich tooltip shows

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java
git commit -m "feat: add Nat20 rich tooltips to all inventory and armor grid slots"
```

---

### Task 5: Live Stat Updates on Equipment Change

**Files:**
- Modify: `src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java`
- Modify: `src/main/java/com/chonbosmods/ui/Nat20InventoryInterceptor.java`

**Context:** When the player drags an item into an equipment slot, the `Nat20EquipmentListener` fires and updates `EntityStatMap`. We need to detect this and `sendUpdate()` the stat values on the page. Two approaches: (a) bind `Dropped` events on `#ArmorGrid` and refresh stats in `handleDataEvent`, or (b) have the interceptor store a reference to the active page and call a refresh method from `Nat20EquipmentListener`. Approach (a) is simpler and self-contained.

**Step 1: Add equipment change event bindings**

In `build()`, add:
```java
events.addEventBinding(CustomUIEventBindingType.Dropped, "#ArmorGrid",
    EventData.of("Action", "equipChange"), false);
events.addEventBinding(CustomUIEventBindingType.Dropped, "#InventoryGrid",
    EventData.of("Action", "equipChange"), false);
```

**Step 2: Add refresh handler**

In `handleDataEvent`, when action is `"equipChange"`:
```java
private void handleEquipChange(Ref<EntityStore> ref, Store<EntityStore> store) {
    Player player = store.getComponent(ref, Player.getComponentType());
    if (player == null) return;

    UICommandBuilder cmd = new UICommandBuilder();

    Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
    if (playerData != null) {
        populateAbilityScores(cmd, playerData);
    }

    EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
    if (statMap != null) {
        populateHytaleStats(cmd, statMap);
    }

    PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;
    populateDerivedStats(cmd, playerStats, statMap);

    sendUpdate(cmd);
}
```

**Step 3: In-game test**

- [ ] Drag armor piece into armor slot: stat values update immediately
- [ ] Remove armor piece: stats revert
- [ ] D&D ability scores show correct base values
- [ ] Hytale stats (Health, Stamina, Mana, Defense) update with equipment

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/ui/Nat20InventoryPage.java
git commit -m "feat: live stat updates when equipment changes via drag-drop"
```

---

### Task 6: Clean Up Old Equipment Page and Sniffer

**Files:**
- Delete: `src/main/java/com/chonbosmods/ui/Nat20EquipmentPage.java`
- Delete: `src/main/resources/Common/UI/Custom/Pages/Nat20_Equipment.ui`
- Delete: `src/main/java/com/chonbosmods/commands/PacketSnifferCommand.java`
- Modify: `src/main/java/com/chonbosmods/commands/Nat20Command.java` (remove `EquipmentCommand` and `PacketSnifferCommand` subcommands)
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (remove `PacketSnifferCommand.cleanup()`)
- Delete: `src/main/java/com/chonbosmods/commands/EquipmentCommand.java`

**Context:** The old equipment page and packet sniffer were debug/PoC tools. Now that the full inventory page replaces them, clean up.

**Step 1: Remove old files and references**

Remove `addSubCommand(new EquipmentCommand())` and `addSubCommand(new PacketSnifferCommand())` from `Nat20Command.java`. Remove `PacketSnifferCommand.cleanup()` and its import from `Natural20.java`. Delete the four files listed above.

**Step 2: Verify compilation**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove old equipment page, sniffer command, and equipment command"
```

---

### Task 7: Template Polish and Texture Placeholders

**Files:**
- Modify: `src/main/resources/Common/UI/Custom/Pages/Nat20_Inventory.ui`
- Create: placeholder texture PNGs if needed (can reuse vanilla paths or use solid colors initially)

**Context:** The template references `Nat20_ContainerPatch.png` and `Nat20_ContainerHeader.png`. For the initial version, use solid color backgrounds instead of textures (since plugin textures can't be set at runtime via `cmd.set`). Later, we can add custom textures to the resources directory. Also adjust any layout issues found during testing.

**Step 1: Replace texture references with color backgrounds**

For `@PanelBg` and `@HeaderBg`, use solid colors that approximate the vanilla look:
```
@PanelBg = (Color: #1a2433(0.95));
@HeaderBg = (Color: #1e2d40(0.95));
```

**Step 2: In-game visual test**

- [ ] All three panels render with visible boundaries
- [ ] Text is readable against backgrounds
- [ ] Layout proportions match vanilla inventory
- [ ] Stat sheet panel is visually distinct in the character panel center

**Step 3: Commit**

```bash
git add src/main/resources/Common/UI/Custom/Pages/Nat20_Inventory.ui
git commit -m "fix: use color backgrounds for initial template, remove texture refs"
```

---

## Future Tasks (Not in This Plan)

These are documented for future implementation plans:

1. **D&D ability score affixes**: New affix type that modifies `Nat20PlayerData.stats` instead of `EntityStatMap`. Requires new `AffixType.ABILITY_SCORE` or extending STAT affixes with a `"targetAbilityScore"` field.

2. **Comparison tooltips**: When hovering an inventory item while an equipment slot has a Nat20 item, show green/red delta indicators on the tooltip using `ComparisonDeltas`.

3. **Custom textures**: Create proper 9-patch PNGs for container backgrounds, headers, slot backgrounds that match vanilla visual quality.

4. **Hotbar grid section**: Add a hotbar `ItemGrid` section below the main inventory grid, bound to the hotbar `InventorySectionId`.

5. **Top navigation bar**: Add the `GamePageNavigation`-style tab bar with page switching and version display.

6. **Item info panel 3D preview**: If Hytale SDK later exposes `ItemPreviewComponent` for custom pages, integrate it.

7. **Filter tabs and autosort**: Wire `TabNavigation` for item category filtering and add autosort button functionality.

8. **Sound effects**: Play inventory open/close sounds matching vanilla (`InventoryOpen.ogg`, `InventoryClose.ogg`).
