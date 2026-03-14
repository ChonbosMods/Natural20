package com.chonbosmods.loot.display;

import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nullable;

public class Nat20TooltipBuilder {

    private static final String COLOR_MUTED = "#888888";
    private static final String COLOR_EFFECT = "#cc99ff";
    private static final String COLOR_SOCKET_EMPTY = "#666666";
    private static final String COLOR_MET = "#33cc33";
    private static final String COLOR_UNMET = "#cc3333";

    /**
     * Build a rich tooltip Message from resolved display data.
     *
     * @param data    the resolved item display data
     * @param deltas  nullable comparison deltas (null = no comparison)
     * @return composed Message suitable for TooltipTextSpans
     */
    public static Message build(Nat20ItemDisplayData data, @Nullable ComparisonDeltas deltas) {
        Message tooltip = Message.raw(data.name()).bold(true).color(data.rarityColor());

        // Rarity label
        tooltip.insert(Message.raw("\n" + data.rarity()).color(data.rarityColor()));

        // STAT affix lines
        for (AffixLine affix : data.affixes()) {
            if (!"STAT".equals(affix.type())) continue;
            tooltip.insert(buildStatLine(affix, deltas));
        }

        // EFFECT affix lines
        for (AffixLine affix : data.affixes()) {
            if (!"EFFECT".equals(affix.type())) continue;
            tooltip.insert(buildEffectLine(affix));
        }

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

    private static Message buildStatLine(AffixLine affix, @Nullable ComparisonDeltas deltas) {
        String line = "\n" + affix.value() + affix.unit() + " " + affix.statName();
        Message msg = Message.raw(line);

        // Color the stat value by scaling stat if present
        if (affix.scalingStat() != null) {
            msg.color(Stat.colorFor(affix.scalingStat()));
            msg.insert(Message.raw(" " + affix.scalingStat()).color(COLOR_MUTED));
        }

        // Comparison delta
        if (deltas != null) {
            ComparisonDeltas.Delta delta = deltas.getForStat(affix.statName());
            if (delta != null) {
                msg.insert(Message.raw(" " + delta.symbol()).color(delta.color()));
            }
        }

        // Unmet requirement warning
        if (!affix.requirementMet() && affix.requirementText() != null) {
            msg.insert(Message.raw("\n  Requires: " + affix.requirementText()).color(COLOR_UNMET));
        }

        return msg;
    }

    private static Message buildEffectLine(AffixLine affix) {
        // Use description if available, otherwise fall back to value + stat
        String text;
        if (affix.description() != null && !affix.description().isEmpty()) {
            text = "\n" + affix.description();
        } else {
            text = "\n" + affix.value() + affix.unit() + " " + affix.statName();
        }
        Message msg = Message.raw(text).color(COLOR_EFFECT);

        // Append proc chance and cooldown as secondary info
        StringBuilder details = new StringBuilder();
        if (affix.procChance() != null) {
            details.append(affix.procChance()).append(" chance");
        }
        if (affix.cooldown() != null) {
            if (!details.isEmpty()) details.append(", ");
            details.append(affix.cooldown()).append(" cd");
        }
        if (!details.isEmpty()) {
            msg.insert(Message.raw(" (" + details + ")").color(COLOR_MUTED));
        }

        // Unmet requirement warning
        if (!affix.requirementMet() && affix.requirementText() != null) {
            msg.insert(Message.raw("\n  Requires: " + affix.requirementText()).color(COLOR_UNMET));
        }

        return msg;
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
