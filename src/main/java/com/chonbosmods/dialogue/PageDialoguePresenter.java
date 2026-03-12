package com.chonbosmods.dialogue;

import com.chonbosmods.dialogue.model.DialogueNode;
import com.chonbosmods.dialogue.model.LogEntry;
import com.chonbosmods.dialogue.model.ResolvedTopic;
import com.chonbosmods.dice.Nat20DiceRoller;
import com.chonbosmods.dice.SkillCheckRequest;
import com.chonbosmods.dice.SkillCheckResult;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.chonbosmods.ui.Nat20DialoguePage;
import com.chonbosmods.ui.Nat20DiceRollPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PageDialoguePresenter implements DialoguePresenter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Presenter");
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nat20-PageTransition");
        t.setDaemon(true);
        return t;
    });

    private final Player player;
    private final PlayerRef playerRef;
    private final Ref<EntityStore> entityRef;
    private final Store<EntityStore> store;
    private final DialogueManager manager;
    private final String npcName;

    private Nat20DialoguePage dialoguePage;
    private Nat20DiceRollPage diceRollPage;
    private boolean hudHidden;

    // Cached state for rebuilding after transitions
    private List<LogEntry> currentLog = List.of();
    private List<ResolvedTopic> currentTopics = List.of();
    private int currentDisposition;
    private boolean currentTopicsLocked;

    public PageDialoguePresenter(Player player, PlayerRef playerRef,
                                  Ref<EntityStore> entityRef, Store<EntityStore> store,
                                  DialogueManager manager, String npcName) {
        this.player = player;
        this.playerRef = playerRef;
        this.entityRef = entityRef;
        this.store = store;
        this.manager = manager;
        this.npcName = npcName;
    }

    // --- DialoguePresenter interface ---

    @Override
    public void refreshLog(List<LogEntry> log) {
        currentLog = log;
        if (dialoguePage != null) {
            dialoguePage.updateLogAndFollowUps(log, currentTopicsLocked);
        }
    }

    @Override
    public void refreshTopics(List<ResolvedTopic> visibleTopics) {
        currentTopics = visibleTopics;
        if (dialoguePage != null) {
            dialoguePage.updateTopics(visibleTopics, currentTopicsLocked);
        } else if (diceRollPage == null) {
            // Post-dice transition: session calls refreshTopics which triggers reopening
            openDialoguePage();
        }
    }

    @Override
    public void refreshDisposition(int disposition) {
        currentDisposition = disposition;
        if (dialoguePage != null) {
            dialoguePage.updateDisposition(disposition);
        }
    }

    @Override
    public void showSkillCheck(DialogueNode.SkillCheckNode node, int effectiveDC, PlayerStats stats) {
        // Pre-determine the roll result
        Stat stat = node.stat() != null ? node.stat() : node.skill().getAssociatedStat();
        SkillCheckRequest request = new SkillCheckRequest(node.skill(), node.stat(), effectiveDC);
        SkillCheckResult result = Nat20DiceRoller.roll(stats, request);

        int dcModifier = effectiveDC - node.baseDC();

        // Detach dialogue page handler to prevent goodbye on dismiss
        if (dialoguePage != null) {
            dialoguePage.clearEventHandler();
            dialoguePage = null;
        }

        // Defer dice page open: opening a page inside another page's event handler
        // causes the page manager to immediately dismiss the new page
        diceRollPage = new Nat20DiceRollPage(
                playerRef, node.skill(), stat, result, dcModifier,
                r -> onDiceRollContinue(r, node));
        SCHEDULER.schedule(() ->
            player.getPageManager().openCustomPage(entityRef, store, diceRollPage),
            50, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (dialoguePage != null) {
            dialoguePage.clearEventHandler();
            dialoguePage.closePage();
            dialoguePage = null;
        }
        if (diceRollPage != null) {
            diceRollPage.closePage();
            diceRollPage = null;
        }
        restoreHud();
    }

    // --- Public methods ---

    public void openDialoguePage() {
        hideHud();
        dialoguePage = new Nat20DialoguePage(playerRef);
        dialoguePage.setState(npcName, currentLog, currentTopics,
                currentDisposition, currentTopicsLocked, this::handleDialogueEvent);
        player.getPageManager().openCustomPage(entityRef, store, dialoguePage);
    }

    public void setTopicsLocked(boolean locked) {
        currentTopicsLocked = locked;
    }

    // --- Private methods ---

    private void handleDialogueEvent(String type, String id) {
        UUID uuid = playerRef.getUuid();
        switch (type) {
            case "topic" -> manager.handleTopicSelected(uuid, id);
            case "followup" -> manager.handleFollowUpSelected(uuid, id);
            case "goodbye" -> manager.endSession(uuid);
        }
    }

    private void onDiceRollContinue(SkillCheckResult result, DialogueNode.SkillCheckNode checkNode) {
        // Null out the dice page ref (don't call closePage inside event handler)
        diceRollPage = null;

        // Defer to avoid opening dialogue page inside dice page's event handler
        SCHEDULER.schedule(() -> {
            UUID uuid = playerRef.getUuid();
            ConversationSession session = manager.getSession(uuid);
            if (session != null) {
                currentTopicsLocked = session.isTopicsLocked();
                // This triggers refreshLog + refreshTopics on this presenter, which reopens dialogue page
                session.handleSkillCheckResult(result, checkNode);
            } else {
                LOGGER.atWarning().log("No session found after dice roll for player %s", uuid);
                close();
            }
        }, 50, TimeUnit.MILLISECONDS);
    }

    private void hideHud() {
        if (!hudHidden) {
            hudHidden = true;
            player.getHudManager().hideHudComponents(playerRef,
                    HudComponent.Hotbar, HudComponent.Reticle);
            LOGGER.atInfo().log("HUD hidden");
        }
    }

    private void restoreHud() {
        if (hudHidden) {
            hudHidden = false;
            player.getHudManager().showHudComponents(playerRef,
                    HudComponent.Hotbar, HudComponent.Reticle);
            LOGGER.atInfo().log("HUD restored");
        }
    }
}
