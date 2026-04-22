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
        LOGGER.atInfo().log("Constructed BackgroundPickerPage for player %s",
                playerRef.getUuid());
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events,
                      Store<EntityStore> store) {
        boolean onDetail = selectedBackground != null;
        LOGGER.atInfo().log("Build: onDetail=%s selected=%s",
                onDetail,
                selectedBackground == null ? "null" : selectedBackground.name());
        cmd.append(PAGE_LAYOUT);

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
        cmd.set("#BgDetailFlavor.Text", flavorFor(bg));
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
        LOGGER.atInfo().log("handleDataEvent: type=%s id=%s selected=%s",
                type == null ? "null" : type,
                id == null ? "null" : id,
                selectedBackground == null ? "null" : selectedBackground.name());
        if (type == null || type.isEmpty()) {
            LOGGER.atWarning().log("Empty event type; ignoring");
            return;
        }

        switch (type) {
            case "pick" -> {
                Background bg = findBackground(id);
                if (bg == null) {
                    LOGGER.atWarning().log("Background pick: unknown id '%s'", id);
                    return;
                }
                LOGGER.atInfo().log("Pick: %s (%s) -> detail screen", bg.name(), bg.displayName());
                selectedBackground = bg;
                rebuild();
            }
            case "back" -> {
                LOGGER.atInfo().log("Back: returning to grid");
                selectedBackground = null;
                rebuild();
            }
            case "confirm" -> {
                if (selectedBackground == null) {
                    LOGGER.atWarning().log("Confirm received with no selected background");
                    return;
                }
                Background bg = selectedBackground;
                LOGGER.atInfo().log("Confirm: committing %s (%s)", bg.name(), bg.displayName());
                try {
                    BackgroundCommitter.commit(ref, store, bg, new Random());
                    LOGGER.atInfo().log("Background committed: %s (%s)", bg.name(), bg.displayName());
                } catch (RuntimeException e) {
                    LOGGER.atSevere().withCause(e).log(
                            "BackgroundCommitter.commit failed for '%s'", bg.name());
                }
                // Close the picker, then hand off to the standard Jiub dialogue
                // page if he's spawned. If Jiub isn't spawned yet (the player
                // joined faster than the chunk-pre-load hook could run), we
                // still closed the picker and committed the background, so
                // the player is free in the world. They can F-interact Jiub
                // later when he's present.
                close();
                Ref<EntityStore> jiubRef = Natural20.getInstance().getJiubManager().getJiubRef();
                if (jiubRef == null) {
                    LOGGER.atWarning().log(
                            "Picker confirmed but Jiub ref is null; "
                                    + "commit applied, post-commit dialogue skipped");
                    return;
                }
                // Defer: opening a new page inside this handler crashes the
                // PageManager. 50ms matches PageDialoguePresenter's transition
                // delay and is enough for the dismiss to land first.
                final Ref<EntityStore> jiub = jiubRef;
                LOGGER.atInfo().log("Scheduling post-commit dialogue session open in %dms",
                        PAGE_TRANSITION_DELAY_MS);
                SCHEDULER.schedule(() -> {
                            LOGGER.atInfo().log("Opening post-commit DialogueManager session");
                            Natural20.getInstance().getDialogueManager()
                                    .startSession(ref, jiub, store, () -> {});
                        },
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

    /**
     * Per-background flavor paragraph shown on the picker's detail screen.
     * Read by the player as their character's backstory; written in second
     * person, never names places, NPCs, or events outside the registry.
     * Mechanics (stats and kit) are listed separately on the same screen,
     * so this is pure identity prose.
     */
    private static String flavorFor(Background bg) {
        return switch (bg) {
            case SOLDIER -> "You marched where you were told to march, for longer than made sense, and "
                    + "you held the line when the line needed holding. The work made your shoulders "
                    + "thick and your patience longer. You came back from it slower than you left, "
                    + "but you came back on your own feet.";
            case SAILOR -> "You learned your footing on wet planks and your grip on wet rope. The ship "
                    + "was always asking for more of your back and more of your balance, and giving "
                    + "it became the shape of your days. Half the crew couldn't name the year, but "
                    + "every one of them could climb the mast in a blow.";
            case VETERAN -> "You were the one they sent when the fighting was going to last longer than "
                    + "the speeches promised. You learned which orders to follow and which to walk "
                    + "into a tent and talk out of a captain's mouth. The swinging came easy. The "
                    + "knowing when came slower.";
            case FOLK_HERO -> "You did something your village needed doing when nobody else would, and "
                    + "the kind of people who remember things remembered you for it. You never went "
                    + "looking to be anyone's hero, and you still don't think of yourself as one. "
                    + "The sword came off a tavern wall on your way out of town, and nobody asked "
                    + "for it back.";
            case NOBLE -> "You grew up knowing which fork to use and which smile to wear when the wrong "
                    + "cousin came to dinner. The sword at your hip was your grandfather's, and "
                    + "you've trained with it long enough to carry its weight like your own. Every "
                    + "room you walked into, you walked into like you belonged there, and so far no "
                    + "one has argued.";
            case CRIMINAL -> "You learned early which rooftops held your weight and which guards took a "
                    + "second cup of wine with dinner. Nothing in that life was given, and what you "
                    + "kept you kept because you could run longer, climb quieter, and take a hit "
                    + "better than the people chasing you. The daggers stayed because they fit your "
                    + "hand and didn't ask questions.";
            case ARTISAN -> "You spent your apprenticeship learning that the hand and the eye lie to "
                    + "each other, and the only thing that tells the truth is the finished piece. "
                    + "Years at the bench teach you to read a flaw before your master sees it, and "
                    + "to shape wood or metal until it does what the drawing said it would. The "
                    + "club was the first thing you picked up that didn't need explaining.";
            case SCOUT -> "You learned the woods the way other people learn their neighbors, by watching "
                    + "and by being patient enough to be shown. A deer path told you more than a "
                    + "signpost, and a broken twig told you more than the deer path did. The bow "
                    + "came naturally once you understood that the arrow goes where your breath does.";
            case ENTERTAINER -> "You played rooms that wanted to love you and rooms that wanted to hate "
                    + "you, and you learned to tell them apart before you spoke. The act needed "
                    + "quick hands, quicker feet, and a smile that could hold when something went "
                    + "wrong onstage. The crossbow was a prop once. A few rooms turned ugly, and "
                    + "eventually the prop had to work.";
            case HERMIT -> "You spent years with only the weather and your own thoughts, and eventually "
                    + "you learned to let the thoughts go quiet too. What came after was room "
                    + "enough in your head to notice things the noise had been hiding, and the "
                    + "patience to sit with them. The staff was what you leaned on when your legs "
                    + "got tired of the long walk to the well.";
            case OUTLANDER -> "You were born where the maps get lazy and the roads stop bothering. "
                    + "Winter out there teaches you what to carry and what to do without, and "
                    + "reading the weather matters more than reading anything else. The axe was "
                    + "the first tool put in your hand and the last one you'd ever put down.";
            case URCHIN -> "You knew which alleys had a dry corner by the time you were six, and which "
                    + "shopkeepers left the day-old bread where a small hand could reach it by the "
                    + "time you were seven. You learned to go hungry and keep going, and you "
                    + "learned to get people to like you because being liked kept you fed. The "
                    + "club came later, when people stopped underestimating someone your size.";
            case SAGE -> "You read everything they put in front of you, and then the books they kept "
                    + "locked up, and then the ones that didn't belong to any library you could "
                    + "name. Somewhere along the way you stopped collecting answers and started "
                    + "collecting better questions. The staff was a gift from a teacher who didn't "
                    + "like goodbyes.";
            case CHARLATAN -> "You've been six different people in three different cities, and the only "
                    + "thing they had in common was the face in the mirror. You can read a mark in "
                    + "the time it takes them to sit down, and write a whole story around them by "
                    + "the time they've ordered a drink. The staff isn't yours, strictly speaking, "
                    + "but the person it belongs to is unlikely to come asking.";
            case ACOLYTE -> "You held the hands of the sick and the dying, and you said the words that "
                    + "needed saying, even on the nights you weren't sure you believed them "
                    + "yourself. People listened to you because you listened to them first. The "
                    + "mace was the temple's, and the temple is a long way from here.";
        };
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
