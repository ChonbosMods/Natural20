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
 *
 * <p>Two forms are supported: {@code text} renders in the default
 * {@link #HIGHLIGHT_COLOR}, and {@code #RRGGBBtext} renders
 * in an explicit hex color. The color prefix is invisible to line wrapping,
 * typewriter reveal, and length counting.</p>
 */
public final class EntityHighlight {

    /** Start of highlighted entity name span. */
    public static final char MARK_START = 1;
    /** End of highlighted entity name span. */
    public static final char MARK_END = 2;
    /** Separator between an inline {@code #RRGGBB} color prefix and the span text. */
    public static final char COLOR_SEP = 0x1F;

    /** Default highlight color for entity names. */
    public static final String HIGHLIGHT_COLOR = "#CC99FF";

    /** Length of an inline color prefix ({@code #RRGGBB} + separator). */
    private static final int COLOR_PREFIX_LEN = 8;

    private EntityHighlight() {}

    /** Wrap a value with highlight markers so the UI renders it in the entity color. */
    public static String wrap(String value) {
        if (value == null || value.isEmpty()) return value;
        return MARK_START + value + MARK_END;
    }

    /**
     * Wrap a value with an explicit {@code #RRGGBB} color. If {@code color} is null
     * or malformed, falls back to the default {@link #wrap(String)} behavior.
     */
    public static String wrap(String value, String color) {
        if (value == null || value.isEmpty()) return value;
        if (color == null || !isValidHexColor(color)) return wrap(value);
        return "" + MARK_START + color + COLOR_SEP + value + MARK_END;
    }

    /** Strip markers (and any inline color prefix) for plain-text contexts. */
    public static String stripMarkers(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int mlen = markerLength(text, i);
            if (mlen > 0) {
                i += mlen;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /** Count visible characters (excluding markers and color prefixes). */
    public static int visibleLength(String text) {
        if (text == null) return 0;
        int len = 0;
        int i = 0;
        while (i < text.length()) {
            int mlen = markerLength(text, i);
            if (mlen > 0) {
                i += mlen;
            } else {
                len++;
                i++;
            }
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
     * replaced with {@code MARK_END + \n + MARK_START} (plus the span's color prefix
     * if it had one) so the resulting highlighted spans never contain a newline.
     * Hytale's renderer collapses all sibling-span colors to the first child's color
     * whenever any span contains {@code \n}. Splitting the highlight around the
     * newline avoids both failure modes.
     *
     * <p>Markers and color prefixes are preserved verbatim and don't count toward
     * line width. Existing newlines reset the line counter. Words longer than
     * {@code maxLineWidth} fall through unbroken.
     */
    public static String wrapMarkedText(String text, int maxLineWidth) {
        if (text == null || text.isEmpty() || maxLineWidth <= 0) return text;
        StringBuilder out = new StringBuilder(text.length() + 8);
        int lineWidth = 0;
        int lastSpaceOut = -1;
        int lineWidthAtLastSpace = 0;
        boolean insideHighlight = false;
        String openColorPrefix = "";
        boolean lastSpaceInsideHighlight = false;
        String lastSpaceOpenColorPrefix = "";

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (c == MARK_START) {
                out.append(c);
                insideHighlight = true;
                int prefixEnd = readColorPrefixEnd(text, i + 1);
                if (prefixEnd > i + 1) {
                    String prefix = text.substring(i + 1, prefixEnd);
                    out.append(prefix);
                    openColorPrefix = prefix;
                    i = prefixEnd;
                } else {
                    openColorPrefix = "";
                    i++;
                }
                continue;
            }
            if (c == MARK_END) {
                out.append(c);
                insideHighlight = false;
                openColorPrefix = "";
                i++;
                continue;
            }

            if (c == '\n') {
                out.append(c);
                lineWidth = 0;
                lastSpaceOut = -1;
                lineWidthAtLastSpace = 0;
                lastSpaceInsideHighlight = false;
                lastSpaceOpenColorPrefix = "";
                i++;
                continue;
            }

            out.append(c);
            lineWidth++;

            if (c == ' ') {
                lastSpaceOut = out.length() - 1;
                lineWidthAtLastSpace = lineWidth;
                lastSpaceInsideHighlight = insideHighlight;
                lastSpaceOpenColorPrefix = openColorPrefix;
            }

            if (lineWidth > maxLineWidth && lastSpaceOut >= 0) {
                if (lastSpaceInsideHighlight) {
                    // Close the highlight, insert the newline, reopen the highlight
                    // (with the same color prefix if any) so neither half contains \n.
                    String replacement = "" + MARK_END + '\n' + MARK_START + lastSpaceOpenColorPrefix;
                    out.replace(lastSpaceOut, lastSpaceOut + 1, replacement);
                } else {
                    out.setCharAt(lastSpaceOut, '\n');
                }
                lineWidth = lineWidth - lineWidthAtLastSpace;
                lastSpaceOut = -1;
                lineWidthAtLastSpace = 0;
                lastSpaceInsideHighlight = false;
                lastSpaceOpenColorPrefix = "";
            }
            i++;
        }
        return out.toString();
    }

    /**
     * Find the visible-character index that corresponds to the given raw string index.
     * Used by the typewriter to map visible character count to substring positions.
     */
    public static int rawIndexForVisibleCount(String text, int visibleCount) {
        int visible = 0;
        int i = 0;
        while (i < text.length()) {
            int mlen = markerLength(text, i);
            if (mlen > 0) {
                i += mlen;
            } else {
                visible++;
                i++;
                if (visible >= visibleCount) return i;
            }
        }
        return text.length();
    }

    /**
     * Convert marked text to a Hytale Message with colored spans.
     * Normal text uses baseColor, marked spans use their inline color (if any) or {@link #HIGHLIGHT_COLOR}.
     */
    public static Message toMessage(String text, String baseColor) {
        if (text == null || text.isEmpty()) return Message.raw("").color(baseColor);

        List<Span> spans = parse(text);
        if (spans.isEmpty()) return Message.raw("").color(baseColor);

        // Single span with no highlights: simple colored message
        if (spans.size() == 1 && spans.getFirst().color == null) {
            return Message.raw(spans.getFirst().text).color(baseColor);
        }

        // Hytale's TextSpans renderer applies the label's default text color to
        // the root Message, ignoring its explicit .color(). All visible text must
        // therefore be inserted as children so each span's color is respected.
        Message msg = Message.raw("").color(baseColor);
        for (Span span : spans) {
            if (span.text.isEmpty()) continue;
            String color = (span.color != null) ? span.color : baseColor;
            msg = msg.insert(Message.raw(span.text).color(color));
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
        String currentColor = null;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == MARK_START) {
                if (!current.isEmpty()) {
                    spans.add(new Span(current.toString(), currentColor));
                    current.setLength(0);
                }
                int prefixEnd = readColorPrefixEnd(text, i + 1);
                if (prefixEnd > i + 1) {
                    // Consume "#RRGGBB"; color is the 7-char hex (including '#')
                    currentColor = text.substring(i + 1, prefixEnd - 1);
                    i = prefixEnd;
                } else {
                    currentColor = HIGHLIGHT_COLOR;
                    i++;
                }
            } else if (c == MARK_END) {
                if (!current.isEmpty()) {
                    spans.add(new Span(current.toString(), currentColor));
                    current.setLength(0);
                }
                currentColor = null;
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        if (!current.isEmpty()) {
            spans.add(new Span(current.toString(), currentColor));
        }
        return spans;
    }

    /**
     * Returns the number of non-visible characters at index {@code i}, or 0 if the
     * character at {@code i} is visible. A {@link #MARK_START} may be followed by
     * an optional 8-char color prefix; both are non-visible as a unit.
     */
    private static int markerLength(String text, int i) {
        if (text == null || i >= text.length()) return 0;
        char c = text.charAt(i);
        if (c == MARK_END) return 1;
        if (c == MARK_START) {
            int prefixEnd = readColorPrefixEnd(text, i + 1);
            if (prefixEnd > i + 1) return 1 + (prefixEnd - (i + 1));
            return 1;
        }
        return 0;
    }

    /**
     * If {@code text} at {@code start} matches {@code #RRGGBB}, returns the
     * index just past the separator (i.e. {@code start + 8}). Otherwise returns
     * {@code start} to signal "no color prefix here".
     */
    private static int readColorPrefixEnd(String text, int start) {
        if (start + COLOR_PREFIX_LEN > text.length()) return start;
        if (text.charAt(start) != '#') return start;
        for (int j = 0; j < 6; j++) {
            if (!isHexChar(text.charAt(start + 1 + j))) return start;
        }
        if (text.charAt(start + 7) != COLOR_SEP) return start;
        return start + COLOR_PREFIX_LEN;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isValidHexColor(String s) {
        if (s == null || s.length() != 7 || s.charAt(0) != '#') return false;
        for (int i = 1; i < 7; i++) if (!isHexChar(s.charAt(i))) return false;
        return true;
    }

    private record Span(String text, String color) {}
}
