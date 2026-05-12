package com.judgeTool.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Testcase;
import com.judgeTool.util.ConfigStore;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GeminiService {
    private static final String MODEL = "gemini-2.5-flash";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;

    public GeminiService() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
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
        String key = apiKey();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=" + key;
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
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
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end >= 0) {
                t = t.substring(0, end);
            }
        }
        return t.trim();
    }

    public Problem analyzeProblem(String rawStatement, String contestTypeHint) throws IOException {
        String head = """
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

                """;
        String tail = """

                Return ONLY valid JSON, no markdown, no explanation.
                """;
        String prompt = head + rawStatement + tail;
        String text = generateContent(prompt);
        String json = stripCodeFences(text);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
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
        String extra = "";
        if (!includeEdge) {
            extra += "\n- Skip dedicated edge-case-only scenarios; focus on normal and stress.\n";
        }
        if (!includeStress) {
            extra += "\n- Do not include stress tests with maximum constraint sizes.\n";
        }
        if (extraRequirements != null && !extraRequirements.isBlank()) {
            extra += "\nAdditional requirements:\n" + extraRequirements + "\n";
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
                fmtSafe(nullSafe(problem.getConstraints())),
                fmtSafe(nullSafe(problem.getInputFormat())),
                fmtSafe(nullSafe(problem.getOutputFormat())),
                fmtSafe(nullSafe(problem.getStatement())),
                fmtSafe(extra));
        String text = generateContent(prompt);
        String json = stripCodeFences(text);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
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

    private static String mapCaseType(String ct) {
        if (ct == null) {
            return "generated";
        }
        return switch (ct.toLowerCase()) {
            case "edge", "boundary" -> "manual";
            case "stress" -> "stress";
            default -> "generated";
        };
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
                fmtSafe(nullSafe(problem.getStatement())),
                fmtSafe(nullSafe(problem.getConstraints())));
        return stripCodeFences(generateContent(prompt));
    }

    public String generateChecker(Problem problem) throws IOException {
        String prompt = """
                Write a checker for this competitive programming problem.
                The checker receives three arguments: input_file, expected_output_file, contestant_output_file.
                Language: Python 3.

                Problem:
                %s
                Output format: %s

                Return ONLY the complete checker code, no explanation, no markdown.
                """.formatted(fmtSafe(nullSafe(problem.getStatement())), fmtSafe(nullSafe(problem.getOutputFormat())));
        return stripCodeFences(generateContent(prompt));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
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
