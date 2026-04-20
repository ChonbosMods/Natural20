package com.chonbosmods.prefab;

import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Minimal {@link IPrefabBuffer} for unit-testing scanners. Holds a list of
 * pre-seeded block cells and replays them through the {@code BlockConsumer}
 * passed to {@link #forEach}. Other callbacks (entities, children) are ignored.
 * Methods not exercised by Nat20 scanner tests throw
 * {@link UnsupportedOperationException}.
 */
final class FakePrefabBuffer implements IPrefabBuffer {

    record Cell(int x, int y, int z, int blockId) {}

    private final List<Cell> cells;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    FakePrefabBuffer(List<Cell> cells) {
        this.cells = List.copyOf(cells);
        this.minX = cells.stream().mapToInt(Cell::x).min().orElse(0);
        this.minY = cells.stream().mapToInt(Cell::y).min().orElse(0);
        this.minZ = cells.stream().mapToInt(Cell::z).min().orElse(0);
        this.maxX = cells.stream().mapToInt(Cell::x).max().orElse(0);
        this.maxY = cells.stream().mapToInt(Cell::y).max().orElse(0);
        this.maxZ = cells.stream().mapToInt(Cell::z).max().orElse(0);
    }

    @Override
    public int getAnchorX() {
        return 0;
    }

    @Override
    public int getAnchorY() {
        return 0;
    }

    @Override
    public int getAnchorZ() {
        return 0;
    }

    @Override
    public int getMinX(@Nonnull PrefabRotation rotation) {
        return minX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMinZ(@Nonnull PrefabRotation rotation) {
        return minZ;
    }

    @Override
    public int getMaxX(@Nonnull PrefabRotation rotation) {
        return maxX;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public int getMaxZ(@Nonnull PrefabRotation rotation) {
        return maxZ;
    }

    @Override
    public int getMinYAt(@Nonnull PrefabRotation rotation, int x, int z) {
        throw new UnsupportedOperationException("getMinYAt unused by tests");
    }

    @Override
    public int getMaxYAt(@Nonnull PrefabRotation rotation, int x, int z) {
        throw new UnsupportedOperationException("getMaxYAt unused by tests");
    }

    @Override
    public int getColumnCount() {
        return cells.size();
    }

    @Nonnull
    @Override
    public PrefabBuffer.ChildPrefab[] getChildPrefabs() {
        return new PrefabBuffer.ChildPrefab[0];
    }

    @Override
    public <T extends PrefabBufferCall> void forEach(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull BlockConsumer<T> blockConsumer,
            @Nullable EntityConsumer<T> entityConsumer,
            @Nullable ChildConsumer<T> childConsumer,
            @Nonnull T call
    ) {
        for (Cell c : cells) {
            // signature: (x, y, z, blockId, holder, supportValue, rotation, filler, call, fluidId, fluidLevel)
            blockConsumer.accept(c.x, c.y, c.z, c.blockId, null, 0, 0, 0, call, 0, 0);
        }
    }

    @Override
    public <T> void forEachRaw(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull RawBlockConsumer<T> rawBlockConsumer,
            @Nonnull FluidConsumer<T> fluidConsumer,
            @Nullable EntityConsumer<T> entityConsumer,
            @Nullable T call
    ) {
        throw new UnsupportedOperationException("forEachRaw unused by tests");
    }

    @Override
    public <T> boolean forEachRaw(
            @Nonnull ColumnPredicate<T> columnPredicate,
            @Nonnull RawBlockPredicate<T> rawBlockPredicate,
            @Nonnull FluidPredicate<T> fluidPredicate,
            @Nullable EntityPredicate<T> entityPredicate,
            @Nullable T call
    ) {
        throw new UnsupportedOperationException("forEachRaw (predicate) unused by tests");
    }

    @Override
    public void release() {
        // no-op
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        throw new UnsupportedOperationException("getBlockId unused by tests");
    }

    @Override
    public int getFiller(int x, int y, int z) {
        throw new UnsupportedOperationException("getFiller unused by tests");
    }

    @Override
    public int getRotationIndex(int x, int y, int z) {
        throw new UnsupportedOperationException("getRotationIndex unused by tests");
    }
}
