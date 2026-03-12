package com.chonbosmods.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Diagnostic page: loads a template and tries ONE selector in build().
 * Run the command repeatedly with different selectors.
 * If client crashes: that selector is invalid. If page opens: it works.
 */
public class UITemplateProbePage extends InteractiveCustomUIPage<UITemplateProbePage.ProbeEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Probe");

    public static final BuilderCodec<ProbeEventData> EVENT_CODEC = BuilderCodec.builder(ProbeEventData.class, ProbeEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), ProbeEventData::setAction, ProbeEventData::getAction)
            .build();

    private final String templatePath;
    private final String selector;    // null = bare load
    private final String valueType;   // "string", "message", "clear", "none"
    private final Runnable onDismissCallback;

    public UITemplateProbePage(PlayerRef playerRef, String templatePath,
                                String selector, String valueType,
                                Runnable onDismissCallback) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
        this.templatePath = templatePath;
        this.selector = selector;
        this.valueType = valueType;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        LOGGER.atInfo().log("PROBE: template='" + templatePath + "' selector='" + selector + "' type='" + valueType + "'");

        cmd.append(templatePath);

        // Full-page test modes: no selector needed, just valueType
        if ("customdlg".equals(valueType)) {
            buildCustomDialogueTest(cmd, events);
            return;
        }

        if (selector != null && !selector.isEmpty()) {
            if (valueType.startsWith("append:")) {
                // e.g. "append:Pages/ShopElementButton.ui"
                String childTemplate = valueType.substring(7);
                cmd.clear(selector);
                cmd.append(selector, childTemplate);
                LOGGER.atInfo().log("PROBE: cleared + appended '" + childTemplate + "' into '" + selector + "'");
            } else if (valueType.startsWith("inline:")) {
                // e.g. "inline:Label #Test { Text: Hello; }"
                String xmlContent = valueType.substring(7);
                cmd.appendInline(selector, xmlContent);
                LOGGER.atInfo().log("PROBE: appendInline into '" + selector + "': " + xmlContent);
            } else if (valueType.equals("inlinetest")) {
                // Minimal test: just labels, no special chars, no events
                cmd.clear(selector);

                cmd.appendInline(selector, "Label #L1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"NPC SPEECH HERE\"; }");
                cmd.appendInline(selector, "Label #L2 { Anchor: (Height: 30); Style: (TextColor: #aaddff, FontSize: 14); Text: \"Option One\"; }");
                cmd.appendInline(selector, "Label #L3 { Anchor: (Height: 30); Style: (TextColor: #ff8888, FontSize: 14); Text: \"Goodbye\"; }");

                LOGGER.atInfo().log("PROBE: inlinetest minimal - 3 labels, no events");
            } else if (valueType.equals("inlinetest2")) {
                // HYBRID: inline Labels for display + BarterTradeRow for clickable options
                cmd.clear(selector);

                // Row 0: Inline label for NPC speech (display only)
                cmd.appendInline(selector, "Label #Speech { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business.\"; }");

                // Row 1: Inline label for more speech (display only)
                cmd.appendInline(selector, "Label #Speech2 { Anchor: (Height: 25); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"No one passes without the captain's orders.\"; }");

                // Row 2: BarterTradeRow for clickable option
                cmd.append(selector, "Pages/BarterTradeRow.ui");
                String row0 = selector + "[2]";
                cmd.set(row0 + " #HaveNeedLabel.Text", "I'm just passing through.");
                cmd.set(row0 + " #HaveNeedLabel.Style.TextColor", "#aaddff");
                cmd.set(row0 + " #Stock.Visible", false);
                cmd.set(row0 + " #OutOfStockOverlay.Visible", false);
                cmd.set(row0 + " #OutputQuantity.Text", "");
                cmd.set(row0 + " #InputQuantity.Text", "");
                cmd.set(row0 + " #TradeButton.Disabled", false);
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        row0 + " #TradeButton",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "option1"),
                        false);

                // Row 3: BarterTradeRow for goodbye
                cmd.append(selector, "Pages/BarterTradeRow.ui");
                String row1 = selector + "[3]";
                cmd.set(row1 + " #HaveNeedLabel.Text", "[ Goodbye ]");
                cmd.set(row1 + " #HaveNeedLabel.Style.TextColor", "#ff8888");
                cmd.set(row1 + " #Stock.Visible", false);
                cmd.set(row1 + " #OutOfStockOverlay.Visible", false);
                cmd.set(row1 + " #OutputQuantity.Text", "");
                cmd.set(row1 + " #InputQuantity.Text", "");
                cmd.set(row1 + " #TradeButton.Disabled", false);
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        row1 + " #TradeButton",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "goodbye"),
                        false);

                LOGGER.atInfo().log("PROBE: inlinetest2 HYBRID - inline labels + BarterTradeRow buttons");
            } else if (valueType.equals("inlinetest3")) {
                // Test: BarterTradeRow with everything hidden except text + button
                cmd.clear(selector);
                cmd.append(selector, "Pages/BarterTradeRow.ui");
                String row = selector + "[0]";

                // Set the text
                cmd.set(row + " #HaveNeedLabel.Text", "I'm just passing through.");
                cmd.set(row + " #HaveNeedLabel.Style.TextColor", "#aaddff");

                // Hide everything we can
                cmd.set(row + " #Stock.Visible", false);
                cmd.set(row + " #OutOfStockOverlay.Visible", false);
                cmd.set(row + " #OutputQuantity.Text", "");
                cmd.set(row + " #InputQuantity.Text", "");

                // Try hiding the icon slots
                try { cmd.set(row + " #OutputSlot.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("OutputSlot.Visible failed"); }
                try { cmd.set(row + " #InputSlot.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("InputSlot.Visible failed"); }
                try { cmd.set(row + " #InputSlotBorder.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("InputSlotBorder.Visible failed"); }
                try { cmd.set(row + " #OutputSlotBorder.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("OutputSlotBorder.Visible failed"); }

                // Try hiding the Cost label (unknown selector - guess)
                try { cmd.set(row + " #CostLabel.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("CostLabel.Visible failed"); }
                try { cmd.set(row + " #Cost.Visible", false); } catch (Exception e) { LOGGER.atInfo().log("Cost.Visible failed"); }

                // Disable button (just to see the card without button highlight)
                cmd.set(row + " #TradeButton.Disabled", false);

                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        row + " #TradeButton",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "click"),
                        false);

                LOGGER.atInfo().log("PROBE: inlinetest3 - BarterTradeRow with slots hidden");
            } else if (valueType.equals("inlinetest4")) {
                // Test: ShopPage hybrid - inline labels + ShopElementButton for clicks
                // NOTE: call this with shop:#ElementList:inlinetest4
                cmd.clear(selector);

                // Inline labels for NPC speech
                cmd.appendInline(selector, "Label #Speech1 { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business.\"; }");
                cmd.appendInline(selector, "Label #Speech2 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"No one passes without the captain's orders.\"; }");

                // Inline label for disposition
                cmd.appendInline(selector, "Label #Disp { Anchor: (Height: 25); Style: (TextColor: #aaaaaa, FontSize: 12); Text: \"Disposition: 45 / 100\"; }");

                // ShopElementButton for clickable option
                cmd.append(selector, "Pages/ShopElementButton.ui");
                cmd.set(selector + "[3] #Name.Text", "I'm just passing through.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[3]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "option1"),
                        false);

                // ShopElementButton for skill-gated option
                cmd.append(selector, "Pages/ShopElementButton.ui");
                cmd.set(selector + "[4] #Name.Text", "[CHA] Perhaps we can work something out...");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[4]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "option2"),
                        false);

                // ShopElementButton for goodbye
                cmd.append(selector, "Pages/ShopElementButton.ui");
                cmd.set(selector + "[5] #Name.Text", "[ Goodbye ]");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[5]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "goodbye"),
                        false);

                LOGGER.atInfo().log("PROBE: inlinetest4 - ShopPage hybrid with inline labels + ShopElementButtons");
            } else if (valueType.equals("inlinetest5")) {
                // BasicTextButton: try #Text.Text selector
                cmd.clear(selector);
                cmd.append(selector, "Pages/BasicTextButton.ui");
                // BasicTextButton with long text
                cmd.set(selector + "[0].Text", "I'm just passing through, I mean no harm to anyone here.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[0]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "btn1"),
                        false);

                // ShopElementButton with same text for comparison
                cmd.append(selector, "Pages/ShopElementButton.ui");
                cmd.set(selector + "[1] #Name.Text", "I'm just passing through, I mean no harm to anyone here.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[1]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "btn2"),
                        false);

                // Another BasicTextButton with medium text
                cmd.append(selector, "Pages/BasicTextButton.ui");
                cmd.set(selector + "[2].Text", "[CHA] Perhaps we can work something out...");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[2]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "btn3"),
                        false);

                LOGGER.atInfo().log("PROBE: inlinetest5 - BasicTextButton vs ShopElementButton truncation test");
            } else if (valueType.equals("inlinetest6")) {
                // FULL HYBRID: inline Labels + BasicTextButtons (the winning combo)
                cmd.clear(selector);

                // NPC speech
                cmd.appendInline(selector, "Label #Speech1 { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business. No one passes without the captain's orders.\"; }");

                // Disposition
                cmd.appendInline(selector, "Label #Disp { Anchor: (Height: 25); Style: (TextColor: #aaaaaa, FontSize: 12); Text: \"Disposition: 45 / 100\"; }");

                // Spacer
                cmd.appendInline(selector, "Label #Spacer { Anchor: (Height: 10); Text: \"\"; }");

                // BasicTextButton options
                cmd.append(selector, "Pages/BasicTextButton.ui");
                cmd.set(selector + "[3].Text", "I'm just passing through, I mean no harm.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[3]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "option1"),
                        false);

                cmd.append(selector, "Pages/BasicTextButton.ui");
                cmd.set(selector + "[4].Text", "[CHA] Perhaps we can work something out, captain...");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[4]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "option2"),
                        false);

                cmd.append(selector, "Pages/BasicTextButton.ui");
                cmd.set(selector + "[5].Text", "Tell me about the captain.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[5]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "topic1"),
                        false);

                cmd.append(selector, "Pages/BasicTextButton.ui");
                cmd.set(selector + "[6].Text", "Goodbye.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        selector + "[6]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "goodbye"),
                        false);

                LOGGER.atInfo().log("PROBE: inlinetest6 - FULL HYBRID: inline labels + BasicTextButtons");
            } else if (valueType.equals("inlinetest7")) {
                // Try insertBeforeInline to add NPC name ABOVE #ElementList
                cmd.clear(selector);
                cmd.insertBeforeInline(selector, "Label #NpcName { Anchor: (Height: 40); Style: (TextColor: #ffffff, FontSize: 18); Text: \"Guard Captain\"; }");
                cmd.appendInline(selector, "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"Speech text here\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest7 - insertBeforeInline above #ElementList");
            } else if (valueType.equals("inlinetest8")) {
                // Try cmd.remove() on index [0] to remove the title element (first child of root)
                cmd.clear(selector);
                cmd.remove("[0]");
                cmd.appendInline(selector, "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"Removed [0]\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest8 - remove [0]");
            } else if (valueType.equals("inlinetest9")) {
                // Try setting .Visible on the title via index: [0].Visible = false
                cmd.clear(selector);
                cmd.set("[0].Visible", false);
                cmd.appendInline(selector, "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"Hidden [0]\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest9 - hide [0].Visible");
            } else if (valueType.equals("inlinetest10")) {
                // WarpListPage: just #WarpList with inline label + BasicTextButton (no title set)
                cmd.append("Pages/WarpListPage.ui");
                cmd.clear("#WarpList");
                cmd.appendInline("#WarpList", "Label #Speech { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business.\"; }");
                cmd.append("#WarpList", "Pages/BasicTextButton.ui");
                cmd.set("#WarpList[1].Text", "I'm just passing through.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        "#WarpList[1]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "opt1"),
                        false);
                LOGGER.atInfo().log("PROBE: inlinetest10 - WarpListPage #WarpList hybrid (no title)");
            } else if (valueType.equals("inlinetest11")) {
                // WarpListPage: #WarpList hybrid + #Header.Text title
                cmd.append("Pages/WarpListPage.ui");
                cmd.set("#Header.Text", "Guard Captain");
                cmd.clear("#WarpList");
                cmd.appendInline("#WarpList", "Label #Speech { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business.\"; }");
                cmd.append("#WarpList", "Pages/BasicTextButton.ui");
                cmd.set("#WarpList[1].Text", "I'm just passing through.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        "#WarpList[1]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "opt1"),
                        false);
                LOGGER.atInfo().log("PROBE: inlinetest11 - WarpListPage #WarpList hybrid + #Header.Text");
            } else if (valueType.equals("inlinetest12")) {
                // WarpListPage: #WarpList hybrid + #Title.Text
                cmd.append("Pages/WarpListPage.ui");
                cmd.set("#Title.Text", "Guard Captain");
                cmd.clear("#WarpList");
                cmd.appendInline("#WarpList", "Label #Speech { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Testing Title.Text\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest12 - WarpListPage #Title.Text");
            } else if (valueType.equals("inlinetest13")) {
                // WarpListPage: #WarpList as container (like #ElementList)
                cmd.append("Pages/WarpListPage.ui");
                cmd.clear("#WarpList");
                cmd.appendInline("#WarpList", "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"WarpList container works\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest13 - WarpListPage #WarpList container");
            } else if (valueType.equals("inlinetest14")) {
                // WarpListPage: #EntryList as container
                cmd.append("Pages/WarpListPage.ui");
                cmd.clear("#EntryList");
                cmd.appendInline("#EntryList", "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"EntryList container works\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest14 - WarpListPage #EntryList container");
            } else if (valueType.equals("inlinetest15")) {
                // WarpListPage: #ElementList as container (same as ShopPage?)
                cmd.append("Pages/WarpListPage.ui");
                cmd.clear("#ElementList");
                cmd.appendInline("#ElementList", "Label #S1 { Anchor: (Height: 30); Style: (TextColor: #ffcc00, FontSize: 14); Text: \"ElementList in WarpList works\"; }");
                LOGGER.atInfo().log("PROBE: inlinetest15 - WarpListPage #ElementList container");
            } else if (valueType.equals("inlinetest16")) {
                // WarpListPage: full hybrid test with #ElementList
                cmd.append("Pages/WarpListPage.ui");
                cmd.clear("#ElementList");
                cmd.appendInline("#ElementList", "Label #Speech { Anchor: (Height: 40); Style: (TextColor: #ffcc00, FontSize: 16); Text: \"Halt! State your business.\"; }");
                cmd.appendInline("#ElementList", "Label #Disp { Anchor: (Height: 25); Style: (TextColor: #aaaaaa, FontSize: 12); Text: \"Disposition: 45 / 100\"; }");
                cmd.append("#ElementList", "Pages/BasicTextButton.ui");
                cmd.set("#ElementList[2].Text", "I'm just passing through.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        "#ElementList[2]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "opt1"),
                        false);
                cmd.append("#ElementList", "Pages/BasicTextButton.ui");
                cmd.set("#ElementList[3].Text", "Goodbye.");
                events.addEventBinding(
                        com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                        "#ElementList[3]",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "goodbye"),
                        false);
                LOGGER.atInfo().log("PROBE: inlinetest16 - WarpListPage full hybrid with #ElementList");
            } else if (valueType.equals("rowprobe")) {
                // Append a BarterTradeRow and probe its sub-elements
                cmd.clear(selector);
                cmd.append(selector, "Pages/BarterTradeRow.ui");
                LOGGER.atInfo().log("PROBE: appended BarterTradeRow into " + selector + ", probing sub-elements...");

                // Real selectors from BarterPage bytecode bootstrap methods
                String row = "#TradeGrid[0]";

                // .Text properties take plain String (NOT Message)
                cmd.set(row + " #OutputQuantity.Text", "5");
                cmd.set(row + " #InputQuantity.Text", "3");
                cmd.set(row + " #HaveNeedLabel.Text", "HAVE_NEED");
                cmd.set(row + " #OutOfStockLabel.Text", "NO_STOCK");

                // .TextSpans takes Message
                cmd.set(row + " #Stock.TextSpans", Message.raw("STOCK_TEXT").color("#00FF00"));

                // Visibility (bool)
                cmd.set(row + " #Stock.Visible", true);
                cmd.set(row + " #OutOfStockOverlay.Visible", false);

                // Button state
                cmd.set(row + " #TradeButton.Disabled", false);

                // Colors (string hex)
                cmd.set(row + " #HaveNeedLabel.Style.TextColor", "#00FF00");
                cmd.set(row + " #InputSlotBorder.Background", "#FF00FF");
                cmd.set(row + " #TradeButton.Style.Default.Background", "#0000FF");

                LOGGER.atInfo().log("PROBE: set all known BarterTradeRow sub-elements");
            } else {
                switch (valueType) {
                    case "string" -> cmd.set(selector, "PROBE_TEST");
                    case "message" -> cmd.set(selector, Message.raw("PROBE_TEST").color("#FF00FF"));
                    case "clear" -> cmd.clear(selector);
                    case "int" -> cmd.set(selector, 42);
                    case "bool" -> cmd.set(selector, true);
                    default -> LOGGER.atWarning().log("PROBE: unknown type '" + valueType + "'");
                }
            }
            LOGGER.atInfo().log("PROBE: set '" + selector + "' as " + valueType + " — if page opens, selector is valid!");
        } else {
            LOGGER.atInfo().log("PROBE: bare load, no selector");
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, ProbeEventData data) {
        LOGGER.atInfo().log("PROBE EVENT: action='" + data.getAction() + "'");
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        LOGGER.atInfo().log("PROBE: dismissed");
        if (onDismissCallback != null) onDismissCallback.run();
    }

    public void closePage() {
        close();
    }

    private void buildCustomDialogueTest(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.set("#NPCName.Text", "Guard Captain");

        cmd.set("#Disposition.Text", "Disposition: 45 / 100");
        cmd.set("#Disposition.Visible", true);

        cmd.set("#SpeechText.Text", "Halt! State your business. No one passes through this checkpoint without the captain's explicit orders. The roads have been dangerous lately, and we can't afford to let just anyone through.");

        cmd.set("#Response1.Text", "I'm just passing through, I mean no harm.");
        cmd.set("#Response1.Visible", true);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                "#Response1",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Type", "select").append("Id", "pass_through"),
                false);

        cmd.set("#Response2.Text", "[CHA 15] Perhaps we can work something out, captain...");
        cmd.set("#Response2.Visible", true);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                "#Response2",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Type", "select").append("Id", "persuade_check"),
                false);

        cmd.set("#Response3.Text", "[STR 13] Step aside, or I'll move you myself.");
        cmd.set("#Response3.Visible", true);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                "#Response3",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Type", "select").append("Id", "intimidate_check"),
                false);

        cmd.set("#Response4.Text", "Tell me about these dangerous roads.");
        cmd.set("#Response4.Visible", true);
        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                "#Response4",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Type", "select").append("Id", "ask_roads"),
                false);

        events.addEventBinding(
                com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating,
                "#LeaveButton",
                com.hypixel.hytale.server.core.ui.builder.EventData.of("Type", "goodbye"),
                false);

        LOGGER.atInfo().log("PROBE: customdlg - Nat20_Dialogue.ui custom template test");
    }

    public static class ProbeEventData {
        private String action = "";
        public ProbeEventData() {}
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
}
