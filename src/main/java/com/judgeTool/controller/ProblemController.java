package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Problem;
import com.judgeTool.util.UiAlerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonType;

import java.util.List;

public class ProblemController {

    @FXML
    private ComboBox<String> filterContest;
    @FXML
    private TableView<Problem> table;
    @FXML
    private TableColumn<Problem, Long> colId;
    @FXML
    private TableColumn<Problem, String> colTitle;
    @FXML
    private TableColumn<Problem, String> colType;
    @FXML
    private TableColumn<Problem, Integer> colTime;
    @FXML
    private TableColumn<Problem, Integer> colMem;

    private final ObservableList<Problem> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colType.setCellValueFactory(new PropertyValueFactory<>("contestType"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("timeLimitMs"));
        colMem.setCellValueFactory(new PropertyValueFactory<>("memoryLimitMb"));
        table.setItems(rows);
        filterContest.setItems(FXCollections.observableArrayList("", "IOI", "ICPC", "CF", "other"));
        filterContest.getSelectionModel().select(0);
        filterContest.setOnAction(e -> refresh());
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            String f = filterContest.getSelectionModel().getSelectedItem();
            if (f != null && f.isBlank()) {
                f = null;
            }
            List<Problem> list = AppContext.get().database.listProblems(f);
            rows.setAll(list);
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void addNew() {
        AppContext.get().selectTab(1);
    }

    @FXML
    private void edit() {
        Problem p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn một đề để sửa.");
            return;
        }
        Dialog<Problem> dlg = editDialog(p);
        dlg.showAndWait().ifPresent(updated -> {
            try {
                AppContext.get().database.updateProblem(updated);
                refresh();
            } catch (Exception ex) {
                UiAlerts.error(ex.getMessage());
            }
        });
    }

    private Dialog<Problem> editDialog(Problem p) {
        Dialog<Problem> d = new Dialog<>();
        d.setTitle("Sửa đề");
        DialogPane pane = d.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField title = new TextField(p.getTitle());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("IOI", "ICPC", "CF", "other"));
        type.getSelectionModel().select(p.getContestType());
        TextArea stmt = new TextArea(p.getStatement());
        stmt.setPrefRowCount(6);
        TextArea cons = new TextArea(p.getConstraints());
        cons.setPrefRowCount(3);
        TextArea inf = new TextArea(p.getInputFormat());
        inf.setPrefRowCount(3);
        TextArea outf = new TextArea(p.getOutputFormat());
        outf.setPrefRowCount(3);
        TextField tl = new TextField(String.valueOf(p.getTimeLimitMs()));
        TextField mem = new TextField(String.valueOf(p.getMemoryLimitMb()));
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        g.add(new Label("Tiêu đề"), 0, r);
        g.add(title, 1, r++);
        g.add(new Label("Loại"), 0, r);
        g.add(type, 1, r++);
        g.add(new Label("Đề bài"), 0, r);
        g.add(stmt, 1, r++);
        g.add(new Label("Ràng buộc"), 0, r);
        g.add(cons, 1, r++);
        g.add(new Label("Input format"), 0, r);
        g.add(inf, 1, r++);
        g.add(new Label("Output format"), 0, r);
        g.add(outf, 1, r++);
        g.add(new Label("Time (ms)"), 0, r);
        g.add(tl, 1, r++);
        g.add(new Label("Memory (MB)"), 0, r);
        g.add(mem, 1, r++);
        pane.setContent(g);
        d.setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            Problem np = new Problem();
            np.setId(p.getId());
            np.setTitle(title.getText());
            np.setContestType(type.getSelectionModel().getSelectedItem() != null ? type.getSelectionModel().getSelectedItem() : "other");
            np.setStatement(stmt.getText());
            np.setConstraints(cons.getText());
            np.setInputFormat(inf.getText());
            np.setOutputFormat(outf.getText());
            try {
                np.setTimeLimitMs(Integer.parseInt(tl.getText().trim()));
            } catch (NumberFormatException e) {
                np.setTimeLimitMs(p.getTimeLimitMs());
            }
            try {
                np.setMemoryLimitMb(Integer.parseInt(mem.getText().trim()));
            } catch (NumberFormatException e) {
                np.setMemoryLimitMb(p.getMemoryLimitMb());
            }
            np.setCreatedAt(p.getCreatedAt());
            return np;
        });
        return d;
    }

    @FXML
    private void delete() {
        Problem p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn một đề để xóa.");
            return;
        }
        javafx.scene.control.Alert c = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        c.setHeaderText("Xóa đề #" + p.getId() + "?");
        c.setContentText("Mọi testcase, solution và checker liên quan sẽ bị xóa.");
        c.showAndWait().ifPresent(bt -> {
            if (bt == javafx.scene.control.ButtonType.OK) {
                try {
                    AppContext.get().database.deleteProblem(p.getId());
                    refresh();
                } catch (Exception ex) {
                    UiAlerts.error(ex.getMessage());
                }
            }
        });
    }

    @FXML
    private void detail() {
        Problem p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn một đề.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(p.getId()).append("\n");
        sb.append("Tiêu đề: ").append(p.getTitle()).append("\n");
        sb.append("Loại: ").append(p.getContestType()).append("\n");
        sb.append("TL/Mem: ").append(p.getTimeLimitMs()).append(" ms / ").append(p.getMemoryLimitMb()).append(" MB\n\n");
        sb.append("Đề bài:\n").append(p.getStatement()).append("\n\n");
        sb.append("Ràng buộc:\n").append(p.getConstraints()).append("\n\n");
        sb.append("Input:\n").append(p.getInputFormat()).append("\n\n");
        sb.append("Output:\n").append(p.getOutputFormat());
        UiAlerts.infoLarge("Chi tiết đề", sb.toString());
    }
}
