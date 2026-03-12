package com.chonbosmods.ui;

import com.chonbosmods.dialogue.model.FollowUpState;
import com.chonbosmods.dialogue.model.LogEntry;
import com.chonbosmods.dialogue.model.ResolvedTopic;
import com.chonbosmods.dialogue.model.TopicState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.stats.Stat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class Nat20DialoguePage extends InteractiveCustomUIPage<Nat20DialoguePage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|DialoguePage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_Dialogue.ui";

    private static final int MAX_TOPICS = 10;
    private static final int MAX_FOLLOW_UPS = 6;
    private static final int MAX_LOG_LABELS = 20;

    // Log rendering colors (per visual polish guide)
    private static final String COLOR_TOPIC_HEADER = "#666666";
    private static final String COLOR_NPC_SPEECH = "#FFCC00";
    private static final String COLOR_SELECTED_FOLLOW_UP = "#888888";
    private static final String COLOR_SYSTEM_TEXT = "#66BB77";
    private static final String COLOR_RETURN_GREETING = "#FFCC00";

    public static final BuilderCodec<PageEventData> EVENT_CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new)
            .addField(new KeyedCodec<>("Type", Codec.STRING), PageEventData::setType, PageEventData::getType)
            .addField(new KeyedCodec<>("Id", Codec.STRING), PageEventData::setId, PageEventData::getId)
            .build();

    // State fields: pushed by presenter before opening
    private String npcName;
    private List<LogEntry> log = List.of();
    private List<ResolvedTopic> topics = List.of();
    private int disposition;
    private boolean topicsLocked;
    private BiConsumer<String, String> onEvent;

    public Nat20DialoguePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
    }

    public void setState(String npcName, List<LogEntry> log, List<ResolvedTopic> topics,
                         int disposition, boolean topicsLocked, BiConsumer<String, String> onEvent) {
        this.npcName = npcName;
        this.log = log != null ? log : List.of();
        this.topics = topics != null ? topics : List.of();
        this.disposition = disposition;
        this.topicsLocked = topicsLocked;
        this.onEvent = onEvent;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        if (onEvent == null) return;

        cmd.append(PAGE_LAYOUT);
        cmd.set("#NPCName.Text", npcName != null ? npcName : "");

        buildTopics(cmd, events);
        buildLog(cmd);
        buildFollowUps(cmd, events);
        buildDisposition(cmd);

        // Goodbye button binding
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#GoodbyeButton",
                EventData.of("Type", "goodbye").append("Id", ""),
                false);
    }

    private void buildTopics(UICommandBuilder cmd, UIEventBuilder events) {
        if (topics.size() > MAX_TOPICS) {
            LOGGER.atWarning().log("Too many topics: " + topics.size() + " (max " + MAX_TOPICS + ")");
        }

        for (int i = 0; i < MAX_TOPICS; i++) {
            String selector = "#Topic" + (i + 1);
            if (i < topics.size()) {
                ResolvedTopic rt = topics.get(i);
                boolean grayed = rt.state() == TopicState.GRAYED;
                cmd.set(selector + ".Visible", true);

                if (grayed) {
                    cmd.set(selector + ".Text", rt.topic().label());
                    cmd.set(selector + ".Style.TextColor", "#666666");
                } else if (rt.topic().statPrefix() != null) {
                    String statColor = Stat.colorFor(rt.topic().statPrefix());
                    String bracket = "[" + rt.topic().statPrefix().replace("[","").replace("]","").trim().toUpperCase() + "]";
                    Message label = Message.raw(bracket).color(statColor)
                            .insert(Message.raw(" " + rt.topic().label()).color("#FFFFFF"));
                    cmd.set(selector + ".TextSpans", label);
                } else {
                    cmd.set(selector + ".Text", rt.topic().label());
                }

                cmd.set(selector + ".Disabled", topicsLocked);

                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector,
                        EventData.of("Type", "topic").append("Id", rt.topic().id()),
                        false);
            } else {
                cmd.set(selector + ".Visible", false);
            }
        }
    }

    private void buildLog(UICommandBuilder cmd) {
        // Collect renderable log entries
        List<LogLine> lines = new ArrayList<>();
        for (LogEntry entry : log) {
            switch (entry) {
                case LogEntry.TopicHeader h -> {
                    if (!lines.isEmpty()) {
                        lines.add(new LogLine(" ", COLOR_TOPIC_HEADER));
                    }
                    lines.add(new LogLine("-- " + h.label() + " --", COLOR_TOPIC_HEADER));
                }
                case LogEntry.NpcSpeech s ->
                    lines.add(new LogLine(s.text(), COLOR_NPC_SPEECH));
                case LogEntry.FollowUp f -> {
                    if (f.state() == FollowUpState.SELECTED) {
                        String clean = cleanText(f.displayText());
                        if (f.statPrefix() != null) {
                            String bracket = "[" + f.statPrefix().replace("[","").replace("]","").trim().toUpperCase() + "]";
                            lines.add(new LogLine("> " + bracket + " " + clean, COLOR_SELECTED_FOLLOW_UP));
                        } else {
                            lines.add(new LogLine("> " + clean, COLOR_SELECTED_FOLLOW_UP));
                        }
                    }
                }
                case LogEntry.SystemText s ->
                    lines.add(new LogLine(s.text(), COLOR_SYSTEM_TEXT));
                case LogEntry.ReturnGreeting r ->
                    lines.add(new LogLine(r.text(), COLOR_RETURN_GREETING));
                case LogEntry.ReturnDivider ignored ->
                    lines.add(new LogLine("\u2500\u2500\u2500", "#555555"));
            }
        }

        // Fill pre-defined #Log1..#Log20 labels
        for (int i = 0; i < MAX_LOG_LABELS; i++) {
            String selector = "#Log" + (i + 1);
            if (i < lines.size()) {
                LogLine line = lines.get(i);
                cmd.set(selector + ".Visible", true);
                cmd.set(selector + ".TextSpans", Message.raw(line.text).color(line.color));
            } else {
                cmd.set(selector + ".Visible", false);
            }
        }
    }

    private record LogLine(String text, String color) {}

    /** Strip leading ?, >, or special prefixes from display text (legacy data cleanup). */
    private static String cleanText(String text) {
        if (text == null) return "";
        text = text.strip();
        while (text.startsWith("?") || text.startsWith(">") || text.startsWith("\u25b8")) {
            text = text.substring(1).strip();
        }
        return text;
    }

    private void buildFollowUps(UICommandBuilder cmd, UIEventBuilder events) {
        List<LogEntry.FollowUp> visible = new ArrayList<>();
        for (LogEntry entry : log) {
            if (entry instanceof LogEntry.FollowUp f
                    && (f.state() == FollowUpState.AVAILABLE || f.state() == FollowUpState.GRAYED)) {
                visible.add(f);
            }
        }

        cmd.set("#FollowUpArea.Visible", !visible.isEmpty());

        if (visible.size() > MAX_FOLLOW_UPS) {
            LOGGER.atWarning().log("Too many follow-ups: " + visible.size() + " (max " + MAX_FOLLOW_UPS + ")");
        }

        for (int i = 0; i < MAX_FOLLOW_UPS; i++) {
            String selector = "#FollowUp" + (i + 1);
            if (i < visible.size()) {
                LogEntry.FollowUp f = visible.get(i);
                boolean grayed = f.state() == FollowUpState.GRAYED;
                cmd.set(selector + ".Visible", true);

                if (grayed) {
                    String clean = cleanText(f.displayText());
                    cmd.set(selector + ".TextSpans", Message.raw("\u25b8 " + clean).color("#666666"));
                    cmd.set(selector + ".Disabled", true);
                } else {
                    String clean = cleanText(f.displayText());
                    if (f.statPrefix() != null) {
                        String statColor = Stat.colorFor(f.statPrefix());
                        String bracket = "[" + f.statPrefix().replace("[","").replace("]","").trim().toUpperCase() + "]";
                        Message label = Message.raw(bracket).color(statColor)
                                .insert(Message.raw(" " + clean).color("#FFFFFF"));
                        cmd.set(selector + ".TextSpans", label);
                    } else {
                        cmd.set(selector + ".TextSpans", Message.raw(clean).color("#FFFFFF"));
                    }

                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            selector,
                            EventData.of("Type", "followup").append("Id", f.responseId()),
                            false);
                }
            } else {
                cmd.set(selector + ".Visible", false);
            }
        }
    }

    private void buildDisposition(UICommandBuilder cmd) {
        DispositionBracket bracket = DispositionBracket.fromDisposition(disposition);
        String bracketName = switch (bracket) {
            case HOSTILE -> "Hostile";
            case UNFRIENDLY -> "Unfriendly";
            case NEUTRAL -> "Neutral";
            case FRIENDLY -> "Friendly";
            case ALLIED -> "Allied";
        };
        String color = switch (bracket) {
            case HOSTILE -> "#CC4444";
            case UNFRIENDLY -> "#CC8844";
            case NEUTRAL -> "#CCCC44";
            case FRIENDLY -> "#44CC44";
            case ALLIED -> "#4488FF";
        };

        cmd.set("#DispositionLabel.Visible", true);
        cmd.set("#DispositionLabel.TextSpans",
                Message.raw(disposition + " \u2014 " + bracketName).color(color));
    }

    // --- Update methods called by presenter for incremental updates ---

    public void updateLogAndFollowUps(List<LogEntry> log, boolean topicsLocked) {
        this.log = log != null ? log : List.of();
        this.topicsLocked = topicsLocked;
        rebuild();
    }

    public void updateTopics(List<ResolvedTopic> topics, boolean topicsLocked) {
        this.topics = topics != null ? topics : List.of();
        this.topicsLocked = topicsLocked;
        rebuild();
    }

    public void updateDisposition(int disposition) {
        this.disposition = disposition;
        UICommandBuilder cmd = new UICommandBuilder();
        buildDisposition(cmd);
        sendUpdate(cmd);
    }

    // --- Event handling ---

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        String id = data.getId();
        LOGGER.atInfo().log("handleDataEvent: type='" + type + "', id='" + id + "'");
        if (type == null || type.isEmpty()) return;

        // Server-side guard: reject topic clicks while locked
        if ("topic".equals(type) && topicsLocked) {
            LOGGER.atWarning().log("Rejected topic click while locked: id='" + id + "'");
            return;
        }

        if (onEvent != null) {
            onEvent.accept(type, id);
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        LOGGER.atInfo().log("onDismiss");
        if (onEvent != null) {
            onEvent.accept("goodbye", "");
        }
    }

    public void clearEventHandler() {
        onEvent = null;
    }

    public void closePage() {
        close();
    }

    // --- Inner event data class ---

    public static class PageEventData {
        private String type = "";
        private String id = "";

        public PageEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
