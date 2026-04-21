package com.chonbosmods.settlement;

/**
 * How a {@link SettlementType} materializes in the world. Two modes:
 *
 * <ul>
 *   <li>{@link FullPlacement}: paste a single authored prefab (current OUTPOST behavior).</li>
 *   <li>{@link PiecePlacement}: paste {@code minPieces..maxPieces} randomly-selected prefabs
 *       from a category pool, spaced around a shared center (TOWN behavior).</li>
 * </ul>
 */
public sealed interface SettlementPlacement permits FullPlacement, PiecePlacement {}
