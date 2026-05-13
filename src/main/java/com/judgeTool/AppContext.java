package com.judgeTool;

import com.judgeTool.service.CodeRunnerService;
import com.judgeTool.service.DatabaseService;
import com.judgeTool.service.GeminiService;
import com.judgeTool.service.OcrService;
import com.judgeTool.service.TestcaseGeneratorService;
import javafx.scene.control.TabPane;

import java.nio.file.Path;

public final class AppContext {
    private static AppContext instance;

    private TabPane mainTabPane;

    public final DatabaseService database;
    public final GeminiService gemini;
    public final TestcaseGeneratorService testcaseGenerator;
    public final CodeRunnerService codeRunner;
    public final OcrService ocr;

    private AppContext(Path dbFile) throws Exception {
        this.database = new DatabaseService(dbFile);
        this.database.init();
        this.gemini = new GeminiService();
        this.testcaseGenerator = new TestcaseGeneratorService(gemini);
        this.codeRunner = new CodeRunnerService();
        this.ocr = new OcrService(gemini);
    }

    public static void init(Path dbFile) throws Exception {
        instance = new AppContext(dbFile);
    }

    public static AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("AppContext chưa được khởi tạo");
        }
        return instance;
    }

    public void setMainTabPane(TabPane tabPane) {
        this.mainTabPane = tabPane;
    }

    public void selectTab(int index) {
        if (mainTabPane != null && index >= 0 && index < mainTabPane.getTabs().size()) {
            mainTabPane.getSelectionModel().select(index);
        }
    }
}
