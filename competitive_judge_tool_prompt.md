# Competitive Programming Judge Tool — Java Desktop Application

## Mô tả dự án

Xây dựng ứng dụng Java desktop để quản lý đề thi lập trình thi đấu (IOI, ICPC,...), sử dụng Gemini API để phân tích đề, sinh testcase và checker tự động, kèm hệ thống chạy và đánh giá code mẫu.

---

## Tech Stack bắt buộc

| Thành phần | Công nghệ |
|---|---|
| UI | JavaFX (FXML + CSS) |
| Database | SQLite (`sqlite-jdbc`) |
| AI | Google Gemini API (REST) |
| HTTP Client | OkHttp 4.x |
| JSON | Gson |
| Build tool | Maven |
| OCR (tuỳ chọn) | Tesseract qua `tess4j` |
| Java version | 17+ |

---

## Cấu trúc thư mục

```
src/
├── main/
│   ├── java/com/judgeTool/
│   │   ├── Main.java
│   │   ├── controller/
│   │   │   ├── MainController.java
│   │   │   ├── ProblemController.java
│   │   │   ├── TestcaseController.java
│   │   │   └── SolutionController.java
│   │   ├── model/
│   │   │   ├── Problem.java
│   │   │   ├── Testcase.java
│   │   │   ├── Solution.java
│   │   │   └── JudgeResult.java
│   │   ├── service/
│   │   │   ├── DatabaseService.java
│   │   │   ├── GeminiService.java
│   │   │   ├── TestcaseGeneratorService.java
│   │   │   ├── CodeRunnerService.java
│   │   │   └── OcrService.java
│   │   └── util/
│   │       ├── FileUtil.java
│   │       └── ProcessUtil.java
│   └── resources/
│       ├── fxml/
│       │   ├── main.fxml
│       │   ├── problem.fxml
│       │   ├── testcase.fxml
│       │   └── solution.fxml
│       ├── css/style.css
│       └── db/schema.sql
```

---

## Database Schema (SQLite)

```sql
CREATE TABLE IF NOT EXISTS problems (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    title           TEXT    NOT NULL,
    contest_type    TEXT    NOT NULL,   -- 'IOI', 'ICPC', 'CF', 'other'
    statement       TEXT    NOT NULL,
    constraints     TEXT,
    input_format    TEXT,
    output_format   TEXT,
    time_limit      INTEGER DEFAULT 1000,   -- ms
    memory_limit    INTEGER DEFAULT 256,    -- MB
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS testcases (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    problem_id       INTEGER NOT NULL,
    input_data       TEXT    NOT NULL,
    expected_output  TEXT,
    case_type        TEXT    DEFAULT 'generated',  -- 'generated', 'manual', 'stress'
    is_edge_case     INTEGER DEFAULT 0,
    generator_prompt TEXT,
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);

CREATE TABLE IF NOT EXISTS solutions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    problem_id  INTEGER NOT NULL,
    code        TEXT    NOT NULL,
    language    TEXT    NOT NULL,   -- 'java', 'cpp', 'python'
    verdict     TEXT,               -- 'AC', 'WA', 'TLE', 'RE', 'unknown'
    note        TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);

CREATE TABLE IF NOT EXISTS judge_results (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    solution_id   INTEGER NOT NULL,
    testcase_id   INTEGER NOT NULL,
    verdict       TEXT    NOT NULL,   -- 'AC', 'WA', 'TLE', 'RE'
    actual_output TEXT,
    time_ms       INTEGER,
    memory_kb     INTEGER,
    FOREIGN KEY (solution_id) REFERENCES solutions(id),
    FOREIGN KEY (testcase_id) REFERENCES testcases(id)
);

CREATE TABLE IF NOT EXISTS checkers (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    problem_id   INTEGER NOT NULL,
    checker_code TEXT    NOT NULL,
    checker_type TEXT    DEFAULT 'exact',  -- 'exact', 'special_judge', 'interactive'
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);
```

---

## Giao diện JavaFX

```
MainWindow (TabPane)
├── Tab 1: "Quản lý đề"
│   ├── TableView danh sách đề bài
│   ├── Nút: [Thêm mới] [Sửa] [Xóa] [Xem chi tiết]
│   └── ComboBox lọc theo loại kỳ thi (IOI / ICPC / CF)
│
├── Tab 2: "Nhập đề"
│   ├── TextArea lớn để paste text đề
│   ├── Nút: [Chọn file ảnh/PDF] để OCR
│   ├── ComboBox: Loại kỳ thi
│   ├── TextField: Time limit, Memory limit
│   ├── Nút: [Phân tích bằng AI] → gọi Gemini API
│   └── Preview: title, constraints, input/output format
│
├── Tab 3: "Testcase"
│   ├── ComboBox: chọn đề
│   ├── Spinner: số lượng testcase cần sinh
│   ├── CheckBox: bao gồm edge cases
│   ├── CheckBox: bao gồm stress tests
│   ├── TextArea: yêu cầu bổ sung cho AI
│   ├── Nút: [Sinh testcase bằng AI]
│   ├── TableView: input | expected_output | type
│   ├── Nút: [Thêm tay] [Sửa] [Xóa] [Export to files]
│   └── TextArea: xem nội dung testcase được chọn
│
├── Tab 4: "Kiểm thử code"
│   ├── ComboBox: chọn đề
│   ├── ComboBox: ngôn ngữ (Java / C++ / Python)
│   ├── TextArea: paste code mẫu
│   ├── ComboBox: kỳ vọng verdict (AC / WA / TLE)
│   ├── Nút: [Chạy tất cả testcase] [Sinh code AC bằng AI]
│   ├── ProgressBar: tiến trình chạy
│   └── TableView: testcase | verdict | time | actual_output
│
└── Tab 5: "Checker"
    ├── ComboBox: chọn đề
    ├── RadioButton: Exact match / Special judge
    ├── TextArea: code checker (special judge)
    ├── Nút: [Sinh checker bằng AI]
    └── Nút: [Test checker]
```

---

## GeminiService.java

```java
// Endpoint: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
// Dùng OkHttp để gọi REST, parse JSON bằng Gson

public class GeminiService {
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String MODEL   = "gemini-1.5-flash";

    // Gọi API, trả về text response
    public String generateContent(String prompt) { ... }

    // Parse đề → trả về Problem object
    public Problem analyzeProblem(String rawStatement) { ... }

    // Sinh testcase → trả về List<Testcase>
    public List<Testcase> generateTestcases(Problem problem, int count, boolean includeEdge) { ... }

    // Sinh code AC mẫu → trả về String code
    public String generateSolutionCode(Problem problem, String language) { ... }

    // Sinh checker code
    public String generateChecker(Problem problem) { ... }
}
```

---

## Prompts cho Gemini API

### Prompt phân tích đề bài

```
Analyze this competitive programming problem and extract structured information.
Return a JSON object with these exact fields:
{
  "title": "problem title",
  "contest_type": "IOI|ICPC|CF|other",
  "constraints": "e.g. 1 <= N <= 10^5, 1 <= A[i] <= 10^9",
  "input_format": "description of input format",
  "output_format": "description of output format",
  "time_limit_ms": 1000,
  "memory_limit_mb": 256,
  "problem_type": "e.g. graph, dp, greedy, math",
  "summary": "brief 2-3 sentence summary"
}

Problem statement:
{{PROBLEM_STATEMENT}}

Return ONLY valid JSON, no markdown, no explanation.
```

### Prompt sinh testcase

```
Generate {{COUNT}} test cases for this competitive programming problem.
Return a JSON array where each element has:
{
  "input": "exact input as it would appear in stdin",
  "expected_output": "exact expected output",
  "case_type": "normal|edge|stress|boundary",
  "description": "brief description of what this case tests"
}

Problem constraints: {{CONSTRAINTS}}
Input format:        {{INPUT_FORMAT}}
Output format:       {{OUTPUT_FORMAT}}

Requirements:
- Include boundary values (min/max from constraints)
- Include edge cases: empty input, single element, all same values
- Include stress tests with large N near the limit
- All inputs must strictly follow the input format
{{EXTRA_REQUIREMENTS}}

Return ONLY a valid JSON array, no markdown, no explanation.
```

### Prompt sinh code AC mẫu

```
Write a correct, efficient solution for this competitive programming problem.
Language:     {{LANGUAGE}}
Time limit:   {{TIME_LIMIT}}ms
Memory limit: {{MEMORY_LIMIT}}MB

Problem:
{{PROBLEM_STATEMENT}}
Constraints: {{CONSTRAINTS}}

Requirements:
- The solution MUST be AC (accepted)
- Use the most optimal algorithm fitting within the constraints
- Handle all edge cases
- Read from stdin, write to stdout

Return ONLY the complete compilable code, no explanation, no markdown.
```

### Prompt sinh checker

```
Write a checker for this competitive programming problem.
The checker receives three arguments: input_file, expected_output_file, contestant_output_file.
Language: C++ (using testlib.h convention) or Python.

Problem:
{{PROBLEM_STATEMENT}}
Output format: {{OUTPUT_FORMAT}}

Return ONLY the complete checker code, no explanation, no markdown.
```

---

## CodeRunnerService.java

```java
public class CodeRunnerService {

    /**
     * Compile và chạy code với từng testcase.
     * Trả về: verdict (AC/WA/TLE/RE), actual_output, time_ms
     */
    public JudgeResult judgeCode(Solution solution, Testcase testcase, int timeLimitMs) {
        // 1. Compile nếu cần (C++: g++, Java: javac)
        // 2. Chạy process với stdin = testcase.input
        // 3. So sánh output với expected (hoặc chạy qua checker)
        // 4. Trả về verdict
    }

    // Exact match hoặc dùng checker
    private boolean checkOutput(String actual, String expected) { ... }
}
```

---

## pom.xml — Dependencies

```xml
<dependencies>

    <!-- JavaFX -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>21</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-fxml</artifactId>
        <version>21</version>
    </dependency>

    <!-- SQLite -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.45.1.0</version>
    </dependency>

    <!-- HTTP Client cho Gemini API -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>

    <!-- JSON -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <!-- OCR (bỏ comment nếu cần đọc đề từ ảnh) -->
    <!--
    <dependency>
        <groupId>net.sourceforge.tess4j</groupId>
        <artifactId>tess4j</artifactId>
        <version>5.11.0</version>
    </dependency>
    -->

</dependencies>
```

---

## Yêu cầu kỹ thuật quan trọng

- **Bất đồng bộ**: Dùng `javafx.concurrent.Task` khi gọi Gemini API, không block UI thread.
- **Loading indicator**: Hiển thị `ProgressIndicator` khi đang chờ AI phản hồi.
- **API Key**: Đọc từ environment variable `GEMINI_API_KEY` hoặc file config; có ô nhập key trong Settings.
- **Xử lý lỗi**: Try-catch toàn bộ API calls, hiển thị `Alert` khi có lỗi.
- **Export testcase**: Nút export ra thư mục với format `input/1.in`, `output/1.out`, `input/2.in`,...
- **Timeout**: Bắt buộc có timeout khi chạy code mẫu (mặc định 5 giây).
- **Sandbox an toàn**: Hạn chế network access và memory của subprocess khi có thể.

---

## Thứ tự implement

1. `DatabaseService.java` — khởi tạo DB, CRUD cho Problem / Testcase / Solution
2. Model classes — `Problem`, `Testcase`, `Solution`, `JudgeResult`
3. `GeminiService.java` — gọi API, parse JSON response
4. `main.fxml` + `MainController` — layout chính với TabPane
5. Tab **Nhập đề** — import text/ảnh, phân tích AI, lưu DB
6. Tab **Testcase** — sinh và quản lý testcase
7. `CodeRunnerService.java` — compile và chạy code
8. Tab **Kiểm thử code** — chạy và hiển thị kết quả
9. Tab **Checker** — tạo và test checker
10. Polish UI — CSS style, error handling toàn diện

> **Hãy implement từng class một, đầy đủ code, không bỏ TODO.**
