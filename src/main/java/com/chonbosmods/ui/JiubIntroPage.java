package com.chonbosmods.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Forced two-line intro page shown to brand-new players before the
 * background picker (Task 6.1).
 *
 * <p>State machine: {@code intro1} -> Continue -> {@code intro2} ->
 * Continue -> close + open {@link BackgroundPickerPage}. There is no
 * back button and no skip: the page is constructed with
 * {@link CustomPageLifetime#CantClose}, so the client cannot dismiss
 * it via Esc, click-out, or any other player input. The only way out
 * is the Continue button on the second line, which closes the intro
 * and immediately hands off to the picker.
 *
 * <p>The two intro lines are placeholders: real prose lands in a
 * later authoring pass. The second line ("Tell me about your
 * background.") is design-doc-locked because it sets up the picker
 * narratively.
 *
 * <p>Page hand-off is deferred via {@link #SCHEDULER} (mirroring
 * {@code PageDialoguePresenter}): Hytale's PageManager crashes if a
 * new page is opened inside the event handler of the page being
 * dismissed, so we let the handler stack unwind first.
 */
public class JiubIntroPage extends InteractiveCustomUIPage<JiubIntroPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|JiubIntroPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_JiubIntro.ui";

    /** Same value as {@code PageDialoguePresenter.PAGE_TRANSITION_DELAY_MS}. */
    private static final long PAGE_TRANSITION_DELAY_MS = 50;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nat20-JiubIntroTransition");
        t.setDaemon(true);
        return t;
    });

    /** Placeholder copy: real prose lands in a later authoring pass. */
    private static final String INTRO1_TEXT =
            "Welcome, traveler. The world has changed since you slept.";
    /** Design-doc-locked second line: it sets up the picker narratively. */
    private static final String INTRO2_TEXT = "Tell me about your background.";

    public static final BuilderCodec<PageEventData> EVENT_CODEC =
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
                    .addField(new KeyedCodec<>("Type", Codec.STRING),
                            PageEventData::setType, PageEventData::getType)
                    .build();

    private enum State { INTRO1, INTRO2 }

    private State state = State.INTRO1;

    public JiubIntroPage(PlayerRef playerRef) {
        // CantClose = the client cannot dismiss this page through any user input
        // (Esc, click-out, etc). Server-initiated close() still works.
        super(playerRef, CustomPageLifetime.CantClose, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events,
                      Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        String text = state == State.INTRO1 ? INTRO1_TEXT : INTRO2_TEXT;
        cmd.set("#JiubIntroLine.Text", text);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JiubIntroContinueBtn",
                EventData.of("Type", "continue"),
                false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        if (!"continue".equals(type)) {
            LOGGER.atWarning().log("Unknown event type '%s'", type);
            return;
        }

        if (state == State.INTRO1) {
            state = State.INTRO2;
            rebuild();
            return;
        }

        // INTRO2 -> close intro, open picker. Defer the picker open: the
        // PageManager crashes if a new page is opened inside the handler of
        // the page being dismissed.
        close();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("Continue from intro2 but Player component is null; picker not opened");
            return;
        }
        PlayerRef pRef = player.getPlayerRef();
        SCHEDULER.schedule(() -> {
            BackgroundPickerPage picker = new BackgroundPickerPage(pRef);
            player.getPageManager().openCustomPage(ref, store, picker);
        }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Static entry point used by the first-join hook in
     * {@code Natural20.PlayerReadyEvent} (Task 4.1 / 6.1).
     *
     * <p>The {@code jiubRef} parameter is unused by the intro itself: Jiub
     * is the conversational owner of the post-picker dialogue, but the
     * intro and picker pages do not need an NPC ref. It is kept on the
     * signature so the call site documents the dependency (no Jiub ->
     * no flow) and so future first-join logic can route through here.
     */
    @SuppressWarnings("unused")
    public static void open(Ref<EntityStore> playerRef, Store<EntityStore> store,
                            Ref<EntityStore> jiubRef) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("JiubIntroPage.open: Player component is null; intro not opened");
            return;
        }
        JiubIntroPage page = new JiubIntroPage(player.getPlayerRef());
        player.getPageManager().openCustomPage(playerRef, store, page);
    }

    public static class PageEventData {
        private String type = "";

        public PageEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
