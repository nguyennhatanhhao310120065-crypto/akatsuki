package com.judgeTool.service;

import com.judgeTool.model.Checker;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Solution;
import com.judgeTool.model.Testcase;
import com.judgeTool.model.Verdict;
import com.judgeTool.util.FileUtil;
import com.judgeTool.util.ProcessUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CodeRunnerService {

    public static final int DEFAULT_RUN_TIMEOUT_MS = 5000;

    private static final int COMPILE_TIMEOUT_MS = 30_000;
    private static final String JAVA_MAIN = "Main";

    public record RunOutcome(String verdict, String actualOutput, int timeMs) {
    }

    /**
     * Compile và chạy code với testcase; so khớp exact hoặc qua checker (Python).
     */
    public RunOutcome judgeCode(Solution solution, Testcase testcase, Problem problem, Checker checkerOrNull,
                                int timeLimitMs, boolean useChecker) throws IOException, InterruptedException {
        long start = System.nanoTime();
        Path work = Files.createTempDirectory("judge_" + UUID.randomUUID());
        try {
            String lang = solution.getLanguage() == null ? "java" : solution.getLanguage().toLowerCase(Locale.ROOT);
            List<String> cmd = buildRunCommand(work, solution, lang);
            if (cmd == null || cmd.isEmpty()) {
                return new RunOutcome(Verdict.RE, "Unsupported language or compile error: " + lang, 0);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(work.toFile());
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put("NO_NETWORK", "1");

            Process p = pb.start();
            try {
                p.getOutputStream().write(
                        testcase.getInputData() != null ? testcase.getInputData().getBytes() : new byte[0]);
                p.getOutputStream().close();

                int timeout = Math.max(timeLimitMs, DEFAULT_RUN_TIMEOUT_MS);
                boolean finished = ProcessUtil.waitFor(p, timeout);
                if (!finished) {
                    p.destroyForcibly();
                    return new RunOutcome(Verdict.TLE, "", timeout);
                }

                int exit = p.exitValue();
                String out = ProcessUtil.drain(p.getInputStream());
                int elapsed = (int) ((System.nanoTime() - start) / 1_000_000L);

                if (exit != 0) {
                    return new RunOutcome(Verdict.RE, out, elapsed);
                }

                boolean ok;
                if (useChecker && checkerOrNull != null
                        && "special_judge".equalsIgnoreCase(checkerOrNull.getCheckerType())) {
                    ok = runJavaChecker(work, checkerOrNull, testcase, out);
                } else {
                    ok = checkOutput(out, testcase.getExpectedOutput());
                }
                return new RunOutcome(ok ? Verdict.AC : Verdict.WA, out, elapsed);
            } finally {
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        } finally {
            deleteRecursive(work);
        }
    }

    /**
     * Smoke test: chạy checker với contestant_output = expected_output (phải được chấp nhận).
     */
    public boolean smokeTestSpecialChecker(Checker checker, Testcase tc) throws IOException, InterruptedException {
        Path work = Files.createTempDirectory("chk_" + UUID.randomUUID());
        try {
            String out = tc.getExpectedOutput() != null ? tc.getExpectedOutput() : "";
            return runJavaChecker(work, checker, tc, out);
        } finally {
            deleteRecursive(work);
        }
    }

    private List<String> buildRunCommand(Path work, Solution solution, String lang) throws IOException {
        String code = solution.getCode();
        return switch (lang) {
            case "java" -> {
                Path src = work.resolve(JAVA_MAIN + ".java");
                FileUtil.writeText(src, code);
                if (!compile(work, "javac", "-encoding", "UTF-8", JAVA_MAIN + ".java")) {
                    yield List.of();
                }
                yield List.of("java", "-Xmx256m", JAVA_MAIN);
            }
            case "cpp", "c++" -> {
                Path src = work.resolve("sol.cpp");
                FileUtil.writeText(src, code);
                if (!compile(work, "g++", "-O2", "-std=c++17", "sol.cpp", "-o", "sol.exe")) {
                    yield List.of();
                }
                yield List.of(work.resolve("sol.exe").toAbsolutePath().toString());
            }
            case "python" -> {
                Path src = work.resolve("sol.py");
                FileUtil.writeText(src, code);
                yield List.of("python", src.toAbsolutePath().toString());
            }
            default -> null;
        };
    }

    private boolean compile(Path work, String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(work.toFile());
        pb.redirectErrorStream(true);
        Process c = pb.start();
        try {
            ProcessUtil.drain(c.getInputStream());
            return ProcessUtil.waitFor(c, COMPILE_TIMEOUT_MS) && c.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (c.isAlive()) {
                c.destroyForcibly();
            }
        }
    }

    private boolean runJavaChecker(Path work, Checker checker, Testcase tc, String contestantOut)
            throws IOException, InterruptedException {
        Path checkerJava = work.resolve("Checker.java");
        FileUtil.writeText(checkerJava, checker.getCheckerCode());
        Path inFile = work.resolve("input.txt");
        Path expFile = work.resolve("expected.txt");
        Path outFile = work.resolve("contestant.txt");
        FileUtil.writeText(inFile, tc.getInputData() != null ? tc.getInputData() : "");
        FileUtil.writeText(expFile, tc.getExpectedOutput() != null ? tc.getExpectedOutput() : "");
        FileUtil.writeText(outFile, contestantOut != null ? contestantOut : "");

        if (!compile(work, "javac", "-encoding", "UTF-8", "Checker.java")) {
            return false;
        }

        ProcessBuilder pb = new ProcessBuilder("java", "Checker",
                inFile.toAbsolutePath().toString(),
                expFile.toAbsolutePath().toString(),
                outFile.toAbsolutePath().toString());
        pb.directory(work.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            if (!ProcessUtil.waitFor(p, DEFAULT_RUN_TIMEOUT_MS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    boolean checkOutput(String actual, String expected) {
        if (expected == null) {
            return true;
        }
        return normalize(actual).equals(normalize(expected));
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] lines = t.split("\n", -1);
        List<String> trimmed = new ArrayList<>(lines.length);
        for (String line : lines) {
            trimmed.add(line.stripTrailing());
        }
        while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1).isEmpty()) {
            trimmed.remove(trimmed.size() - 1);
        }
        return String.join("\n", trimmed);
    }

    private static void deleteRecursive(Path root) {
        if (root == null) {
            return;
        }
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
