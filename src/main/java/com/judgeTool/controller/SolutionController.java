package com.judgeTool.controller;

import com.judgeTool.AppContext;
import com.judgeTool.model.Checker;
import com.judgeTool.model.JudgeResult;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Solution;
import com.judgeTool.model.Testcase;
import com.judgeTool.model.Verdict;
import com.judgeTool.service.CodeRunnerService;
import com.judgeTool.util.StringUtil;
import com.judgeTool.util.UiAlerts;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class SolutionController {

    private static final int OUTPUT_PREVIEW_LEN = 200;

    private static final String LANG_JAVA = "java";
    private static final String LANG_CPP = "cpp";
    private static final String LANG_PYTHON = "python";

    @FXML
    private ComboBox<Problem> problemCombo;
    @FXML
    private ComboBox<String> langCombo;
    @FXML
    private ComboBox<String> expectCombo;
    @FXML
    private TextArea codeArea;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private ProgressIndicator aiProgress;
    @FXML
    private TableView<RunRow> resultTable;
    @FXML
    private TableColumn<RunRow, Long> colTc;
    @FXML
    private TableColumn<RunRow, String> colVer;
    @FXML
    private TableColumn<RunRow, Integer> colTime;
    @FXML
    private TableColumn<RunRow, String> colOut;

    @FXML
    public void initialize() {
        langCombo.setItems(FXCollections.observableArrayList(LANG_JAVA, LANG_CPP));
        langCombo.getSelectionModel().select(LANG_CPP);
        expectCombo.setItems(FXCollections.observableArrayList(
                Verdict.AC, Verdict.WA, Verdict.TLE, Verdict.RE, Verdict.UNKNOWN));
        expectCombo.getSelectionModel().select(Verdict.AC);

        colTc.setCellValueFactory(new PropertyValueFactory<>("testcaseId"));
        colVer.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("timeMs"));
        colOut.setCellValueFactory(new PropertyValueFactory<>("outputShort"));

        ProblemCells.install(problemCombo);
        reloadProblems();
        problemCombo.setOnShowing(e -> reloadProblems());
    }

    public void reloadProblems() {
        try {
            List<Problem> list = AppContext.get().database.listProblems(null);
            problemCombo.setItems(FXCollections.observableArrayList(list));
        } catch (Exception ex) {
            UiAlerts.error(ex.getMessage());
        }
    }

    @FXML
    private void genAc() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        String lang = langCombo.getSelectionModel().getSelectedItem();
        aiProgress.setVisible(true);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                Problem full = AppContext.get().database.findProblem(p.getId());
                if (full == null) {
                    throw new IllegalStateException("Không tìm thấy đề");
                }
                return AppContext.get().gemini.generateSolutionCode(full, lang);
            }
        };
        task.setOnSucceeded(e -> {
            aiProgress.setVisible(false);
            codeArea.setText(task.getValue());
        });
        task.setOnFailed(e -> {
            aiProgress.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi");
        });
        new Thread(task, "gemini-sol").start();
    }

    @FXML
    private void runAll() {
        Problem p = problemCombo.getSelectionModel().getSelectedItem();
        if (p == null) {
            UiAlerts.info("Chọn đề.");
            return;
        }
        String code = codeArea.getText();
        if (code == null || code.isBlank()) {
            UiAlerts.info("Nhập code.");
            return;
        }
        String lang = langCombo.getSelectionModel().getSelectedItem();
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        resultTable.setItems(FXCollections.observableArrayList());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Problem full = AppContext.get().database.findProblem(p.getId());
                if (full == null) {
                    throw new IllegalStateException("Không tìm thấy đề");
                }
                List<Testcase> tcs = AppContext.get().database.listTestcases(full.getId());
                Checker ch = AppContext.get().database.findCheckerByProblem(full.getId());
                boolean useChecker = ch != null && "special_judge".equalsIgnoreCase(ch.getCheckerType());

                Solution sol = new Solution();
                sol.setProblemId(full.getId());
                sol.setCode(code);
                sol.setLanguage(lang);
                sol.setVerdict(Verdict.UNKNOWN);
                sol.setNote("expect=" + expectCombo.getSelectionModel().getSelectedItem());
                long solId = AppContext.get().database.insertSolution(sol);
                sol.setId(solId);
                AppContext.get().database.deleteJudgeResultsForSolution(solId);

                ObservableList<RunRow> rows = FXCollections.observableArrayList();
                int n = tcs.size();
                int i = 0;
                String aggregate = Verdict.AC;
                for (Testcase tc : tcs) {
                    CodeRunnerService.RunOutcome out = AppContext.get().codeRunner.judgeCode(
                            sol, tc, full, ch, full.getTimeLimitMs(), useChecker);
                    String v = out.verdict();
                    aggregate = worse(aggregate, v);
                    String shortOut = StringUtil.shorten(out.actualOutput(), OUTPUT_PREVIEW_LEN);
                    RunRow row = new RunRow(tc.getId(), v, out.timeMs(), shortOut);
                    Platform.runLater(() -> rows.add(row));

                    JudgeResult jr = new JudgeResult();
                    jr.setSolutionId(solId);
                    jr.setTestcaseId(tc.getId());
                    jr.setVerdict(v);
                    jr.setActualOutput(out.actualOutput());
                    jr.setTimeMs(out.timeMs());
                    jr.setMemoryKb(null);
                    AppContext.get().database.insertJudgeResult(jr);

                    i++;
                    final int done = i;
                    Platform.runLater(() -> progressBar.setProgress((double) done / Math.max(1, n)));
                }
                AppContext.get().database.updateSolutionVerdict(solId, aggregate);
                Platform.runLater(() -> resultTable.setItems(rows));
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            UiAlerts.info("Chạy xong. Verdict tổng hợp đã lưu vào solutions.");
        });
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable t = task.getException();
            UiAlerts.error(t != null ? t.getMessage() : "Lỗi");
        });
        new Thread(task, "judge-run").start();
    }

    private static String worse(String a, String b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(String v) {
        if (v == null) {
            return 0;
        }
        return switch (v) {
            case Verdict.AC -> 1;
            case Verdict.WA -> 2;
            case Verdict.RE -> 3;
            case Verdict.TLE -> 4;
            default -> 2;
        };
    }

    public static class RunRow {
        private final long testcaseId;
        private final String verdict;
        private final int timeMs;
        private final String outputShort;

        public RunRow(long testcaseId, String verdict, int timeMs, String outputShort) {
            this.testcaseId = testcaseId;
            this.verdict = verdict;
            this.timeMs = timeMs;
            this.outputShort = outputShort;
        }

        public long getTestcaseId() {
            return testcaseId;
        }

        public String getVerdict() {
            return verdict;
        }

        public int getTimeMs() {
            return timeMs;
        }

        public String getOutputShort() {
            return outputShort;
        }
    }
}
