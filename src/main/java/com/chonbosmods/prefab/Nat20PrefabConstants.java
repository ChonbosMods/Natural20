package com.chonbosmods.prefab;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Set;

/**
 * Resolves and caches the block IDs for Nat20 prefab marker blocks and the
 * vanilla authoring/spawner blocks we want to strip when pasting prefabs at
 * runtime. Must be initialized by calling {@link #resolve()} after the asset
 * pack has loaded (from {@code Natural20.setup()} after asset registration).
 *
 * <p>Fail-fast semantics: if any Nat20_* marker fails to resolve, resolution
 * throws {@link IllegalStateException}: that signals our asset pack failed to
 * register its own blocks. Vanilla keys (Editor_*, *_Spawner*) are allowed to
 * be missing: they are logged as warnings and skipped.
 */
public final class Nat20PrefabConstants {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PrefabMarkers");

    /** The 14 keys whose IDs should be stripped when pasting prefabs at runtime. */
    public static final Set<String> STRIP_KEYS = Set.of(
            "Nat20_Anchor",
            "Nat20_Direction",
            "Nat20_Npc_Spawn",
            "Nat20_Mob_Group_Spawn",
            "Nat20_Chest_Spawn",
            "Nat20_Force_Empty",
            "Editor_Anchor",
            "Editor_Block",
            "Editor_Empty",
            "Prefab_Spawner_Block",
            "Spawner_Rat",
            "Block_Spawner_Block",
            "Block_Spawner_Block_Large",
            "Geyzer_Spawner1"
    );

    // Resolved IDs for the six Nat20 marker blocks.
    public static int anchorId = Integer.MIN_VALUE;
    public static int directionId = Integer.MIN_VALUE;
    public static int npcSpawnId = Integer.MIN_VALUE;
    public static int mobGroupSpawnId = Integer.MIN_VALUE;
    public static int chestSpawnId = Integer.MIN_VALUE;
    public static int forceEmptyId = Integer.MIN_VALUE;

    /** All resolved IDs for the 14 strip keys (markers + vanilla authoring/spawners). */
    public static IntSet stripIds = new IntOpenHashSet();

    private Nat20PrefabConstants() {
        // utility class
    }

    /**
     * Resolve all marker block IDs via the {@link BlockType} asset map. Call
     * once, after the asset pack has loaded.
     *
     * @throws IllegalStateException if any {@code Nat20_*} marker block is not
     *         registered (indicates our asset pack failed to load correctly).
     */
    public static void resolve() {
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();

        anchorId = requireNat20Id(map, "Nat20_Anchor");
        directionId = requireNat20Id(map, "Nat20_Direction");
        npcSpawnId = requireNat20Id(map, "Nat20_Npc_Spawn");
        mobGroupSpawnId = requireNat20Id(map, "Nat20_Mob_Group_Spawn");
        chestSpawnId = requireNat20Id(map, "Nat20_Chest_Spawn");
        forceEmptyId = requireNat20Id(map, "Nat20_Force_Empty");

        IntOpenHashSet ids = new IntOpenHashSet();
        for (String key : STRIP_KEYS) {
            int id = map.getIndex(key);
            if (id == Integer.MIN_VALUE) {
                if (key.startsWith("Nat20_")) {
                    // Should have been caught above, but be defensive.
                    throw new IllegalStateException(
                            "Required Nat20 marker block not registered: " + key);
                }
                LOGGER.atWarning().log(
                        "Vanilla prefab-strip block not registered (skipping): %s", key);
                continue;
            }
            ids.add(id);
        }
        stripIds = ids;

        LOGGER.atInfo().log(
                "Resolved Nat20 prefab markers: anchor=%d direction=%d npcSpawn=%d "
                        + "mobGroupSpawn=%d chestSpawn=%d forceEmpty=%d (stripIds=%d)",
                anchorId, directionId, npcSpawnId,
                mobGroupSpawnId, chestSpawnId, forceEmptyId, stripIds.size());
    }

    private static int requireNat20Id(BlockTypeAssetMap<String, BlockType> map, String key) {
        int id = map.getIndex(key);
        if (id == Integer.MIN_VALUE) {
            throw new IllegalStateException(
                    "Required Nat20 marker block not registered: " + key);
        }
        return id;
    }
}
