package com.chonbosmods.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Character-by-character reveal engine for NPC dialogue text. Schedules timed
 * callbacks that push partial text updates to a label element on a
 * {@link Nat20DialoguePage}.
 *
 * <p>Timing is punctuation-aware: commas pause briefly, sentence-ending
 * punctuation pauses longer, and ellipsis pauses longest. Spaces are
 * consumed instantly (bundled with the next character) so words don't
 * start with a visible gap.</p>
 */
public class DialogueTypewriter {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nat20-Typewriter");
        t.setDaemon(true);
        return t;
    });

    // Timing constants (milliseconds)
    private static final long DELAY_DEFAULT = 30;
    private static final long DELAY_COMMA = 200;
    private static final long DELAY_SENTENCE_END = 350;
    private static final long DELAY_ELLIPSIS = 500;

    private final String fullText;
    private final String color;
    private final String selector;
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
        this.fullText = (fullText != null) ? fullText : "";
        this.color = color;
        this.selector = selector;
        this.page = page;
        this.onTickSound = onTickSound;
        this.onComplete = onComplete;
    }

    /** Schedule the first tick immediately. If the text is empty, completes right away. */
    public void start() {
        if (fullText.isEmpty()) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }
        pendingFuture = SCHEDULER.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
    }

    /** Reveal the next character(s), push the partial text update, and schedule the next tick. */
    private void tick() {
        if (complete || currentIndex >= fullText.length()) return;

        // Advance at least one character
        currentIndex++;

        // Skip past spaces: consume them instantly so words don't start with a gap
        while (currentIndex < fullText.length() && fullText.charAt(currentIndex) == ' ') {
            currentIndex++;
        }

        // Push partial text update
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(selector + ".TextSpans", Message.raw(fullText.substring(0, currentIndex)).color(color));
        page.pushUpdate(cmd);

        // Fire sound callback if set
        if (onTickSound != null) {
            onTickSound.run();
        }

        // Check completion
        if (currentIndex >= fullText.length()) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }

        // Determine delay based on the character just revealed and surrounding context
        long delay = computeDelay();
        pendingFuture = SCHEDULER.schedule(this::tick, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Skip the animation: immediately reveal the full text, mark complete, and fire onComplete.
     * Safe to call from any thread: the actual work is serialized onto the scheduler thread
     * to avoid racing with an in-flight tick().
     */
    public void skip() {
        if (complete) return;
        SCHEDULER.execute(() -> {
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
        });
    }

    /**
     * Cancel without revealing the full text (used on page rebuild/dismiss).
     * Marks complete and cancels the pending future. Does NOT call onComplete.
     *
     * <p>Unlike {@link #skip()}, this is not serialized onto the scheduler thread.
     * A tick() already in-flight may send one final partial update before seeing the
     * volatile complete flag, which is harmless during rebuild/dismiss cleanup.</p>
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

    /** Compute the delay after the just-revealed character, with ellipsis awareness. */
    private long computeDelay() {
        char revealed = fullText.charAt(currentIndex - 1);

        if (revealed == '.' || revealed == '?' || revealed == '!') {
            // Mid-ellipsis: next char is also '.', don't pause yet
            if (currentIndex < fullText.length() && fullText.charAt(currentIndex) == '.') {
                return DELAY_DEFAULT;
            }
            // End of ellipsis (3+ dots): long pause
            if (revealed == '.' && currentIndex >= 3
                    && fullText.charAt(currentIndex - 2) == '.'
                    && fullText.charAt(currentIndex - 3) == '.') {
                return DELAY_ELLIPSIS;
            }
            return DELAY_SENTENCE_END;
        }
        if (revealed == ',') return DELAY_COMMA;
        return DELAY_DEFAULT;
    }
}
