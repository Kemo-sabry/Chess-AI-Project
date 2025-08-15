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

    // selection & highlights
    private int selectedR = -1, selectedC = -1;
    private List<int[]> highlighted = new ArrayList<>();

    // turn
    private boolean whiteToMove = true;

    // settings & AI
    private boolean aiEnabled;
    private boolean aiVsAi;
    private boolean aiPlaysWhite;
    private GameSettings.AIType selectedAIType;
    private int alphaBetaDepth; // used for PVAI
    private boolean useMoveOrdering = true;
    private GameSettings.SettingsResult settings; // keep for AIVAI depths

    // AI vs AI timeline
    private Timeline aiTimeline;
    private volatile boolean aiThinking = false;

    // unicode maps for display
    private static final java.util.Map<Piece.PieceType,String> WHITE_UNI = java.util.Map.of(
            Piece.PieceType.PAWN, "\u2659", Piece.PieceType.KNIGHT, "\u2658", Piece.PieceType.BISHOP, "\u2657",
            Piece.PieceType.ROOK, "\u2656", Piece.PieceType.QUEEN, "\u2655", Piece.PieceType.KING, "\u2654"
    );
    private static final java.util.Map<Piece.PieceType,String> BLACK_UNI = java.util.Map.of(
            Piece.PieceType.PAWN, "\u265F", Piece.PieceType.KNIGHT, "\u265E", Piece.PieceType.BISHOP, "\u265D",
            Piece.PieceType.ROOK, "\u265C", Piece.PieceType.QUEEN, "\u265B", Piece.PieceType.KING, "\u265A"
    );

    // ---------------- constructor ----------------
    public ChessBoard(GameSettings.SettingsResult settings) {
        this.settings = settings;
        applySettings(settings);
        buildUI();
        initStartingPos();
        redrawAll();

        // if PVAI and AI plays first, let it move
        if (aiEnabled && !aiVsAi && aiPlaysWhite && whiteToMove) {
            runAIMoveIfNeeded();
        }

        // start AI vs AI if selected
        if (aiVsAi) {
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

    // ---------------- UI building ----------------
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

    // ---------------- user interaction ----------------
    private void handleUserClick(int r, int c) {
        // prevent user clicks in AI vs AI or during AI turn in PVAI
        if (aiVsAi) return;
        if (aiEnabled && !aiVsAi && whiteToMove == aiPlaysWhite) return;

        Piece clicked = board[r][c];

        if (selectedR == -1) {
            if (clicked != null && clicked.isWhite == whiteToMove) {
                selectedR = r; selectedC = c;
                highlighted = getLegalMovesForUI(r, c);
                highlightSelection(true);
                redrawAll();
            }
            return;
        }

        // deselect if clicked same
        if (selectedR == r && selectedC == c) {
            highlightSelection(false);
            selectedR = selectedC = -1;
            highlighted.clear();
            redrawAll();
            return;
        }

        // try move
        if (isValidMove(selectedR, selectedC, r, c) && isLegalMove(selectedR, selectedC, r, c)) {
            Piece mover = board[selectedR][selectedC];
            board[r][c] = mover;
            board[selectedR][selectedC] = null;

            // promotion
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
        } else {
            // if clicked another own piece -> select it
            if (clicked != null && clicked.isWhite == whiteToMove) {
                selectedR = r; selectedC = c;
                highlighted = getLegalMovesForUI(r, c);
                highlightSelection(true);
                redrawAll();
            } else {
                // invalid -> deselect
                highlightSelection(false);
                selectedR = selectedC = -1;
                highlighted.clear();
                redrawAll();
            }
        }
    }

    // ---------------- AI calls (PVAI) ----------------
    private void runAIMoveIfNeeded() {
        if (!aiEnabled) return;
        if (aiVsAi) return;

        if (aiEnabled && !aiVsAi && whiteToMove == aiPlaysWhite) {
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

    // ---------------- AI vs AI driver ----------------
    private void startAIVsAIGame() {
        // stop existing timeline if any
        if (aiTimeline != null) {
            aiTimeline.stop();
            aiTimeline = null;
        }

        aiTimeline = new Timeline(new KeyFrame(Duration.seconds(0.6), ev -> {
            if (isGameOver()) {
                if (aiTimeline != null) { aiTimeline.stop(); aiTimeline = null; }
                showResultAfterGameOver();
                return;
            }

            if (aiThinking) return; // wait previous

            aiThinking = true;
            Task<Move> t = new Task<>() {
                @Override
                protected Move call() {
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
                    // no move: game over (mate / stalemate)
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
                showAlert("Game over (draw/stalemate).");
            }
        });
    }

    // ---------------- utilities exposed to other classes ----------------
    public List<int[]> getLegalMovesForUI(int fr, int fc) {
        List<int[]> res = new ArrayList<>();
        Piece p = board[fr][fc];
        if (p == null) return res;
        for (int tr=0; tr<BOARD_SQ; tr++) for (int tc=0; tc<BOARD_SQ; tc++) {
            if (isValidMove(fr,fc,tr,tc) && isLegalMove(fr,fc,tr,tc)) res.add(new int[]{tr,tc});
        }
        return res;
    }

    public boolean isValidMove(int fr, int fc, int tr, int tc) {
        if (!inside(fr,fc) || !inside(tr,tc)) return false;
        Piece p = board[fr][fc]; if (p==null) return false;
        if (fr==tr && fc==tc) return false;
        Piece dest = board[tr][tc]; if (dest!=null && dest.isWhite==p.isWhite) return false;

        int dr = tr - fr; int dc = tc - fc;
        switch (p.type) {
            case PAWN: return validPawn(fr,fc,tr,tc,p);
            case KNIGHT: return (Math.abs(dr)==2 && Math.abs(dc)==1) || (Math.abs(dr)==1 && Math.abs(dc)==2);
            case BISHOP: if (Math.abs(dr)!=Math.abs(dc)) return false; return pathClear(fr,fc,tr,tc);
            case ROOK: if (dr!=0 && dc!=0) return false; return pathClear(fr,fc,tr,tc);
            case QUEEN: if (dr==0 || dc==0 || Math.abs(dr)==Math.abs(dc)) return pathClear(fr,fc,tr,tc); return false;
            case KING: return Math.abs(dr)<=1 && Math.abs(dc)<=1;
        }
        return false;
    }

    private boolean validPawn(int fr,int fc,int tr,int tc, Piece p) {
        int dir = p.isWhite ? -1 : 1;
        Piece dest = board[tr][tc];
        int dr = tr - fr; int dc = tc - fc;
        if (dc==0 && dr==dir && dest==null) return true;
        if (dc==0 && dr==2*dir && dest==null) {
            int start = p.isWhite ? 6 : 1;
            if (fr==start && board[fr+dir][fc]==null) return true;
        }
        if (Math.abs(dc)==1 && dr==dir && dest!=null && dest.isWhite!=p.isWhite) return true;
        return false;
    }

    private boolean pathClear(int fr,int fc,int tr,int tc) {
        int dr = Integer.signum(tr-fr), dc = Integer.signum(tc-fc);
        int r = fr + dr, c = fc + dc;
        while (r!=tr || c!=tc) {
            if (board[r][c]!=null) return false;
            r += dr; c += dc;
        }
        return true;
    }

    private boolean inside(int r,int c) { return r>=0 && r<BOARD_SQ && c>=0 && c<BOARD_SQ; }

    public boolean isLegalMove(int fr,int fc,int tr,int tc) {
        Piece mover = board[fr][fc];
        if (mover==null) return false;
        Piece captured = board[tr][tc];
        // simulate
        board[tr][tc] = mover; board[fr][fc] = null;
        boolean kingChecked = isKingInCheck(mover.isWhite);
        // undo simulate
        board[fr][fc] = mover; board[tr][tc] = captured;
        return !kingChecked;
    }

    public boolean isKingInCheck(boolean white) {
        int kr=-1,kc=-1;
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++){
            Piece p = board[r][c];
            if (p!=null && p.isWhite==white && p.type== Piece.PieceType.KING) { kr=r; kc=c; break; }
        }
        if (kr==-1) return false; // no king found (shouldn't happen)
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++){
            Piece attacker = board[r][c];
            if (attacker!=null && attacker.isWhite!=white) {
                if (isValidMove(r,c,kr,kc)) return true;
            }
        }
        return false;
    }

    public boolean isCheckmate(boolean white) {
        if (!isKingInCheck(white)) return false;
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++){
            Piece p = board[r][c];
            if (p!=null && p.isWhite==white) {
                List<Move> mvs = generateAllLegalMovesForPieceForAI(r,c);
                if (!mvs.isEmpty()) return false;
            }
        }
        return true;
    }

    public boolean isGameOver() {
        return generateAllLegalMovesForSide(whiteToMove).isEmpty();
    }

    // ---------------- move generation for AI ----------------
    public List<Move> generateAllLegalMovesForPieceForAI(int fr, int fc) {
        List<Move> out = new ArrayList<>();
        Piece p = board[fr][fc];
        if (p==null) return out;
        for (int tr=0; tr<BOARD_SQ; tr++) for (int tc=0; tc<BOARD_SQ; tc++){
            if (isValidMove(fr,fc,tr,tc) && isLegalMove(fr,fc,tr,tc)) {
                // check promotion possibility -> we leave promotion null (AI will just move and board will auto promote in makeMove)
                out.add(new Move(fr,fc,tr,tc));
            }
        }
        return out;
    }

    public List<Move> generateAllLegalMovesForSide(boolean forWhite) {
        List<Move> out = new ArrayList<>();
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++){
            Piece p = board[r][c];
            if (p!=null && p.isWhite==forWhite) {
                out.addAll(generateAllLegalMovesForPieceForAI(r,c));
            }
        }
        return out;
    }

    // ---------------- accessors & mutators ----------------
    public Piece getPieceAt(int r,int c) { return board[r][c]; }

    public void makeMove(Move mv) {
        Piece moved = board[mv.fromRow][mv.fromCol];
        board[mv.toRow][mv.toCol] = moved;
        board[mv.fromRow][mv.fromCol] = null;
        if (moved!=null && moved.type== Piece.PieceType.PAWN && ((moved.isWhite && mv.toRow==0) || (!moved.isWhite && mv.toRow==7))) {
            board[mv.toRow][mv.toCol] = new Piece(Piece.PieceType.QUEEN, moved.isWhite);
        }
    }

    public void undoMove(Move mv, Piece captured) {
        Piece moved = board[mv.toRow][mv.toCol];
        // move piece back
        board[mv.fromRow][mv.fromCol] = moved;
        // restore captured
        board[mv.toRow][mv.toCol] = captured;
    }

    // ---------------- evaluation ----------------
    public int evaluateBoardSimple() {
        int score = 0;
        for (int r=0;r<BOARD_SQ;r++) for (int c=0;c<BOARD_SQ;c++){
            Piece p = board[r][c];
            if (p!=null) score += (p.isWhite ? 1 : -1) * pieceValue(p.type);
        }
        int wm = generateAllLegalMovesForSide(true).size();
        int bm = generateAllLegalMovesForSide(false).size();
        score += (wm - bm) * 3;
        return score;
    }

    private int pieceValue(Piece.PieceType t) {
        return switch (t) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 20000;
        };
    }

    // ---------------- UI helpers ----------------
    private void highlightSelection(boolean on) {
        if (selectedR<0) return;
        Rectangle rect = (Rectangle) tiles[selectedR][selectedC].getChildren().get(0);
        if (on) { rect.setStroke(Color.RED); rect.setStrokeWidth(3); }
        else rect.setStroke(null);
    }

    private void redrawAll() {
        for (int r = 0; r < BOARD_SQ; r++) {
            for (int c = 0; c < BOARD_SQ; c++) {
                final int rr = r;
                final int cc = c;

                StackPane cell = tiles[rr][cc];
                Rectangle rect = (Rectangle) cell.getChildren().get(0);
                boolean light = (rr + cc) % 2 == 0;
                rect.setFill(light ? Color.web("#f0d9b5") : Color.web("#b58863"));
                rect.setOpacity(1.0);
                rect.setStroke(null);

                if (highlighted.stream().anyMatch(mv -> mv[0] == rr && mv[1] == cc)) {
                    rect.setFill(Color.YELLOW);
                    rect.setOpacity(0.6);
                }

                Piece p = board[rr][cc];
                cell.getChildren().removeIf(n -> n instanceof Label);
                if (p != null) {
                    Label lbl = new Label(p.isWhite ? WHITE_UNI.get(p.type) : BLACK_UNI.get(p.type));
                    lbl.setFont(Font.font(40));
                    cell.getChildren().add(lbl);
                }
            }
        }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Message");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    // ---------------- small helpers ----------------
    public boolean isWhiteToMove() { return whiteToMove; }

    // for external stop if needed
    public void stopAIVsAIGame() {
        if (aiTimeline != null) {
            aiTimeline.stop();
            aiTimeline = null;
        }
    }
}
