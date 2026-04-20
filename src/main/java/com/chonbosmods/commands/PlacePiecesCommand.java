package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.chonbosmods.npc.NpcSpawnRole;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.quest.QuestChestPlacer;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.PiecePlacement;
import com.chonbosmods.settlement.SettlementPieceAssembler;
import com.chonbosmods.settlement.SettlementPlacement;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * {@code /nat20 placepieces}: directly invoke {@link SettlementPieceAssembler}
 * with {@link SettlementType#TOWN}'s {@link PiecePlacement} config at your feet,
 * and fan out to the same NPC / mob-group / chest spawn paths {@code /nat20 placeall}
 * uses. Bypasses worldgen so the piece-assembly algorithm can be iterated on
 * quickly.
 */
public class PlacePiecesCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PlacePieces");
    private static final String DEFAULT_MOB_ROLE = "Goblin_Scrapper";
    private static final String DEFAULT_CHEST_ITEM = "Quest_Treasure";
    private static final int CHAMPION_COUNT = 3;

    public PlacePiecesCommand() {
        super("placepieces", "Assemble a TOWN piece-mode settlement at your feet and fan out spawns");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        SettlementPlacement placement = SettlementType.TOWN.getPlacement();
        if (!(placement instanceof PiecePlacement piece)) {
            ctx.sendMessage(Message.raw("SettlementType.TOWN is not PIECE mode; aborting."));
            return;
        }

        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        if (tf == null || tf.getPosition() == null) {
            ctx.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        Vector3d pos = tf.getPosition();
        Vector3i center = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        ctx.sendMessage(Message.raw(String.format(
            "Assembling %d-%d pieces from 'Nat20/%s/' around (%d, %d, %d)...",
            piece.minPieces(), piece.maxPieces(), piece.poolCategory(),
            center.getX(), center.getY(), center.getZ())));

        long seed = System.currentTimeMillis();
        SettlementPieceAssembler.assemble(world, center, piece, store, new Random(seed))
            .whenComplete((placed, error) -> {
                if (error != null || placed == null) {
                    ctx.sendMessage(Message.raw("Assembly failed"
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
            "Done. center=%s  NPCs=%d/%d  mobGroups=%d/%d  chests=%d/%d",
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

        String cellKey = "placepieces_"
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
