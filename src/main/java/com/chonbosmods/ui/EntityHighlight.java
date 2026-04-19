package com.chonbosmods.ui;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Inline marker system for highlighting entity names (NPCs, settlements) in
 * dialogue text. Control characters delimit highlighted spans so the UI can
 * render them in a distinct color.
 *
 * <p>Markers are invisible: they don't affect string matching, post-processing,
 * or plain-text display. The UI layer converts them to colored Message spans.</p>
 */
public final class EntityHighlight {

    /** Start of highlighted entity name span. */
    public static final char MARK_START = '\u0001';
    /** End of highlighted entity name span. */
    public static final char MARK_END = '\u0002';

    /** Default highlight color for entity names. */
    public static final String HIGHLIGHT_COLOR = "#CC99FF";

    private EntityHighlight() {}

    /** Wrap a value with highlight markers so the UI renders it in the entity color. */
    public static String wrap(String value) {
        if (value == null || value.isEmpty()) return value;
        return MARK_START + value + MARK_END;
    }

    /** Strip markers for plain-text contexts (logging, debugging). */
    public static String stripMarkers(String text) {
        if (text == null) return null;
        return text.replace(String.valueOf(MARK_START), "").replace(String.valueOf(MARK_END), "");
    }

    /** Count visible characters (excluding markers). */
    public static int visibleLength(String text) {
        if (text == null) return 0;
        int len = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != MARK_START && c != MARK_END) len++;
        }
        return len;
    }

    /**
     * Insert explicit {@code \n} at the last word boundary before {@code maxLineWidth}
     * visible characters, repeatedly, so the marked text wraps cleanly when rendered
     * via {@link #toMessage}. Necessary because Hytale's TextSpans renderer wraps
     * multi-{@code Message} chains at chain-element boundaries (character precision)
     * instead of word boundaries: single-span text wraps fine, but anything containing
     * a highlighted span breaks mid-word.
     *
     * <p>If the wrap point falls on a space INSIDE a highlighted span, the space is
     * replaced with {@code MARK_END + \n + MARK_START} so the resulting highlighted
     * spans never contain a newline. Hytale's renderer collapses all sibling-span
     * colors to the first child's color whenever any span contains {@code \n}, which
     * shows up as either "all text becomes the highlight color" (when the first span
     * is highlighted) or "no entity gets highlighted" (when the first span is plain
     * text). Splitting the highlight around the newline avoids both failure modes.
     *
     * <p>Marker characters ({@link #MARK_START} / {@link #MARK_END}) are preserved
     * verbatim and don't count toward line width. Existing newlines reset the line
     * counter. Words longer than {@code maxLineWidth} fall through unbroken.
     */
    public static String wrapMarkedText(String text, int maxLineWidth) {
        if (text == null || text.isEmpty() || maxLineWidth <= 0) return text;
        StringBuilder out = new StringBuilder(text.length() + 8);
        int lineWidth = 0;
        int lastSpaceOut = -1;
        int lineWidthAtLastSpace = 0;
        boolean insideHighlight = false;
        boolean lastSpaceInsideHighlight = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == MARK_START) {
                out.append(c);
                insideHighlight = true;
                continue;
            }
            if (c == MARK_END) {
                out.append(c);
                insideHighlight = false;
                continue;
            }

            if (c == '\n') {
                out.append(c);
                lineWidth = 0;
                lastSpaceOut = -1;
                lineWidthAtLastSpace = 0;
                lastSpaceInsideHighlight = false;
                continue;
            }

            out.append(c);
            lineWidth++;

            if (c == ' ') {
                lastSpaceOut = out.length() - 1;
                lineWidthAtLastSpace = lineWidth;
                lastSpaceInsideHighlight = insideHighlight;
            }

            if (lineWidth > maxLineWidth && lastSpaceOut >= 0) {
                if (lastSpaceInsideHighlight) {
                    // Close the highlight, insert the newline, reopen the highlight
                    // so neither half of the wrapped span contains \n.
                    String replacement = "" + MARK_END + '\n' + MARK_START;
                    out.replace(lastSpaceOut, lastSpaceOut + 1, replacement);
                } else {
                    out.setCharAt(lastSpaceOut, '\n');
                }
                lineWidth = lineWidth - lineWidthAtLastSpace;
                lastSpaceOut = -1;
                lineWidthAtLastSpace = 0;
                lastSpaceInsideHighlight = false;
            }
        }
        return out.toString();
    }

    /**
     * Find the visible-character index that corresponds to the given raw string index.
     * Used by the typewriter to map visible character count to substring positions.
     */
    public static int rawIndexForVisibleCount(String text, int visibleCount) {
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != MARK_START && c != MARK_END) {
                visible++;
                if (visible >= visibleCount) return i + 1;
            }
        }
        return text.length();
    }

    /**
     * Convert marked text to a Hytale Message with colored spans.
     * Normal text uses baseColor, marked spans use {@link #HIGHLIGHT_COLOR}.
     */
    public static Message toMessage(String text, String baseColor) {
        if (text == null || text.isEmpty()) return Message.raw("").color(baseColor);

        List<Span> spans = parse(text);
        if (spans.isEmpty()) return Message.raw("").color(baseColor);

        // Single span with no highlights: simple colored message
        if (spans.size() == 1 && !spans.getFirst().highlighted) {
            return Message.raw(spans.getFirst().text).color(baseColor);
        }

        // Hytale's TextSpans renderer applies the label's default text color to
        // the root Message, ignoring its explicit .color(). All visible text must
        // therefore be inserted as children so each span's color is respected.
        Message msg = Message.raw("").color(baseColor);
        for (Span span : spans) {
            if (span.text.isEmpty()) continue;
            msg = msg.insert(Message.raw(span.text).color(span.highlighted ? HIGHLIGHT_COLOR : baseColor));
        }
        return msg;
    }

    /**
     * Convert a substring of marked text (up to rawEndIndex) to a colored Message.
     * Used by the typewriter for partial reveals.
     */
    public static Message toMessageSubstring(String fullText, int rawEndIndex, String baseColor) {
        String sub = fullText.substring(0, Math.min(rawEndIndex, fullText.length()));
        // If we cut inside a highlighted span, close it gracefully
        return toMessage(sub, baseColor);
    }

    private static List<Span> parse(String text) {
        List<Span> spans = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean highlighted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == MARK_START) {
                if (!current.isEmpty()) {
                    spans.add(new Span(current.toString(), highlighted));
                    current.setLength(0);
                }
                highlighted = true;
            } else if (c == MARK_END) {
                if (!current.isEmpty()) {
                    spans.add(new Span(current.toString(), highlighted));
                    current.setLength(0);
                }
                highlighted = false;
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            spans.add(new Span(current.toString(), highlighted));
        }
        return spans;
    }

    private record Span(String text, boolean highlighted) {}
}
