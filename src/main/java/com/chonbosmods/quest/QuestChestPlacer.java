package com.chonbosmods.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Gson;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.bson.BsonDocument;

/**
 * Places a chest at a world position containing a single quest item.
 * Uses 2-pass hydration: settings=7 (fast place), settings=93 (rehydrate state),
 * then setState to attach the container component with the item.
 */
public class QuestChestPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestChest");
    private static final String CHEST_BLOCK_NAME = "Furniture_Dungeon_Chest_Epic";

    public static boolean placeQuestChest(World world, int x, int y, int z,
                                           String itemTypeId, String itemLabel) {
        try {
            // 1. Resolve chest block type
            int blockId = BlockType.getBlockIdOrUnknown(
                (BlockTypeAssetMap) BlockType.getAssetMap(),
                CHEST_BLOCK_NAME,
                "Failed to find chest block '%s'",
                new Object[]{CHEST_BLOCK_NAME}
            );
            BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null) {
                LOGGER.atWarning().log("Could not resolve chest block type: %s", CHEST_BLOCK_NAME);
                return false;
            }

            // 2. Build container component JSON with quest item in slot 0
            JsonObject itemObj = new JsonObject();
            itemObj.add("Id", new JsonPrimitive(itemTypeId));
            itemObj.add("Quantity", new JsonPrimitive(1));
            itemObj.add("Durability", new JsonPrimitive(0.0));
            itemObj.add("MaxDurability", new JsonPrimitive(0.0));
            itemObj.add("OverrideDroppedItemAnimation", new JsonPrimitive(false));

            JsonObject itemsObj = new JsonObject();
            itemsObj.add("0", itemObj);

            JsonObject itemContainer = new JsonObject();
            itemContainer.add("Capacity", new JsonPrimitive(18));
            itemContainer.add("Items", itemsObj);

            JsonObject container = new JsonObject();
            container.add("Custom", new JsonPrimitive(false));
            container.add("AllowViewing", new JsonPrimitive(true));
            container.add("ItemContainer", itemContainer);

            JsonObject comps = new JsonObject();
            comps.add("container", container);

            JsonObject components = new JsonObject();
            components.add("Components", comps);

            // 3. Deserialize to Holder<ChunkStore>
            BsonDocument bson = BsonDocument.parse(new Gson().toJson(components));
            Holder<ChunkStore> holder = ChunkStore.REGISTRY.deserialize(bson);

            // 4. Get chunk (WorldChunk uses world coordinates)
            long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = world.getNonTickingChunk(chunkKey);
            if (chunk == null) {
                LOGGER.atWarning().log("Chunk not loaded at %d, %d for quest chest", x, z);
                return false;
            }

            // 5. Two-pass placement (world coordinates, filler=0)
            int rotationIndex = 0;
            int filler = 0;

            // Pass 1: place block, skip state
            chunk.setBlock(x, y, z, blockId, blockType, rotationIndex, filler, 7);
            // Pass 2: rehydrate state
            chunk.setBlock(x, y, z, blockId, blockType, rotationIndex, filler, 93);
            // Apply container state
            chunk.setState(x, y, z, blockType, rotationIndex, holder);

            LOGGER.atInfo().log("Placed quest chest at %d, %d, %d with item %s (%s)",
                x, y, z, itemTypeId, itemLabel);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to place quest chest at %d, %d, %d", x, y, z);
            return false;
        }
    }
}
