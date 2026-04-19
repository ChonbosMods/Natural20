package com.chonbosmods.ui;

import com.chonbosmods.dice.SkillCheckResult;
import com.chonbosmods.dice.render.DiceSpriteGenerator;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Nat20DiceRollPage extends InteractiveCustomUIPage<Nat20DiceRollPage.DiceEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|DicePage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_DiceRoll.ui";
    private static final int TUMBLE_FRAMES = DiceSpriteGenerator.TUMBLE_FRAME_COUNT;
    private static final long TUMBLE_FRAME_MS = 80;
    private static final long SETTLE_DELAY_MS = 200;

    // Frame selectors: #T01-#T10 for tumble, #R01-#R20 for results
    private static final String[] TUMBLE_IDS = new String[TUMBLE_FRAMES];
    private static final String[] RESULT_IDS = new String[20];
    static {
        for (int i = 0; i < TUMBLE_FRAMES; i++) TUMBLE_IDS[i] = String.format("#T%02d", i + 1);
        for (int i = 0; i < 20; i++) RESULT_IDS[i] = String.format("#R%02d", i + 1);
    }

    public static final BuilderCodec<DiceEventData> EVENT_CODEC = BuilderCodec.builder(DiceEventData.class, DiceEventData::new)
            .addField(new KeyedCodec<>("Type", Codec.STRING), DiceEventData::setType, DiceEventData::getType)
            .build();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nat20-DiceAnimation");
        t.setDaemon(true);
        return t;
    });

    private final Skill skill;
    private final Stat stat;
    private final SkillCheckResult result;
    private final Consumer<SkillCheckResult> onContinue;
    private final Random random = new Random();

    private enum State { PRE_ROLL, ROLLING, RESULT }
    private volatile State state = State.PRE_ROLL;
    private volatile int visibleFrameIdx = -1;
    private ScheduledFuture<?> animationTask;

    public Nat20DiceRollPage(PlayerRef playerRef, Skill skill, Stat stat,
                             SkillCheckResult result,
                             Consumer<SkillCheckResult> onContinue) {
        super(playerRef, CustomPageLifetime.CantClose, EVENT_CODEC);
        this.skill = skill;
        this.stat = stat;
        this.result = result;
        this.onContinue = onContinue;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        // DC: "Difficulty Class 14" (under title, above dice)
        cmd.set("#DCInfo.Text", "Difficulty Class " + result.dc());

        // Skill name: bold, prominent
        cmd.set("#SkillName.Text", skill.name());

        // Stat line: colored bracket + full name
        if (stat != null) {
            cmd.set("#StatInfo.TextSpans",
                    Message.raw(stat.bracket()).color(stat.color())
                            .insert(Message.raw(" " + stat.fullName()).color("#AAAAAA")));
        }

        // Modifiers: stat name in color + bonus, proficiency label + bonus
        if (stat != null) {
            Message modMsg = Message.raw(stat.fullName() + " ").color(stat.color())
                    .insert(Message.raw(formatBonus(result.statModifier())).color("#FFFFFF"))
                    .insert(Message.raw("     Proficiency ").color("#AAAAAA"))
                    .insert(Message.raw(formatBonus(result.proficiencyBonus())).color("#FFFFFF"));
            cmd.set("#ModInfo.TextSpans", modMsg);
        } else {
            cmd.set("#ModInfo.Text", formatBonus(result.statModifier()) + "     Proficiency " + formatBonus(result.proficiencyBonus()));
        }

        // Show the initial result frame (static preview before roll)
        showFrame(cmd, RESULT_IDS[result.naturalRoll() - 1]);

        // Bind BOTH buttons during build (event bindings only work on game thread).
        // Toggle visibility via sendUpdate from the animation thread.
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RollButton",
                EventData.of("Type", "roll"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ContinueButton",
                EventData.of("Type", "continue"),
                false);

        cmd.set("#RollButton.Visible", true);
        cmd.set("#ContinueButton.Visible", false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, DiceEventData data) {
        String type = data.getType();
        if (type == null) return;

        if ("roll".equals(type) && state == State.PRE_ROLL) {
            state = State.ROLLING;
            startAnimation();
        } else if ("continue".equals(type) && state == State.RESULT) {
            onContinue.accept(result);
        }
    }

    private void startAnimation() {
        // Hide roll button
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#RollButton.Visible", false);
        sendUpdate(cmd);

        // 16-24 frames total, showing random d20 faces that slow down and land on the result
        final int totalFrames = 16 + random.nextInt(9);
        final int slowdownStart = totalFrames - (totalFrames / 4); // last 25% decelerates
        final int startOffset = random.nextInt(20); // different starting face every time

        scheduleAnimFrame(0, totalFrames, slowdownStart, startOffset);
    }

    private void scheduleAnimFrame(int frame, int totalFrames, int slowdownStart, int startOffset) {
        if (frame >= totalFrames) {
            // Land on the actual result
            UICommandBuilder settleCmd = new UICommandBuilder();
            showFrame(settleCmd, RESULT_IDS[result.naturalRoll() - 1]);
            sendUpdate(settleCmd);

            // Brief pause, then reveal stats
            animationTask = SCHEDULER.schedule(() -> {
                state = State.RESULT;
                UICommandBuilder resultCmd = new UICommandBuilder();
                showResultState(resultCmd);
                sendUpdate(resultCmd);
            }, SETTLE_DELAY_MS, TimeUnit.MILLISECONDS);
            return;
        }

        // Show a random tumble sprite
        int spriteIdx = (startOffset + frame + random.nextInt(TUMBLE_FRAMES)) % TUMBLE_FRAMES;

        UICommandBuilder frameCmd = new UICommandBuilder();
        showFrame(frameCmd, TUMBLE_IDS[spriteIdx]);
        sendUpdate(frameCmd);

        // Fast in the first 75%, linearly decelerating to 3x slower in the last 25%
        long delay;
        if (frame < slowdownStart) {
            delay = TUMBLE_FRAME_MS;
        } else {
            float progress = (float) (frame - slowdownStart) / (totalFrames - slowdownStart);
            delay = TUMBLE_FRAME_MS + (long) (progress * TUMBLE_FRAME_MS * 2);
        }

        animationTask = SCHEDULER.schedule(
                () -> scheduleAnimFrame(frame + 1, totalFrames, slowdownStart, startOffset),
                delay, TimeUnit.MILLISECONDS);
    }

    /** Show a single frame by ID, hiding the previously visible one. */
    private void showFrame(UICommandBuilder cmd, String frameId) {
        if (visibleFrameIdx >= 0) {
            String prevId = getFrameId(visibleFrameIdx);
            if (prevId != null) {
                cmd.set(prevId + ".Visible", false);
            }
        }
        cmd.set(frameId + ".Visible", true);
        visibleFrameIdx = parseFrameIdx(frameId);
    }

    /** Map a frame index (0-29) to its element ID. 0-9 = tumble, 10-29 = result. */
    private String getFrameId(int idx) {
        if (idx < TUMBLE_FRAMES) return TUMBLE_IDS[idx];
        if (idx < TUMBLE_FRAMES + 20) return RESULT_IDS[idx - TUMBLE_FRAMES];
        return null;
    }

    /** Parse a frame element ID back to index. */
    private int parseFrameIdx(String frameId) {
        if (frameId.startsWith("#T")) return Integer.parseInt(frameId.substring(2)) - 1;
        if (frameId.startsWith("#R")) return TUMBLE_FRAMES + Integer.parseInt(frameId.substring(2)) - 1;
        return -1;
    }

    private void showResultState(UICommandBuilder cmd) {
        cmd.set("#RollButton.Visible", false);
        showFrame(cmd, RESULT_IDS[result.naturalRoll() - 1]);

        // Show dice result number in gold
        cmd.set("#DiceResultText.Text", String.valueOf(result.naturalRoll()));
        cmd.set("#DiceResultText.Visible", true);

        // Comparison: colored total vs plain DC number (no "DC" label)
        int total = result.totalRoll();
        String totalColor = result.passed() ? "#00CC00" : "#CC0000";
        Message comparison = Message.raw(String.valueOf(total)).color(totalColor)
                .insert(Message.raw(" vs " + result.dc()).color("#FFFFFF"));
        cmd.set("#ComparisonLine.TextSpans", comparison);
        cmd.set("#ComparisonLine.Visible", true);

        // Verdict with decorative framing
        String verdict;
        String verdictColor;
        String verdictSub = null;
        String verdictSubColor = null;
        if (result.critical() && result.naturalRoll() == 20) {
            verdict = "NATURAL 20!";
            verdictColor = "#FFD700";
            verdictSub = "CRITICAL SUCCESS";
            verdictSubColor = "#00CC00";
        } else if (result.critical() && result.naturalRoll() == 1) {
            verdict = "NATURAL 1";
            verdictColor = "#8B0000";
            verdictSub = "CRITICAL FAILURE";
            verdictSubColor = "#CC0000";
        } else if (result.passed()) {
            verdict = "SUCCESS";
            verdictColor = "#00CC00";
        } else {
            verdict = "FAILURE";
            verdictColor = "#CC0000";
        }
        cmd.set("#Verdict.TextSpans", Message.raw(verdict).color(verdictColor));
        cmd.set("#Verdict.Visible", true);

        // Critical sub-text
        if (verdictSub != null) {
            cmd.set("#VerdictSub.TextSpans", Message.raw(verdictSub).color(verdictSubColor));
            cmd.set("#VerdictSub.Visible", true);
        }

        // Continue button (binding already set in build())
        cmd.set("#ContinueButton.Visible", true);
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (animationTask != null) animationTask.cancel(true);
        LOGGER.atInfo().log("Dice page dismissed");
    }

    public void closePage() {
        if (animationTask != null) animationTask.cancel(true);
        close();
    }

    public SkillCheckResult getResult() {
        return result;
    }

    private String formatBonus(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    public static class DiceEventData {
        private String type = "";

        public DiceEventData() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
