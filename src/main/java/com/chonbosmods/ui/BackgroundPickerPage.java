package com.chonbosmods.ui;

import com.chonbosmods.background.Background;
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

/**
 * Background picker grid (Task 5.1).
 *
 * <p>Shows a 2-column grid of 15 buttons, each labelled with the background's
 * {@link Background#displayName()}. Element IDs {@code #BgBtn1} ..
 * {@code #BgBtn15} map to {@code Background.values()[N-1]}.
 *
 * <p><b>Hard lock:</b> the page is constructed with
 * {@link CustomPageLifetime#CantClose}: the client cannot dismiss it via Esc,
 * click-out, or any other player input. The only way out is the server calling
 * {@link #closePage()} (which routes through {@code CustomUIPage.close()} ->
 * {@code PageManager.setPage(ref, store, Page.None)}). For Task 5.1 nothing
 * server-side closes the page yet; Task 5.2 will introduce the detail screen
 * with a Confirm button that commits the choice and closes via this same path.
 *
 * <p>Click handler currently only logs the chosen background. The detail
 * screen lands in Task 5.2 (see {@link #onBgClick}).
 */
public class BackgroundPickerPage extends InteractiveCustomUIPage<BackgroundPickerPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|BackgroundPickerPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_BackgroundPicker.ui";

    public static final BuilderCodec<PageEventData> EVENT_CODEC =
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
                    .addField(new KeyedCodec<>("Type", Codec.STRING),
                            PageEventData::setType, PageEventData::getType)
                    .addField(new KeyedCodec<>("Id", Codec.STRING),
                            PageEventData::setId, PageEventData::getId)
                    .build();

    public BackgroundPickerPage(PlayerRef playerRef) {
        // CantClose = the client cannot dismiss this page through any user input
        // (Esc, click-out, etc). Server-initiated close() still works.
        super(playerRef, CustomPageLifetime.CantClose, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events,
                      Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);
        cmd.set("#BgPickerTitle.Text", "Choose your Background");

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

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        String id = data.getId();
        if (type == null || type.isEmpty()) return;

        if ("pick".equals(type)) {
            Background bg = findBackground(id);
            if (bg == null) {
                LOGGER.atWarning().log("Background pick: unknown id '%s'", id);
                return;
            }
            onBgClick(bg);
        }
    }

    /**
     * Task 5.1 stub: log the selected background. TODO(Task 5.2): replace with
     * a transition into the {@code BackgroundDetailPage} (or analogous detail
     * screen) that shows the chosen background's primary/secondary stats and
     * starter kit, with Confirm + Back buttons.
     */
    private void onBgClick(Background bg) {
        LOGGER.atInfo().log("Background clicked: %s (%s)", bg.name(), bg.displayName());
    }

    /**
     * Server-initiated close. The CantClose lifetime prevents the client from
     * dismissing the page itself, so this is the only path that ends the
     * picker session.
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
