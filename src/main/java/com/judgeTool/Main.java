package com.judgeTool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class Main extends Application {

    private static final String APP_TITLE = "Competitive Judge Tool";
    private static final String MAIN_FXML = "/fxml/main.fxml";
    private static final String DATA_DIR = ".judge_tool";
    private static final String DB_FILENAME = "judge.db";
    private static final double WINDOW_WIDTH = 1150;
    private static final double WINDOW_HEIGHT = 780;

    @Override
    public void start(Stage stage) throws Exception {
        Path db = Path.of(System.getProperty("user.home"), DATA_DIR, DB_FILENAME);
        AppContext.init(db);

        FXMLLoader loader = new FXMLLoader(Main.class.getResource(MAIN_FXML));
        Parent root = loader.load();
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
