package com.chonbosmods.loot.chest;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.Set;

/**
 * Stamps native world-generated loot chests as eligible for Nat20 affix injection when their
 * block component first spawns, then leaves them untouched. Actual loot is minted + injected
 * on demand when a player opens the chest (see {@link Nat20ChestOpenInjectionSystem}); this
 * system only records eligibility so that minting does not happen for chests that are
 * generated but never opened.
 *
 * <p>Gates on {@link AddReason#SPAWN} (a newly generated chest, never a disk reload) and
 * {@code droplist != null} (a genuine worldgen loot chest, not player-placed). Runs before
 * the engine's {@code StashSystem} via {@link RootDependency#firstSet()} because StashSystem
 * consumes the droplist at spawn; we must read it first. Cheap: no rolling, no minting, no
 * client traffic — just a position added to {@link Nat20ChestEligibilityRegistry}.
 */
public class Nat20ChestEligibilityStampSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerType;
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType;
    private final ComponentType<ChunkStore, WorldChunk> worldChunkType;
    private final Query<ChunkStore> query;

    private final Nat20ChestEligibilityRegistry registry;

    public Nat20ChestEligibilityStampSystem(Nat20ChestEligibilityRegistry registry) {
        this.itemContainerType = ItemContainerBlock.getComponentType();
        this.blockStateInfoType = BlockModule.BlockStateInfo.getComponentType();
        this.worldChunkType = WorldChunk.getComponentType();
        this.query = Query.and(this.itemContainerType, this.blockStateInfoType);
        this.registry = registry;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        // Run before StashSystem so the droplist is still set when we read it.
        return RootDependency.firstSet();
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason,
                              Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        if (reason != AddReason.SPAWN) return;

        ItemContainerBlock block = store.getComponent(ref, itemContainerType);
        if (block == null || block.getDroplist() == null) return;

        BlockModule.BlockStateInfo info = store.getComponent(ref, blockStateInfoType);
        if (info == null) return;
        WorldChunk chunk = store.getComponent(info.getChunkRef(), worldChunkType);
        if (chunk == null) return;

        int idx = info.getIndex();
        int wx = ChunkUtil.worldCoordFromLocalCoord(chunk.getX(), ChunkUtil.xFromIndex(idx));
        int wy = ChunkUtil.yFromIndex(idx);
        int wz = ChunkUtil.worldCoordFromLocalCoord(chunk.getZ(), ChunkUtil.zFromIndex(idx));

        registry.markEligible(wx, wy, wz);
    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason,
                               Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        // No-op: eligibility is consumed on loot, not on chunk unload.
    }
}
