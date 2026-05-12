CREATE TABLE IF NOT EXISTS problems (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    title           TEXT    NOT NULL,
    contest_type    TEXT    NOT NULL,
    statement       TEXT    NOT NULL,
    constraints     TEXT,
    input_format    TEXT,
    output_format   TEXT,
    time_limit      INTEGER DEFAULT 1000,
    memory_limit    INTEGER DEFAULT 256,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS testcases (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    problem_id       INTEGER NOT NULL,
    input_data       TEXT    NOT NULL,
    expected_output  TEXT,
    case_type        TEXT    DEFAULT 'generated',
    is_edge_case     INTEGER DEFAULT 0,
    generator_prompt TEXT,
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);

CREATE TABLE IF NOT EXISTS solutions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    problem_id  INTEGER NOT NULL,
    code        TEXT    NOT NULL,
    language    TEXT    NOT NULL,
    verdict     TEXT,
    note        TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);

CREATE TABLE IF NOT EXISTS judge_results (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    solution_id   INTEGER NOT NULL,
    testcase_id   INTEGER NOT NULL,
    verdict       TEXT    NOT NULL,
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
    checker_type TEXT    DEFAULT 'exact',
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);
