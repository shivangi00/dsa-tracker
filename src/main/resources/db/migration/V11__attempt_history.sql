-- Attempt history: every sitting with a problem is an attempt, and every attempt can keep several
-- versions of your solution, each with its own analysis.
--
--   revision 0      = the day you solved it (the "Solved" column)
--   revision 1..3   = the three revisions; "Again" repeats the same revision (try 2, 3, …)
--
-- Notes, drawing, code and analysis move from problems into attempts / solution_versions, so
-- nothing is overwritten any more: an attempt is editable on its own day and frozen after.
-- A problem is fully revised after 3 successful revisions (reps >= 3); it then has no due date.

CREATE TABLE attempts (
    id             BIGSERIAL    PRIMARY KEY,
    problem_id     BIGINT       NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    revision       SMALLINT     NOT NULL CHECK (revision BETWEEN 0 AND 3),
    try_no         SMALLINT     NOT NULL DEFAULT 1 CHECK (try_no >= 1),
    attempted_on   DATE         NOT NULL,
    rating         VARCHAR(5)   CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    learnings      VARCHAR(2000),
    excalidraw_url VARCHAR(500),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT attempts_one_per_try UNIQUE (problem_id, revision, try_no),
    CONSTRAINT attempts_solve_has_notes CHECK (revision > 0 OR btrim(coalesce(learnings, '')) <> '')
);

CREATE TABLE solution_versions (
    id                  BIGSERIAL    PRIMARY KEY,
    attempt_id          BIGINT       NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    version_no          SMALLINT     NOT NULL CHECK (version_no BETWEEN 1 AND 99),
    code                TEXT         NOT NULL CHECK (length(code) BETWEEN 1 AND 10000),
    code_language       VARCHAR(20)  NOT NULL CHECK (code_language IN ('JAVA', 'PYTHON', 'JAVASCRIPT', 'CPP')),
    time_complexity     VARCHAR(40),
    space_complexity    VARCHAR(40),
    analysis_reasons    TEXT,
    analysis_confidence VARCHAR(10),
    analysis_source     VARCHAR(20),
    analysed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT solution_versions_number UNIQUE (attempt_id, version_no),
    CONSTRAINT solution_versions_analysis_complete CHECK ((time_complexity IS NULL) = (analysed_at IS NULL))
);

CREATE INDEX attempts_problem_idx ON attempts (problem_id);
CREATE INDEX solution_versions_attempt_idx ON solution_versions (attempt_id);

-- 1. The solve day of every problem, with its notes.
INSERT INTO attempts (problem_id, revision, try_no, attempted_on, rating, learnings, excalidraw_url)
SELECT id, 0, 1, solved_on, first_rating, learnings, excalidraw_url
FROM problems;

-- 2. Its code and analysis become version 1 of that attempt.
INSERT INTO solution_versions (attempt_id, version_no, code, code_language, time_complexity, space_complexity,
                               analysis_reasons, analysis_confidence, analysis_source, analysed_at)
SELECT a.id, 1, p.code, p.code_language, p.time_complexity, p.space_complexity,
       p.analysis_reasons, p.analysis_confidence, p.analysis_source, p.analysed_at
FROM problems p JOIN attempts a ON a.problem_id = p.id AND a.revision = 0
WHERE p.code IS NOT NULL AND btrim(p.code) <> '';

-- 3. Past reviews become revision attempts: revision n is the one after n - 1 successful reviews,
--    and each "forgot" before a success is another try of the same revision. Reviews after the
--    third success stay in revision_attempts only.
INSERT INTO attempts (problem_id, revision, try_no, attempted_on, rating)
SELECT problem_id, slot,
       ROW_NUMBER() OVER (PARTITION BY problem_id, slot ORDER BY attempted_on, id),
       attempted_on, rating
FROM (
    SELECT ra.id, ra.problem_id, ra.attempted_on,
           COALESCE(ra.rating, CASE WHEN ra.solved THEN 'GOOD' ELSE 'AGAIN' END) AS rating,
           1 + COALESCE(SUM(CASE WHEN ra.solved THEN 1 ELSE 0 END) OVER (
                   PARTITION BY ra.problem_id ORDER BY ra.attempted_on, ra.id
                   ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS slot
    FROM revision_attempts ra
) r
WHERE slot <= 3;

-- 4. Fully revised problems (3 or more successful reviews) have nothing left to review.
ALTER TABLE problems ALTER COLUMN next_due_on DROP NOT NULL;
UPDATE problems SET next_due_on = NULL WHERE reps >= 3;

-- 5. Notes and code now live in attempts / solution_versions.
ALTER TABLE problems
    DROP COLUMN learnings,
    DROP COLUMN excalidraw_url,
    DROP COLUMN code,
    DROP COLUMN code_language,
    DROP COLUMN time_complexity,
    DROP COLUMN space_complexity,
    DROP COLUMN analysis_reasons,
    DROP COLUMN analysis_confidence,
    DROP COLUMN analysis_source,
    DROP COLUMN analysed_at;
