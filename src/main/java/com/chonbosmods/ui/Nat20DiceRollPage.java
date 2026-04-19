package com.chonbosmods.ui;

import com.chonbosmods.dice.RollMode;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Nat20DiceRollPage extends InteractiveCustomUIPage<Nat20DiceRollPage.DiceEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|DicePage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_DiceRoll.ui";
    private static final int TUMBLE_FRAMES = DiceSpriteGenerator.TUMBLE_FRAME_COUNT;
    private static final long TUMBLE_FRAME_MS = 80;
    private static final long SETTLE_DELAY_MS = 200;
    private static final long DIE_B_START_DELAY_MS = 200;

    // Frame selectors: #A_T01-#A_T10 / #B_T01-#B_T10 for tumble,
    // #A_R01-#A_R20 / #B_R01-#B_R20 for results
    private static final String[] TUMBLE_IDS_A = new String[TUMBLE_FRAMES];
    private static final String[] RESULT_IDS_A = new String[20];
    private static final String[] TUMBLE_IDS_B = new String[TUMBLE_FRAMES];
    private static final String[] RESULT_IDS_B = new String[20];
    static {
        for (int i = 0; i < TUMBLE_FRAMES; i++) {
            TUMBLE_IDS_A[i] = String.format("#A_T%02d", i + 1);
            TUMBLE_IDS_B[i] = String.format("#B_T%02d", i + 1);
        }
        for (int i = 0; i < 20; i++) {
            RESULT_IDS_A[i] = String.format("#A_R%02d", i + 1);
            RESULT_IDS_B[i] = String.format("#B_R%02d", i + 1);
        }
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
    private volatile int visibleFrameIdxA = -1;
    private volatile int visibleFrameIdxB = -1;
    private ScheduledFuture<?> animationTaskA;
    private ScheduledFuture<?> animationTaskB;
    private AtomicInteger remainingToSettle;

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

        // Show the initial result frame on die A (static preview before roll)
        showFrameA(cmd, RESULT_IDS_A[result.naturalRoll() - 1]);

        // Dual-dice gating: show die B + gap + mode label only for ADV/DIS
        RollMode mode = result.mode();
        if (mode == RollMode.ADVANTAGE || mode == RollMode.DISADVANTAGE) {
            cmd.set("#DieB.Visible", true);
            cmd.set("#DiceGap.Visible", true);

            // Show the other die's initial result frame
            if (result.otherRoll() >= 1 && result.otherRoll() <= 20) {
                showFrameB(cmd, RESULT_IDS_B[result.otherRoll() - 1]);
            }

            // Mode label: Advantage = cool blue, Disadvantage = warm orange/red
            String modeText = mode == RollMode.ADVANTAGE ? "Advantage" : "Disadvantage";
            String modeColor = mode == RollMode.ADVANTAGE ? "#44AACC" : "#CC6644";
            cmd.set("#ModeLabel.TextSpans", Message.raw(modeText).color(modeColor));
            cmd.set("#ModeLabel.Visible", true);
        }

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

        boolean dual = result.mode() == RollMode.ADVANTAGE || result.mode() == RollMode.DISADVANTAGE;
        remainingToSettle = new AtomicInteger(dual ? 2 : 1);

        // Schedule die A immediately
        scheduleDieA();

        // Schedule die B with a slight head-start delay so animations are offset
        if (dual) {
            animationTaskB = SCHEDULER.schedule(this::scheduleDieB, DIE_B_START_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleDieA() {
        // 16-24 frames total, showing random d20 faces that slow down and land on the result
        final int totalFrames = 16 + random.nextInt(9);
        final int slowdownStart = totalFrames - (totalFrames / 4); // last 25% decelerates
        final int startOffset = random.nextInt(20); // different starting face every time

        scheduleAnimFrameA(0, totalFrames, slowdownStart, startOffset);
    }

    private void scheduleDieB() {
        final int totalFrames = 16 + random.nextInt(9);
        final int slowdownStart = totalFrames - (totalFrames / 4);
        final int startOffset = random.nextInt(20);

        scheduleAnimFrameB(0, totalFrames, slowdownStart, startOffset);
    }

    private void scheduleAnimFrameA(int frame, int totalFrames, int slowdownStart, int startOffset) {
        if (frame >= totalFrames) {
            // Land on the kept (natural) roll
            UICommandBuilder settleCmd = new UICommandBuilder();
            showFrameA(settleCmd, RESULT_IDS_A[result.naturalRoll() - 1]);
            sendUpdate(settleCmd);

            // Brief pause, then signal settle
            animationTaskA = SCHEDULER.schedule(this::onDieSettled, SETTLE_DELAY_MS, TimeUnit.MILLISECONDS);
            return;
        }

        int spriteIdx = (startOffset + frame + random.nextInt(TUMBLE_FRAMES)) % TUMBLE_FRAMES;

        UICommandBuilder frameCmd = new UICommandBuilder();
        showFrameA(frameCmd, TUMBLE_IDS_A[spriteIdx]);
        sendUpdate(frameCmd);

        long delay;
        if (frame < slowdownStart) {
            delay = TUMBLE_FRAME_MS;
        } else {
            float progress = (float) (frame - slowdownStart) / (totalFrames - slowdownStart);
            delay = TUMBLE_FRAME_MS + (long) (progress * TUMBLE_FRAME_MS * 2);
        }

        animationTaskA = SCHEDULER.schedule(
                () -> scheduleAnimFrameA(frame + 1, totalFrames, slowdownStart, startOffset),
                delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleAnimFrameB(int frame, int totalFrames, int slowdownStart, int startOffset) {
        if (frame >= totalFrames) {
            // Land on the dropped roll (otherRoll)
            int other = result.otherRoll();
            if (other >= 1 && other <= 20) {
                UICommandBuilder settleCmd = new UICommandBuilder();
                showFrameB(settleCmd, RESULT_IDS_B[other - 1]);
                sendUpdate(settleCmd);
            }

            animationTaskB = SCHEDULER.schedule(this::onDieSettled, SETTLE_DELAY_MS, TimeUnit.MILLISECONDS);
            return;
        }

        int spriteIdx = (startOffset + frame + random.nextInt(TUMBLE_FRAMES)) % TUMBLE_FRAMES;

        UICommandBuilder frameCmd = new UICommandBuilder();
        showFrameB(frameCmd, TUMBLE_IDS_B[spriteIdx]);
        sendUpdate(frameCmd);

        long delay;
        if (frame < slowdownStart) {
            delay = TUMBLE_FRAME_MS;
        } else {
            float progress = (float) (frame - slowdownStart) / (totalFrames - slowdownStart);
            delay = TUMBLE_FRAME_MS + (long) (progress * TUMBLE_FRAME_MS * 2);
        }

        animationTaskB = SCHEDULER.schedule(
                () -> scheduleAnimFrameB(frame + 1, totalFrames, slowdownStart, startOffset),
                delay, TimeUnit.MILLISECONDS);
    }

    /** Called when each die finishes its settle-delay. Advance to RESULT only after both dice settle. */
    private void onDieSettled() {
        if (remainingToSettle == null) return;
        if (remainingToSettle.decrementAndGet() > 0) return;

        state = State.RESULT;
        UICommandBuilder resultCmd = new UICommandBuilder();
        showResultState(resultCmd);
        sendUpdate(resultCmd);
    }

    /** Show a single frame on die A, hiding the previously visible die-A frame. */
    private void showFrameA(UICommandBuilder cmd, String frameId) {
        if (visibleFrameIdxA >= 0) {
            String prevId = getFrameIdA(visibleFrameIdxA);
            if (prevId != null) {
                cmd.set(prevId + ".Visible", false);
            }
        }
        cmd.set(frameId + ".Visible", true);
        visibleFrameIdxA = parseFrameIdx(frameId);
    }

    /** Show a single frame on die B, hiding the previously visible die-B frame. */
    private void showFrameB(UICommandBuilder cmd, String frameId) {
        if (visibleFrameIdxB >= 0) {
            String prevId = getFrameIdB(visibleFrameIdxB);
            if (prevId != null) {
                cmd.set(prevId + ".Visible", false);
            }
        }
        cmd.set(frameId + ".Visible", true);
        visibleFrameIdxB = parseFrameIdx(frameId);
    }

    /** Map a frame index (0-29) to its die-A element ID. 0-9 = tumble, 10-29 = result. */
    private String getFrameIdA(int idx) {
        if (idx < TUMBLE_FRAMES) return TUMBLE_IDS_A[idx];
        if (idx < TUMBLE_FRAMES + 20) return RESULT_IDS_A[idx - TUMBLE_FRAMES];
        return null;
    }

    /** Map a frame index (0-29) to its die-B element ID. */
    private String getFrameIdB(int idx) {
        if (idx < TUMBLE_FRAMES) return TUMBLE_IDS_B[idx];
        if (idx < TUMBLE_FRAMES + 20) return RESULT_IDS_B[idx - TUMBLE_FRAMES];
        return null;
    }

    /** Parse a frame element ID back to index. Works for either die since the suffix is the same. */
    private int parseFrameIdx(String frameId) {
        // Expected forms: "#A_T01".."#A_T10", "#A_R01".."#A_R20", or the #B_ variants.
        if (frameId.length() < 6) return -1;
        char kind = frameId.charAt(3); // 'T' or 'R'
        int n;
        try {
            n = Integer.parseInt(frameId.substring(4));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (kind == 'T') return n - 1;
        if (kind == 'R') return TUMBLE_FRAMES + n - 1;
        return -1;
    }

    private void showResultState(UICommandBuilder cmd) {
        cmd.set("#RollButton.Visible", false);
        showFrameA(cmd, RESULT_IDS_A[result.naturalRoll() - 1]);

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
        if (animationTaskA != null) animationTaskA.cancel(true);
        if (animationTaskB != null) animationTaskB.cancel(true);
        LOGGER.atInfo().log("Dice page dismissed");
    }

    public void closePage() {
        if (animationTaskA != null) animationTaskA.cancel(true);
        if (animationTaskB != null) animationTaskB.cancel(true);
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
