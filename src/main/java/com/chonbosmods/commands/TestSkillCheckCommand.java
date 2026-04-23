package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.DialogueLoader;
import com.chonbosmods.dialogue.DialogueManager;
import com.chonbosmods.dialogue.DifficultyTier;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.dialogue.model.DialogueNode;
import com.chonbosmods.dialogue.model.ResponseOption;
import com.chonbosmods.dialogue.model.TopicDefinition;
import com.chonbosmods.npc.Nat20NameGenerator;
import com.chonbosmods.npc.Nat20NpcManager;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * End-to-end test command: spawns an NPC with a forced disposition and opens a
 * synthetic dialogue containing a single SkillCheckNode. Validates the full
 * disposition -> DispositionBracket.rollMode() -> dual-dice UI path.
 *
 *   /nat20 testskillcheck low    (disposition=10 -> DISADVANTAGE)
 *   /nat20 testskillcheck high   (disposition=90 -> ADVANTAGE)
 */
public class TestSkillCheckCommand extends AbstractPlayerCommand {

    private static final String ROLE_NAME = "Villager";
    private static final int LOW_DISPOSITION = 10;
    private static final int HIGH_DISPOSITION = 90;

    private final RequiredArg<String> dispArg =
        withRequiredArg("disposition", "low (disadvantage) or high (advantage)", ArgTypes.STRING);

    public TestSkillCheckCommand() {
        super("testskillcheck", "Spawn a test NPC with forced disposition and trigger a skill check");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = dispArg.get(context).toLowerCase();
        int disposition;
        String label;
        if ("low".equals(input)) {
            disposition = LOW_DISPOSITION;
            label = "low (disadvantage)";
        } else if ("high".equals(input)) {
            disposition = HIGH_DISPOSITION;
            label = "high (advantage)";
        } else {
            context.sendMessage(Message.raw("Usage: /nat20 testskillcheck <low|high>"));
            return;
        }

        int roleIndex = NPCPlugin.get().getIndex(ROLE_NAME);
        if (roleIndex < 0) {
            context.sendMessage(Message.raw("Role '" + ROLE_NAME + "' not registered."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3d spawnPos = new Vector3d(pos.getX() + 2, pos.getY(), pos.getZ());
        Vector3f rotation = new Vector3f(0, 0, 0);

        String preName = Nat20NameGenerator.generate(Objects.hash(ROLE_NAME, System.nanoTime()));
        Random skinRng = new Random(preName.hashCode());
        Model model = CosmeticsModule.get().createModel(
            CosmeticsModule.get().generateRandomSkin(skinRng), 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);
        if (result == null) {
            context.sendMessage(Message.raw("Failed to spawn NPC."));
            return;
        }

        Ref<EntityStore> npcRef = result.first();
        NPCEntity npcEntity = result.second();

        String generatedName = Nat20NameGenerator.generate(npcEntity.getUuid().getMostSignificantBits());
        Nat20NpcData npcData = store.addComponent(npcRef, Natural20.getNpcDataType());
        npcData.setGeneratedName(generatedName);
        npcData.setRoleName(ROLE_NAME);

        String displayName = generatedName + " the Test-Dummy";
        store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));

        store.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(
                        Nat20NpcManager.generateNpcSkin(new Random(generatedName.hashCode()))));

        // Register a synthetic dialogue keyed to the generated name so
        // DialogueManager resolves it via dialogueLoader.getGraphForNpc().
        DialogueGraph graph = buildTestGraph(generatedName, disposition);
        Natural20 plugin = Natural20.getInstance();
        plugin.getDialogueLoader().registerGeneratedGraph(generatedName, graph);

        // Explicit disposition set overrides any persisted value and guarantees
        // the test is deterministic across repeated runs.
        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) {
            playerData = store.addComponent(ref, Natural20.getPlayerDataType());
        }
        playerData.setDispositionFor(generatedName, disposition);

        plugin.getDialogueManager().startSession(ref, npcRef, store, () -> {});

        context.sendMessage(Message.raw(
                "Spawned " + displayName + " with " + label + " disposition (" + disposition
                        + "). Click the response to roll."));
    }

    private DialogueGraph buildTestGraph(String npcKey, int defaultDisposition) {
        ResponseOption rollResponse = new ResponseOption(
            "test_roll",
            "[Persuasion DC 15] Try to win them over.",
            null,
            "test_check",
            null,
            null,
            "test_check",
            "CHA",
            null
        );

        DialogueNode.DialogueTextNode greeting = new DialogueNode.DialogueTextNode(
            "I'm the test dummy. Roll the dice on me.",
            null,
            List.of(rollResponse),
            List.of(),
            false,
            false,
            null
        );

        DialogueNode.SkillCheckNode check = new DialogueNode.SkillCheckNode(
            Skill.PERSUASION,
            Stat.CHA,
            DifficultyTier.MEDIUM,
            true,
            "test_pass",
            "test_fail",
            List.of()
        );

        DialogueNode.DialogueTextNode pass = new DialogueNode.DialogueTextNode(
            "You convinced me. Well rolled.",
            null,
            List.of(),
            List.of(),
            true,
            false,
            null
        );

        DialogueNode.DialogueTextNode fail = new DialogueNode.DialogueTextNode(
            "Not buying it.",
            null,
            List.of(),
            List.of(),
            true,
            false,
            null
        );

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        nodes.put("test_greeting", greeting);
        nodes.put("test_check", check);
        nodes.put("test_pass", pass);
        nodes.put("test_fail", fail);

        List<TopicDefinition> topics = new ArrayList<>();

        return new DialogueGraph(
            npcKey,
            defaultDisposition,
            "test_greeting",
            null,
            topics,
            nodes
        );
    }
}
