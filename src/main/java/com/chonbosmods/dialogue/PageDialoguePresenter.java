package com.chonbosmods.dialogue;

import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.topic.PostureResolver;
import com.chonbosmods.topic.PostureSelection;
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

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PageDialoguePresenter implements DialoguePresenter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Presenter");

    /**
     * Delay between closing one page and opening another.
     * Hytale's PageManager crashes if a new page is opened inside
     * the event handler of the page being dismissed. This delay
     * ensures the event handler stack unwinds before the new page
     * opens. 50ms is sufficient for all observed server loads.
     */
    private static final long PAGE_TRANSITION_DELAY_MS = 50;

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
    private final PostureResolver postureResolver;
    private final Map<String, PostureSelection.ResolvedPrompt> postureCache = new HashMap<>();

    private Nat20DialoguePage dialoguePage;
    private Nat20DiceRollPage diceRollPage;
    private boolean hudHidden;

    // Cached state for rebuilding after transitions
    private List<LogEntry> currentLog = List.of();
    private List<ActiveFollowUp> currentFollowUps = List.of();
    private List<ResolvedTopic> currentTopics = List.of();
    private int currentDisposition;

    // Batching flags
    private boolean dirty;
    private boolean needsReopen;

    public PageDialoguePresenter(Player player, PlayerRef playerRef,
                                  Ref<EntityStore> entityRef, Store<EntityStore> store,
                                  DialogueManager manager, String npcName,
                                  PostureResolver postureResolver) {
        this.player = player;
        this.playerRef = playerRef;
        this.entityRef = entityRef;
        this.store = store;
        this.manager = manager;
        this.npcName = npcName;
        this.postureResolver = postureResolver;
    }

    // --- DialoguePresenter interface ---

    @Override
    public void refreshLog(List<LogEntry> log) {
        currentLog = log;
        if (dialoguePage != null) {
            dialoguePage.updateLog(log);
            dirty = true;
        }
    }

    @Override
    public void refreshFollowUps(List<ActiveFollowUp> followUps) {
        currentFollowUps = resolvePostureSlots(followUps);
        if (dialoguePage != null) {
            dialoguePage.updateFollowUps(currentFollowUps);
            dirty = true;
        }
    }

    private List<ActiveFollowUp> resolvePostureSlots(List<ActiveFollowUp> followUps) {
        postureCache.clear();

        int postureCount = 0;
        for (ActiveFollowUp f : followUps) {
            if (f.responseType() == ResponseType.POSTURE) postureCount++;
        }
        if (postureCount == 0) return followUps;

        ConversationSession session = manager.getSession(playerRef.getUuid());
        List<String> recentPostures = session != null ? session.getRecentPostures() : List.of();
        int disposition = session != null ? session.getDisposition() : currentDisposition;

        String npcValence = session != null
            ? session.getValenceTracker().getCurrentValence().name().toLowerCase()
            : "neutral";
        PostureSelection selection = postureResolver.resolve(
            npcValence, disposition, recentPostures, postureCount);

        List<ActiveFollowUp> resolved = new ArrayList<>();
        int postureIdx = 0;
        for (ActiveFollowUp f : followUps) {
            if (f.responseType() == ResponseType.POSTURE && postureIdx < selection.prompts().size()) {
                PostureSelection.ResolvedPrompt prompt = selection.prompts().get(postureIdx++);
                postureCache.put(f.responseId(), prompt);
                resolved.add(new ActiveFollowUp(
                    f.responseId(), prompt.text(), null, f.statPrefix(),
                    f.grayed(), ResponseType.POSTURE));
            } else {
                resolved.add(f);
            }
        }
        return resolved;
    }

    @Override
    public void refreshTopics(List<ResolvedTopic> visibleTopics) {
        currentTopics = visibleTopics;
        if (dialoguePage != null) {
            dialoguePage.updateTopics(visibleTopics, isSessionTopicsLocked());
            dirty = true;
        } else if (diceRollPage == null) {
            // Post-dice transition: need to reopen dialogue page
            needsReopen = true;
        }
    }

    @Override
    public void refreshDisposition(int disposition) {
        currentDisposition = disposition;
        if (dialoguePage != null) {
            dialoguePage.updateDisposition(disposition);
        }
    }

    /**
     * Flush batched state changes. Call once after all refresh methods have been invoked.
     * If the page needs a rebuild, does a single rebuild. If the page needs reopening
     * (post-dice-roll), schedules the reopen on the SCHEDULER to avoid opening inside
     * an event handler.
     */
    public void flushUpdates() {
        if (dirty && dialoguePage != null) {
            dialoguePage.commitUpdates();
            dirty = false;
        }
        if (needsReopen) {
            needsReopen = false;
            SCHEDULER.schedule(() -> {
                synchronized (PageDialoguePresenter.this) {
                    openDialoguePage();
                }
            }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
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
        SCHEDULER.schedule(() -> {
            synchronized (PageDialoguePresenter.this) {
                player.getPageManager().openCustomPage(entityRef, store, diceRollPage);
            }
        }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
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
        dialoguePage.setState(npcName, currentLog, currentFollowUps, currentTopics,
                currentDisposition, isSessionTopicsLocked(), this::handleDialogueEvent);
        player.getPageManager().openCustomPage(entityRef, store, dialoguePage);
    }

    /**
     * Open the dialogue page directly on the caller's thread.
     * Used for the initial page open from session start, where no event handler
     * is on the stack and the scheduler delay is unnecessary. Caches topics and
     * disposition, then opens the page synchronously.
     */
    public void openInitialPage(List<ResolvedTopic> visibleTopics, int disposition) {
        currentTopics = visibleTopics;
        currentDisposition = disposition;
        openDialoguePage();
    }

    // --- Private methods ---

    private boolean isSessionTopicsLocked() {
        ConversationSession session = manager.getSession(playerRef.getUuid());
        return session != null && session.isTopicsLocked();
    }

    private void handleDialogueEvent(String type, String id) {
        synchronized (this) {
            UUID uuid = playerRef.getUuid();
            switch (type) {
                case "topic" -> manager.handleTopicSelected(uuid, id);
                case "followup" -> {
                    PostureSelection.ResolvedPrompt posture = postureCache.remove(id);
                    if (posture != null) {
                        ConversationSession session = manager.getSession(uuid);
                        if (session != null) {
                            session.onPostureSelected(posture);
                        }
                    }
                    manager.handleFollowUpSelected(uuid, id);
                    // Apply posture text override after the log entry is created.
                    // Do NOT call refreshLog+flushUpdates here: handleFollowUpSelected
                    // already flushed. A second flush restarts the typewriter and
                    // truncates NPC text to one character. The override is picked up
                    // on the next rebuild (next player interaction).
                    if (posture != null) {
                        ConversationSession session = manager.getSession(uuid);
                        if (session != null) {
                            session.overrideLastResponseLogText(posture.text());
                        }
                    }
                }
                case "goodbye" -> {
                    ConversationSession session = manager.getSession(uuid);
                    if (session != null && session.isTopicsLocked()) {
                        // Conversation is locked: reopen the page instead of ending
                        dialoguePage = null;
                        SCHEDULER.schedule(() -> {
                            synchronized (PageDialoguePresenter.this) {
                                openDialoguePage();
                            }
                        }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
                    } else {
                        manager.endSession(uuid);
                    }
                }
            }
        }
    }

    private void onDiceRollContinue(SkillCheckResult result, DialogueNode.SkillCheckNode checkNode) {
        // Null out the dice page ref (don't call closePage inside event handler)
        diceRollPage = null;

        // Defer to avoid opening dialogue page inside dice page's event handler
        SCHEDULER.schedule(() -> {
            synchronized (PageDialoguePresenter.this) {
                UUID uuid = playerRef.getUuid();
                ConversationSession session = manager.getSession(uuid);
                if (session != null) {
                    // This triggers refreshLog + refreshTopics on this presenter, which reopens dialogue page
                    session.handleSkillCheckResult(result, checkNode);
                } else {
                    LOGGER.atWarning().log("No session found after dice roll for player %s", uuid);
                    close();
                }
            }
        }, PAGE_TRANSITION_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void hideHud() {
        if (!hudHidden) {
            hudHidden = true;
            player.getHudManager().hideHudComponents(playerRef,
                    HudComponent.Hotbar, HudComponent.Reticle);
            LOGGER.atFine().log("HUD hidden");
        }
    }

    private void restoreHud() {
        if (hudHidden) {
            hudHidden = false;
            player.getHudManager().showHudComponents(playerRef,
                    HudComponent.Hotbar, HudComponent.Reticle);
            LOGGER.atFine().log("HUD restored");
        }
    }
}
