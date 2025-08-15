package com.example.chess;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GameSettings {
    public enum PlayMode { PVP, PVAI, AIVAI }
    public enum AIType { RANDOM, ALPHABETA }

    public static class SettingsResult {
        public PlayMode playMode;
        public AIType aiType;
        public int depth; // used for PVAI single AI (plies)
        public boolean playerIsWhite;
        public boolean aiPlaysWhite = true;

        // new: depths for white/black AI when AIVAI
        public int whiteAIDepth = 4;
        public int blackAIDepth = 2;
    }

    public static SettingsResult showAndWait(Stage owner) {
        final SettingsResult[] out = new SettingsResult[1];

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Game Settings");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbPVP = new RadioButton("Player vs Player");
        RadioButton rbPVAI = new RadioButton("Player vs AI");
        RadioButton rbAIVAI = new RadioButton("AI vs AI");
        rbPVP.setToggleGroup(modeGroup);
        rbPVAI.setToggleGroup(modeGroup);
        rbAIVAI.setToggleGroup(modeGroup);
        rbPVAI.setSelected(true);

        ToggleGroup aiGroup = new ToggleGroup();
        RadioButton aiRandom = new RadioButton("Random AI");
        RadioButton aiAlpha = new RadioButton("Alpha-Beta AI");
        aiRandom.setToggleGroup(aiGroup);
        aiAlpha.setToggleGroup(aiGroup);
        aiAlpha.setSelected(true);

        Label depthLabel = new Label("Alpha-Beta Depth (plies):");
        TextField depthField = new TextField("4");
        depthField.setMaxWidth(80);
        HBox depthBox = new HBox(6, depthLabel, depthField);
        depthBox.setAlignment(Pos.CENTER_LEFT);

        // new: AIVAI specific depths
        Label whiteDepthLabel = new Label("White AI depth:");
        TextField whiteDepthField = new TextField("4");
        whiteDepthField.setMaxWidth(80);
        Label blackDepthLabel = new Label("Black AI depth:");
        TextField blackDepthField = new TextField("2");
        blackDepthField.setMaxWidth(80);

        HBox aiDepthBox = new HBox(8, new VBox(4, whiteDepthLabel, whiteDepthField), new VBox(4, blackDepthLabel, blackDepthField));
        aiDepthBox.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup colorGroup = new ToggleGroup();
        RadioButton plWhite = new RadioButton("Player: White (first)");
        RadioButton plBlack = new RadioButton("Player: Black (second)");
        plWhite.setToggleGroup(colorGroup);
        plBlack.setToggleGroup(colorGroup);
        plWhite.setSelected(true);

        VBox left = new VBox(8, new Label("Play mode:"), rbPVP, rbPVAI, rbAIVAI);
        VBox right = new VBox(8, new Label("AI type:"), aiRandom, aiAlpha, depthBox, new Label("Player color (PVAI):"), plWhite, plBlack, new Label("AI vs AI depths:"), aiDepthBox);
        left.setPadding(new Insets(8));
        right.setPadding(new Insets(8));

        HBox main = new HBox(12, left, right);
        Button startBtn = new Button("Start");
        Button cancelBtn = new Button("Cancel");
        HBox buttons = new HBox(10, startBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox root = new VBox(12, main, buttons);
        root.setPadding(new Insets(12));

        // Control enabling/disabling
        modeGroup.selectedToggleProperty().addListener((obs, oldv, newv) -> {
            if (newv == rbPVP) {
                aiRandom.setDisable(true); aiAlpha.setDisable(true); depthField.setDisable(true); plWhite.setDisable(true); plBlack.setDisable(true);
                whiteDepthField.setDisable(true); blackDepthField.setDisable(true);
            } else if (newv == rbPVAI) {
                aiRandom.setDisable(false); aiAlpha.setDisable(false); depthField.setDisable(false); plWhite.setDisable(false); plBlack.setDisable(false);
                whiteDepthField.setDisable(true); blackDepthField.setDisable(true);
            } else {
                aiRandom.setDisable(false); aiAlpha.setDisable(false); depthField.setDisable(true); plWhite.setDisable(true); plBlack.setDisable(true);
                whiteDepthField.setDisable(false); blackDepthField.setDisable(false);
            }
        });
        rbPVAI.fire();

        startBtn.setOnAction(e -> {
            SettingsResult res = new SettingsResult();
            if (rbPVP.isSelected()) res.playMode = PlayMode.PVP;
            else if (rbPVAI.isSelected()) res.playMode = PlayMode.PVAI;
            else res.playMode = PlayMode.AIVAI;

            res.aiType = aiAlpha.isSelected() ? AIType.ALPHABETA : AIType.RANDOM;

            int d = 4;
            try { d = Integer.parseInt(depthField.getText().trim()); if (d < 1) d = 1; } catch (Exception ex) { d = 4; }
            res.depth = d;
            res.playerIsWhite = plWhite.isSelected();
            res.aiPlaysWhite = true;

            int wd = 4, bd = 2;
            try { wd = Integer.parseInt(whiteDepthField.getText().trim()); if (wd < 1) wd = 1; } catch (Exception ex) { wd = 4; }
            try { bd = Integer.parseInt(blackDepthField.getText().trim()); if (bd < 1) bd = 1; } catch (Exception ex) { bd = 2; }
            res.whiteAIDepth = wd;
            res.blackAIDepth = bd;

            out[0] = res;
            stage.close();
        });

        cancelBtn.setOnAction(e -> {
            out[0] = null;
            stage.close();
        });

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
        return out[0];
    }
}
