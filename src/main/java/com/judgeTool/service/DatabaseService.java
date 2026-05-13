package com.judgeTool.service;

import com.judgeTool.model.Checker;
import com.judgeTool.model.JudgeResult;
import com.judgeTool.model.Problem;
import com.judgeTool.model.Solution;
import com.judgeTool.model.Testcase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseService {

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private final String jdbcUrl;

    public DatabaseService(Path dbFile) {
        Path parent = dbFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ignored) {
            }
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/');
    }

    public void init() throws SQLException, IOException {
        try (Connection c = connect()) {
            runSchema(c);
        }
    }

    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    private void runSchema(Connection c) throws SQLException, IOException {
        try (InputStream in = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing " + SCHEMA_RESOURCE + " on classpath");
            }
            String sql = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            for (String stmt : sql.split(";")) {
                String t = stmt.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try (Statement st = c.createStatement()) {
                    st.execute(t);
                }
            }
        }
    }

    public long insertProblem(Problem p) throws SQLException {
        String sql = """
                INSERT INTO problems (title, contest_type, statement, constraints, input_format, output_format, time_limit, memory_limit)
                VALUES (?,?,?,?,?,?,?,?)
                """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getTitle());
            ps.setString(2, p.getContestType());
            ps.setString(3, p.getStatement());
            ps.setString(4, p.getConstraints());
            ps.setString(5, p.getInputFormat());
            ps.setString(6, p.getOutputFormat());
            ps.setInt(7, p.getTimeLimitMs());
            ps.setInt(8, p.getMemoryLimitMb());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No generated key for problem");
    }

    public void updateProblem(Problem p) throws SQLException {
        String sql = """
                UPDATE problems SET title=?, contest_type=?, statement=?, constraints=?, input_format=?, output_format=?, time_limit=?, memory_limit=?
                WHERE id=?
                """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getTitle());
            ps.setString(2, p.getContestType());
            ps.setString(3, p.getStatement());
            ps.setString(4, p.getConstraints());
            ps.setString(5, p.getInputFormat());
            ps.setString(6, p.getOutputFormat());
            ps.setInt(7, p.getTimeLimitMs());
            ps.setInt(8, p.getMemoryLimitMb());
            ps.setLong(9, p.getId());
            ps.executeUpdate();
        }
    }

    public void deleteProblem(long id) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        DELETE FROM judge_results WHERE testcase_id IN (SELECT id FROM testcases WHERE problem_id=?)
                           OR solution_id IN (SELECT id FROM solutions WHERE problem_id=?)
                        """)) {
                    ps.setLong(1, id);
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                deleteTestcasesForProblem(c, id);
                deleteSolutionsForProblem(c, id);
                deleteCheckersForProblem(c, id);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM problems WHERE id=?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void deleteTestcasesForProblem(Connection c, long problemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM testcases WHERE problem_id=?")) {
            ps.setLong(1, problemId);
            ps.executeUpdate();
        }
    }

    private void deleteSolutionsForProblem(Connection c, long problemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM solutions WHERE problem_id=?")) {
            ps.setLong(1, problemId);
            ps.executeUpdate();
        }
    }

    private void deleteCheckersForProblem(Connection c, long problemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM checkers WHERE problem_id=?")) {
            ps.setLong(1, problemId);
            ps.executeUpdate();
        }
    }

    public Problem findProblem(long id) throws SQLException {
        String sql = "SELECT * FROM problems WHERE id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapProblem(rs);
                }
            }
        }
        return null;
    }

    public List<Problem> listProblems(String contestFilterOrNull) throws SQLException {
        List<Problem> list = new ArrayList<>();
        String sql = contestFilterOrNull == null || contestFilterOrNull.isBlank()
                ? "SELECT * FROM problems ORDER BY id DESC"
                : "SELECT * FROM problems WHERE contest_type=? ORDER BY id DESC";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (contestFilterOrNull != null && !contestFilterOrNull.isBlank()) {
                ps.setString(1, contestFilterOrNull);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapProblem(rs));
                }
            }
        }
        return list;
    }

    private Problem mapProblem(ResultSet rs) throws SQLException {
        Problem p = new Problem();
        p.setId(rs.getLong("id"));
        p.setTitle(rs.getString("title"));
        p.setContestType(rs.getString("contest_type"));
        p.setStatement(rs.getString("statement"));
        p.setConstraints(rs.getString("constraints"));
        p.setInputFormat(rs.getString("input_format"));
        p.setOutputFormat(rs.getString("output_format"));
        p.setTimeLimitMs(rs.getInt("time_limit"));
        p.setMemoryLimitMb(rs.getInt("memory_limit"));
        String ca = rs.getString("created_at");
        if (ca != null) {
            try {
                p.setCreatedAt(LocalDateTime.parse(ca.replace(' ', 'T')));
            } catch (Exception e) {
                p.setCreatedAt(null);
            }
        }
        return p;
    }

    public long insertTestcase(Testcase t) throws SQLException {
        String sql = """
                INSERT INTO testcases (problem_id, input_data, expected_output, case_type, is_edge_case, generator_prompt)
                VALUES (?,?,?,?,?,?)
                """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, t.getProblemId());
            ps.setString(2, t.getInputData());
            ps.setString(3, t.getExpectedOutput());
            ps.setString(4, t.getCaseType() != null ? t.getCaseType() : "generated");
            ps.setInt(5, t.isEdgeCase() ? 1 : 0);
            ps.setString(6, t.getGeneratorPrompt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No generated key for testcase");
    }

    public void updateTestcase(Testcase t) throws SQLException {
        String sql = """
                UPDATE testcases SET input_data=?, expected_output=?, case_type=?, is_edge_case=?, generator_prompt=?
                WHERE id=? AND problem_id=?
                """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getInputData());
            ps.setString(2, t.getExpectedOutput());
            ps.setString(3, t.getCaseType());
            ps.setInt(4, t.isEdgeCase() ? 1 : 0);
            ps.setString(5, t.getGeneratorPrompt());
            ps.setLong(6, t.getId());
            ps.setLong(7, t.getProblemId());
            ps.executeUpdate();
        }
    }

    public void deleteTestcase(long id) throws SQLException {
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM judge_results WHERE testcase_id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM testcases WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
    }

    public List<Testcase> listTestcases(long problemId) throws SQLException {
        List<Testcase> list = new ArrayList<>();
        String sql = "SELECT * FROM testcases WHERE problem_id=? ORDER BY id";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTestcase(rs));
                }
            }
        }
        return list;
    }

    private Testcase mapTestcase(ResultSet rs) throws SQLException {
        Testcase t = new Testcase();
        t.setId(rs.getLong("id"));
        t.setProblemId(rs.getLong("problem_id"));
        t.setInputData(rs.getString("input_data"));
        t.setExpectedOutput(rs.getString("expected_output"));
        t.setCaseType(rs.getString("case_type"));
        t.setEdgeCase(rs.getInt("is_edge_case") != 0);
        t.setGeneratorPrompt(rs.getString("generator_prompt"));
        return t;
    }

    public long insertSolution(Solution s) throws SQLException {
        String sql = "INSERT INTO solutions (problem_id, code, language, verdict, note) VALUES (?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, s.getProblemId());
            ps.setString(2, s.getCode());
            ps.setString(3, s.getLanguage());
            ps.setString(4, s.getVerdict());
            ps.setString(5, s.getNote());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No generated key for solution");
    }

    public void updateSolutionVerdict(long solutionId, String verdict) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("UPDATE solutions SET verdict=? WHERE id=?")) {
            ps.setString(1, verdict);
            ps.setLong(2, solutionId);
            ps.executeUpdate();
        }
    }

    public void insertJudgeResult(JudgeResult r) throws SQLException {
        String sql = "INSERT INTO judge_results (solution_id, testcase_id, verdict, actual_output, time_ms, memory_kb) VALUES (?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, r.getSolutionId());
            ps.setLong(2, r.getTestcaseId());
            ps.setString(3, r.getVerdict());
            ps.setString(4, r.getActualOutput());
            if (r.getTimeMs() != null) {
                ps.setInt(5, r.getTimeMs());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            if (r.getMemoryKb() != null) {
                ps.setInt(6, r.getMemoryKb());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    public void deleteJudgeResultsForSolution(long solutionId) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM judge_results WHERE solution_id=?")) {
            ps.setLong(1, solutionId);
            ps.executeUpdate();
        }
    }

    public long upsertChecker(Checker ch) throws SQLException {
        Checker existing = findCheckerByProblem(ch.getProblemId());
        if (existing == null) {
            String sql = "INSERT INTO checkers (problem_id, checker_code, checker_type) VALUES (?,?,?)";
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, ch.getProblemId());
                ps.setString(2, ch.getCheckerCode());
                ps.setString(3, ch.getCheckerType());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            throw new SQLException("No generated key for checker");
        }
        String sql = "UPDATE checkers SET checker_code=?, checker_type=? WHERE id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ch.getCheckerCode());
            ps.setString(2, ch.getCheckerType());
            ps.setLong(3, existing.getId());
            ps.executeUpdate();
        }
        return existing.getId();
    }

    public Checker findCheckerByProblem(long problemId) throws SQLException {
        String sql = "SELECT * FROM checkers WHERE problem_id=? LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Checker ch = new Checker();
                    ch.setId(rs.getLong("id"));
                    ch.setProblemId(rs.getLong("problem_id"));
                    ch.setCheckerCode(rs.getString("checker_code"));
                    ch.setCheckerType(rs.getString("checker_type"));
                    return ch;
                }
            }
        }
        return null;
    }
}
