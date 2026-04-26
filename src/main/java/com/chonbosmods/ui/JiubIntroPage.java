package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.background.Background;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.TutorialQuestFactory;
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
import com.hypixel.hytale.server.core.universe.world.World;
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

    private static final String INTRO1_TEXT =
            "Stand up. There you go. You were dreaming. Not even last night's storm could wake you.";

    /**
     * INTRO1 holds the page silent (no text rendered) for this many ms before the
     * typewriter starts. Absorbs Hytale's client-side load time on first-spawn so
     * the player doesn't miss the start of the line. INTRO2 + INTRO3 don't need
     * it (player is settled by then).
     */
    private static final long INTRO1_LOAD_BUFFER_PAUSE_MS = 6000;
    private static final String INTRO2_TEXT =
            "What you were before this carries forward with you, whether you mean it to or not. Best I know what I'm working with.";
    /** {0} is substituted with the player's Background displayName prefixed with "a" or "an". */
    private static final String INTRO3_TEMPLATE =
            "Go find Celius Gravus. He has urgent work on his hands, and he could use {0} of your caliber.";

    public static final BuilderCodec<PageEventData> EVENT_CODEC =
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
                    .addField(new KeyedCodec<>("Type", Codec.STRING),
                            PageEventData::setType, PageEventData::getType)
                    .build();

    /** Same reveal color as the standard dialogue page's NPC speech. */
    private static final String TYPEWRITER_COLOR = "#FFCC00";

    private enum State { INTRO1, INTRO2, INTRO3 }

    private State state;
    private final Background background;
    private volatile DialogueTypewriter activeTypewriter;

    public JiubIntroPage(PlayerRef playerRef) {
        this(playerRef, State.INTRO1, null);
    }

    public JiubIntroPage(PlayerRef playerRef, State initialState, Background background) {
        // CantClose = the client cannot dismiss this page through any user input
        // (Esc, click-out, etc). Server-initiated close() still works.
        super(playerRef, CustomPageLifetime.CantClose, EVENT_CODEC);
        this.state = initialState;
        this.background = background;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events,
                      Store<EntityStore> store) {
        LOGGER.atInfo().log("Build: state=%s", state);
        cmd.append(PAGE_LAYOUT);

        String text = switch (state) {
            case INTRO1 -> INTRO1_TEXT;
            case INTRO2 -> INTRO2_TEXT;
            case INTRO3 -> renderIntro3();
        };

        // Clear the line label; typewriter will populate it character-by-character
        // via TextSpans. (Setting .Text would block the subsequent .TextSpans
        // writes from rendering on top.)
        cmd.set("#JiubIntroLine.TextSpans",
                com.hypixel.hytale.server.core.Message.raw("").color(TYPEWRITER_COLOR));

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JiubIntroContinueBtn",
                EventData.of("Type", "continue"),
                false);

        // Cancel any in-flight typewriter from a prior build (state transition
        // rebuilds while previous line is still typing), then start a fresh one.
        if (activeTypewriter != null) {
            activeTypewriter.cancel();
        }
        long leadingPause = (state == State.INTRO1) ? INTRO1_LOAD_BUFFER_PAUSE_MS : 0L;
        activeTypewriter = new DialogueTypewriter(
                text, TYPEWRITER_COLOR, "#JiubIntroLine", this::pushUpdate,
                null, null, leadingPause);
        activeTypewriter.start();
    }

    /** Public bridge so {@link DialogueTypewriter} can push partial text updates. */
    public void pushUpdate(UICommandBuilder cmd) {
        sendUpdate(cmd);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        LOGGER.atInfo().log("handleDataEvent: type=%s state=%s", type, state);
        if (!"continue".equals(type)) {
            LOGGER.atWarning().log("Unknown event type '%s'", type);
            return;
        }

        // Continue mid-typewriter = skip the reveal, don't advance state.
        // Matches the dialogue page's click-to-skip pattern.
        if (activeTypewriter != null && !activeTypewriter.isComplete()) {
            LOGGER.atInfo().log("Skipping typewriter for state=%s", state);
            activeTypewriter.skip();
            return;
        }

        if (state == State.INTRO1) {
            state = State.INTRO2;
            LOGGER.atInfo().log("Advancing to INTRO2");
            rebuild();
            return;
        }

        if (state == State.INTRO2) {
            // INTRO2 -> open the picker. Two safety patterns required for this
            // transition to work reliably:
            //
            //   1. SCHEDULER defer (50ms) so the new page open happens AFTER the
            //      current event handler has unwound. Opening a new page inside
            //      the handler of the page being replaced was observed to leave
            //      the new page in a broken state.
            //
            //   2. world.execute(...) bounce so openCustomPage runs on the world
            //      thread. Without this, the page renders visually but its
            //      server-side event bindings intermittently fail to register;
            //      the player sees buttons but clicks never reach the server.
            //      Same root cause as the post-commit dialogue race that bit us
            //      earlier.
            //
            // We do NOT call close() on the intro page. The picker's openCustomPage
            // replaces it through the page manager automatically. Calling close()
            // first was observed to cause stale state on the next page (proven
            // out in BackgroundPickerPage's Confirm path).
            LOGGER.atInfo().log("INTRO2 Continue; scheduling picker open");
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                LOGGER.atWarning().log("Continue from intro2 but Player component is null; picker not opened");
                return;
            }
            PlayerRef pRef = player.getPlayerRef();
            SCHEDULER.schedule(() -> {
                World world = Natural20.getInstance().getDefaultWorld();
                if (world == null) {
                    LOGGER.atWarning().log("Default world unavailable; picker not opened");
                    return;
                }
                world.execute(() -> {
                    LOGGER.atInfo().log("Opening BackgroundPickerPage after intro");
                    BackgroundPickerPage picker = new BackgroundPickerPage(pRef);
                    player.getPageManager().openCustomPage(ref, store, picker);
                });
            }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
            return;
        }

        // INTRO3 Continue: create the tutorial quest on the player's data, then
        // schedule the standard post-commit Jiub dialogue session.
        LOGGER.atInfo().log("INTRO3 Continue; creating tutorial quest and scheduling Jiub session");
        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerData == null || player == null) {
            LOGGER.atWarning().log(
                "Continue from intro3 but playerData=%s player=%s; tutorial quest not created",
                playerData, player);
            return;
        }
        TutorialQuestFactory.createAndAssign(playerData, background,
            player.getPlayerRef().getUuid());
        schedulePostCommitDialogue(ref, store);
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
        LOGGER.atInfo().log(
                "Opening JiubIntroPage for player %s (jiubRef=%s)",
                player.getPlayerRef().getUuid(),
                jiubRef == null ? "null" : "present");
        JiubIntroPage page = new JiubIntroPage(player.getPlayerRef());
        player.getPageManager().openCustomPage(playerRef, store, page);
    }

    /**
     * Entry point for the post-background-commit INTRO3 line. Reads the just-committed
     * {@link Background} from player data and opens this page in INTRO3 state. When
     * the player clicks Continue, the tutorial quest is created and the standard Jiub
     * dialogue session is scheduled.
     */
    public static void openIntro3(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("openIntro3: Player component is null; INTRO3 not opened");
            return;
        }
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        Background bg = playerData != null ? playerData.getBackground() : null;
        LOGGER.atInfo().log("openIntro3: background=%s for player %s",
            bg == null ? "null" : bg.name(), player.getPlayerRef().getUuid());
        JiubIntroPage page = new JiubIntroPage(player.getPlayerRef(), State.INTRO3, bg);
        player.getPageManager().openCustomPage(playerRef, store, page);
    }

    private String renderIntro3() {
        String article = "a";
        String name;
        if (background != null) {
            name = background.displayName();
            if (!name.isEmpty() && isVowel(name.charAt(0))) {
                article = "an";
            }
        } else {
            name = "adventurer";
        }
        return INTRO3_TEMPLATE.replace("{0}", article + " " + name);
    }

    private static boolean isVowel(char c) {
        return "AEIOUaeiou".indexOf(c) >= 0;
    }

    /**
     * Defer the standard Jiub dialogue session the same way the picker used to do it:
     * scheduler unwinds first, then world.execute bounces onto the world thread so
     * store.getComponent calls inside startSession are thread-safe. Mirrors the
     * former {@code BackgroundPickerPage.schedulePostCommitDialogue}.
     */
    private static void schedulePostCommitDialogue(Ref<EntityStore> ref,
                                                   Store<EntityStore> store) {
        SCHEDULER.schedule(() -> {
            World world = Natural20.getInstance().getDefaultWorld();
            if (world == null) {
                LOGGER.atWarning().log("Post-commit dialogue: default world unavailable; skipping");
                return;
            }
            world.execute(() -> {
                Ref<EntityStore> jiub = Natural20.getInstance().getJiubManager().getJiubRef();
                if (jiub == null) {
                    LOGGER.atWarning().log(
                        "Post-commit dialogue: Jiub not spawned; player can F-interact later");
                    return;
                }
                LOGGER.atInfo().log("Opening post-commit DialogueManager session");
                Natural20.getInstance().getDialogueManager()
                    .startSession(ref, jiub, store, () -> {});
            });
        }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static class PageEventData {
        private String type = "";

        public PageEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
