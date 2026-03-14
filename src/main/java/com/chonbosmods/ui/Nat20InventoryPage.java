package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.display.Nat20TooltipBuilder;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Custom inventory page that displays D&D ability scores, Hytale stats,
 * derived stats, and interactive item grids with a right-side info panel.
 *
 * The page extends InteractiveCustomUIPage to receive typed hover events
 * from the inventory and armor item grids.
 */
public class Nat20InventoryPage extends InteractiveCustomUIPage<Nat20InventoryPage.InventoryEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|InvPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_Inventory.ui";

    public static final BuilderCodec<InventoryEventData> EVENT_CODEC =
            BuilderCodec.builder(InventoryEventData.class, InventoryEventData::new)
                    .addField(new KeyedCodec<>("Action", Codec.STRING),
                            InventoryEventData::setAction, InventoryEventData::getAction)
                    .addField(new KeyedCodec<>("GridId", Codec.STRING),
                            InventoryEventData::setGridId, InventoryEventData::getGridId)
                    .addField(new KeyedCodec<>("MouseOverIndex", Codec.STRING),
                            InventoryEventData::setSlotIndex, InventoryEventData::getSlotIndex)
                    .build();

    public Nat20InventoryPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("Could not resolve player for inventory page");
            return;
        }

        // Resolve player data for D&D stats
        @Nullable Nat20PlayerData playerData =
                store.getComponent(ref, Natural20.getPlayerDataType());
        @Nullable PlayerStats playerStats =
                playerData != null ? PlayerStats.from(playerData) : null;

        // Populate all stat sections
        populateAbilityScores(cmd, playerStats);
        populateHytaleStats(cmd, ref, store);
        populateDerivedStats(cmd, playerStats, ref, store);

        // Set rich tooltips on inventory and armor grid slots
        populateInventoryTooltips(cmd, player, playerStats);

        // Clear info panel to default state
        clearItemInfo(cmd);

        // Bind hover events on inventory grid
        events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#InventoryGrid",
                EventData.of("Action", "hover").append("GridId", "inventory"), false);
        events.addEventBinding(CustomUIEventBindingType.SlotMouseExited, "#InventoryGrid",
                EventData.of("Action", "unhover").append("GridId", "inventory"), false);

        // Bind hover events on armor grid
        events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#ArmorGrid",
                EventData.of("Action", "hover").append("GridId", "armor"), false);
        events.addEventBinding(CustomUIEventBindingType.SlotMouseExited, "#ArmorGrid",
                EventData.of("Action", "unhover").append("GridId", "armor"), false);

        // Bind drop events for live stat updates on equipment change
        events.addEventBinding(CustomUIEventBindingType.Dropped, "#ArmorGrid",
                EventData.of("Action", "equipChange"), false);
        events.addEventBinding(CustomUIEventBindingType.Dropped, "#InventoryGrid",
                EventData.of("Action", "equipChange"), false);
    }

    /**
     * Populate the six D&D ability score labels: base value and equipment bonus.
     * Equipment D&D bonus is hardcoded to 0 for now (future feature).
     */
    private void populateAbilityScores(UICommandBuilder cmd, @Nullable PlayerStats playerStats) {
        for (Stat stat : Stat.values()) {
            int baseValue = playerStats != null ? playerStats.getStat(stat) : 10;
            // Equipment D&D bonus: future feature, hardcode 0 for now
            int equipBonus = 0;
            int total = baseValue + equipBonus;

            // Total score: base + equipment bonus
            cmd.set("#StatBase" + stat.name() + ".Text", String.valueOf(total));

            // Equipment bonus label: colored green if positive, red if negative
            String bonusText = equipBonus >= 0 ? "(+" + equipBonus + ")" : "(" + equipBonus + ")";
            if (equipBonus > 0) {
                cmd.set("#StatBonus" + stat.name() + ".Style.TextColor", "#44dd66");
            } else if (equipBonus < 0) {
                cmd.set("#StatBonus" + stat.name() + ".Style.TextColor", "#cc3333");
            }
            cmd.set("#StatBonus" + stat.name() + ".Text", bonusText);
        }
    }

    /**
     * Populate Hytale entity stats: Health, Stamina, Mana, Defense.
     */
    private void populateHytaleStats(UICommandBuilder cmd, Ref<EntityStore> ref,
                                     Store<EntityStore> store) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            cmd.set("#StatsHealth.Text", "—");
            cmd.set("#StatsStamina.Text", "—");
            cmd.set("#StatsMana.Text", "—");
            cmd.set("#StatsDefense.Text", "—");
            return;
        }

        setHytaleStat(cmd, statMap, "Health", "#StatsHealth");
        setHytaleStat(cmd, statMap, "Stamina", "#StatsStamina");
        setHytaleStat(cmd, statMap, "Mana", "#StatsMana");
        setHytaleStat(cmd, statMap, "Defense", "#StatsDefense");
    }

    private void setHytaleStat(UICommandBuilder cmd, EntityStatMap statMap,
                               String statName, String selector) {
        int index = EntityStatType.getAssetMap().getIndex(statName);
        if (index < 0) {
            cmd.set(selector + ".Text", "—");
            return;
        }
        float value = statMap.get(index).get();
        cmd.set(selector + ".Text", String.format("%.0f", value));
    }

    /**
     * Populate derived combat stats: AC, Attack bonus, Damage.
     */
    private void populateDerivedStats(UICommandBuilder cmd, @Nullable PlayerStats playerStats,
                                      Ref<EntityStore> ref, Store<EntityStore> store) {
        int dexMod = playerStats != null ? playerStats.getModifier(Stat.DEX) : 0;
        int strMod = playerStats != null ? playerStats.getModifier(Stat.STR) : 0;
        int profBonus = playerStats != null ? playerStats.getProficiencyBonus() : 2;

        // Defense from entity stats
        float defense = 0f;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            int defIndex = EntityStatType.getAssetMap().getIndex("Defense");
            if (defIndex >= 0) {
                defense = statMap.get(defIndex).get();
            }
        }

        // AC = 10 + DEX modifier + defense
        int ac = 10 + dexMod + (int) defense;
        cmd.set("#DerivedAC.Text", String.valueOf(ac));

        // Attack = STR modifier + proficiency bonus
        int attack = strMod + profBonus;
        cmd.set("#DerivedAttack.Text", formatBonus(attack));

        // Damage: placeholder for now
        cmd.set("#DerivedDamage.Text", "—");
    }

    /**
     * Set TooltipTextSpans on each inventory and armor grid slot that contains a Nat20 item.
     */
    private void populateInventoryTooltips(UICommandBuilder cmd, Player player,
                                           @Nullable PlayerStats playerStats) {
        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();

        // Storage inventory grid
        ItemContainer storage = player.getInventory().getStorage();
        for (int i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack((short) i);
            if (stack == null || stack.isEmpty()) continue;

            Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);
            if (displayData == null) continue;

            Message tooltip = Nat20TooltipBuilder.build(displayData, null);
            cmd.set("#InventoryGrid[" + i + "].TooltipTextSpans", tooltip);
        }

        // Armor grid
        ItemContainer armor = player.getInventory().getArmor();
        for (int i = 0; i < armor.getCapacity(); i++) {
            ItemStack stack = armor.getItemStack((short) i);
            if (stack == null || stack.isEmpty()) continue;

            Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);
            if (displayData == null) continue;

            Message tooltip = Nat20TooltipBuilder.build(displayData, null);
            cmd.set("#ArmorGrid[" + i + "].TooltipTextSpans", tooltip);
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                InventoryEventData data) {
        String action = data.getAction();
        if (action == null) return;

        if ("hover".equals(action)) {
            handleHover(ref, store, data);
        } else if ("unhover".equals(action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            clearItemInfo(cmd);
            sendUpdate(cmd);
        } else if ("equipChange".equals(action)) {
            handleEquipChange(ref, store);
        }
    }

    private void handleHover(Ref<EntityStore> ref, Store<EntityStore> store,
                             InventoryEventData data) {
        int slotIndex;
        try {
            slotIndex = Integer.parseInt(data.getSlotIndex());
        } catch (NumberFormatException | NullPointerException e) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

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
            UICommandBuilder cmd = new UICommandBuilder();
            clearItemInfo(cmd);
            sendUpdate(cmd);
            return;
        }

        UICommandBuilder cmd = new UICommandBuilder();

        // Set item icon
        cmd.set("#InfoItemIcon.ItemId", stack.getItemId());
        cmd.set("#InfoItemIcon.Visible", true);

        // Resolve Nat20 loot data for rich tooltip
        @Nullable Nat20PlayerData playerData =
                store.getComponent(ref, Natural20.getPlayerDataType());
        @Nullable PlayerStats playerStats =
                playerData != null ? PlayerStats.from(playerData) : null;

        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
        Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);

        if (displayData != null) {
            // Rich item name with rarity color
            cmd.set("#InfoItemName.TextSpans",
                    Message.raw(displayData.name()).bold(true).color(displayData.rarityColor()));

            // Full tooltip in the info label
            Message tooltip = Nat20TooltipBuilder.build(displayData, null);
            cmd.set("#InfoTooltipLabel.TextSpans", tooltip);
        } else {
            // Non-Nat20 item: show basic item ID
            cmd.set("#InfoItemName.Text", stack.getItemId());
            cmd.set("#InfoTooltipLabel.Text", "");
        }

        sendUpdate(cmd);
    }

    /**
     * Refresh all stat displays when equipment changes via drag-drop.
     */
    private void handleEquipChange(Ref<EntityStore> ref, Store<EntityStore> store) {
        @Nullable Nat20PlayerData playerData =
                store.getComponent(ref, Natural20.getPlayerDataType());
        @Nullable PlayerStats playerStats =
                playerData != null ? PlayerStats.from(playerData) : null;

        UICommandBuilder cmd = new UICommandBuilder();
        populateAbilityScores(cmd, playerStats);
        populateHytaleStats(cmd, ref, store);
        populateDerivedStats(cmd, playerStats, ref, store);

        // Refresh tooltips since items may have moved
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            populateInventoryTooltips(cmd, player, playerStats);
        }

        sendUpdate(cmd);
    }

    /**
     * Reset the right-side info panel to its default empty state.
     */
    private void clearItemInfo(UICommandBuilder cmd) {
        cmd.set("#InfoItemIcon.Visible", false);
        cmd.set("#InfoItemName.Text", "");
        cmd.set("#InfoTooltipLabel.Text", "Hover over an item to see details.");
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    }

    private String formatBonus(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    // --- Inner event data class ---

    public static class InventoryEventData {
        private String action = "";
        private String slotIndex = "";
        private String gridId = "";

        public InventoryEventData() {}

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action != null ? action : ""; }

        public String getSlotIndex() { return slotIndex; }
        public void setSlotIndex(String slotIndex) { this.slotIndex = slotIndex != null ? slotIndex : ""; }

        public String getGridId() { return gridId; }
        public void setGridId(String gridId) { this.gridId = gridId != null ? gridId : ""; }
    }
}
