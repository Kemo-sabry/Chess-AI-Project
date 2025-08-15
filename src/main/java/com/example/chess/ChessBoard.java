package com.example.chess;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class ChessBoard {

    public static final int BOARD_SQ = 8;
    public static final int TILE_PX = 76;
    public static final int BOARD_PX = BOARD_SQ * TILE_PX;

    private Piece[][] board = new Piece[BOARD_SQ][BOARD_SQ];
    private StackPane[][] tiles = new StackPane[BOARD_SQ][BOARD_SQ];
    private GridPane root;

    // selection
    private int selectedR = -1, selectedC = -1;
    private List<int[]> highlighted = new ArrayList<>();

    // turn
    private boolean whiteToMove = true;

    // settings
    private boolean aiEnabled;
    private boolean aiVsAi;
    private boolean aiPlaysWhite;
    private GameSettings.AIType selectedAIType;
    private int alphaBetaDepth; // used for PVAI (single AI)
    private boolean useMoveOrdering = true;
    private GameSettings.SettingsResult settings; // keep whole settings for AIVAI depths

    // AI vs AI
    private Timeline aiTimeline;
    private volatile boolean aiThinking = false; // to prevent overlap

    // unicode maps
    private static final java.util.Map<Piece.PieceType,String> WHITE_UNI = java.util.Map.of(
            Piece.PieceType.PAWN, "\u2659", Piece.PieceType.KNIGHT, "\u2658", Piece.PieceType.BISHOP, "\u2657",
            Piece.PieceType.ROOK, "\u2656", Piece.PieceType.QUEEN, "\u2655", Piece.PieceType.KING, "\u2654"
    );
    private static final java.util.Map<Piece.PieceType,String> BLACK_UNI = java.util.Map.of(
            Piece.PieceType.PAWN, "\u265F", Piece.PieceType.KNIGHT, "\u265E", Piece.PieceType.BISHOP, "\u265D",
            Piece.PieceType.ROOK, "\u265C", Piece.PieceType.QUEEN, "\u265B", Piece.PieceType.KING, "\u265A"
    );

    public ChessBoard(GameSettings.SettingsResult settings) {
        this.settings = settings;
        applySettings(settings);
        buildUI();
        initStartingPos();
        redrawAll();

        if (aiEnabled && (aiPlaysWhite || aiVsAi) && whiteToMove == aiPlaysWhite) {
            runAIMoveIfNeeded();
        }

        if (aiVsAi) {
            // start automatic AI vs AI driver
            startAIVsAIGame();
        }
    }

    private void applySettings(GameSettings.SettingsResult s) {
        this.settings = s;
        this.aiEnabled = s.playMode != GameSettings.PlayMode.PVP;
        this.aiVsAi = s.playMode == GameSettings.PlayMode.AIVAI;
        this.selectedAIType = s.aiType;
        this.alphaBetaDepth = Math.max(1, s.depth);
        this.aiPlaysWhite = (s.playMode == GameSettings.PlayMode.PVAI) ? !s.playerIsWhite : s.aiPlaysWhite;
        this.useMoveOrdering = true;
    }

    public GridPane getRoot() { return root; }

    private void buildUI() {
        root = new GridPane();
        for (int r=0;r<BOARD_SQ;r++){
            for (int c=0;c<BOARD_SQ;c++){
                Rectangle rect = new Rectangle(TILE_PX, TILE_PX);
                boolean light = (r + c) % 2 == 0;
                rect.setFill(light ? Color.web("#f0d9b5") : Color.web("#b58863"));
                StackPane cell = new StackPane(rect);
                cell.setAlignment(Pos.CENTER);
                final int rr = r, cc = c;
                cell.setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    handleUserClick(rr, cc);
                });
                tiles[r][c] = cell;
                root.add(cell, c, r);
            }
        }
    }

    private void initStartingPos() {
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++) board[r][c] = null;

        // black
        board[0][0] = new Piece(Piece.PieceType.ROOK, false);
        board[0][1] = new Piece(Piece.PieceType.KNIGHT, false);
        board[0][2] = new Piece(Piece.PieceType.BISHOP, false);
        board[0][3] = new Piece(Piece.PieceType.QUEEN, false);
        board[0][4] = new Piece(Piece.PieceType.KING, false);
        board[0][5] = new Piece(Piece.PieceType.BISHOP, false);
        board[0][6] = new Piece(Piece.PieceType.KNIGHT, false);
        board[0][7] = new Piece(Piece.PieceType.ROOK, false);
        for (int i=0;i<8;i++) board[1][i] = new Piece(Piece.PieceType.PAWN, false);

        // white
        board[7][0] = new Piece(Piece.PieceType.ROOK, true);
        board[7][1] = new Piece(Piece.PieceType.KNIGHT, true);
        board[7][2] = new Piece(Piece.PieceType.BISHOP, true);
        board[7][3] = new Piece(Piece.PieceType.QUEEN, true);
        board[7][4] = new Piece(Piece.PieceType.KING, true);
        board[7][5] = new Piece(Piece.PieceType.BISHOP, true);
        board[7][6] = new Piece(Piece.PieceType.KNIGHT, true);
        board[7][7] = new Piece(Piece.PieceType.ROOK, true);
        for (int i=0;i<8;i++) board[6][i] = new Piece(Piece.PieceType.PAWN, true);
    }

    private void handleUserClick(int r, int c) {
        // تجاهل لو دور الـAI (PVAI) أو لو AIvsAI
        if (aiEnabled && !aiVsAi && whiteToMove == aiPlaysWhite) return;
        if (aiVsAi) return; // منع التداخل أثناء AI vs AI

        Piece clicked = board[r][c];

        // لو مفيش قطعة مختارة
        if (selectedR == -1) {
            if (clicked != null && clicked.isWhite == whiteToMove) {
                selectedR = r;
                selectedC = c;
                highlighted = getLegalMovesForUI(r, c);
                highlightSelection(true);
                redrawAll();
            }
            return;
        }

        // لو ضغط على نفس المربع → إلغاء التحديد
        if (selectedR == r && selectedC == c) {
            highlightSelection(false);
            selectedR = selectedC = -1;
            highlighted.clear();
            redrawAll();
            return;
        }

        // محاولة تحريك القطعة
        if (isValidMove(selectedR, selectedC, r, c) && isLegalMove(selectedR, selectedC, r, c)) {
            Piece mover = board[selectedR][selectedC];
            board[r][c] = mover;
            board[selectedR][selectedC] = null;

            // ترقية البيدق إلى ملكة تلقائيًا
            if (mover.type == Piece.PieceType.PAWN && ((mover.isWhite && r == 0) || (!mover.isWhite && r == 7))) {
                board[r][c] = new Piece(Piece.PieceType.QUEEN, mover.isWhite);
            }

            whiteToMove = !whiteToMove;

            if (isCheckmate(whiteToMove)) {
                showAlert((whiteToMove ? "White" : "Black") + " is checkmated!");
            } else if (isKingInCheck(whiteToMove)) {
                showAlert((whiteToMove ? "White" : "Black") + " is in check!");
            }

            highlightSelection(false);
            selectedR = selectedC = -1;
            highlighted.clear();
            redrawAll();

            runAIMoveIfNeeded();
        }
        else {
            // لو ضغط على قطعة تانية من نفس اللون → اختيارها
            if (clicked != null && clicked.isWhite == whiteToMove) {
                selectedR = r;
                selectedC = c;
                highlighted = getLegalMovesForUI(r, c);
                highlightSelection(true);
                redrawAll();
            }
            else {
                // لو الحركة غلط → إلغاء التحديد
                highlightSelection(false);
                selectedR = selectedC = -1;
                highlighted.clear();
                redrawAll();
            }
        }
    }


    private void runAIMoveIfNeeded() {
        if (!aiEnabled) return;
        if (aiVsAi) return; // AIvsAI driven by timeline
        if (aiVsAi || (!aiVsAi && whiteToMove == aiPlaysWhite)) {
            Task<Move> aiTask = new Task<>() {
                @Override
                protected Move call() {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    if (selectedAIType == GameSettings.AIType.RANDOM) {
                        return AI.randomMove(ChessBoard.this, whiteToMove);
                    } else {
                        return AI.alphaBetaMove(ChessBoard.this, whiteToMove, alphaBetaDepth, useMoveOrdering);
                    }
                }
            };

            aiTask.setOnSucceeded(e -> {
                Move mv = aiTask.getValue();
                if (mv != null) {
                    Piece moved = board[mv.fromRow][mv.fromCol];
                    if (moved != null) {
                        board[mv.toRow][mv.toCol] = moved;
                        board[mv.fromRow][mv.fromCol] = null;
                        if (moved.type == Piece.PieceType.PAWN && ((moved.isWhite && mv.toRow == 0) || (!moved.isWhite && mv.toRow == 7))) {
                            board[mv.toRow][mv.toCol] = new Piece(Piece.PieceType.QUEEN, moved.isWhite);
                        }
                        whiteToMove = !whiteToMove;

                        if (isCheckmate(whiteToMove)) {
                            showAlert((whiteToMove ? "White" : "Black") + " is checkmated!");
                        } else if (isKingInCheck(whiteToMove)) {
                            showAlert((whiteToMove ? "White" : "Black") + " is in check!");
                        }
                    }
                }
                redrawAll();
            });

            aiTask.setOnFailed(e -> aiTask.getException().printStackTrace());
            Thread t = new Thread(aiTask);
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------------- AI vs AI ----------------

    private void startAIVsAIGame() {
        // Stop existing timeline if any
        if (aiTimeline != null) {
            aiTimeline.stop();
            aiTimeline = null;
        }

        aiTimeline = new Timeline(new KeyFrame(Duration.seconds(0.6), ev -> {
            if (isGameOver()) {
                // stop timeline and show result
                aiTimeline.stop();
                aiTimeline = null;
                showResultAfterGameOver();
                return;
            }

            if (aiThinking) return; // wait previous task finish

            aiThinking = true;
            Task<Move> t = new Task<>() {
                @Override
                protected Move call() {
                    // choose depth for current player from settings
                    int depthToUse = whiteToMove ? Math.max(1, settings.whiteAIDepth) : Math.max(1, settings.blackAIDepth);
                    if (settings.aiType == GameSettings.AIType.RANDOM) {
                        return AI.randomMove(ChessBoard.this, whiteToMove);
                    } else {
                        return AI.alphaBetaMove(ChessBoard.this, whiteToMove, depthToUse, useMoveOrdering);
                    }
                }
            };

            t.setOnSucceeded(ae -> {
                Move mv = t.getValue();
                if (mv != null) {
                    Piece moved = board[mv.fromRow][mv.fromCol];
                    if (moved != null) {
                        board[mv.toRow][mv.toCol] = moved;
                        board[mv.fromRow][mv.fromCol] = null;
                        if (moved.type == Piece.PieceType.PAWN && ((moved.isWhite && mv.toRow == 0) || (!moved.isWhite && mv.toRow == 7))) {
                            board[mv.toRow][mv.toCol] = new Piece(Piece.PieceType.QUEEN, moved.isWhite);
                        }
                        whiteToMove = !whiteToMove;
                        redrawAll();
                    }
                } else {
                    // no move - game over (mate or stalemate)
                    redrawAll();
                }

                if (isGameOver()) {
                    if (aiTimeline != null) { aiTimeline.stop(); aiTimeline = null; }
                    showResultAfterGameOver();
                }
                aiThinking = false;
            });

            t.setOnFailed(ae -> {
                t.getException().printStackTrace();
                aiThinking = false;
            });

            Thread thread = new Thread(t);
            thread.setDaemon(true);
            thread.start();
        }));

        aiTimeline.setCycleCount(Animation.INDEFINITE);
        aiTimeline.play();
    }

    private void showResultAfterGameOver() {
        Platform.runLater(() -> {
            if (isCheckmate(true)) {
                showAlert("White is checkmated! Black wins.");
            } else if (isCheckmate(false)) {
                showAlert("Black is checkmated! White wins.");
            } else {
                showAlert("Game over (draw / stalemate).");
            }
        });
    }

    // ---------------- end AI vs AI ----------------

    // ... rest of methods (getLegalMovesForUI, isValidMove, isLegalMove, pathClear, etc.)
    // For brevity the rest of the methods are identical to your original implementation:
    // getLegalMovesForUI, isValidMove, validPawn, pathClear, inside, isLegalMove,
    // isKingInCheck, isCheckmate, isGameOver, generateAllLegalMovesForPieceForAI,
    // generateAllLegalMovesForSide, getPieceAt, makeMove, undoMove, evaluateBoardSimple,
    // pieceValue, highlightSelection, redrawAll, showAlert.

    // (Paste here the rest of your original methods unchanged — to keep response concise I omitted re-copying them.
    // Make sure in your actual file you include the methods from your previous ChessBoard.java exactly as they were:
    // getLegalMovesForUI(...), isValidMove(...), validPawn(...), pathClear(...), inside(...),
    // isLegalMove(...), isKingInCheck(...), isCheckmate(...), isGameOver(), generateAllLegalMovesForPieceForAI(...),
    // generateAllLegalMovesForSide(...), getPieceAt(...), makeMove(...), undoMove(...),
    // evaluateBoardSimple(), pieceValue(...), highlightSelection(...), redrawAll(...), showAlert(...).
    //
    // Note: I DID modify runAIMoveIfNeeded earlier to not run during AIVAI; keep that version.
    //
    // )
}
