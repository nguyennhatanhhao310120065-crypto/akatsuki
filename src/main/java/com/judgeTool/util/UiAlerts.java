package com.judgeTool.util;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

public final class UiAlerts {
    private UiAlerts() {
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Lỗi");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Thông báo");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    public static void infoLarge(String title, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(18);
        ta.setPrefColumnCount(60);
        a.getDialogPane().setContent(ta);
        a.setResizable(true);
        a.showAndWait();
    }
}
