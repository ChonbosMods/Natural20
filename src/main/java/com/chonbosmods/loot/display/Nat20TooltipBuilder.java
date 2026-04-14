package com.chonbosmods.loot.display;

import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nullable;

public class Nat20TooltipBuilder {

    private static final String COLOR_MUTED = "#888888";
    private static final String COLOR_SOCKET_EMPTY = "#666666";
    private static final String COLOR_MET = "#33cc33";
    private static final String COLOR_UNMET = "#cc3333";

    /**
     * Build a rich tooltip Message from resolved display data. Each affix line's
     * {@code renderedText} contains the canonical Hytale colour-markup form, so this builder
     * emits the raw markup inside {@link Message#raw} — the client-side parser handles it.
     *
     * @param data    the resolved item display data
     * @param deltas  nullable comparison deltas (null = no comparison)
     * @return composed Message suitable for TooltipTextSpans
     */
    public static Message build(Nat20ItemDisplayData data, @Nullable ComparisonDeltas deltas) {
        Message tooltip = Message.raw(data.name()).bold(true).color(data.rarityColor());

        // Rarity label
        tooltip.insert(Message.raw("\n" + data.rarity()).color(data.rarityColor()));

        // Affix lines: STAT → EFFECT → ABILITY order.
        appendAffixes(tooltip, data, "STAT", deltas);
        appendAffixes(tooltip, data, "EFFECT", deltas);
        appendAffixes(tooltip, data, "ABILITY", deltas);

        // Socket lines
        if (!data.sockets().isEmpty()) {
            long filled = data.sockets().stream().filter(SocketLine::filled).count();
            tooltip.insert(Message.raw("\nSockets [" + filled + "/" + data.sockets().size() + "]:").color(COLOR_MUTED));
            for (SocketLine socket : data.sockets()) {
                tooltip.insert(buildSocketLine(socket));
            }
        }

        // Requirement line
        if (data.requirement() != null) {
            String reqColor = data.requirement().met() ? COLOR_MET : COLOR_UNMET;
            tooltip.insert(Message.raw("\nRequires: " + data.requirement().text()).color(reqColor));
        }

        // Description / flavor text
        if (data.description() != null && !data.description().isEmpty()) {
            tooltip.insert(Message.raw("\n" + data.description()).italic(true).color(COLOR_MUTED));
        }

        return tooltip;
    }

    private static void appendAffixes(Message tooltip, Nat20ItemDisplayData data, String type,
                                       @Nullable ComparisonDeltas deltas) {
        for (AffixLine affix : data.affixes()) {
            if (!type.equals(affix.type())) continue;
            tooltip.insert(Message.raw("\n" + affix.renderedText()));

            // Comparison delta (STAT only)
            if (deltas != null && "STAT".equals(type)) {
                ComparisonDeltas.Delta delta = deltas.getForStat(affix.statName());
                if (delta != null) {
                    tooltip.insert(Message.raw(" " + delta.symbol()).color(delta.color()));
                }
            }

            if (!affix.requirementMet() && affix.requirementText() != null) {
                tooltip.insert(Message.raw("\n  Requires: " + affix.requirementText()).color(COLOR_UNMET));
            }
        }
    }

    private static Message buildSocketLine(SocketLine socket) {
        if (socket.filled()) {
            String gemText = "\n  " + socket.purity() + " " + socket.gemName();
            Message msg = Message.raw(gemText).color(socket.gemColor());
            if (socket.bonusValue() != null && socket.bonusStat() != null) {
                msg.insert(Message.raw(" " + socket.bonusValue() + " " + socket.bonusStat()).color(COLOR_MUTED));
            }
            return msg;
        } else {
            return Message.raw("\n  [Empty Socket]").color(COLOR_SOCKET_EMPTY);
        }
    }
}
