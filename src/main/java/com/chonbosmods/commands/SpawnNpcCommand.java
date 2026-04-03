package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.npc.Nat20NameGenerator;
import com.chonbosmods.npc.Nat20NpcManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
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
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class SpawnNpcCommand extends AbstractPlayerCommand {

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        Map.entry("villager", "Villager"),
        Map.entry("guard", "Guard"),
        Map.entry("blacksmith", "ArtisanBlacksmith"),
        Map.entry("artisan_blacksmith", "ArtisanBlacksmith"),
        Map.entry("alchemist", "ArtisanAlchemist"),
        Map.entry("artisan_alchemist", "ArtisanAlchemist"),
        Map.entry("cook", "ArtisanCook"),
        Map.entry("artisan_cook", "ArtisanCook"),
        Map.entry("traveler", "Traveler"),
        Map.entry("tavern_keeper", "TavernKeeper"),
        Map.entry("tavernkeeper", "TavernKeeper")
    );

    private final RequiredArg<String> roleArg =
        withRequiredArg("role", "NPC role: villager, guard, blacksmith, alchemist, cook, traveler, tavern_keeper", ArgTypes.STRING);

    public SpawnNpcCommand() {
        super("spawnnpc", "Spawn a Nat20 NPC at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = roleArg.get(context).toLowerCase();
        String roleName = ROLE_ALIASES.get(input);

        if (roleName == null) {
            context.sendMessage(Message.raw("Unknown role: " + input +
                ". Use: villager, guard, blacksmith, alchemist, cook, traveler, tavern_keeper"));
            return;
        }

        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            context.sendMessage(Message.raw("Role '" + roleName + "' not registered in NPCPlugin."));
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

        // Create model from unmodified skin: engine serialization breaks
        // on modified skins (beard/hair changes cause scale=0 on chunk reload)
        String preName = Nat20NameGenerator.generate(Objects.hash(roleName, System.nanoTime()));
        Random rng = new Random(preName.hashCode());
        Model model = CosmeticsModule.get().createModel(
            CosmeticsModule.get().generateRandomSkin(rng), 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result != null) {
            Ref<EntityStore> npcRef = result.first();
            NPCEntity npcEntity = result.second();

            // Fix PersistentModel: spawnEntity calls model.toReference() which returns
            // DEFAULT_PLAYER_MODEL (scale=-1.0f) for Player models, crashing on chunk
            // reload. Remove PersistentModel so SetRenderedModel never fires.
            store.removeComponentIfExists(npcRef, PersistentModel.getComponentType());

            // Attach Nat20NpcData so dialogue system can identify this NPC
            String name = Nat20NameGenerator.generate(npcEntity.getUuid().getMostSignificantBits());
            Nat20NpcData npcData = store.addComponent(npcRef, Natural20.getNpcDataType());
            npcData.setGeneratedName(name);
            npcData.setRoleName(roleName);

            // Set nameplate using Nameplate component (overrides role's DisplayNames)
            String displayName = name + " the " + formatDisplayRole(roleName);
            store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));

            // Apply modified skin (beard reduction, hair color matching)
            com.hypixel.hytale.protocol.PlayerSkin displaySkin =
                Nat20NpcManager.generateNpcSkin(new Random(name.hashCode()));
            store.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                    new PlayerSkinComponent(displaySkin));

            context.sendMessage(Message.raw("Spawned " + displayName + " at " +
                (int) spawnPos.getX() + ", " + (int) spawnPos.getY() + ", " + (int) spawnPos.getZ()));
        } else {
            context.sendMessage(Message.raw("Failed to spawn " + roleName + "."));
        }
    }

    private String formatDisplayRole(String roleName) {
        if (roleName.startsWith("Artisan")) {
            return roleName.substring("Artisan".length());
        }
        // CamelCase to spaced: "TavernKeeper" -> "Tavern Keeper"
        return roleName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
