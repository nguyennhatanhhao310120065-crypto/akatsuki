package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Checker;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Testcase;
import com.judgeTool.util.UiAlerts;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.util.List;

public class CheckerController {

    @FXML
    private ComboBox<Problem> problemCombo;
    @FXML
    private ToggleButton toggleExact;
    @FXML
    private ToggleButton toggleSpecial;
    @FXML
    private TextArea checkerCode;
    @FXML
    private ProgressIndicator aiProgress;

    private ToggleGroup modeGroup;

    @FXML
    public void initialize() {
        modeGroup = new ToggleGroup();
        toggleExact.setToggleGroup(modeGroup);
        toggleSpecial.setToggleGroup(modeGroup);
        toggleExact.setSelected(true);
        reloadProblems();
        problemCombo.setOnShowing(e -> reloadProblems());
        problemCombo.setOnAction(e -> loadChecker());
        problemCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Problem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#" + item.getId() + " — " + item.getTitle());
            }
        });
        problemCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Problem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#" + item.getId() + " — " + item.getTitle());
            }
        });
    }

    private void reloadProblems() {
        try {
            List<Problem> list = AppContext.get().database.listProblems(null);
            problemCombo.setItems(FXCollections.observableArrayList(list));
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    private void loadChecker() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            checkerCode.clear();
            return;
        }
        try {
            Checker c = AppContext.get().database.findCheckerByProblem(p.getId());
            if (c != null) {
                checkerCode.setText(c.getCheckerCode());
                if ("special_judge".equalsIgnoreCase(c.getCheckerType())) {
                    toggleSpecial.setSelected(true);
                } else {
                    toggleExact.setSelected(true);
                }
            } else {
                checkerCode.clear();
                toggleExact.setSelected(true);
            }
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void genAi() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        aiProgress.setVisible(true);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                Problem full = AppContext.get().database.findProblem(p.getId());
                if (full == null) {
                    throw new IllegalStateException("Không tìm thấy đề");
                }
                return AppContext.get().gemini.generateChecker(full);
            }
        };
        task.setOnSucceeded(e -> {
            aiProgress.setVisible(false);
            checkerCode.setText(task.getValue());
            toggleSpecial.setSelected(true);
        });
        task.setOnFailed(e -> {
            aiProgress.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi");
        });
        new Thread(task, "gemini-checker").start();
    }

    @FXML
    private void saveChecker() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        try {
            Checker ch = new Checker();
            ch.setProblemId(p.getId());
            ch.setCheckerCode(checkerCode.getText() != null ? checkerCode.getText() : "");
            ch.setCheckerType(toggleSpecial.isSelected() ? "special_judge" : "exact");
            AppContext.get().database.upsertChecker(ch);
            UiAlerts.info("Đã lưu checker.");
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void testChecker() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        try {
            List<Testcase> tcs = AppContext.get().database.listTestcases(p.getId());
            if (tcs.isEmpty()) {
                UiAlerts.info("Chưa có testcase.");
                return;
            }
            Testcase tc = tcs.get(0);
            if (toggleExact.isSelected()) {
                UiAlerts.info("Chế độ exact match: so khớp output trực tiếp khi chạy code ở tab \"Kiểm thử code\". Không cần script checker.");
                return;
            }
            Checker ch = new Checker();
            ch.setProblemId(p.getId());
            ch.setCheckerCode(checkerCode.getText() != null ? checkerCode.getText() : "");
            ch.setCheckerType("special_judge");
            boolean ok = AppContext.get().codeRunner.smokeTestSpecialChecker(ch, tc);
            UiAlerts.info(ok
                    ? "Smoke test OK: checker chấp nhận output trùng expected trên testcase #" + tc.getId() + "."
                    : "Smoke test thất bại: checker từ chối expected output (hoặc script lỗi) trên testcase #" + tc.getId() + ".");
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
