-- One row per problem you log. The scheduling state lives on the row so
-- "what is due today?" is a single indexed query.
CREATE TABLE problems (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200)  NOT NULL,
    link                VARCHAR(500),
    method              VARCHAR(2000),
    solved_myself       BOOLEAN       NOT NULL,
    solved_on           DATE          NOT NULL,
    initial_difficulty  VARCHAR(6)    NOT NULL CHECK (initial_difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    current_difficulty  VARCHAR(6)    NOT NULL CHECK (current_difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    required_revisions  INT           NOT NULL CHECK (required_revisions > 0),
    completed_revisions INT           NOT NULL DEFAULT 0 CHECK (completed_revisions >= 0),
    next_due_on         DATE,
    status              VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'MASTERED')),
    last_reset_on       DATE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- A mastered problem has nothing left to schedule; an active one always does.
    CHECK ((status = 'ACTIVE') = (next_due_on IS NOT NULL))
);

-- The same problem can't be logged twice (case-insensitive).
CREATE UNIQUE INDEX problems_name_unique ON problems (lower(name));

-- Partial index: only active problems are ever looked up by due date.
CREATE INDEX problems_due_idx ON problems (next_due_on) WHERE status = 'ACTIVE';

-- One row per Yes/No answer on a revision. This is the history the streak is built from.
CREATE TABLE revision_attempts (
    id                BIGSERIAL PRIMARY KEY,
    problem_id        BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    attempted_on      DATE        NOT NULL,
    solved            BOOLEAN     NOT NULL,
    difficulty_before VARCHAR(6)  NOT NULL,
    difficulty_after  VARCHAR(6)  NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX revision_attempts_day_idx ON revision_attempts (attempted_on);
CREATE INDEX revision_attempts_problem_idx ON revision_attempts (problem_id);
