package com.chonbosmods.commands;

import com.chonbosmods.dialogue.model.ActiveFollowUp;
import com.chonbosmods.dialogue.model.LogEntry;
import com.chonbosmods.dialogue.model.ResolvedTopic;
import com.chonbosmods.dialogue.model.TopicDefinition;
import com.chonbosmods.dialogue.model.TopicScope;
import com.chonbosmods.dialogue.model.TopicState;
import com.chonbosmods.dice.RollMode;
import com.chonbosmods.dice.SkillCheckResult;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;
import com.chonbosmods.ui.Nat20DialoguePage;
import com.chonbosmods.ui.Nat20DiceRollPage;
import com.chonbosmods.ui.UITemplateProbePage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Debug command: /nat20 probe <args>
 *
 * Examples:
 *   /nat20 probe barter                              — bare load
 *   /nat20 probe barter,#ShopTitle.TextSpans,message  — try selector with Message
 *   /nat20 probe barter,#ShopTitle.Text,string        — try selector with String
 *   /nat20 probe barter,#TradeGrid,clear              — try clearing container
 *
 * Use commas, no spaces. Template aliases: barter, shop, shopbutton, textbutton, barterrow
 */
public class ProbeCommand extends AbstractPlayerCommand {

    private static final Map<String, String> TEMPLATE_ALIASES = Map.ofEntries(
        Map.entry("dialog", "Pages/DialogPage.ui"),
        Map.entry("barter", "Pages/BarterPage.ui"),
        Map.entry("shop", "Pages/ShopPage.ui"),
        Map.entry("shopbutton", "Pages/ShopElementButton.ui"),
        Map.entry("textbutton", "Pages/BasicTextButton.ui"),
        Map.entry("barterrow", "Pages/BarterTradeRow.ui"),
        Map.entry("repair", "Pages/ItemRepairPage.ui"),
        Map.entry("gallery", "Pages/UIGallery/UIGalleryPage.ui"),
        Map.entry("nat20dlg", "Pages/Nat20_Dialogue.ui")
    );

    private final RequiredArg<String> argsArg =
        withRequiredArg("args", "template[:selector:type] e.g. barter:#ShopTitle.TextSpans:message", ArgTypes.STRING);

    public ProbeCommand() {
        super("probe", "Probe a UI template element");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String raw = argsArg.get(context);
        String[] parts = raw.split(":", 3);

        String templateKey = parts[0].toLowerCase();

        // Special probe pages (check before alias lookup)
        if ("dualpanel".equals(templateKey) || "diceroll".equals(templateKey)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Could not resolve player."));
                return;
            }
            if ("dualpanel".equals(templateKey)) {
                probeDualPanel(player, playerRef, ref, store, context);
            } else {
                probeDiceRoll(player, playerRef, ref, store, context);
            }
            return;
        }

        String templatePath = TEMPLATE_ALIASES.get(templateKey);
        if (templatePath == null) {
            if (templateKey.contains("/") && templateKey.endsWith(".ui")) {
                templatePath = templateKey;
            } else {
                context.sendMessage(Message.raw("Unknown template: " + templateKey +
                    ". Use: " + String.join(", ", TEMPLATE_ALIASES.keySet()) + ", dualpanel, diceroll"));
                return;
            }
        }

        String selector = parts.length >= 2 ? parts[1] : null;
        String type = parts.length >= 3 ? parts[2] : "message";

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not resolve player."));
            return;
        }

        if (selector == null || selector.isEmpty()) {
            context.sendMessage(Message.raw("Probe: " + templatePath + " (bare)"));
        } else {
            context.sendMessage(Message.raw("Probe: " + templatePath + " | " + selector + " (" + type + ")"));
        }

        String finalTemplatePath = templatePath;
        UITemplateProbePage probePage = new UITemplateProbePage(
                playerRef, templatePath, selector, type,
                () -> {
                    player.getHudManager().showHudComponents(playerRef,
                            HudComponent.Hotbar, HudComponent.Reticle);
                    player.sendMessage(Message.raw("Probe closed: " + finalTemplatePath));
                });

        player.getHudManager().hideHudComponents(playerRef,
                HudComponent.Hotbar, HudComponent.Reticle);
        player.getPageManager().openCustomPage(ref, store, probePage);
    }

    private void probeDualPanel(Player player, PlayerRef playerRef,
                                Ref<EntityStore> ref, Store<EntityStore> store,
                                CommandContext context) {
        List<LogEntry> testLog = List.of(
            new LogEntry.TopicHeader("Repair", false),
            new LogEntry.NpcSpeech("Aye, I can fix that blade for ye. But it'll cost ya. Good steel doesn't come cheap around here.")
        );

        List<ActiveFollowUp> testFollowUps = List.of(
            new ActiveFollowUp("r1", "How much will it cost?", null, null, false),
            new ActiveFollowUp("r2", "I'll find someone cheaper.", null, "CHA", false),
            new ActiveFollowUp("r3", "Just do it.", null, null, false)
        );

        List<ResolvedTopic> testTopics = List.of(
            new ResolvedTopic(new TopicDefinition("repair", "Repair", "n1", TopicScope.LOCAL, null, true, null, 0, null, false), TopicState.ACTIVE),
            new ResolvedTopic(new TopicDefinition("rumors", "Rumors", "n2", TopicScope.LOCAL, null, true, "INT", 1, null, false), TopicState.ACTIVE),
            new ResolvedTopic(new TopicDefinition("quest", "The Missing Shipment", "n3", TopicScope.LOCAL, null, true, "CHA +2", 2, null, true), TopicState.ACTIVE)
        );

        Nat20DialoguePage page = new Nat20DialoguePage(playerRef);
        page.setState("Roderick the Blacksmith", testLog, testFollowUps, testTopics, 55, false,
            (type, id) -> player.sendMessage(Message.raw("Probe event: " + type + " / " + id)));

        player.getHudManager().hideHudComponents(playerRef, HudComponent.Hotbar, HudComponent.Reticle);
        player.getPageManager().openCustomPage(ref, store, page);
        context.sendMessage(Message.raw("Opened dual-panel dialogue probe"));
    }

    private void probeDiceRoll(Player player, PlayerRef playerRef,
                               Ref<EntityStore> ref, Store<EntityStore> store,
                               CommandContext context) {
        SkillCheckResult testResult = new SkillCheckResult(17, -1, RollMode.NORMAL, 3, 2, 22, 14, true, false);
        Nat20DiceRollPage page = new Nat20DiceRollPage(playerRef, Skill.PERSUASION, Stat.CHA,
            testResult, 2,
            r -> {
                player.sendMessage(Message.raw("Dice probe continue: passed=" + r.passed()));
                player.getHudManager().showHudComponents(playerRef, HudComponent.Hotbar, HudComponent.Reticle);
            });

        player.getHudManager().hideHudComponents(playerRef, HudComponent.Hotbar, HudComponent.Reticle);
        player.getPageManager().openCustomPage(ref, store, page);
        context.sendMessage(Message.raw("Opened dice roll probe"));
    }
}
