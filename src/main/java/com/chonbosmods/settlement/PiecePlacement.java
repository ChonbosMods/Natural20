package com.chonbosmods.settlement;

/**
 * Compose a settlement from {@code minPieces..maxPieces} prefabs drawn from a
 * category pool, arranged around a shared center.
 *
 * @param poolCategory subdirectory under {@code Server/Prefabs/Nat20/}
 *                     (e.g. {@code "settlement_pieces"}) that enumerates candidate
 *                     piece prefabs.
 * @param minPieces    inclusive lower bound on piece count per settlement.
 * @param maxPieces    inclusive upper bound on piece count per settlement.
 * @param outerRadius  maximum block distance from the shared center that any
 *                     piece's anchor may land at.
 * @param minSpacing   minimum gap (in blocks, on X or Z) between any two pieces'
 *                     bounding boxes. Pieces whose random position can't respect
 *                     this after the configured retry budget are dropped.
 */
public record PiecePlacement(
    String poolCategory,
    int minPieces,
    int maxPieces,
    int outerRadius,
    int minSpacing
) {
    public PiecePlacement {
        if (minPieces < 1 || maxPieces < minPieces) {
            throw new IllegalArgumentException(
                "Invalid piece count range: [" + minPieces + ".." + maxPieces + "]");
        }
        if (outerRadius < 1) {
            throw new IllegalArgumentException("outerRadius must be >= 1");
        }
        if (minSpacing < 0) {
            throw new IllegalArgumentException("minSpacing must be >= 0");
        }
    }
}
