package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.CombatDebugSystem;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.Random;

public class CombatTestCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> affixArg =
        withOptionalArg("affix", "Affix ID like mighty, vampiric", ArgTypes.STRING);
    private final OptionalArg<String> rarityArg =
        withOptionalArg("rarity", "Rarity like Rare, Epic (defaults to Rare)", ArgTypes.STRING);

    public CombatTestCommand() {
        super("combattest", "Spawn combat dummies and optionally get a test weapon");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();

        // Spawn passive Combat Dummy 5 blocks ahead
        boolean passiveSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 5, pos.getY(), pos.getZ()),
            "CombatDummy", "Combat Dummy");

        // Spawn aggressive Attacker Dummy offset to the side
        boolean attackerSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 3, pos.getY(), pos.getZ() + 4),
            "AttackerDummy", "Attacker Dummy");

        // Enable combat debug logging for this player
        CombatDebugSystem.enable(playerRef.getUuid());

        // Handle optional test weapon
        if (context.provided(affixArg)) {
            String affixId = affixArg.get(context);
            giveTestWeapon(context, store, ref, affixId,
                context.provided(rarityArg) ? rarityArg.get(context) : "Rare");
        }

        // Confirmation message
        StringBuilder msg = new StringBuilder("Combat test setup:");
        msg.append(passiveSpawned ? " Combat Dummy spawned." : " Combat Dummy failed to spawn.");
        msg.append(attackerSpawned ? " Attacker Dummy spawned." : " Attacker Dummy failed to spawn.");
        msg.append(" Debug logging enabled.");
        context.sendMessage(Message.raw(msg.toString()));
    }

    private boolean spawnDummy(CommandContext context, Store<EntityStore> store,
                               Vector3d playerPos, Vector3d spawnPos,
                               String roleName, String displayName) {
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            context.sendMessage(Message.raw("Role '" + roleName + "' not registered in NPCPlugin."));
            return false;
        }

        // Face toward the player
        double dx = playerPos.getX() - spawnPos.getX();
        double dz = playerPos.getZ() - spawnPos.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        Vector3f rotation = new Vector3f(0, yaw, 0);

        Random rng = new Random(System.nanoTime());
        Model model = CosmeticsModule.get().createModel(
            CosmeticsModule.get().generateRandomSkin(rng), 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result != null) {
            store.putComponent(result.first(), Nameplate.getComponentType(), new Nameplate(displayName));
            return true;
        }
        return false;
    }

    private void giveTestWeapon(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, String affixId, String rarityName) {
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();

        // Normalize affix ID with namespace prefix
        String fullAffixId = affixId.contains(":") ? affixId : "nat20:" + affixId;
        if (lootSystem.getAffixRegistry().get(fullAffixId) == null) {
            context.sendMessage(Message.raw("Unknown affix: " + affixId));
            return;
        }

        Nat20RarityDef rarityDef = lootSystem.getRarityRegistry().get(rarityName);
        if (rarityDef == null) {
            context.sendMessage(Message.raw("Unknown rarity: " + rarityName
                + ". Valid: Common, Uncommon, Rare, Epic, Legendary"));
            return;
        }

        int tier = rarityDef.qualityValue();
        Random random = new Random();
        Nat20LootData lootData = lootSystem.getPipeline().generate(
            "Weapon_Sword_Iron", "Iron Sword", "melee_weapon", tier, tier, random);
        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate test weapon."));
            return;
        }

        String stackItemId = lootData.getUniqueItemId() != null ? lootData.getUniqueItemId() : "Weapon_Sword_Iron";
        ItemStack stack = new ItemStack(stackItemId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave test weapon: " + lootData.getGeneratedName()
            + " [" + lootData.getRarity() + "]"));
    }
}
