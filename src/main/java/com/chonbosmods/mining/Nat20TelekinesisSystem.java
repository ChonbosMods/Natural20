package com.chonbosmods.mining;

import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

/**
 * Telekinesis: pulls nearby item entities into the inventory of any player holding a tool with
 * the {@code nat20:telekinesis} affix. Runs per tick on every player entity.
 *
 * <p>This is independent of block-break events, so it handles arbitrary item drops (mob loot,
 * chest spills, other players' drops) — not just drops from blocks the player mined. Shape
 * cascade drops also flow through here after they land on the ground from
 * {@link Nat20ShapeMiningSystem}.
 *
 * <p>The pickup loop mirrors vanilla's {@code PlayerItemEntityPickupSystem}:
 * {@code player.giveItem} returns a transaction with a remainder when the inventory is full,
 * and we handle the partial-stack case. Items with a custom Pickup interaction are skipped
 * to avoid stealing things like traps or special containers.
 */
public class Nat20TelekinesisSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final String AFFIX_ID = "nat20:telekinesis";

    /** Pickup radius in blocks. */
    private static final double RADIUS = 8.0;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;

    public Nat20TelekinesisSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
        this.query = Query.and(Player.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index,
                     ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        ItemStack tool = InventoryComponent.getItemInHand(store, playerRef);
        if (tool == null || tool.isEmpty()) return;

        Nat20LootData lootData = tool.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        boolean hasTelekinesis = false;
        for (RolledAffix a : lootData.getAffixes()) {
            if (AFFIX_ID.equals(a.id())) {
                hasTelekinesis = true;
                break;
            }
        }
        if (!hasTelekinesis) return;

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d playerPos = transform.getPosition();
        if (playerPos == null) return;

        SpatialResource<Ref<EntityStore>, EntityStore> itemSpatial =
            store.getResource(EntityModule.get().getItemSpatialResourceType());
        if (itemSpatial == null) return;
        SpatialStructure<Ref<EntityStore>> spatial = itemSpatial.getSpatialStructure();

        List<Ref<EntityStore>> itemRefs = SpatialResource.getThreadLocalReferenceList();
        spatial.ordered(playerPos, RADIUS, itemRefs);

        for (Ref<EntityStore> itemRef : itemRefs) {
            if (itemRef == null || !itemRef.isValid()) continue;

            ItemComponent ic = store.getComponent(itemRef, ItemComponent.getComponentType());
            if (ic == null) continue;

            ItemStack itemStack = ic.getItemStack();
            if (itemStack == null || itemStack.isEmpty()) continue;

            // Skip items with custom Pickup interactions (e.g. traps, barrels) to match
            // vanilla PlayerItemEntityPickupSystem behavior.
            Item item = itemStack.getItem();
            if (item != null && item.getInteractions() != null
                    && item.getInteractions().get(InteractionType.Pickup) != null) {
                continue;
            }

            ItemStackTransaction tx = player.giveItem(itemStack, playerRef, commandBuffer);
            ItemStack remainder = tx.getRemainder();

            if (ItemStack.isEmpty(remainder)) {
                ic.setRemovedByPlayerPickup(true);
                commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);
                player.notifyPickupItem(playerRef, itemStack, playerPos, commandBuffer);
            } else if (!remainder.equals(itemStack)) {
                int quantity = itemStack.getQuantity() - remainder.getQuantity();
                ic.setItemStack(remainder);
                if (quantity > 0) {
                    player.notifyPickupItem(playerRef, remainder.withQuantity(quantity), playerPos, commandBuffer);
                }
            }
        }
    }
}
