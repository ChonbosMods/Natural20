package com.chonbosmods.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Word-by-word reveal engine for NPC dialogue text. Schedules timed callbacks
 * that push partial text updates to a label element on a {@link Nat20DialoguePage}.
 *
 * <p>Timing is punctuation-aware: commas pause briefly, sentence-ending punctuation
 * pauses longer, and ellipsis pauses longest. Short words (3 chars or fewer) are
 * batched with the next word to avoid choppy single-preposition reveals.</p>
 */
public class DialogueTypewriter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Typewriter");

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nat20-Typewriter");
        t.setDaemon(true);
        return t;
    });

    // Timing constants (milliseconds)
    private static final long DELAY_DEFAULT = 80;
    private static final long DELAY_COMMA = 200;
    private static final long DELAY_SENTENCE_END = 350;
    private static final long DELAY_ELLIPSIS = 500;

    private final String fullText;
    private final String color;
    private final String selector;
    private final String[] words;
    private final Nat20DialoguePage page;
    private final Runnable onTickSound;
    private final Runnable onComplete;

    private int currentIndex;
    private volatile boolean complete;
    private ScheduledFuture<?> pendingFuture;

    /**
     * @param fullText    complete NPC speech text
     * @param color       hex color for the text (e.g. "#FFCC00")
     * @param selector    label selector (e.g. "#Log17")
     * @param page        dialogue page to push updates to
     * @param onTickSound nullable sound callback fired each tick (future use)
     * @param onComplete  called when reveal finishes or is skipped (not on cancel)
     */
    public DialogueTypewriter(String fullText, String color, String selector,
                              Nat20DialoguePage page, Runnable onTickSound, Runnable onComplete) {
        this.fullText = fullText;
        this.color = color;
        this.selector = selector;
        this.page = page;
        this.onTickSound = onTickSound;
        this.onComplete = onComplete;
        this.words = (fullText != null && !fullText.isBlank())
                ? fullText.trim().split("\\s+")
                : new String[0];
    }

    /** Schedule the first tick immediately. If the text is empty, completes right away. */
    public void start() {
        if (words.length == 0) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }
        pendingFuture = SCHEDULER.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
    }

    /** Reveal the next word(s), push the partial text update, and schedule the next tick. */
    private void tick() {
        if (complete || currentIndex >= words.length) return;

        // Smart batching: if the current word is 3 chars or fewer and there is a next word,
        // bundle them into one reveal. Max 2 words per chunk.
        int advanceCount = 1;
        if (words[currentIndex].length() <= 3 && currentIndex + 1 < words.length) {
            advanceCount = 2;
        }

        currentIndex = Math.min(currentIndex + advanceCount, words.length);

        // Build partial string from words[0..currentIndex)
        StringBuilder partial = new StringBuilder();
        for (int i = 0; i < currentIndex; i++) {
            if (i > 0) partial.append(' ');
            partial.append(words[i]);
        }

        // Push update to the UI label
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(selector + ".TextSpans", Message.raw(partial.toString()).color(color));
        page.pushUpdate(cmd);

        // Fire sound callback if set
        if (onTickSound != null) {
            onTickSound.run();
        }

        // Check completion
        if (currentIndex >= words.length) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }

        // Determine delay based on the last revealed word's trailing punctuation
        String lastWord = words[currentIndex - 1];
        long delay = computeDelay(lastWord);

        pendingFuture = SCHEDULER.schedule(this::tick, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Skip the animation: immediately reveal the full text, mark complete, and fire onComplete.
     * Safe to call from any thread.
     */
    public void skip() {
        if (complete) return;
        complete = true;

        if (pendingFuture != null) {
            pendingFuture.cancel(false);
        }

        // Reveal the full text
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(selector + ".TextSpans", Message.raw(fullText).color(color));
        page.pushUpdate(cmd);

        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * Cancel without revealing the full text (used on page rebuild).
     * Marks complete and cancels the pending future. Does NOT call onComplete.
     */
    public void cancel() {
        complete = true;
        if (pendingFuture != null) {
            pendingFuture.cancel(false);
        }
    }

    /** Returns whether the typewriter has finished or been skipped/cancelled. */
    public boolean isComplete() {
        return complete;
    }

    /** Compute the delay before the next word based on trailing punctuation. */
    private static long computeDelay(String word) {
        if (word.endsWith("...")) return DELAY_ELLIPSIS;
        if (word.endsWith(".") || word.endsWith("?") || word.endsWith("!")) return DELAY_SENTENCE_END;
        if (word.endsWith(",")) return DELAY_COMMA;
        return DELAY_DEFAULT;
    }
}
