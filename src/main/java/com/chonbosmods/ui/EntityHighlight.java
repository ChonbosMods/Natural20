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
        if (spans.size() == 1) {
            return Message.raw(spans.getFirst().text)
                .color(spans.getFirst().highlighted ? HIGHLIGHT_COLOR : baseColor);
        }

        // Multiple spans: build chain starting from the first span
        Message msg = Message.raw(spans.getFirst().text)
            .color(spans.getFirst().highlighted ? HIGHLIGHT_COLOR : baseColor);
        for (int i = 1; i < spans.size(); i++) {
            Span span = spans.get(i);
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
