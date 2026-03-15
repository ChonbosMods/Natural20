package com.chonbosmods.loot.display;

import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.stats.Stat;

/**
 * Generates tooltip description strings with Hytale color markup for I18n injection.
 * Uses {@code <color is="#hex">text</color>} syntax supported by the client tooltip renderer.
 */
public class Nat20TooltipStringBuilder {

    private static final String COLOR_MUTED = "#888888";
    private static final String COLOR_EFFECT = "#cc99ff";
    private static final String COLOR_SOCKET_EMPTY = "#666666";
    private static final String COLOR_MET = "#33cc33";
    private static final String COLOR_UNMET = "#cc3333";

    public static String buildDescription(Nat20ItemDisplayData data) {
        StringBuilder sb = new StringBuilder();

        // STAT affix lines
        for (AffixLine affix : data.affixes()) {
            if (!"STAT".equals(affix.type())) continue;
            if (!sb.isEmpty()) sb.append("\n");
            appendStatLine(sb, affix);
        }

        // EFFECT affix lines
        for (AffixLine affix : data.affixes()) {
            if (!"EFFECT".equals(affix.type())) continue;
            if (!sb.isEmpty()) sb.append("\n");
            appendEffectLine(sb, affix);
        }

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

    private static void appendStatLine(StringBuilder sb, AffixLine affix) {
        String valueText = affix.value() + affix.unit() + " " + affix.statName();
        if (affix.scalingStat() != null) {
            String statColor = Stat.colorFor(affix.scalingStat());
            sb.append(color(statColor, valueText));
            sb.append(" ").append(color(COLOR_MUTED, affix.scalingStat()));
        } else {
            sb.append(valueText);
        }

        if (!affix.requirementMet() && affix.requirementText() != null) {
            sb.append("\n  ").append(color(COLOR_UNMET, "Requires: " + affix.requirementText()));
        }
    }

    private static void appendEffectLine(StringBuilder sb, AffixLine affix) {
        String text;
        if (affix.description() != null && !affix.description().isEmpty()) {
            text = affix.description();
        } else {
            text = affix.value() + affix.unit() + " " + affix.statName();
        }
        sb.append(color(COLOR_EFFECT, text));

        StringBuilder details = new StringBuilder();
        if (affix.procChance() != null) {
            details.append(affix.procChance()).append(" chance");
        }
        if (affix.cooldown() != null) {
            if (!details.isEmpty()) details.append(", ");
            details.append(affix.cooldown()).append(" cd");
        }
        if (!details.isEmpty()) {
            sb.append(" ").append(color(COLOR_MUTED, "(" + details + ")"));
        }

        if (!affix.requirementMet() && affix.requirementText() != null) {
            sb.append("\n  ").append(color(COLOR_UNMET, "Requires: " + affix.requirementText()));
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
