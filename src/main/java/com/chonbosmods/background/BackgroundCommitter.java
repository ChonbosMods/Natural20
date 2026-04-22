package com.chonbosmods.background;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.AffixRewardRoller;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Random;

/**
 * Applies a Background's effects to a player's persistent data.
 *
 * <p>This class is the single point of commit for "the player has chosen their
 * character background." {@link #applyStats} mutates only {@link Nat20PlayerData}
 * and is fully unit-testable; {@link #grantKit} and {@link #commit} require a
 * live ECS {@link Store} and player inventory and are validated via the Phase 7
 * smoke test (Task 7).
 */
public final class BackgroundCommitter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|BackgroundCommit");

    private BackgroundCommitter() {}

    /**
     * Adds +3 to the background's primary stat and +3 to its secondary, and
     * marks the player's {@code firstJoinSeen} flag so the auto-trigger in
     * {@code PlayerReadyEvent} won't re-fire on subsequent logins.
     *
     * <p>Stats are added on top of whatever value currently exists (not reset).
     * Callers are responsible for gating against double-application; see the
     * first-join flag in {@link Nat20PlayerData#isFirstJoinSeen()}.
     */
    public static void applyStats(Nat20PlayerData data, Background background) {
        int[] stats = data.getStats().clone();
        stats[background.primary().index()] += 3;
        stats[background.secondary().index()] += 3;
        data.setStats(stats);
        data.setBackground(background);
        data.setFirstJoinSeen(true);
    }

    /**
     * Delivers the background's starter kit into the player's inventory.
     *
     * <p>For each {@link KitItem} in {@code background.kit()}:
     * <ul>
     *   <li>If {@link KitItem#rollAffix()} is {@code true}, the item is rolled through
     *       {@link AffixRewardRoller#rollFor(String, String, int, Random)} at Common tier
     *       and ilvl 1, producing an {@link ItemStack} with {@code Nat20LootData} metadata.
     *       {@code AffixRewardRoller.rollFor} always returns quantity 1; every affixed
     *       starter weapon in {@link Background} is defined as quantity 1 by design.</li>
     *   <li>If {@code rollAffix} is {@code false}, a plain {@code new ItemStack(itemId, quantity)}
     *       is created with no affix metadata (used for arrows: a "+5% crit chance arrow"
     *       reads wrong).</li>
     * </ul>
     *
     * <p>Each stack is delivered via {@link Player#giveItem}, the same call used by
     * quest-reward dispense ({@code DialogueActionRegistry.dispensePhaseReward}) and the
     * {@code /loot} command. {@code giveItem} returns a transaction that may indicate
     * inventory overflow; per the Task 2.3 YAGNI scope, no overflow handling, no
     * partial-commit rollback, no transaction inspection: whatever {@code giveItem}
     * does on a full inventory (typically: returns a non-succeeded transaction with
     * a remainder; the item is silently lost) is what we inherit. The picker UI is
     * triggered on first join when the inventory is empty, so this should not occur
     * in practice.
     *
     * @throws IllegalStateException if the loot system is not initialized or cannot
     *                               produce a valid stack for an affixed kit item
     *                               (propagated from {@link AffixRewardRoller#rollFor})
     */
    public static void grantKit(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                Background background, Random random) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            throw new IllegalStateException(
                    "Cannot grant kit: no Player component on the supplied entity ref "
                            + "(background=" + background.name() + ")");
        }

        for (KitItem item : background.kit()) {
            ItemStack stack;
            if (item.rollAffix()) {
                stack = AffixRewardRoller.rollFor(item.itemId(), "Common", /*ilvl=*/1, random);
            } else {
                stack = new ItemStack(item.itemId(), item.quantity());
            }
            ItemStackTransaction tx = player.giveItem(stack, playerRef, store);
            if (tx == null) {
                // giveItem should always return a transaction; if it doesn't, we have a
                // bigger problem than starter kits. Fail loudly rather than silently.
                throw new IllegalStateException(
                        "Player.giveItem returned null transaction for kit item '"
                                + item.itemId() + "' (background=" + background.name() + ")");
            }
            if (!tx.succeeded()) {
                // Picker fires on first join with empty inventory, so this should not
                // happen in practice. Log if it does so a tester re-triggering commit
                // (e.g., via a future debug command) can see the silent loss.
                ItemStack remainder = tx.getRemainder();
                int remainderQty = remainder != null ? remainder.getQuantity() : item.quantity();
                LOGGER.atSevere().log(
                        "Background kit grant REFUSED for '%s' (background=%s); "
                                + "remainder qty=%d. Inventory full?",
                        item.itemId(), background.name(), remainderQty);
            }
        }
    }

    /**
     * Top-level orchestrator called by the Background picker UI's Confirm button
     * (Task 5.2). Performs the full commit:
     * <ol>
     *   <li>Resolves {@link Nat20PlayerData} from the player's component store
     *       (lazy-creating it if absent, mirroring the pattern in
     *       {@code SetStatsCommand} and {@code DialogueManager.startSession}).</li>
     *   <li>Calls {@link #applyStats} to bank +3/+3 and flip {@code firstJoinSeen}.</li>
     *   <li>Calls {@link #grantKit} to deliver the starter inventory.</li>
     * </ol>
     *
     * <p>Persistence is implicit: {@link Nat20PlayerData} is registered as a player
     * component with a CODEC ({@code Natural20.java:573}), so any mutation through
     * its setters is automatically serialized by the ECS framework on the next
     * tick / disconnect. No explicit save call is required (mirrors the pattern
     * in {@code SetStatsCommand}, which mutates and exits without saving).
     *
     * @throws IllegalStateException if the loot system is not initialized or cannot
     *                               produce a valid stack for an affixed kit item,
     *                               or if the player ref has no {@link Player} component
     */
    public static void commit(Ref<EntityStore> playerRef, Store<EntityStore> store,
                              Background background, Random random) {
        Nat20PlayerData data = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (data == null) {
            data = store.addComponent(playerRef, Natural20.getPlayerDataType());
        }
        // Bind the runtime playerUuid eagerly so downstream quest-state code can
        // resolve the owner. PlayerReadyEvent only binds the UUID when the
        // component already exists; because Nat20PlayerData is lazy-created here
        // for brand-new players, PlayerReady's bind would otherwise be skipped
        // and the UUID would stay null until first NPC dialogue. Mirrors the
        // pattern in DialogueManager.startSession.
        if (data.getPlayerUuid() == null) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                data.setPlayerUuid(player.getPlayerRef().getUuid());
            }
        }
        applyStats(data, background);
        grantKit(playerRef, store, background, random);
    }
}
