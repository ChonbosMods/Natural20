package com.chonbosmods.loot.display;

import com.chonbosmods.loot.Nat20ItemDisplayData;

/**
 * Generates tooltip description strings with Hytale color markup for I18n injection.
 * Uses {@code <color is="#hex">text</color>} syntax supported by the client tooltip renderer.
 */
public class Nat20TooltipStringBuilder {

    private static final String COLOR_MUTED = "#888888";
    private static final String COLOR_SOCKET_EMPTY = "#666666";
    private static final String COLOR_MET = "#33cc33";
    private static final String COLOR_UNMET = "#cc3333";

    public static String buildDescription(Nat20ItemDisplayData data) {
        StringBuilder sb = new StringBuilder();

        // Affix lines in fixed order: STAT scores, then EFFECT, then ABILITY.
        appendAffixesOfType(sb, data, "STAT");
        appendAffixesOfType(sb, data, "EFFECT");
        appendAffixesOfType(sb, data, "ABILITY");

        // Socket lines
        if (!data.sockets().isEmpty()) {
            long filled = data.sockets().stream().filter(SocketLine::filled).count();
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(color(COLOR_MUTED, "Sockets [" + filled + "/" + data.sockets().size() + "]:"));
            for (SocketLine socket : data.sockets()) {
                sb.append("\n");
                appendSocketLine(sb, socket);
            }
        }

        // Requirement line
        if (data.requirement() != null) {
            if (!sb.isEmpty()) sb.append("\n");
            String reqColor = data.requirement().met() ? COLOR_MET : COLOR_UNMET;
            sb.append(color(reqColor, "Requires: " + data.requirement().text()));
        }

        return sb.toString();
    }

    private static void appendAffixesOfType(StringBuilder sb, Nat20ItemDisplayData data, String type) {
        for (AffixLine affix : data.affixes()) {
            if (!type.equals(affix.type())) continue;
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(affix.renderedText());
            if (!affix.requirementMet() && affix.requirementText() != null) {
                sb.append("\n  ").append(color(COLOR_UNMET, "Requires: " + affix.requirementText()));
            }
        }
    }

    private static void appendSocketLine(StringBuilder sb, SocketLine socket) {
        if (socket.filled()) {
            sb.append("  ").append(color(socket.gemColor(), socket.purity() + " " + socket.gemName()));
            if (socket.bonusValue() != null && socket.bonusStat() != null) {
                sb.append(" ").append(color(COLOR_MUTED, socket.bonusValue() + " " + socket.bonusStat()));
            }
        } else {
            sb.append("  ").append(color(COLOR_SOCKET_EMPTY, "[Empty Socket]"));
        }
    }

    private static String color(String hex, String text) {
        return "<color is=\"" + hex + "\">" + text + "</color>";
    }
}
