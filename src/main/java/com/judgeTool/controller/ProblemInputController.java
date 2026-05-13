package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Problem;
import com.judgeTool.util.UiAlerts;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;

public class ProblemInputController {

    private static final int DEFAULT_TIME_LIMIT_MS = 1000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final String DEFAULT_TITLE = "Untitled";
    private static final String DEFAULT_CONTEST = "other";

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

    @FXML
    public void initialize() {
        contestCombo.setItems(FXCollections.observableArrayList("IOI", "ICPC", "CF", "other"));
        contestCombo.getSelectionModel().select("ICPC");
    }

    @FXML
    private void pickImage() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Chọn ảnh đề");
        ch.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.tif", "*.bmp", "*.webp"));
        File f = ch.showOpenDialog(rawStatement.getScene().getWindow());
        if (f == null) {
            return;
        }
        aiProgress.setVisible(true);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return AppContext.get().ocr.extractTextFromImage(f.toPath());
            }
        };
        task.setOnSucceeded(e -> {
            aiProgress.setVisible(false);
            rawStatement.setText(task.getValue());
        });
        task.setOnFailed(e -> {
            aiProgress.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi OCR");
        });
        new Thread(task, "gemini-ocr").start();
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
            Problem analyzed = task.getValue();
            applyLimitsFromFields(analyzed);
            previewTitle.setText(analyzed.getTitle());
            previewConstraints.setText(analyzed.getConstraints());
            previewIn.setText(analyzed.getInputFormat());
            previewOut.setText(analyzed.getOutputFormat());
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
            p.setTimeLimitMs(DEFAULT_TIME_LIMIT_MS);
        }
        try {
            p.setMemoryLimitMb(Integer.parseInt(memoryLimit.getText().trim()));
        } catch (NumberFormatException ignored) {
            p.setMemoryLimitMb(DEFAULT_MEMORY_LIMIT_MB);
        }
    }

    @FXML
    private void saveDb() {
        try {
            String title = previewTitle.getText();
            String contest = contestCombo.getSelectionModel().getSelectedItem();

            Problem p = new Problem();
            p.setTitle(title != null && !title.isBlank() ? title : DEFAULT_TITLE);
            p.setContestType(contest != null ? contest : DEFAULT_CONTEST);
            p.setStatement(rawStatement.getText());
            p.setConstraints(previewConstraints.getText());
            p.setInputFormat(previewIn.getText());
            p.setOutputFormat(previewOut.getText());
            applyLimitsFromFields(p);

            long id = AppContext.get().database.insertProblem(p);
            p.setId(id);
            UiAlerts.info("Đã lưu đề #" + id);
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }
}
