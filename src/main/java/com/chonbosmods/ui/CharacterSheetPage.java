package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.Nat20ScoreDirtyFlag;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.progression.Nat20XpMath;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Character Sheet page (Tasks 7-14+).
 *
 * <p>Loads {@code Pages/Nat20_CharacterSheet.ui} and renders the header, XP bar,
 * unspent points banner, and six ability rows. Task 12 wired the {@code +}
 * button click handlers and added a client-local preview state. Task 13 wired
 * the {@code -} handler (decrements {@link #pendingDelta}, never below 0) and
 * surfaces the {@code #CSApplyBtn} enable state. Task 14 commits the queued
 * preview to {@link Nat20PlayerData} when the player clicks Apply, with
 * server-side validation (no negatives, sum &le; pool, score &le; 30) and
 * dirty-flagging for {@code Nat20ScoreBonusSystem}.
 */
public class CharacterSheetPage extends InteractiveCustomUIPage<CharacterSheetPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CharacterSheetPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_CharacterSheet.ui";

    /** XP track width in pixels: keep in sync with #CSXpBarTrack.Anchor.Width in the .ui template. */
    private static final int XP_BAR_TRACK_WIDTH = 500;

    /** Hard cap per ability score (design Q5). */
    private static final int MAX_ABILITY_SCORE = 30;

    /** Gold accent for active banner (matches #CSName / #CSXpBarFill). */
    private static final String COLOR_BANNER_ACTIVE = "#ffd700";
    /** Muted grey for the zero-points banner. */
    private static final String COLOR_BANNER_MUTED = "#888888";
    /** White for ability scores at applied (snapshot) value. */
    private static final String COLOR_SCORE_APPLIED = "#ffffff";
    /** Gold flip for ability scores when previewed (pending delta > 0). */
    private static final String COLOR_SCORE_PENDING = "#ffd700";

    public static final BuilderCodec<PageEventData> EVENT_CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new)
            .addField(new KeyedCodec<>("Type", Codec.STRING), PageEventData::setType, PageEventData::getType)
            .addField(new KeyedCodec<>("Id", Codec.STRING), PageEventData::setId, PageEventData::getId)
            .build();

    private boolean dismissed;

    // Snapshot taken on open. Preview delta is maintained on the page; committed
    // to Nat20PlayerData only when the player clicks Apply (Task 14).
    private int[] appliedScores = new int[6];
    private int appliedPendingPoints;
    private final int[] pendingDelta = new int[6];

    public CharacterSheetPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        Player player = store.getComponent(ref, Player.getComponentType());
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (player == null || data == null) {
            // Defensive: page chrome still renders, dynamic bindings just stay blank.
            return;
        }

        // Snapshot applied state on every build. rebuild() also re-enters here, so
        // the snapshot must reflect what's currently in Nat20PlayerData. The
        // pendingDelta is preserved across rebuilds within the same session
        // because it's a field, but on first open we zero it out below.
        // First-open guard: detect that this is the initial open vs a + click
        // rebuild by checking whether snapshot has ever been taken. We use a
        // simple sentinel: appliedScores all-zero is unlikely in practice, but
        // safer to always re-snapshot from data and ONLY clear pendingDelta
        // when applied state actually changed (Apply commit). For Task 12, we
        // re-snapshot every build but DO NOT clear pendingDelta on rebuilds:
        // the rebuild() path needs the delta to keep its values so the user
        // sees their preview. Tasks 14/15 will clear pendingDelta on Apply or
        // page close.
        int[] currentApplied = data.getStats();
        if (currentApplied != null) {
            System.arraycopy(currentApplied, 0, appliedScores, 0, Math.min(6, currentApplied.length));
        }
        appliedPendingPoints = data.getPendingAbilityPoints();

        // Header: name + level
        cmd.set("#CSName.Text", player.getDisplayName());
        cmd.set("#CSLevel.Text", "Lvl " + data.getLevel());

        // XP readout + bar fill
        int level = data.getLevel();
        long totalXp = data.getTotalXp();
        if (level >= Nat20XpMath.LEVEL_CAP) {
            cmd.set("#CSXpText.Text", "Level " + level);
            setXpBarFillPixels(cmd, XP_BAR_TRACK_WIDTH);
        } else {
            long cumulativeAtCurrent = Nat20XpMath.cumulativeXp(level);
            int threshold = Nat20XpMath.xpToNextLevel(level);
            long xpIntoLevel = Math.max(0L, totalXp - cumulativeAtCurrent);
            long safeThreshold = Math.max(1L, threshold);
            long clampedXp = Math.min(xpIntoLevel, safeThreshold);
            int fillPx = (int) ((XP_BAR_TRACK_WIDTH * clampedXp) / safeThreshold);
            cmd.set("#CSXpText.Text", xpIntoLevel + " / " + threshold);
            setXpBarFillPixels(cmd, fillPx);
        }

        // Banner + ability rows: unified renderer, used both on first open and
        // after every + click rebuild.
        renderStatPanel(cmd);

        // Bind click handlers on each + and - button. Even when Disabled is
        // true the bindings must exist so the SDK can dispatch once they're
        // enabled by a subsequent rebuild.
        for (Stat stat : Stat.values()) {
            String key = stat.name();
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSPlus_" + key,
                    EventData.of("Type", "plus").append("Id", key),
                    false);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSMinus_" + key,
                    EventData.of("Type", "minus").append("Id", key),
                    false);
        }

        // Apply button commits the queued preview to Nat20PlayerData (Task 14).
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSApplyBtn",
                EventData.of("Type", "apply"),
                false);
    }

    /**
     * Render banner text/color and all six ability rows' score text, score color,
     * and +/- button enable states from the current preview state. Called both
     * on first open (from {@link #build}) and after every + click rebuild.
     */
    private void renderStatPanel(UICommandBuilder cmd) {
        int unspent = displayedPendingPoints();

        // Unspent ability points banner. Gold when > 0, muted when = 0.
        cmd.set("#CSUnspentBanner.Text", "Unspent Points: " + unspent);
        cmd.set("#CSUnspentBanner.Style.TextColor",
                unspent > 0 ? COLOR_BANNER_ACTIVE : COLOR_BANNER_MUTED);

        // Six ability rows.
        for (Stat stat : Stat.values()) {
            int i = stat.index();
            if (i < 0 || i >= 6) continue;
            String key = stat.name();
            int displayed = displayedScore(i);
            boolean hasDelta = pendingDelta[i] > 0;

            cmd.set("#CSAbilityScore_" + key + ".Text", String.valueOf(displayed));
            cmd.set("#CSAbilityScore_" + key + ".Style.TextColor",
                    hasDelta ? COLOR_SCORE_PENDING : COLOR_SCORE_APPLIED);
            cmd.set("#CSPlus_" + key + ".Disabled",
                    unspent <= 0 || displayed >= MAX_ABILITY_SCORE);
            // Minus enables as soon as pending delta > 0 for this row.
            cmd.set("#CSMinus_" + key + ".Disabled", !hasDelta);
        }

        // Apply button enables the moment any pendingDelta > 0. Task 14 wires
        // the OnClick handler that commits the preview to Nat20PlayerData.
        cmd.set("#CSApplyBtn.Disabled", !hasPendingSpend());
    }

    /** True if any ability has a pending spend (preview delta > 0). */
    private boolean hasPendingSpend() {
        for (int d : pendingDelta) {
            if (d > 0) return true;
        }
        return false;
    }

    /** Snapshot + preview combined for ability index {@code i}. */
    private int displayedScore(int i) {
        return appliedScores[i] + pendingDelta[i];
    }

    /** Snapshot pending pool minus everything spent in the preview. */
    private int displayedPendingPoints() {
        int spent = 0;
        for (int d : pendingDelta) spent += d;
        return appliedPendingPoints - spent;
    }

    /** Set the XP bar fill width in pixels. Track width is fixed in the template. */
    private static void setXpBarFillPixels(UICommandBuilder cmd, int pixels) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setTop(Value.of(0));
        anchor.setBottom(Value.of(0));
        anchor.setWidth(Value.of(pixels));
        cmd.setObject("#CSXpBarFill.Anchor", anchor);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        String type = data.getType();
        if (type == null || type.isEmpty()) return;
        if ("plus".equals(type)) {
            handlePlusClicked(data.getId());
            return;
        }
        if ("minus".equals(type)) {
            handleMinusClicked(data.getId());
            return;
        }
        if ("apply".equals(type)) {
            handleApplyClicked(ref, store);
        }
    }

    private void handlePlusClicked(String key) {
        Stat stat = findStat(key);
        if (stat == null) return;
        int i = stat.index();
        if (i < 0 || i >= 6) return;
        // Defensive: guard against stale clicks (e.g. spam-click before rebuild
        // arrives at the client). Server is authoritative on cap + pool.
        if (displayedPendingPoints() <= 0) return;
        if (displayedScore(i) >= MAX_ABILITY_SCORE) return;
        pendingDelta[i]++;
        rebuild();
    }

    private void handleMinusClicked(String key) {
        Stat stat = findStat(key);
        if (stat == null) return;
        int i = stat.index();
        if (i < 0 || i >= 6) return;
        // Minus only walks back previewed increments: never below 0. The
        // applied snapshot is unchanged; reversing an already-applied spend is
        // out of scope for this task.
        if (pendingDelta[i] <= 0) return;
        pendingDelta[i]--;
        rebuild();
    }

    /**
     * Commit the queued {@link #pendingDelta} to {@link Nat20PlayerData}.
     * Server-side validation (defense in depth: client already enforces caps,
     * but a replayed / forged event could try to bypass them):
     * <ul>
     *   <li>no negative delta entries,</li>
     *   <li>sum of delta &le; current pending pool,</li>
     *   <li>no resulting score exceeds {@link #MAX_ABILITY_SCORE}.</li>
     * </ul>
     * On success: write new stats, decrement pool, mark the score-bonus dirty
     * flag (mirrors {@code /nat20 setstats}), zero the preview, and rebuild.
     * On validation failure: zero the preview and rebuild to re-sync the client
     * to authoritative state (forged events must not leave stale deltas in view).
     */
    private void handleApplyClicked(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!hasPendingSpend()) return;

        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) return;

        int[] currentStats = data.getStats();
        if (currentStats == null || currentStats.length != 6) return;

        int totalSpend = 0;
        for (int d : pendingDelta) {
            if (d < 0) {
                LOGGER.atWarning().log("Apply validation failed (negative delta) player=%s",
                        this.playerRef.getUuid());
                java.util.Arrays.fill(pendingDelta, 0);
                rebuild();
                return;
            }
            totalSpend += d;
        }
        int pool = data.getPendingAbilityPoints();
        if (totalSpend > pool) {
            LOGGER.atWarning().log("Apply validation failed player=%s totalSpend=%d pool=%d",
                    this.playerRef.getUuid(), totalSpend, pool);
            java.util.Arrays.fill(pendingDelta, 0);
            rebuild();
            return;
        }
        for (int i = 0; i < 6; i++) {
            if (currentStats[i] + pendingDelta[i] > MAX_ABILITY_SCORE) {
                LOGGER.atWarning().log("Apply validation failed (score>cap) player=%s i=%d",
                        this.playerRef.getUuid(), i);
                java.util.Arrays.fill(pendingDelta, 0);
                rebuild();
                return;
            }
        }

        // Commit: write new stats array, decrement pending pool, zero preview.
        int[] newStats = currentStats.clone();
        for (int i = 0; i < 6; i++) newStats[i] += pendingDelta[i];
        data.setStats(newStats);
        data.setPendingAbilityPoints(pool - totalSpend);

        // Mirrors SetStatsCommand: tells Nat20ScoreBonusSystem to recompute
        // stat-derived modifiers on the next tick.
        Nat20ScoreDirtyFlag.markDirty(this.playerRef.getUuid());

        java.util.Arrays.fill(pendingDelta, 0);

        // Re-render: build() re-snapshots from the now-committed data.
        rebuild();
    }

    private static Stat findStat(String name) {
        if (name == null) return null;
        for (Stat s : Stat.values()) {
            if (s.name().equals(name)) return s;
        }
        return null;
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        dismissed = true;
        LOGGER.atFine().log("CharacterSheetPage.onDismiss");
        CharacterSheetManager mgr = CharacterSheetManager.get();
        if (mgr != null) {
            // Use the inherited PlayerRef field, not the entity Ref parameter (Ref has no getUuid).
            mgr.handlePageClosed(this.playerRef.getUuid());
        }
    }

    /** Manager-initiated close: dismisses the page on the client. Idempotent. */
    public void closePage() {
        if (dismissed) return;
        close();
    }

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
