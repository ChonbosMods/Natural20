package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.background.Background;
import com.chonbosmods.background.BackgroundCommitter;
import com.chonbosmods.background.KitItem;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background picker with a two-screen flow (Tasks 5.1 + 5.2).
 *
 * <p>Screen 1 (grid): 2-column grid of 15 buttons, each labelled with a
 * background's {@link Background#displayName()}. Element IDs {@code #BgBtn1} ..
 * {@code #BgBtn15} map to {@code Background.values()[N-1]}.
 *
 * <p>Screen 2 (detail): shown after the player clicks a grid button. Displays
 * the chosen background's name, a placeholder flavor line, the {@code +3/+3}
 * stat bonuses, and the starter kit (one line per {@link KitItem}). Two
 * buttons: {@code #BgBackBtn} returns to the grid; {@code #BgConfirmBtn}
 * commits the choice via {@link BackgroundCommitter#commit} and closes the
 * page.
 *
 * <p><b>Hard lock:</b> the page is constructed with
 * {@link CustomPageLifetime#CantClose}: the client cannot dismiss it via Esc,
 * click-out, or any other player input. The only way out is the server calling
 * {@link #closePage()} (or the Confirm-path close below), which routes through
 * {@code CustomUIPage.close()} -> {@code PageManager.setPage(ref, store, Page.None)}.
 *
 * <p>Screen toggling uses two sibling Groups in the {@code .ui} template:
 * {@code #BgGridGroup} and {@code #BgDetailGroup}. {@code build()} sets the
 * visibility of both every time it runs, driven by whether
 * {@link #selectedBackground} is null (grid) or set (detail). State changes
 * trigger a {@code rebuild()} so the whole panel re-renders with the new
 * visibility + populated detail fields.
 */
public class BackgroundPickerPage extends InteractiveCustomUIPage<BackgroundPickerPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|BackgroundPickerPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_BackgroundPicker.ui";

    /** Max number of kit item lines rendered on the detail screen. All current
     *  backgrounds have <= 2 kit items; reserve a third slot as headroom. If a
     *  future background adds a 4th item, bump this constant and add the
     *  matching {@code #BgDetailKitLine4} label in the template. */
    private static final int MAX_KIT_LINES = 3;

    /** Same value as {@code PageDialoguePresenter.PAGE_TRANSITION_DELAY_MS}.
     *  Hytale's PageManager crashes if a new page is opened inside the event
     *  handler of the page being dismissed; the post-Confirm hand-off to
     *  {@code Nat20DialoguePage} is deferred by this many ms so the handler
     *  stack unwinds first. */
    private static final long PAGE_TRANSITION_DELAY_MS = 50;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Nat20-PickerTransition");
                t.setDaemon(true);
                return t;
            });

    public static final BuilderCodec<PageEventData> EVENT_CODEC =
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
                    .addField(new KeyedCodec<>("Type", Codec.STRING),
                            PageEventData::setType, PageEventData::getType)
                    .addField(new KeyedCodec<>("Id", Codec.STRING),
                            PageEventData::setId, PageEventData::getId)
                    .build();

    /** Null = screen 1 (grid); non-null = screen 2 (detail for this bg). */
    private Background selectedBackground;

    public BackgroundPickerPage(PlayerRef playerRef) {
        // CantClose = the client cannot dismiss this page through any user input
        // (Esc, click-out, etc). Server-initiated close() still works.
        super(playerRef, CustomPageLifetime.CantClose, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events,
                      Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        boolean onDetail = selectedBackground != null;
        cmd.set("#BgGridGroup.Visible", !onDetail);
        cmd.set("#BgDetailGroup.Visible", onDetail);

        if (onDetail) {
            cmd.set("#BgPickerTitle.Text", "Background Details");
            buildDetail(cmd, events, selectedBackground);
        } else {
            cmd.set("#BgPickerTitle.Text", "Choose your Background");
            buildGrid(cmd, events);
        }
    }

    private void buildGrid(UICommandBuilder cmd, UIEventBuilder events) {
        Background[] all = Background.values();
        for (int i = 0; i < all.length; i++) {
            Background bg = all[i];
            String selector = "#BgBtn" + (i + 1);
            cmd.set(selector + ".Text", bg.displayName());
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Type", "pick").append("Id", bg.name()),
                    false);
        }
    }

    private void buildDetail(UICommandBuilder cmd, UIEventBuilder events, Background bg) {
        cmd.set("#BgDetailTitle.Text", bg.displayName());
        cmd.set("#BgDetailFlavor.Text",
                "Placeholder flavor for " + bg.displayName()
                        + ". Real prose lands in a later authoring pass.");
        cmd.set("#BgDetailStats.Text",
                "+3 " + bg.primary().name() + ", +3 " + bg.secondary().name());

        // Kit lines: populate N, hide the rest
        List<KitItem> kit = bg.kit();
        Nat20LootEntryRegistry entryRegistry =
                Natural20.getInstance().getLootSystem().getLootEntryRegistry();
        for (int i = 0; i < MAX_KIT_LINES; i++) {
            String selector = "#BgDetailKitLine" + (i + 1);
            if (i < kit.size()) {
                KitItem item = kit.get(i);
                String displayName = entryRegistry.getDisplayName(item.itemId());
                if (displayName == null) {
                    // Loot registry miss: fall back to the raw id so the player
                    // still sees something, and so a tester spots the gap.
                    displayName = item.itemId();
                }
                String line = item.quantity() > 1
                        ? displayName + " x " + item.quantity()
                        : displayName;
                cmd.set(selector + ".Text", line);
                cmd.set(selector + ".Visible", true);
            } else {
                cmd.set(selector + ".Visible", false);
            }
        }

        // Back + Confirm bindings
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BgBackBtn",
                EventData.of("Type", "back").append("Id", ""),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BgConfirmBtn",
                EventData.of("Type", "confirm").append("Id", ""),
                false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        String id = data.getId();
        if (type == null || type.isEmpty()) return;

        switch (type) {
            case "pick" -> {
                Background bg = findBackground(id);
                if (bg == null) {
                    LOGGER.atWarning().log("Background pick: unknown id '%s'", id);
                    return;
                }
                selectedBackground = bg;
                rebuild();
            }
            case "back" -> {
                selectedBackground = null;
                rebuild();
            }
            case "confirm" -> {
                if (selectedBackground == null) {
                    LOGGER.atWarning().log("Confirm received with no selected background");
                    return;
                }
                Background bg = selectedBackground;
                try {
                    BackgroundCommitter.commit(ref, store, bg, new Random());
                    LOGGER.atInfo().log("Background committed: %s (%s)", bg.name(), bg.displayName());
                } catch (RuntimeException e) {
                    LOGGER.atSevere().withCause(e).log(
                            "BackgroundCommitter.commit failed for '%s'", bg.name());
                }
                // Close the picker, then hand off to the standard Jiub dialogue
                // page. Jiub's greeting node serves as the "transition line"
                // (see Task 6.1 design): no separate transition page is needed
                // because the greeting already reads as a post-pick segue.
                close();
                Ref<EntityStore> jiubRef = Natural20.getInstance().getJiubManager().getJiubRef();
                if (jiubRef == null) {
                    LOGGER.atWarning().log(
                            "Picker confirmed but Jiub ref is null; standard dialogue not opened");
                    return;
                }
                // Defer: opening a new page inside this handler crashes the
                // PageManager. 50ms matches PageDialoguePresenter's transition
                // delay and is enough for the dismiss to land first.
                final Ref<EntityStore> jiub = jiubRef;
                SCHEDULER.schedule(() -> Natural20.getInstance().getDialogueManager()
                                .startSession(ref, jiub, store, () -> {}),
                        PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            default -> LOGGER.atWarning().log("Unknown event type '%s'", type);
        }
    }

    /**
     * Server-initiated close. The CantClose lifetime prevents the client from
     * dismissing the page itself, so this is the only path that ends the
     * picker session (other than the Confirm button, which closes internally).
     */
    public void closePage() {
        close();
    }

    private static Background findBackground(String name) {
        if (name == null) return null;
        for (Background b : Background.values()) {
            if (b.name().equals(name)) return b;
        }
        return null;
    }

    public static class PageEventData {
        private String type = "";
        private String id = "";

        public PageEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
