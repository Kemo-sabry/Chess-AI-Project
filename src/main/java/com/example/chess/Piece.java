package com.example.chess;

public class Piece {
    public enum PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

    public PieceType type;
    public boolean isWhite;

    public Piece(PieceType t, boolean white) {
        this.type = t;
        this.isWhite = white;
    }
}
