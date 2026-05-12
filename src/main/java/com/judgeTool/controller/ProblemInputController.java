package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Problem;
import com.judgeTool.util.UiAlerts;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

public class ProblemInputController {

    @FXML
    private TextArea rawStatement;
    @FXML
    private ComboBox<String> contestCombo;
    @FXML
    private TextField timeLimit;
    @FXML
    private TextField memoryLimit;
    @FXML
    private TextField previewTitle;
    @FXML
    private TextArea previewConstraints;
    @FXML
    private TextArea previewIn;
    @FXML
    private TextArea previewOut;
    @FXML
    private ProgressIndicator aiProgress;

    private Problem current;

    @FXML
    public void initialize() {
        contestCombo.setItems(FXCollections.observableArrayList("IOI", "ICPC", "CF", "other"));
        contestCombo.getSelectionModel().select("ICPC");
    }

    @FXML
    private void pickImage() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Chọn ảnh đề");
        ch.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.tif", "*.bmp"));
        File f = ch.showOpenDialog(rawStatement.getScene().getWindow());
        if (f == null) {
            return;
        }
        try {
            String text = AppContext.get().ocr.extractTextFromImage(Path.of(f.toURI()));
            rawStatement.setText(text);
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void analyzeAi() {
        String raw = rawStatement.getText();
        if (raw == null || raw.isBlank()) {
            UiAlerts.info("Nhập hoặc dán nội dung đề trước.");
            return;
        }
        String hint = contestCombo.getSelectionModel().getSelectedItem();
        aiProgress.setVisible(true);
        Task<Problem> task = new Task<>() {
            @Override
            protected Problem call() throws Exception {
                return AppContext.get().gemini.analyzeProblem(raw, hint);
            }
        };
        task.setOnSucceeded(e -> {
            aiProgress.setVisible(false);
            current = task.getValue();
            applyLimitsFromFields(current);
            previewTitle.setText(current.getTitle());
            previewConstraints.setText(current.getConstraints());
            previewIn.setText(current.getInputFormat());
            previewOut.setText(current.getOutputFormat());
            UiAlerts.info("Phân tích xong. Kiểm tra preview rồi bấm Lưu vào CSDL.");
        });
        task.setOnFailed(e -> {
            aiProgress.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi không xác định");
        });
        new Thread(task, "gemini-analyze").start();
    }

    private void applyLimitsFromFields(Problem p) {
        try {
            p.setTimeLimitMs(Integer.parseInt(timeLimit.getText().trim()));
        } catch (NumberFormatException ignored) {
            p.setTimeLimitMs(1000);
        }
        try {
            p.setMemoryLimitMb(Integer.parseInt(memoryLimit.getText().trim()));
        } catch (NumberFormatException ignored) {
            p.setMemoryLimitMb(256);
        }
    }

    @FXML
    private void saveDb() {
        try {
            Problem p = new Problem();
            p.setTitle(previewTitle.getText() != null && !previewTitle.getText().isBlank() ? previewTitle.getText() : "Untitled");
            p.setContestType(contestCombo.getSelectionModel().getSelectedItem() != null
                    ? contestCombo.getSelectionModel().getSelectedItem() : "other");
            p.setStatement(rawStatement.getText());
            p.setConstraints(previewConstraints.getText());
            p.setInputFormat(previewIn.getText());
            p.setOutputFormat(previewOut.getText());
            applyLimitsFromFields(p);
            long id = AppContext.get().database.insertProblem(p);
            p.setId(id);
            current = p;
            Platform.runLater(() -> UiAlerts.info("Đã lưu đề #" + id));
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }
}
