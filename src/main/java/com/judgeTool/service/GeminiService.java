package com.judgeTool.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Testcase;
import com.judgeTool.util.ConfigStore;
import com.judgeTool.util.StringUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GeminiService {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String ENDPOINT_FMT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final int CONNECT_TIMEOUT_S = 60;
    private static final int READ_TIMEOUT_S = 120;
    private static final int WRITE_TIMEOUT_S = 60;

    private final OkHttpClient http;

    public GeminiService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .build();
    }

    private static String apiKey() {
        String k = ConfigStore.getGeminiApiKey();
        if (k == null || k.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình GEMINI_API_KEY (biến môi trường hoặc Cài đặt).");
        }
        return k;
    }

    public String generateContent(String prompt) throws IOException {
        JsonArray parts = new JsonArray();
        parts.add(textPart(prompt));
        return callApi(parts);
    }

    /**
     * Gửi ảnh + prompt tới Gemini Vision và trả về text trích xuất.
     */
    public String extractTextFromImage(Path imagePath) throws IOException {
        if (imagePath == null || !Files.exists(imagePath)) {
            throw new IOException("Không tìm thấy file ảnh: " + imagePath);
        }
        byte[] imgBytes = Files.readAllBytes(imagePath);
        String base64 = Base64.getEncoder().encodeToString(imgBytes);
        String mime = guessMime(imagePath);

        JsonArray parts = new JsonArray();
        parts.add(textPart(
                "Trích xuất toàn bộ nội dung đề bài lập trình thi đấu từ ảnh này. "
                        + "Giữ nguyên định dạng, công thức, ràng buộc, ví dụ input/output. "
                        + "Chỉ trả về phần text của đề bài, không thêm giải thích, không thêm markdown."));
        parts.add(inlineImagePart(mime, base64));
        return callApi(parts);
    }

    public Problem analyzeProblem(String rawStatement, String contestTypeHint) throws IOException {
        String prompt = """
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

                """ + rawStatement + """

                Return ONLY valid JSON, no markdown, no explanation.
                """;
        JsonObject obj = JsonParser.parseString(stripCodeFences(generateContent(prompt))).getAsJsonObject();
        Problem p = new Problem();
        p.setTitle(getStr(obj, "title", "Untitled"));
        String ct = getStr(obj, "contest_type", "other");
        if (contestTypeHint != null && !contestTypeHint.isBlank() && !"other".equalsIgnoreCase(contestTypeHint)) {
            ct = contestTypeHint;
        }
        p.setContestType(ct);
        p.setStatement(rawStatement);
        p.setConstraints(getStr(obj, "constraints", ""));
        p.setInputFormat(getStr(obj, "input_format", ""));
        p.setOutputFormat(getStr(obj, "output_format", ""));
        p.setTimeLimitMs(getInt(obj, "time_limit_ms", 1000));
        p.setMemoryLimitMb(getInt(obj, "memory_limit_mb", 256));
        return p;
    }

    public List<Testcase> generateTestcases(Problem problem, int count, boolean includeEdge, boolean includeStress,
                                            String extraRequirements) throws IOException {
        StringBuilder extra = new StringBuilder();
        if (!includeEdge) {
            extra.append("\n- Skip dedicated edge-case-only scenarios; focus on normal and stress.\n");
        }
        if (!includeStress) {
            extra.append("\n- Do not include stress tests with maximum constraint sizes.\n");
        }
        if (extraRequirements != null && !extraRequirements.isBlank()) {
            extra.append("\nAdditional requirements:\n").append(extraRequirements).append('\n');
        }
        String prompt = """
                Generate %d test cases for this competitive programming problem.
                Return a JSON array where each element has:
                {
                  "input": "exact input as it would appear in stdin",
                  "expected_output": "exact expected output",
                  "case_type": "normal|edge|stress|boundary",
                  "description": "brief description of what this case tests"
                }

                Problem constraints: %s
                Input format:        %s
                Output format:       %s

                Full statement (for reference):
                %s

                Requirements:
                - Include boundary values (min/max from constraints)
                - Include edge cases: empty input, single element, all same values
                - Include stress tests with large N near the limit
                - All inputs must strictly follow the input format
                %s

                Return ONLY a valid JSON array, no markdown, no explanation.
                """.formatted(
                count,
                fmtSafe(StringUtil.nullSafe(problem.getConstraints())),
                fmtSafe(StringUtil.nullSafe(problem.getInputFormat())),
                fmtSafe(StringUtil.nullSafe(problem.getOutputFormat())),
                fmtSafe(StringUtil.nullSafe(problem.getStatement())),
                fmtSafe(extra.toString()));

        JsonArray arr = JsonParser.parseString(stripCodeFences(generateContent(prompt))).getAsJsonArray();
        List<Testcase> list = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            Testcase t = new Testcase();
            t.setProblemId(problem.getId());
            t.setInputData(getStr(o, "input", ""));
            t.setExpectedOutput(getStr(o, "expected_output", ""));
            String ct = getStr(o, "case_type", "generated");
            t.setCaseType(mapCaseType(ct));
            t.setEdgeCase("edge".equalsIgnoreCase(ct) || "boundary".equalsIgnoreCase(ct));
            t.setGeneratorPrompt(getStr(o, "description", ""));
            list.add(t);
        }
        return list;
    }

    public String generateSolutionCode(Problem problem, String language) throws IOException {
        String prompt = """
                Write a correct, efficient solution for this competitive programming problem.
                Language:     %s
                Time limit:   %dms
                Memory limit: %dMB

                Problem:
                %s
                Constraints: %s

                Requirements:
                - The solution MUST be AC (accepted)
                - Use the most optimal algorithm fitting within the constraints
                - Handle all edge cases
                - Read from stdin, write to stdout

                Return ONLY the complete compilable code, no explanation, no markdown.
                """.formatted(
                fmtSafe(language),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitMb(),
                fmtSafe(StringUtil.nullSafe(problem.getStatement())),
                fmtSafe(StringUtil.nullSafe(problem.getConstraints())));
        return stripCodeFences(generateContent(prompt));
    }

    public String generateChecker(Problem problem) throws IOException {
        String prompt = """
                Write a checker for this competitive programming problem.
                The checker receives three arguments: input_file, expected_output_file, contestant_output_file.
                Language: Java.
                The class MUST be named `Checker` and contain `public static void main(String[] args)`.
                Exit with code 0 if the contestant output is correct, otherwise exit with a non-zero code.

                Problem:
                %s
                Output format: %s

                Return ONLY the complete compilable Java code, no explanation, no markdown.
                """.formatted(
                fmtSafe(StringUtil.nullSafe(problem.getStatement())),
                fmtSafe(StringUtil.nullSafe(problem.getOutputFormat())));
        return stripCodeFences(generateContent(prompt));
    }

    private String callApi(JsonArray parts) throws IOException {
        String url = ENDPOINT_FMT.formatted(MODEL, apiKey());
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject body = new JsonObject();
        body.add("contents", contents);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Gemini API lỗi HTTP " + response.code() + ": " + respBody);
            }
            return extractTextFromGeminiResponse(respBody);
        }
    }

    private static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        return part;
    }

    private static JsonObject inlineImagePart(String mime, String base64) {
        JsonObject inline = new JsonObject();
        inline.addProperty("mime_type", mime);
        inline.addProperty("data", base64);
        JsonObject part = new JsonObject();
        part.add("inline_data", inline);
        return part;
    }

    private static String guessMime(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".bmp")) return "image/bmp";
        if (name.endsWith(".tif") || name.endsWith(".tiff")) return "image/tiff";
        return "image/png";
    }

    static String extractTextFromGeminiResponse(String json) throws IOException {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IOException("Phản hồi Gemini không hợp lệ");
        }
        JsonObject o = root.getAsJsonObject();
        if (o.has("error")) {
            throw new IOException("Gemini error: " + o.get("error"));
        }
        JsonArray candidates = o.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("Không có candidates từ Gemini");
        }
        JsonObject first = candidates.get(0).getAsJsonObject();
        JsonObject content = first.getAsJsonObject("content");
        if (content == null) {
            throw new IOException("Phản hồi Gemini thiếu trường 'content'");
        }
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("Nội dung trống từ Gemini");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement p : parts) {
            if (p.isJsonObject() && p.getAsJsonObject().has("text")) {
                sb.append(p.getAsJsonObject().get("text").getAsString());
            }
        }
        return sb.toString().trim();
    }

    static String stripCodeFences(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl > 0) {
            t = t.substring(firstNl + 1);
        }
        int end = t.lastIndexOf("```");
        if (end >= 0) {
            t = t.substring(0, end);
        }
        return t.trim();
    }

    private static String mapCaseType(String ct) {
        if (ct == null) {
            return "generated";
        }
        return switch (ct.toLowerCase(Locale.ROOT)) {
            case "edge", "boundary" -> "manual";
            case "stress" -> "stress";
            default -> "generated";
        };
    }

    private static String fmtSafe(String s) {
        return s.replace("%", "%%");
    }

    private static String getStr(JsonObject o, String key, String def) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return def;
        }
        return o.get(key).getAsString();
    }

    private static int getInt(JsonObject o, String key, int def) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return def;
        }
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }
}
