package com.chonbosmods.dungeon;

public record ConnectionRecord(
    PlacedPiece pieceA, int cellAx, int cellAz, Face faceA,
    PlacedPiece pieceB, int cellBx, int cellBz, Face faceB
) {}
