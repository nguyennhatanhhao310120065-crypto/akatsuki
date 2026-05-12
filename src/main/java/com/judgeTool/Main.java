package com.judgeTool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Path db = Path.of(System.getProperty("user.home"), ".judge_tool", "judge.db");
        AppContext.init(db);

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1150, 780);
        stage.setTitle("Competitive Judge Tool");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
