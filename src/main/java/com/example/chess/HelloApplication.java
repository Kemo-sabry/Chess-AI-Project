package com.example.chess;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) {
        GameSettings.SettingsResult settings = GameSettings.showAndWait(primaryStage);
        if (settings == null) {
            Platform.exit();
            return;
        }

        ChessBoard board = new ChessBoard(settings);
        Stage gameStage = new Stage();
        gameStage.setTitle("Chess - " + (settings.playMode == GameSettings.PlayMode.PVP ? "Player vs Player" :
                settings.playMode == GameSettings.PlayMode.PVAI ? "Player vs AI" : "AI vs AI"));
        Scene scene = new Scene(board.getRoot(), ChessBoard.BOARD_PX + 20, ChessBoard.BOARD_PX + 20);
        gameStage.setScene(scene);
        gameStage.show();

        // start AI if needed (handled inside ChessBoard)
    }

    public static void main(String[] args) {
        launch(args);
    }
}

