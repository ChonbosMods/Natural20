package com.chonbosmods.prefab;

import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps an {@link IPrefabBuffer} and intercepts the block callback during
 * {@link #forEach} to apply Nat20-specific filtering:
 *
 * <ul>
 *     <li>Cells whose blockId is in
 *         {@link Nat20PrefabConstants#stripIds()} (the six Nat20 markers plus
 *         vanilla editor/spawner blocks) are dropped: the downstream callback
 *         is not invoked.</li>
 *     <li>Cells with blockId 0 and fluidId 0 (plain Empty passthrough) are
 *         dropped: "leave the existing world cell alone" semantics. Empty
 *         cells with a fluid are still forwarded so the fluid is painted.</li>
 *     <li>Cells whose blockId equals {@link Nat20PrefabConstants#forceEmptyId}
 *         (the authored {@code Nat20_Force_Empty} marker) are rewritten to
 *         blockId 0 before the downstream callback fires: this lets the
 *         vanilla {@code PrefabUtil.paste} force-overwrite the world cell to
 *         air.</li>
 *     <li>Everything else forwards through unchanged, preserving holder,
 *         support value, rotation, filler, fluid id, and fluid level.</li>
 * </ul>
 *
 * <p>Only the block callback is filtered. Entity and child-prefab callbacks
 * are forwarded to the inner buffer as-is. Every other {@link IPrefabBuffer}
 * method delegates straight through to the wrapped buffer.
 *
 * <p>This wrapper is read-only: it never writes back into the inner buffer.
 * Callers must still invoke {@link #release()} when done, which propagates to
 * the inner buffer.
 */
public final class Nat20FilteredBuffer implements IPrefabBuffer {

    private final IPrefabBuffer inner;

    public Nat20FilteredBuffer(IPrefabBuffer inner) {
        this.inner = inner;
    }

    @Override
    public int getAnchorX() {
        return inner.getAnchorX();
    }

    @Override
    public int getAnchorY() {
        return inner.getAnchorY();
    }

    @Override
    public int getAnchorZ() {
        return inner.getAnchorZ();
    }

    @Override
    public int getMinX(@Nonnull PrefabRotation rotation) {
        return inner.getMinX(rotation);
    }

    @Override
    public int getMinY() {
        return inner.getMinY();
    }

    @Override
    public int getMinZ(@Nonnull PrefabRotation rotation) {
        return inner.getMinZ(rotation);
    }

    @Override
    public int getMaxX(@Nonnull PrefabRotation rotation) {
        return inner.getMaxX(rotation);
    }

    @Override
    public int getMaxY() {
        return inner.getMaxY();
    }

    @Override
    public int getMaxZ(@Nonnull PrefabRotation rotation) {
        return inner.getMaxZ(rotation);
    }

    @Override
    public int getMinYAt(@Nonnull PrefabRotation rotation, int x, int z) {
        return inner.getMinYAt(rotation, x, z);
    }

    @Override
    public int getMaxYAt(@Nonnull PrefabRotation rotation, int x, int z) {
        return inner.getMaxYAt(rotation, x, z);
    }

    @Override
    public int getColumnCount() {
        return inner.getColumnCount();
    }

    @Nonnull
    @Override
    public PrefabBuffer.ChildPrefab[] getChildPrefabs() {
        return inner.getChildPrefabs();
    }

    @Override
    public <T extends PrefabBufferCall> void forEach(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull BlockConsumer<T> blockConsumer,
            @Nullable EntityConsumer<T> entityConsumer,
            @Nullable ChildConsumer<T> childConsumer,
            @Nonnull T call
    ) {
        inner.forEach(
                columnPredicate,
                (x, y, z, blockId, holder, supportValue, rotation, filler,
                 c, fluidId, fluidLevel) -> {
                    // Drop Nat20 markers and vanilla authoring/spawner blocks.
                    if (Nat20PrefabConstants.stripIds().contains(blockId)) {
                        // Force_Empty + all spawn/anchor/direction Nat20 markers
                        // must still carve their cell to air: rewrite to Empty
                        // and forward so PrefabUtil.paste overwrites the world
                        // cell. Without this, the marker block stays in the
                        // world after the scanner has extracted its position.
                        if (blockId == Nat20PrefabConstants.forceEmptyId
                                || blockId == Nat20PrefabConstants.anchorId
                                || blockId == Nat20PrefabConstants.directionId
                                || blockId == Nat20PrefabConstants.npcSpawnId
                                || blockId == Nat20PrefabConstants.mobGroupSpawnId) {
                            blockConsumer.accept(x, y, z, 0, holder,
                                    supportValue, rotation, filler,
                                    c, fluidId, fluidLevel);
                        }
                        return;
                    }
                    // Drop plain Empty (blockId 0 with no fluid): "leave the
                    // existing world cell alone" passthrough. Empty with a
                    // fluid is kept so the fluid is still painted.
                    if (blockId == 0 && fluidId == 0) {
                        return;
                    }
                    blockConsumer.accept(x, y, z, blockId, holder,
                            supportValue, rotation, filler,
                            c, fluidId, fluidLevel);
                },
                entityConsumer,
                childConsumer,
                call
        );
    }

    @Override
    public <T> void forEachRaw(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull RawBlockConsumer<T> rawBlockConsumer,
            @Nonnull FluidConsumer<T> fluidConsumer,
            @Nullable EntityConsumer<T> entityConsumer,
            @Nullable T call
    ) {
        inner.forEachRaw(columnPredicate, rawBlockConsumer, fluidConsumer,
                entityConsumer, call);
    }

    @Override
    public <T> boolean forEachRaw(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull RawBlockPredicate<T> rawBlockPredicate,
            @Nonnull FluidPredicate<T> fluidPredicate,
            @Nullable EntityPredicate<T> entityPredicate,
            @Nullable T call
    ) {
        return inner.forEachRaw(columnPredicate, rawBlockPredicate,
                fluidPredicate, entityPredicate, call);
    }

    @Override
    public void release() {
        inner.release();
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return inner.getBlockId(x, y, z);
    }

    @Override
    public int getFiller(int x, int y, int z) {
        return inner.getFiller(x, y, z);
    }

    @Override
    public int getRotationIndex(int x, int y, int z) {
        return inner.getRotationIndex(x, y, z);
    }
}
