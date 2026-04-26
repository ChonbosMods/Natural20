package com.chonbosmods.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    private static final long DELAY_COMMA = 250;
    private static final long DELAY_SENTENCE_END = 400;
    private static final long DELAY_ELLIPSIS = 400;

    private final String fullText;
    private final String color;
    private final String selector;
    private final Consumer<UICommandBuilder> pushUpdate;
    private final Runnable onTickSound;
    private final Runnable onComplete;
    private final long initialDelayMs;

    private final int totalVisible;
    private int visibleRevealed;
    private volatile boolean complete;
    private ScheduledFuture<?> pendingFuture;

    /**
     * @param fullText    complete NPC speech text
     * @param color       hex color for the text (e.g. "#FFCC00")
     * @param selector    label selector (e.g. "#Log17")
     * @param pushUpdate  consumer that pushes a UICommandBuilder to the page
     *                    (typically a method reference like {@code page::pushUpdate})
     * @param onTickSound nullable sound callback fired each tick (future use)
     * @param onComplete  called when reveal finishes or is skipped (not on cancel)
     */
    public DialogueTypewriter(String fullText, String color, String selector,
                              Consumer<UICommandBuilder> pushUpdate,
                              Runnable onTickSound, Runnable onComplete) {
        this(fullText, color, selector, pushUpdate, onTickSound, onComplete, 0L);
    }

    /**
     * Constructor with a pre-roll delay before the first character reveals. Used to
     * absorb client-side load time on first-spawn dialogue (see {@link
     * com.chonbosmods.ui.JiubIntroPage}). Page renders empty until the delay elapses,
     * then the typewriter begins. Pass 0 to start immediately.
     */
    public DialogueTypewriter(String fullText, String color, String selector,
                              Consumer<UICommandBuilder> pushUpdate,
                              Runnable onTickSound, Runnable onComplete,
                              long initialDelayMs) {
        this.fullText = (fullText != null) ? fullText : "";
        this.color = color;
        this.selector = selector;
        this.pushUpdate = pushUpdate;
        this.onTickSound = onTickSound;
        this.onComplete = onComplete;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.totalVisible = EntityHighlight.visibleLength(this.fullText);
    }

    /**
     * Schedule the first tick. With a non-zero {@code initialDelayMs} the page renders
     * empty until the delay elapses. If the text is empty, completes right away.
     */
    public void start() {
        if (fullText.isEmpty()) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }
        pendingFuture = SCHEDULER.schedule(this::tick, initialDelayMs, TimeUnit.MILLISECONDS);
    }

    /** Reveal the next visible character(s), push the partial text update, and schedule the next tick. */
    private void tick() {
        if (complete || visibleRevealed >= totalVisible) return;

        // Advance one visible character
        visibleRevealed++;

        // Map visible count to raw string index (skipping highlight markers)
        int rawEnd = EntityHighlight.rawIndexForVisibleCount(fullText, visibleRevealed);

        // Remember the visible character just revealed for delay calculation
        String stripped = EntityHighlight.stripMarkers(fullText);
        int revealedIndex = visibleRevealed - 1;

        // Skip past spaces: consume them instantly so words don't start with a gap.
        // Keep the pre-skip index for delay calculation so punctuation pauses
        // aren't swallowed by the trailing space.
        int delayIndex = revealedIndex;
        while (visibleRevealed < totalVisible && revealedIndex + 1 < stripped.length()
                && stripped.charAt(revealedIndex + 1) == ' ') {
            visibleRevealed++;
            rawEnd = EntityHighlight.rawIndexForVisibleCount(fullText, visibleRevealed);
            revealedIndex = visibleRevealed - 1;
        }

        // Push partial text update with colored entity spans
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(selector + ".TextSpans", EntityHighlight.toMessageSubstring(fullText, rawEnd, color));
        pushUpdate.accept(cmd);

        if (onTickSound != null) {
            onTickSound.run();
        }

        // Check completion
        if (visibleRevealed >= totalVisible) {
            complete = true;
            if (onComplete != null) onComplete.run();
            return;
        }

        // Determine delay based on the visible character revealed (before space skip)
        long delay = computeDelayFromStripped(stripped, delayIndex);
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

            // Reveal the full text with entity highlighting
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set(selector + ".TextSpans", EntityHighlight.toMessage(fullText, color));
            pushUpdate.accept(cmd);

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
    private long computeDelayFromStripped(String stripped, int revealedIndex) {
        if (revealedIndex < 0 || revealedIndex >= stripped.length()) return DELAY_DEFAULT;
        char revealed = stripped.charAt(revealedIndex);

        if (revealed == '.' || revealed == '?' || revealed == '!') {
            if (revealedIndex + 1 < stripped.length() && stripped.charAt(revealedIndex + 1) == '.') {
                return DELAY_DEFAULT;
            }
            if (revealed == '.' && revealedIndex >= 2
                    && stripped.charAt(revealedIndex - 1) == '.'
                    && stripped.charAt(revealedIndex - 2) == '.') {
                return DELAY_ELLIPSIS;
            }
            return DELAY_SENTENCE_END;
        }
        if (revealed == ',') return DELAY_COMMA;
        return DELAY_DEFAULT;
    }
}
