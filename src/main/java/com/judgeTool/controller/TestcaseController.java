package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Testcase;
import com.judgeTool.util.FileUtil;
import com.judgeTool.util.StringUtil;
import com.judgeTool.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class TestcaseController {

    private static final int TC_COUNT_MIN = 1;
    private static final int TC_COUNT_MAX = 200;
    private static final int TC_COUNT_DEFAULT = 8;
    private static final int INPUT_PREVIEW_LEN = 120;

    @FXML
    private ComboBox<Problem> problemCombo;
    @FXML
    private Spinner<Integer> countSpinner;
    @FXML
    private CheckBox chkEdge;
    @FXML
    private CheckBox chkStress;
    @FXML
    private TextArea extraReq;
    @FXML
    private TableView<Testcase> table;
    @FXML
    private TableColumn<Testcase, Long> colId;
    @FXML
    private TableColumn<Testcase, String> colType;
    @FXML
    private TableColumn<Testcase, Boolean> colEdge;
    @FXML
    private TableColumn<Testcase, String> colInPreview;
    @FXML
    private TextArea detailArea;
    @FXML
    private ProgressIndicator aiProgress;

    @FXML
    public void initialize() {
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                TC_COUNT_MIN, TC_COUNT_MAX, TC_COUNT_DEFAULT));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colType.setCellValueFactory(new PropertyValueFactory<>("caseType"));
        colEdge.setCellValueFactory(new PropertyValueFactory<>("edgeCase"));
        colInPreview.setCellValueFactory(tc -> new SimpleStringProperty(
                StringUtil.shorten(tc.getValue().getInputData(), INPUT_PREVIEW_LEN)));
        ProblemCells.install(problemCombo);

        reloadProblems();
        problemCombo.setOnShowing(e -> reloadProblems());
        problemCombo.setOnAction(e -> loadTable());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                detailArea.setText("INPUT:\n" + StringUtil.nullSafe(newV.getInputData())
                        + "\n\nEXPECTED:\n" + StringUtil.nullSafe(newV.getExpectedOutput()));
            }
        });
    }

    public void reloadProblems() {
        try {
            List<Problem> list = AppContext.get().database.listProblems(null);
            problemCombo.setItems(FXCollections.observableArrayList(list));
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    private void loadTable() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            table.setItems(FXCollections.observableArrayList());
            return;
        }
        try {
            List<Testcase> list = AppContext.get().database.listTestcases(p.getId());
            table.setItems(FXCollections.observableArrayList(list));
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void generateAi() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        int count = countSpinner.getValue();
        aiProgress.setVisible(true);
        Task<List<Testcase>> task = new Task<>() {
            @Override
            protected List<Testcase> call() throws Exception {
                Problem full = AppContext.get().database.findProblem(p.getId());
                if (full == null) {
                    throw new IllegalStateException("Không tìm thấy đề trong CSDL");
                }
                return AppContext.get().testcaseGenerator.generate(
                        full, count, chkEdge.isSelected(), chkStress.isSelected(), extraReq.getText());
            }
        };
        task.setOnSucceeded(e -> {
            aiProgress.setVisible(false);
            try {
                for (Testcase t : task.getValue()) {
                    long id = AppContext.get().database.insertTestcase(t);
                    t.setId(id);
                }
                loadTable();
                UiAlerts.info("Đã sinh và lưu " + task.getValue().size() + " testcase.");
            } catch (Exception ex) {
                UiAlerts.error(ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            aiProgress.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi");
        });
        new Thread(task, "gemini-tc").start();
    }

    @FXML
    private void addManual() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        Dialog<Testcase> d = new Dialog<>();
        d.setTitle("Thêm testcase");
        DialogPane pane = d.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea in = new TextArea();
        in.setPromptText("Input");
        TextArea out = new TextArea();
        out.setPromptText("Expected output");
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(new Label("Input"), 0, 0);
        g.add(in, 0, 1);
        g.add(new Label("Output"), 0, 2);
        g.add(out, 0, 3);
        pane.setContent(g);
        d.setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            Testcase t = new Testcase();
            t.setProblemId(p.getId());
            t.setInputData(in.getText());
            t.setExpectedOutput(out.getText());
            t.setCaseType("manual");
            t.setEdgeCase(false);
            return t;
        });
        d.showAndWait().ifPresent(t -> {
            try {
                AppContext.get().database.insertTestcase(t);
                loadTable();
            } catch (Exception ex) {
                UiAlerts.error(ex.getMessage());
            }
        });
    }

    @FXML
    private void edit() {
        Testcase t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            UiAlerts.info("Chọn testcase.");
            return;
        }
        Dialog<Testcase> d = new Dialog<>();
        d.setTitle("Sửa testcase");
        DialogPane pane = d.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea in = new TextArea(t.getInputData());
        TextArea out = new TextArea(t.getExpectedOutput());
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(new Label("Input"), 0, 0);
        g.add(in, 0, 1);
        g.add(new Label("Output"), 0, 2);
        g.add(out, 0, 3);
        pane.setContent(g);
        d.setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            t.setInputData(in.getText());
            t.setExpectedOutput(out.getText());
            return t;
        });
        d.showAndWait().ifPresent(updated -> {
            try {
                AppContext.get().database.updateTestcase(updated);
                loadTable();
            } catch (Exception ex) {
                UiAlerts.error(ex.getMessage());
            }
        });
    }

    @FXML
    private void delete() {
        Testcase t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            UiAlerts.info("Chọn testcase.");
            return;
        }
        try {
            AppContext.get().database.deleteTestcase(t.getId());
            loadTable();
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void exportFiles() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        DirectoryChooser ch = new DirectoryChooser();
        ch.setTitle("Chọn thư mục export");
        File dir = ch.showDialog(table.getScene().getWindow());
        if (dir == null) {
            return;
        }
        try {
            Path root = dir.toPath();
            Path inDir = root.resolve("input");
            Path outDir = root.resolve("output");
            FileUtil.ensureDir(inDir);
            FileUtil.ensureDir(outDir);
            List<Testcase> list = AppContext.get().database.listTestcases(p.getId());
            int i = 1;
            for (Testcase t : list) {
                FileUtil.writeText(inDir.resolve(i + ".in"), StringUtil.nullSafe(t.getInputData()));
                FileUtil.writeText(outDir.resolve(i + ".out"), StringUtil.nullSafe(t.getExpectedOutput()));
                i++;
            }
            UiAlerts.info("Đã export " + list.size() + " cặp file vào:\n" + root);
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }
}
