package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.chonbosmods.npc.NpcSpawnRole;
import com.chonbosmods.prefab.MarkerScan;
import com.chonbosmods.prefab.Nat20PrefabMarkerScanner;
import com.chonbosmods.prefab.Nat20PrefabPath;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.prefab.YawAlignment;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.quest.QuestChestPlacer;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Paste {@code Nat20/testStructure} at the player's feet and exercise all
 * marker-driven spawn paths (NPCs, mob group, chest) in one call. Default
 * entry point: no rotation. See {@link PlaceNorthCommand}, {@link PlaceSouthCommand},
 * {@link PlaceEastCommand}, {@link PlaceWestCommand} for rotated variants.
 */
public class PlaceAllCommand extends AbstractPlayerCommand {

    static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PlaceAll");
    static final String PREFAB_KEY = "Nat20/settlement_pieces/testStructure";
    static final String DEFAULT_CHEST_ITEM = "Quest_Treasure";
    static final String DEFAULT_MOB_ROLE = "Goblin_Scrapper";
    static final int CHAMPION_COUNT = 3;

    public PlaceAllCommand() {
        super("placeall", "Paste Nat20/testStructure and spawn NPCs/mob group/chest from its markers");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        placeAndSpawn(ctx, store, ref, world, null);
    }

    /**
     * Shared fan-out used by {@link PlaceAllCommand} and its rotation-test siblings.
     *
     * @param wantedWorldDir if non-null, the prefab is rotated so its direction
     *                       marker points this direction; if null, no rotation.
     */
    static void placeAndSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              World world, @Nullable Vector3i wantedWorldDir) {
        Path path = Nat20PrefabPath.resolve(PREFAB_KEY);
        if (path == null) {
            ctx.sendMessage(Message.raw("Prefab not found: " + PREFAB_KEY));
            return;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(path);
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Failed to load buffer: " + e.getMessage()));
            LOGGER.atWarning().withCause(e).log("Buffer load failed for %s", PREFAB_KEY);
            return;
        }

        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        if (tf == null || tf.getPosition() == null) {
            ctx.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        Vector3d pos = tf.getPosition();
        Vector3i anchorPos = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        Rotation yaw = Rotation.None;
        String rotationDesc = "None";
        if (wantedWorldDir != null) {
            MarkerScan scan;
            try {
                scan = Nat20PrefabMarkerScanner.scan(buffer);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Scanner rejected prefab: " + e.getMessage()));
                return;
            }
            yaw = YawAlignment.computeYawToAlign(scan.directionVector(), wantedWorldDir);
            rotationDesc = String.format("%s (prefabDir=%s -> worldDir=%s)",
                yaw, scan.directionVector(), wantedWorldDir);
        }

        ctx.sendMessage(Message.raw("Pasting " + PREFAB_KEY + " at ("
            + anchorPos.getX() + ", " + anchorPos.getY() + ", " + anchorPos.getZ()
            + ") rotation=" + rotationDesc));

        long seed = System.currentTimeMillis();
        Nat20PrefabPaster.paste(buffer, world, anchorPos, yaw, new Random(seed), store)
            .whenComplete((placed, error) -> {
                if (error != null || placed == null) {
                    ctx.sendMessage(Message.raw("Paste failed"
                        + (error != null ? ": " + error.getMessage() : "")));
                    return;
                }
                world.execute(() -> fanOut(ctx, world, store, placed, seed));
            });
    }

    private static void fanOut(CommandContext ctx, World world, Store<EntityStore> store,
                                PlacedMarkers placed, long seed) {
        int npcs = spawnNpcs(world, store, placed, seed);
        int groups = spawnMobGroups(world, placed);
        int chests = placeChests(world, placed);

        ctx.sendMessage(Message.raw(String.format(
            "Done. anchor=%s  NPCs=%d/%d  mobGroups=%d/%d  chests=%d/%d",
            placed.anchorWorld(),
            npcs, placed.npcSpawnsWorld().size(),
            groups, placed.mobGroupSpawnsWorld().size(),
            chests, placed.chestSpawnsWorld().size())));
    }

    private static int spawnNpcs(World world, Store<EntityStore> store,
                                  PlacedMarkers placed, long seed) {
        if (placed.npcSpawnsWorld().isEmpty()) return 0;
        SettlementType type = SettlementType.TOWN;
        List<Vector3d> markers = new ArrayList<>(placed.npcSpawnsWorld());
        Collections.shuffle(markers, new Random(seed));

        String cellKey = "placeall_"
            + placed.anchorWorld().getX() + "_" + placed.anchorWorld().getZ()
            + "_" + seed;

        List<NpcRecord> spawned = new ArrayList<>();
        int markerIdx = 0;
        for (NpcSpawnRole role : type.getNpcSpawns()) {
            for (int i = 0; i < role.count() && markerIdx < markers.size(); i++, markerIdx++) {
                NpcRecord rec = Natural20.getInstance().getNpcManager()
                    .spawnSettlementNpc(store, world, role, markers.get(markerIdx),
                                        cellKey, seed);
                if (rec != null) spawned.add(rec);
            }
        }

        SettlementRecord record = new SettlementRecord(
            cellKey, UUID.nameUUIDFromBytes(world.getName().getBytes()),
            placed.anchorWorld().getX(), placed.anchorWorld().getY(), placed.anchorWorld().getZ(),
            type);
        record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode(),
            Natural20.getInstance().getSettlementRegistry().getUsedNames()));
        record.getNpcs().addAll(spawned);
        Natural20.getInstance().getSettlementRegistry().register(record);
        return spawned.size();
    }

    private static int spawnMobGroups(World world, PlacedMarkers placed) {
        if (placed.mobGroupSpawnsWorld().isEmpty()) return 0;
        Nat20MobGroupSpawner spawner = Natural20.getInstance().getMobGroupSpawner();
        int spawned = 0;
        for (Vector3d mgsPos : placed.mobGroupSpawnsWorld()) {
            Nat20MobGroupSpawner.SpawnResult r = spawner.spawnGroup(
                world, DEFAULT_MOB_ROLE, CHAMPION_COUNT, mgsPos, null, false);
            if (r != null) spawned++;
        }
        return spawned;
    }

    private static int placeChests(World world, PlacedMarkers placed) {
        if (placed.chestSpawnsWorld().isEmpty()) return 0;
        int placedCount = 0;
        for (Vector3d c : placed.chestSpawnsWorld()) {
            boolean ok = QuestChestPlacer.placeQuestChest(world,
                (int) Math.floor(c.getX()),
                (int) Math.floor(c.getY()),
                (int) Math.floor(c.getZ()),
                DEFAULT_CHEST_ITEM,
                "test loot");
            if (ok) placedCount++;
        }
        return placedCount;
    }
}
