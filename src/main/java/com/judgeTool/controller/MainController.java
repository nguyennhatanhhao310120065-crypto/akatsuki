package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.util.ConfigStore;
import com.judgeTool.util.UiAlerts;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.util.Optional;

public class MainController {

    @FXML
    private TabPane tabPane;

    @FXML
    public void initialize() {
        AppContext.get().setMainTabPane(tabPane);
    }

    @FXML
    private void openSettings() {
        PasswordField pf = new PasswordField();
        pf.setPromptText("GEMINI_API_KEY");
        String existing = ConfigStore.getGeminiApiKey();
        if (existing != null && !existing.isBlank()) {
            pf.setText(existing);
        }
        javafx.scene.control.Dialog<Pair<String, String>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Cài đặt Gemini API key");
        dialog.setHeaderText("Key được lưu cục bộ (Java Preferences). Biến môi trường GEMINI_API_KEY vẫn được ưu tiên.");
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("API key:"), 0, 0);
        grid.add(pf, 1, 0);
        pane.setContent(grid);
        Platform.runLater(pf::requestFocus);
        dialog.setResultConverter(btn -> btn == javafx.scene.control.ButtonType.OK ? new Pair<>("", pf.getText()) : null);
        dialog.setOnShown(e -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage st) {
                st.setMinWidth(420);
            }
        });
        Optional<Pair<String, String>> r = dialog.showAndWait();
        r.ifPresent(p -> ConfigStore.setGeminiApiKey(p.getValue()));
    }

    @FXML
    private void exitApp() {
        Platform.exit();
    }
}
