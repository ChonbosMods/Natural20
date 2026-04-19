package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.progression.Nat20XpMath;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Empty Character Sheet page shell (Task 7).
 *
 * <p>Loads {@code Pages/Nat20_CharacterSheet.ui} and nothing else. Tasks 8-19
 * populate the left and right panels (XP bar, ability rows, stats, equipment,
 * quest log, etc.). Task 12 will route ability button events through the
 * {@link PageEventData#type} field, and later tasks will extend the codec with
 * additional fields (ability key, slot index, etc.) as those interactions land.
 */
public class CharacterSheetPage extends InteractiveCustomUIPage<CharacterSheetPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CharacterSheetPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_CharacterSheet.ui";

    /** XP track width in pixels: keep in sync with #CSXpBarTrack.Anchor.Width in the .ui template. */
    private static final int XP_BAR_TRACK_WIDTH = 500;

    public static final BuilderCodec<PageEventData> EVENT_CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new)
            .addField(new KeyedCodec<>("Type", Codec.STRING), PageEventData::setType, PageEventData::getType)
            .build();

    private boolean dismissed;

    public CharacterSheetPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        Player player = store.getComponent(ref, Player.getComponentType());
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (player == null || data == null) {
            // Defensive: page chrome still renders, dynamic bindings just stay blank.
            return;
        }

        // Header: name + level
        cmd.set("#CSName.Text", player.getDisplayName());
        cmd.set("#CSLevel.Text", "Lvl " + data.getLevel());

        // XP readout + bar fill
        int level = data.getLevel();
        long totalXp = data.getTotalXp();
        if (level >= Nat20XpMath.LEVEL_CAP) {
            cmd.set("#CSXpText.Text", "Level " + level);
            setXpBarFillPixels(cmd, XP_BAR_TRACK_WIDTH);
        } else {
            long cumulativeAtCurrent = Nat20XpMath.cumulativeXp(level);
            int threshold = Nat20XpMath.xpToNextLevel(level);
            long xpIntoLevel = Math.max(0L, totalXp - cumulativeAtCurrent);
            long safeThreshold = Math.max(1L, threshold);
            long clampedXp = Math.min(xpIntoLevel, safeThreshold);
            int fillPx = (int) ((XP_BAR_TRACK_WIDTH * clampedXp) / safeThreshold);
            cmd.set("#CSXpText.Text", xpIntoLevel + " / " + threshold);
            setXpBarFillPixels(cmd, fillPx);
        }
    }

    /** Set the XP bar fill width in pixels. Track width is fixed in the template. */
    private static void setXpBarFillPixels(UICommandBuilder cmd, int pixels) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setTop(Value.of(0));
        anchor.setBottom(Value.of(0));
        anchor.setWidth(Value.of(pixels));
        cmd.setObject("#CSXpBarFill.Anchor", anchor);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        // No interactive elements yet. Task 12+ will route ability/equipment clicks here.
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        dismissed = true;
        LOGGER.atFine().log("CharacterSheetPage.onDismiss");
        CharacterSheetManager mgr = CharacterSheetManager.get();
        if (mgr != null) {
            // Use the inherited PlayerRef field, not the entity Ref parameter (Ref has no getUuid).
            mgr.handlePageClosed(this.playerRef.getUuid());
        }
    }

    /** Manager-initiated close: dismisses the page on the client. Idempotent. */
    public void closePage() {
        if (dismissed) return;
        close();
    }

    public static class PageEventData {
        private String type = "";

        public PageEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
