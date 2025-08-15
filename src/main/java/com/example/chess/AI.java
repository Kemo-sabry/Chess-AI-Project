package com.example.chess;

import java.util.List;
import java.util.Random;
import java.util.Comparator;

public class AI {
    private static final Random RNG = new Random();
    private static final int MATE_SCORE = 1_000_000;

    /** Random agent: returns a Move or null */
    public static Move randomMove(ChessBoard board, boolean forWhite) {
        List<Move> moves = board.generateAllLegalMovesForSide(forWhite);
        if (moves.isEmpty()) return null;
        return moves.get(RNG.nextInt(moves.size()));
    }

    /** Alpha-Beta top-level: returns Move or null */
    public static Move alphaBetaMove(ChessBoard board, boolean forWhite, int depth, boolean useMoveOrdering) {
        List<Move> moves = board.generateAllLegalMovesForSide(forWhite);
        if (moves.isEmpty()) return null;

        // Immediate checkmate search: if any move delivers mate, pick it instantly
        for (Move mv : moves) {
            Piece captured = board.getPieceAt(mv.toRow, mv.toCol);
            board.makeMove(mv);
            boolean oppWhite = !forWhite;
            if (isCheckmate(board, oppWhite)) {
                board.undoMove(mv, captured);
                return mv; // Found mate, take it immediately
            }
            board.undoMove(mv, captured);
        }

        if (useMoveOrdering) {
            moves.sort((m1, m2) -> Integer.compare(moveHeuristic(board, m2, forWhite),
                    moveHeuristic(board, m1, forWhite)));
        }

        Move best = null;
        int bestScore = forWhite ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move mv : moves) {
            Piece captured = board.getPieceAt(mv.toRow, mv.toCol);
            board.makeMove(mv);
            int score = alphabeta(board, depth - 1, Integer.MIN_VALUE/2, Integer.MAX_VALUE/2, !forWhite, useMoveOrdering, forWhite);
            board.undoMove(mv, captured);

            if (forWhite) {
                if (score > bestScore) { bestScore = score; best = mv; }
            } else {
                if (score < bestScore) { bestScore = score; best = mv; }
            }
        }
        return best;
    }

    private static int alphabeta(ChessBoard board, int depth, int alpha, int beta, boolean currentSideWhite, boolean useMoveOrdering, boolean myColorWhite) {
        if (depth == 0) {
            // Material evaluation scaled down so mate dominates
            return board.evaluateBoardSimple() / 10;
        }

        List<Move> moves = board.generateAllLegalMovesForSide(currentSideWhite);
        if (moves.isEmpty()) {
            if (board.isKingInCheck(currentSideWhite)) {
                // Mate score depends on who gets mated
                return (currentSideWhite == myColorWhite) ? -MATE_SCORE + depth : MATE_SCORE - depth;
            } else {
                return 0; // stalemate
            }
        }

        if (useMoveOrdering) {
            moves.sort((m1, m2) -> Integer.compare(moveHeuristic(board, m2, currentSideWhite),
                    moveHeuristic(board, m1, currentSideWhite)));
        }

        if (currentSideWhite) {
            int value = Integer.MIN_VALUE/2;
            for (Move mv : moves) {
                Piece captured = board.getPieceAt(mv.toRow, mv.toCol);
                board.makeMove(mv);

                // Immediate mate shortcut
                if (isCheckmate(board, !currentSideWhite)) {
                    board.undoMove(mv, captured);
                    return MATE_SCORE - depth;
                }

                int v = alphabeta(board, depth - 1, alpha, beta, false, useMoveOrdering, myColorWhite);
                board.undoMove(mv, captured);

                value = Math.max(value, v);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE/2;
            for (Move mv : moves) {
                Piece captured = board.getPieceAt(mv.toRow, mv.toCol);
                board.makeMove(mv);

                // Immediate mate shortcut
                if (isCheckmate(board, !currentSideWhite)) {
                    board.undoMove(mv, captured);
                    return -MATE_SCORE + depth;
                }

                int v = alphabeta(board, depth - 1, alpha, beta, true, useMoveOrdering, myColorWhite);
                board.undoMove(mv, captured);

                value = Math.min(value, v);
                beta = Math.min(beta, value);
                if (alpha >= beta) break;
            }
            return value;
        }
    }

    /** Check if the given side is checkmated */
    private static boolean isCheckmate(ChessBoard board, boolean sideWhite) {
        if (!board.isKingInCheck(sideWhite)) return false;
        return board.generateAllLegalMovesForSide(sideWhite).isEmpty();
    }

    /** Heuristic to prioritize captures and checks */
    private static int moveHeuristic(ChessBoard board, Move m, boolean sideWhite) {
        int score = captureScore(board, m);
        // Bonus if the move gives check
        Piece captured = board.getPieceAt(m.toRow, m.toCol);
        board.makeMove(m);
        boolean givesCheck = board.isKingInCheck(!sideWhite);
        board.undoMove(m, captured);
        if (givesCheck) score += 5000;
        return score;
    }

    private static int captureScore(ChessBoard board, Move m) {
        Piece cap = board.getPieceAt(m.toRow, m.toCol);
        return cap == null ? 0 : pieceValue(cap.type);
    }

    private static int pieceValue(Piece.PieceType t) {
        return switch (t) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 20000;
        };
    }
}

