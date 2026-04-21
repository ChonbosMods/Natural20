package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.Nat20ScoreDirtyFlag;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.party.Nat20Party;
import com.chonbosmods.party.Nat20PartyInviteRegistry;
import com.chonbosmods.party.Nat20PartyRegistry;
import com.chonbosmods.party.PartyInvite;
import com.chonbosmods.party.PartyInviteBanner;
import com.chonbosmods.progression.Nat20XpMath;
import com.chonbosmods.quest.CompletedQuestRecord;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.stats.Stat;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.server.core.universe.world.World;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * dirty-flagging for {@code Nat20ScoreBonusSystem}. Task 18 adds row-tap
 * waypoint toggling on the Active tab: dim/bright color flip indicates state.
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

    /** Quest log: max pre-baked row slots in the .ui template. */
    private static final int MAX_QUEST_ROWS = 20;
    /** Selected tab text color (white). */
    private static final String COLOR_TAB_ACTIVE = "#ffffff";
    /** Inactive tab text color (muted grey). */
    private static final String COLOR_TAB_INACTIVE = "#888888";
    /** Active quest row name color. */
    private static final String COLOR_QUEST_ACTIVE = "#ffffff";
    /** Dimmed (waypoint disabled) quest row name color. */
    private static final String COLOR_QUEST_DIMMED = "#888888";
    /** Active quest row objective color. */
    private static final String COLOR_OBJ_ACTIVE = "#cccccc";
    /** Dimmed (waypoint disabled) quest row objective color. */
    private static final String COLOR_OBJ_DIMMED = "#666666";

    /** Quest Log tab selection. Resets to ACTIVE on every new page instance. */
    private enum QuestTab { ACTIVE, COMPLETED }

    /** Party section: max pre-baked row slots in the .ui template. */
    private static final int MAX_PARTY_ROWS = 8;
    /** Party section tab selection. Resets to PARTY on every new page instance. */
    private enum PartyTab { PARTY, SERVER, INVITES }
    /** Greyed-out name color for offline party members. */
    private static final String COLOR_PLAYER_OFFLINE = "#888888";
    /** White for online / server-listed players. */
    private static final String COLOR_PLAYER_ONLINE = "#ffffff";
    /** Gold for the party leader's name row. */
    private static final String COLOR_PLAYER_LEADER = "#ffd700";

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

    /** Currently selected Quest Log tab. Defaults to ACTIVE on every new page. */
    private QuestTab currentTab = QuestTab.ACTIVE;
    /** Slot index -> questId mapping populated on each Active-tab render so that
     *  Task 18's row-tap handler can resolve which quest a slot represents. */
    private final Map<Integer, String> slotToQuestId = new HashMap<>();

    /** Currently selected Party tab. Defaults to PARTY on every new page. */
    private PartyTab currentPartyTab = PartyTab.PARTY;
    /** Slot index -> player UUID mapping for the currently rendered party row
     *  set. Repopulated on every rebuild. Maps to the target of any
     *  Kick / Promote / Invite / Accept / Decline action. */
    private final Map<Integer, UUID> slotToPlayerUuid = new HashMap<>();
    /** Token of the most-recently-armed confirm action, or null if none.
     *  Example values: "kick-3", "leave". On click, if token matches, commit
     *  the action; else arm this one and rebuild. Any unrelated click (tab
     *  switch, different row) clears the pending confirm on the next rebuild. */
    private String pendingConfirmToken;

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

        // Quest Log right panel: Active / Completed tab + 20 quest row slots.
        // Task 16 + 17.
        renderQuestList(cmd, data);

        // Bind click handlers on each + and - button. Even when Disabled is
        // true the bindings must exist so the SDK can dispatch once they're
        // enabled by a subsequent rebuild.
        for (Stat stat : Stat.values()) {
            String key = stat.name();
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSPlus" + key,
                    EventData.of("Type", "plus").append("Id", key),
                    false);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSMinus" + key,
                    EventData.of("Type", "minus").append("Id", key),
                    false);
        }

        // Apply button commits the queued preview to Nat20PlayerData (Task 14).
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSApplyBtn",
                EventData.of("Type", "apply"),
                false);

        // Quest Log tab buttons. Click toggles which list mode is rendered. Task 16.
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSTabActive",
                EventData.of("Type", "tab").append("Id", "active"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSTabCompleted",
                EventData.of("Type", "tab").append("Id", "completed"),
                false);

        // Per-row waypoint toggle button on the right side of each active-tab row.
        // Plain Groups have no Activating event, so waypoint toggling is attached
        // to a dedicated button aligned on the right instead of the whole row.
        // Task 18. Bindings install unconditionally for all 20 slots; the handler
        // no-ops on hidden / Completed rows, and the button itself is hidden on
        // the Completed tab.
        for (int i = 0; i < MAX_QUEST_ROWS; i++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSQuestToggle" + i,
                    EventData.of("Type", "questrow").append("Id", String.valueOf(i)),
                    false);
        }

        // Party section: tab buttons, per-row action buttons, and Leave.
        // Slice 5e bindings. Button semantics vary by current tab; the
        // dispatcher resolves slot -> target UUID via slotToPlayerUuid.
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSPartyTabParty",
                EventData.of("Type", "party-tab").append("Id", "party"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSPartyTabServer",
                EventData.of("Type", "party-tab").append("Id", "server"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSPartyTabInvites",
                EventData.of("Type", "party-tab").append("Id", "invites"),
                false);
        for (int i = 0; i < MAX_PARTY_ROWS; i++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSPartyRowBtnA" + i,
                    EventData.of("Type", "party-row-a").append("Id", String.valueOf(i)),
                    false);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CSPartyRowBtnB" + i,
                    EventData.of("Type", "party-row-b").append("Id", String.valueOf(i)),
                    false);
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSPartyLeaveBtn",
                EventData.of("Type", "party-leave"),
                false);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CSPartyLeaveCancelBtn",
                EventData.of("Type", "party-leave-cancel"),
                false);

        // Render party section on first open + every rebuild.
        renderPartySection(cmd, store);
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

            cmd.set("#CSAbilityScore" + key + ".Text", String.valueOf(displayed));
            cmd.set("#CSAbilityScore" + key + ".Style.TextColor",
                    hasDelta ? COLOR_SCORE_PENDING : COLOR_SCORE_APPLIED);
            cmd.set("#CSPlus" + key + ".Disabled",
                    unspent <= 0 || displayed >= MAX_ABILITY_SCORE);
            // Minus enables as soon as pending delta > 0 for this row.
            cmd.set("#CSMinus" + key + ".Disabled", !hasDelta);
        }

        // Apply button enables the moment any pendingDelta > 0. Task 14 wires
        // the OnClick handler that commits the preview to Nat20PlayerData.
        cmd.set("#CSApplyBtn.Disabled", !hasPendingSpend());
    }

    /**
     * Render the Quest Log right panel: tab highlighting + quest rows for the
     * currently selected tab. Task 16 + 17. Completed-tab rendering itself is
     * stubbed: Task 19 fills it in.
     */
    private void renderQuestList(UICommandBuilder cmd, Nat20PlayerData data) {
        // Tab highlighting: gold for the active tab, muted grey for the inactive.
        cmd.set("#CSTabActive.Style.Default.LabelStyle.TextColor",
                currentTab == QuestTab.ACTIVE ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        cmd.set("#CSTabCompleted.Style.Default.LabelStyle.TextColor",
                currentTab == QuestTab.COMPLETED ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

        if (currentTab == QuestTab.ACTIVE) {
            renderActiveList(cmd, data);
        } else {
            renderCompletedList(cmd, data);
        }
    }

    /**
     * Render up to {@link #MAX_QUEST_ROWS} active quests with name + objective
     * text. Rows past the active count are hidden. Empty state label appears
     * when zero active quests. Quest rows whose {@code waypointEnabled} flag is
     * false render in dimmed colors. Task 17.
     *
     * <p>{@link #slotToQuestId} is repopulated unconditionally so Task 18's
     * row-tap handler can find which quest a slot represents.
     */
    private void renderActiveList(UICommandBuilder cmd, Nat20PlayerData data) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        List<QuestInstance> list;
        if (questSystem == null) {
            list = new ArrayList<>();
        } else {
            QuestStateManager sm = questSystem.getStateManager();
            Map<String, QuestInstance> active = sm.getActiveQuests(data);
            list = new ArrayList<>(active.values());
        }
        int n = Math.min(list.size(), MAX_QUEST_ROWS);

        slotToQuestId.clear();
        for (int i = 0; i < n; i++) {
            slotToQuestId.put(i, list.get(i).getQuestId());
        }

        for (int i = 0; i < MAX_QUEST_ROWS; i++) {
            if (i < n) {
                QuestInstance q = list.get(i);
                boolean enabled = q.isWaypointEnabled();
                cmd.set("#CSQuestRow" + i + ".Visible", true);
                cmd.set("#CSQuestName" + i + ".Text", resolveQuestName(q));
                cmd.set("#CSQuestName" + i + ".Style.TextColor",
                        enabled ? COLOR_QUEST_ACTIVE : COLOR_QUEST_DIMMED);
                cmd.set("#CSQuestObj" + i + ".Text", resolveObjectiveText(q));
                cmd.set("#CSQuestObj" + i + ".Style.TextColor",
                        enabled ? COLOR_OBJ_ACTIVE : COLOR_OBJ_DIMMED);
                cmd.set("#CSQuestToggle" + i + ".Visible", true);
                cmd.set("#CSQuestToggle" + i + ".Text", enabled ? "Tracking" : "Track");
            } else {
                cmd.set("#CSQuestRow" + i + ".Visible", false);
                cmd.set("#CSQuestToggle" + i + ".Visible", false);
            }
        }

        cmd.set("#CSEmptyState.Text", "No active quests");
        cmd.set("#CSEmptyState.Visible", n == 0);
    }

    /**
     * Render up to {@link #MAX_QUEST_ROWS} completed quest snapshots in
     * most-recent-first order (the ordering invariant maintained by
     * {@link QuestStateManager#markQuestCompleted}). Rows are non-interactive on
     * this tab (Task 18 enforces) and styled with the dimmed name/objective
     * colors to visually distinguish history from active work.
     */
    private void renderCompletedList(UICommandBuilder cmd, Nat20PlayerData data) {
        slotToQuestId.clear(); // Completed rows are non-interactive; clear for safety.

        List<CompletedQuestRecord> completed = data.getCompletedQuests();
        int n = Math.min(completed.size(), MAX_QUEST_ROWS);

        for (int i = 0; i < MAX_QUEST_ROWS; i++) {
            if (i < n) {
                CompletedQuestRecord record = completed.get(i);
                cmd.set("#CSQuestRow" + i + ".Visible", true);
                cmd.set("#CSQuestName" + i + ".Text", record.getQuestName());
                cmd.set("#CSQuestName" + i + ".Style.TextColor", COLOR_QUEST_DIMMED);
                cmd.set("#CSQuestObj" + i + ".Text", record.getFinalObjectiveText());
                cmd.set("#CSQuestObj" + i + ".Style.TextColor", COLOR_OBJ_DIMMED);
            } else {
                cmd.set("#CSQuestRow" + i + ".Visible", false);
            }
            // Toggle button is Active-tab-only; hide unconditionally on this tab.
            cmd.set("#CSQuestToggle" + i + ".Visible", false);
        }
        cmd.set("#CSEmptyState.Text", "No completed quests yet");
        cmd.set("#CSEmptyState.Visible", n == 0);
    }

    /**
     * Resolve the display name for a quest row. Prefers {@code quest_topic_header}
     * (the authored flavor topic label, also used by QuestCompletionBanner and the
     * dialogue topic list) so it doesn't duplicate the objective text below it.
     * Falls back to {@code subject_name}, then {@link QuestInstance#getSituationId()},
     * then questId. Does NOT fall back to {@code quest_objective_summary}, which is
     * reserved for the objective row.
     */
    private static String resolveQuestName(QuestInstance q) {
        Map<String, String> b = q.getVariableBindings();
        String sit = q.getSituationId();
        String fallback = sit != null ? sit : q.getQuestId();
        if (b == null) return fallback;
        return b.getOrDefault("quest_topic_header",
                b.getOrDefault("subject_name", fallback));
    }

    /** Objective text mirrors what dialogue actions write to {@code quest_objective_summary}. */
    private static String resolveObjectiveText(QuestInstance q) {
        Map<String, String> b = q.getVariableBindings();
        if (b == null) return "";
        return b.getOrDefault("quest_objective_summary", "");
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
    /**
     * Render the Party section: tab highlighting, row population for the
     * currently selected tab, and the Leave button (Party tab only).
     * Repopulates {@link #slotToPlayerUuid} so row button dispatch can resolve
     * target players.
     */
    private void renderPartySection(UICommandBuilder cmd, Store<EntityStore> store) {
        slotToPlayerUuid.clear();

        // Tab highlighting: gold active, muted inactive. Buttons use the
        // Style.Default.LabelStyle.TextColor path, NOT Style.TextColor
        // (that one only applies to plain Labels; setting it on a button
        // element crashes the client). Matches the Quest Log #CSTabActive /
        // #CSTabCompleted pattern in renderQuestList().
        cmd.set("#CSPartyTabParty.Style.Default.LabelStyle.TextColor",
                currentPartyTab == PartyTab.PARTY ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        cmd.set("#CSPartyTabServer.Style.Default.LabelStyle.TextColor",
                currentPartyTab == PartyTab.SERVER ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

        // Invites tab: grey out when zero pending invites for this player.
        Nat20PartyInviteRegistry inviteReg = Natural20.getInstance().getPartyInviteRegistry();
        boolean hasInvite = inviteReg != null
                && inviteReg.getForInvitee(this.playerRef.getUuid()) != null;
        cmd.set("#CSPartyTabInvites.Style.Default.LabelStyle.TextColor",
                currentPartyTab == PartyTab.INVITES ? COLOR_TAB_ACTIVE
                        : (hasInvite ? COLOR_TAB_INACTIVE : COLOR_PLAYER_OFFLINE));
        cmd.set("#CSPartyTabInvites.Disabled", !hasInvite && currentPartyTab != PartyTab.INVITES);

        // Leave button row shows only on Party tab and only when the viewer
        // is in a multi-member party (nothing to leave from a size-1 default
        // party). The Cancel button only appears while a Leave confirm is
        // armed, so the resting state is a single button.
        Nat20PartyRegistry partyReg = Natural20.getInstance().getPartyRegistry();
        Nat20Party myParty = partyReg != null ? partyReg.getParty(this.playerRef.getUuid()) : null;
        boolean canLeave = myParty != null && !myParty.isSolo();
        boolean confirmingLeave = "leave".equals(pendingConfirmToken);
        boolean showLeaveRow = currentPartyTab == PartyTab.PARTY && canLeave;
        cmd.set("#CSPartyLeaveRow.Visible", showLeaveRow);
        cmd.set("#CSPartyLeaveBtn.Text", confirmingLeave ? "Confirm Leave" : "Leave Party");
        cmd.set("#CSPartyLeaveCancelBtn.Visible", showLeaveRow && confirmingLeave);

        // Dispatch per-tab row renderer. Hidden rows are reset to blank / disabled.
        switch (currentPartyTab) {
            case PARTY -> renderPartyTabRows(cmd, store, partyReg, myParty);
            case SERVER -> renderServerTabRows(cmd, store, partyReg);
            case INVITES -> renderInvitesTabRows(cmd, store, partyReg, inviteReg);
        }
    }

    private void renderPartyTabRows(UICommandBuilder cmd, Store<EntityStore> store,
                                    Nat20PartyRegistry partyReg, Nat20Party myParty) {
        if (partyReg == null || myParty == null) {
            hidePartyRows(cmd);
            cmd.set("#CSPartyEmptyState.Visible", true);
            cmd.set("#CSPartyEmptyState.Text", "Party system unavailable.");
            return;
        }

        List<UUID> members = myParty.getMembers();
        UUID viewer = this.playerRef.getUuid();
        UUID leader = myParty.getLeader();
        boolean viewerIsLeader = viewer.equals(leader);

        cmd.set("#CSPartyEmptyState.Visible", members.size() <= 1);
        if (members.size() <= 1) {
            cmd.set("#CSPartyEmptyState.Text",
                "You are in a solo party. Invite someone from the Server tab.");
        }

        for (int i = 0; i < MAX_PARTY_ROWS; i++) {
            if (i < members.size()) {
                UUID member = members.get(i);
                boolean isSelf = member.equals(viewer);
                boolean isLeader = member.equals(leader);
                boolean online = partyReg.isOnline(member);
                String name = resolveDisplayName(store, member);
                if (name == null) name = "Unknown";
                if (isLeader) name = "[Leader] " + name;

                slotToPlayerUuid.put(i, member);
                cmd.set("#CSPartyRow" + i + ".Visible", true);
                cmd.set("#CSPartyRowName" + i + ".Text", name);
                cmd.set("#CSPartyRowName" + i + ".Style.TextColor",
                        isLeader ? COLOR_PLAYER_LEADER
                                : (online ? COLOR_PLAYER_ONLINE : COLOR_PLAYER_OFFLINE));

                // Button A = Kick, Button B = Promote. Both leader-only, non-self.
                boolean showKick = viewerIsLeader && !isSelf;
                boolean showPromote = viewerIsLeader && !isSelf;
                String kickToken = "kick-" + i;
                boolean confirmingKick = kickToken.equals(pendingConfirmToken);

                cmd.set("#CSPartyRowBtnA" + i + ".Visible", showKick);
                cmd.set("#CSPartyRowBtnA" + i + ".Text", confirmingKick ? "Confirm?" : "Kick");
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", showPromote);
                cmd.set("#CSPartyRowBtnB" + i + ".Text", "Promote");
            } else {
                cmd.set("#CSPartyRow" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnA" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", false);
            }
        }
    }

    private void renderServerTabRows(UICommandBuilder cmd, Store<EntityStore> store,
                                     Nat20PartyRegistry partyReg) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null || partyReg == null) {
            hidePartyRows(cmd);
            cmd.set("#CSPartyEmptyState.Visible", true);
            cmd.set("#CSPartyEmptyState.Text", "Server player list unavailable.");
            return;
        }

        UUID viewer = this.playerRef.getUuid();
        List<UUID> candidates = new ArrayList<>();
        for (PlayerRef other : world.getPlayerRefs()) {
            UUID uuid = other.getUuid();
            if (uuid == null || uuid.equals(viewer)) continue;
            candidates.add(uuid);
        }

        cmd.set("#CSPartyEmptyState.Visible", candidates.isEmpty());
        if (candidates.isEmpty()) {
            cmd.set("#CSPartyEmptyState.Text", "No other players online.");
        }

        for (int i = 0; i < MAX_PARTY_ROWS; i++) {
            if (i < candidates.size()) {
                UUID target = candidates.get(i);
                String name = resolveDisplayName(store, target);
                if (name == null) name = "Player";
                boolean targetAlreadyPartied = !partyReg.getParty(target).isSolo();

                slotToPlayerUuid.put(i, target);
                cmd.set("#CSPartyRow" + i + ".Visible", true);
                cmd.set("#CSPartyRowName" + i + ".Text", name);
                cmd.set("#CSPartyRowName" + i + ".Style.TextColor", COLOR_PLAYER_ONLINE);

                cmd.set("#CSPartyRowBtnA" + i + ".Visible", true);
                cmd.set("#CSPartyRowBtnA" + i + ".Text",
                        targetAlreadyPartied ? "In Party" : "Invite");
                cmd.set("#CSPartyRowBtnA" + i + ".Disabled", targetAlreadyPartied);
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", false);
            } else {
                cmd.set("#CSPartyRow" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnA" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", false);
            }
        }
    }

    private void renderInvitesTabRows(UICommandBuilder cmd, Store<EntityStore> store,
                                      Nat20PartyRegistry partyReg,
                                      Nat20PartyInviteRegistry inviteReg) {
        if (inviteReg == null) {
            hidePartyRows(cmd);
            cmd.set("#CSPartyEmptyState.Visible", true);
            cmd.set("#CSPartyEmptyState.Text", "Invites unavailable.");
            return;
        }

        UUID viewer = this.playerRef.getUuid();
        PartyInvite invite = inviteReg.getForInvitee(viewer);
        List<PartyInvite> list = new ArrayList<>();
        if (invite != null) list.add(invite);

        cmd.set("#CSPartyEmptyState.Visible", list.isEmpty());
        if (list.isEmpty()) {
            cmd.set("#CSPartyEmptyState.Text", "No pending invites.");
        }

        for (int i = 0; i < MAX_PARTY_ROWS; i++) {
            if (i < list.size()) {
                PartyInvite pi = list.get(i);
                String name = resolveDisplayName(store, pi.inviterUuid());
                if (name == null) name = "Someone";

                slotToPlayerUuid.put(i, pi.inviterUuid());
                cmd.set("#CSPartyRow" + i + ".Visible", true);
                cmd.set("#CSPartyRowName" + i + ".Text", name + " invited you");
                cmd.set("#CSPartyRowName" + i + ".Style.TextColor", COLOR_PLAYER_ONLINE);

                cmd.set("#CSPartyRowBtnA" + i + ".Visible", true);
                cmd.set("#CSPartyRowBtnA" + i + ".Text", "Accept");
                cmd.set("#CSPartyRowBtnA" + i + ".Disabled", false);
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", true);
                cmd.set("#CSPartyRowBtnB" + i + ".Text", "Decline");
                cmd.set("#CSPartyRowBtnB" + i + ".Disabled", false);
            } else {
                cmd.set("#CSPartyRow" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnA" + i + ".Visible", false);
                cmd.set("#CSPartyRowBtnB" + i + ".Visible", false);
            }
        }
    }

    private static void hidePartyRows(UICommandBuilder cmd) {
        for (int i = 0; i < MAX_PARTY_ROWS; i++) {
            cmd.set("#CSPartyRow" + i + ".Visible", false);
            cmd.set("#CSPartyRowBtnA" + i + ".Visible", false);
            cmd.set("#CSPartyRowBtnB" + i + ".Visible", false);
        }
    }

    /** Resolve a UUID to its display name via the default world's player ref,
     *  or null if the player is offline / unresolvable. */
    private static String resolveDisplayName(Store<EntityStore> store, UUID uuid) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return null;
        Ref<EntityStore> entityRef = world.getEntityRef(uuid);
        if (entityRef == null) return null;
        Player player = store.getComponent(entityRef, Player.getComponentType());
        return player != null ? player.getDisplayName() : null;
    }

    /** Walk {@code world.getPlayerRefs()} for a PlayerRef matching the given
     *  UUID, or null if the player is offline in this world. */
    private static PlayerRef findPlayerRef(World world, UUID uuid) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (uuid.equals(pr.getUuid())) return pr;
        }
        return null;
    }

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
        LOGGER.atFine().log("handleDataEvent type=%s id=%s", type, data.getId());
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
            return;
        }
        if ("tab".equals(type)) {
            QuestTab target = "completed".equals(data.getId()) ? QuestTab.COMPLETED : QuestTab.ACTIVE;
            if (currentTab != target) {
                currentTab = target;
                rebuild();
            }
            return;
        }
        if ("questrow".equals(type)) {
            handleQuestRowClicked(ref, store, data.getId());
            return;
        }
        if ("party-tab".equals(type)) {
            PartyTab target = switch (data.getId()) {
                case "server" -> PartyTab.SERVER;
                case "invites" -> PartyTab.INVITES;
                default -> PartyTab.PARTY;
            };
            if (currentPartyTab != target) {
                currentPartyTab = target;
                pendingConfirmToken = null;
                rebuild();
            }
            return;
        }
        if ("party-row-a".equals(type)) {
            handlePartyRowButton(ref, store, data.getId(), true);
            return;
        }
        if ("party-row-b".equals(type)) {
            handlePartyRowButton(ref, store, data.getId(), false);
            return;
        }
        if ("party-leave".equals(type)) {
            handleLeaveClicked();
            return;
        }
        if ("party-leave-cancel".equals(type)) {
            if ("leave".equals(pendingConfirmToken)) {
                pendingConfirmToken = null;
                rebuild();
            }
        }
    }

    /**
     * Dispatch for a party row button click. Button A / B semantics depend on
     * the current tab:
     *   PARTY   : A=Kick (confirm), B=Promote
     *   SERVER  : A=Invite, B=(hidden)
     *   INVITES : A=Accept, B=Decline
     */
    private void handlePartyRowButton(Ref<EntityStore> ref, Store<EntityStore> store,
                                      String slotStr, boolean isButtonA) {
        int slot;
        try {
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            return;
        }
        UUID target = slotToPlayerUuid.get(slot);
        if (target == null) return;

        switch (currentPartyTab) {
            case PARTY -> {
                if (isButtonA) handleKickClicked(slot, target);
                else handlePromoteClicked(target);
            }
            case SERVER -> {
                if (isButtonA) handleInviteClicked(store, target);
            }
            case INVITES -> {
                if (isButtonA) handleAcceptInviteClicked(target);
                else handleDeclineInviteClicked(target);
            }
        }
    }

    private void handleKickClicked(int slot, UUID target) {
        Nat20PartyRegistry reg = Natural20.getInstance().getPartyRegistry();
        if (reg == null) return;
        String token = "kick-" + slot;
        if (!token.equals(pendingConfirmToken)) {
            pendingConfirmToken = token;
            rebuild();
            return;
        }
        try {
            reg.kick(this.playerRef.getUuid(), target);
        } catch (RuntimeException e) {
            LOGGER.atWarning().log("Kick failed: %s", e.getMessage());
        }
        pendingConfirmToken = null;
        persistPartyState();
        rebuild();
    }

    private void handlePromoteClicked(UUID target) {
        Nat20PartyRegistry reg = Natural20.getInstance().getPartyRegistry();
        if (reg == null) return;
        UUID viewer = this.playerRef.getUuid();
        Nat20Party party = reg.getParty(viewer);
        if (!viewer.equals(party.getLeader())) return;
        if (!party.getMembers().contains(target)) return;
        party.promoteToLeader(target);
        persistPartyState();
        rebuild();
    }

    private void handleInviteClicked(Store<EntityStore> store, UUID target) {
        Nat20PartyRegistry partyReg = Natural20.getInstance().getPartyRegistry();
        Nat20PartyInviteRegistry inviteReg = Natural20.getInstance().getPartyInviteRegistry();
        if (partyReg == null || inviteReg == null) return;

        UUID viewer = this.playerRef.getUuid();
        try {
            partyReg.createInvite(inviteReg, viewer, target);
        } catch (RuntimeException e) {
            LOGGER.atWarning().log("Invite failed: %s", e.getMessage());
            rebuild();
            return;
        }

        // Fire the banner only to online invitees. Offline invitees see the
        // invite on their next /sheet open via the Invites tab.
        if (partyReg.isOnline(target)) {
            World world = Natural20.getInstance().getDefaultWorld();
            if (world != null) {
                PlayerRef inviteeRef = findPlayerRef(world, target);
                if (inviteeRef != null) {
                    String inviterName = resolveDisplayName(store, viewer);
                    if (inviterName == null) inviterName = "Someone";
                    PartyInviteBanner.show(inviteeRef, inviterName);
                }
            }
        }

        persistInviteState();
        rebuild();
    }

    private void handleAcceptInviteClicked(UUID inviter) {
        Nat20PartyRegistry partyReg = Natural20.getInstance().getPartyRegistry();
        Nat20PartyInviteRegistry inviteReg = Natural20.getInstance().getPartyInviteRegistry();
        if (partyReg == null || inviteReg == null) return;
        try {
            partyReg.acceptPendingInvite(inviteReg, this.playerRef.getUuid());
        } catch (RuntimeException e) {
            LOGGER.atWarning().log("Accept failed: %s", e.getMessage());
        }
        currentPartyTab = PartyTab.PARTY;
        persistPartyState();
        persistInviteState();
        rebuild();
    }

    private void handleDeclineInviteClicked(UUID inviter) {
        Nat20PartyInviteRegistry inviteReg = Natural20.getInstance().getPartyInviteRegistry();
        if (inviteReg == null) return;
        Natural20.getInstance().getPartyRegistry()
            .declinePendingInvite(inviteReg, this.playerRef.getUuid());
        persistInviteState();
        rebuild();
    }

    private void handleLeaveClicked() {
        Nat20PartyRegistry reg = Natural20.getInstance().getPartyRegistry();
        if (reg == null) return;
        if (!"leave".equals(pendingConfirmToken)) {
            pendingConfirmToken = "leave";
            rebuild();
            return;
        }
        reg.leave(this.playerRef.getUuid());
        pendingConfirmToken = null;
        persistPartyState();
        rebuild();
    }

    private static void persistPartyState() {
        try {
            Natural20.getInstance().getPartyRegistry()
                .saveTo(Natural20.getInstance().getDataDirectory().resolve("parties.json"));
        } catch (java.io.IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist party registry");
        }
    }

    private static void persistInviteState() {
        try {
            Natural20.getInstance().getPartyInviteRegistry()
                .saveTo(Natural20.getInstance().getDataDirectory().resolve("party_invites.json"));
        } catch (java.io.IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist party invite registry");
        }
    }

    /**
     * Tap on an active-tab quest row flips that quest's {@code waypointEnabled}
     * flag, refreshes the on-map marker cache so the change is visible
     * immediately (otherwise it would only show after the next quest-state
     * mutation triggers refreshMarkers), and rebuilds the page so the row's
     * dimmed/bright color reflects the new state.
     *
     * <p>Completed-tab clicks are ignored: those rows are non-interactive.
     */
    private void handleQuestRowClicked(Ref<EntityStore> ref, Store<EntityStore> store, String slotStr) {
        if (currentTab != QuestTab.ACTIVE) return;
        int slot;
        try {
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            return;
        }
        if (slot < 0 || slot >= MAX_QUEST_ROWS) return;

        String questId = slotToQuestId.get(slot);
        if (questId == null) return;

        Nat20PlayerData pdata = store.getComponent(ref, Natural20.getPlayerDataType());
        if (pdata == null) return;

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;
        QuestStateManager sm = questSystem.getStateManager();
        Map<String, QuestInstance> active = sm.getActiveQuests(pdata);
        QuestInstance quest = active.get(questId);
        if (quest == null) return;

        quest.setWaypointEnabled(!quest.isWaypointEnabled());

        // CRITICAL: getActiveQuests returns a fresh Gson-deserialized map every
        // call. The QuestInstance we just mutated is transient until we write
        // the whole map back via saveActiveQuests. Without this persist step,
        // the next getActiveQuests call (including the one refreshMarkers does
        // internally) reads the old JSON and the flag flip is lost.
        sm.saveActiveQuests(pdata, active);

        // Marker cache rebuild: copies the same call shape used by
        // FetchItemTrackingSystem / DialogueActionRegistry. Task 5 added the
        // isWaypointEnabled() gate inside refreshMarkers, so the dropped /
        // restored marker shows up on the next map render.
        QuestMarkerProvider.refreshMarkers(this.playerRef.getUuid(), pdata);

        rebuild();
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

    /**
     * External refresh trigger for {@link CharacterSheetManager} hooks (Tasks 20+21).
     * Re-runs {@link #build} via the inherited protected {@code rebuild()} so that
     * the snapshot fields (applied scores, pending pool, level/XP) and the quest
     * lists re-read from {@link Nat20PlayerData}.
     *
     * <p>The {@link #pendingDelta} preview state is intentionally preserved across
     * refresh: it is a page field, not recomputed in {@code build()}, so a queued
     * preview the player has not yet Applied survives a level-up or quest
     * completion that arrives mid-edit.
     */
    public void refresh() {
        if (dismissed) return;
        rebuild();
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
