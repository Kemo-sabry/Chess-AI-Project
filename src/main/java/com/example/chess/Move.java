package com.example.chess;


public class Move {
    public final int fromRow, fromCol, toRow, toCol;
    public Piece.PieceType promotion; // null if none

    public Move(int fr, int fc, int tr, int tc) {
        this.fromRow = fr; this.fromCol = fc; this.toRow = tr; this.toCol = tc;
        this.promotion = null;
    }

    public Move(int fr, int fc, int tr, int tc, Piece.PieceType promo) {
        this(fr, fc, tr, tc);
        this.promotion = promo;
    }

    @Override
    public String toString() {
        return "[" + fromRow + "," + fromCol + "]->[" + toRow + "," + toCol + "]" + (promotion!=null ? "="+promotion : "");
    }
}

