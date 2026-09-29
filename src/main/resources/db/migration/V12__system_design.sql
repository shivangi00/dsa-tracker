-- System design tracker: a separate section alongside the NeetCode tracker, same accounts and plan.
--
--   sd_items     one topic studied or one design problem attempted by a user, with its review
--                schedule (topics: 3 revisions, problems: 2; all finished by the end of the plan)
--   sd_attempts  each sitting: the first study / design (revision 0) and each revision;
--                "Again" repeats a revision as another try. Topics keep their quiz result here.
--   sd_answers   saved versions of a written design (five sections), each with its coverage score
--
-- Topic and problem content (quiz questions, rubrics, reference designs) lives in
-- sd-topics.json and sd-problems.json, keyed by item_key.

CREATE TABLE sd_items (
    id               BIGSERIAL        PRIMARY KEY,
    version          BIGINT           NOT NULL DEFAULT 0,
    user_id          BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind             VARCHAR(7)       NOT NULL CHECK (kind IN ('TOPIC', 'PROBLEM')),
    item_key         VARCHAR(40)      NOT NULL,
    started_on       DATE             NOT NULL,
    first_rating     VARCHAR(5)       NOT NULL CHECK (first_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    interval_days    INT              NOT NULL CHECK (interval_days >= 1),
    ease             DOUBLE PRECISION NOT NULL,
    reps             INT              NOT NULL DEFAULT 0 CHECK (reps >= 0),
    lapses           INT              NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    last_reviewed_on DATE             NOT NULL,
    next_due_on      DATE,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT sd_items_once UNIQUE (user_id, kind, item_key)
);

CREATE INDEX sd_items_due_idx ON sd_items (user_id, next_due_on);

CREATE TABLE sd_attempts (
    id             BIGSERIAL     PRIMARY KEY,
    version        BIGINT        NOT NULL DEFAULT 0,
    item_id        BIGINT        NOT NULL REFERENCES sd_items (id) ON DELETE CASCADE,
    revision       SMALLINT      NOT NULL CHECK (revision BETWEEN 0 AND 3),
    try_no         SMALLINT      NOT NULL DEFAULT 1 CHECK (try_no >= 1),
    attempted_on   DATE          NOT NULL,
    rating         VARCHAR(5)    NOT NULL CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    notes          VARCHAR(2000),
    excalidraw_url VARCHAR(500),
    quiz_score     SMALLINT      CHECK (quiz_score >= 0),
    quiz_total     SMALLINT      CHECK (quiz_total >= 1),
    -- which questions were asked and how each went, e.g. "3+,7-,1+" (question index, right/wrong)
    quiz_detail    VARCHAR(200),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT sd_attempts_one_per_try UNIQUE (item_id, revision, try_no),
    CONSTRAINT sd_attempts_quiz_complete CHECK ((quiz_score IS NULL) = (quiz_total IS NULL)),
    CONSTRAINT sd_attempts_quiz_fits CHECK (quiz_score IS NULL OR quiz_score <= quiz_total)
);

CREATE INDEX sd_attempts_item_idx ON sd_attempts (item_id);

CREATE TABLE sd_answers (
    id              BIGSERIAL    PRIMARY KEY,
    attempt_id      BIGINT       NOT NULL REFERENCES sd_attempts (id) ON DELETE CASCADE,
    version_no      SMALLINT     NOT NULL CHECK (version_no BETWEEN 1 AND 99),
    requirements    VARCHAR(4000),
    entities        VARCHAR(4000),
    api             VARCHAR(4000),
    high_level      VARCHAR(4000),
    deep_dives      VARCHAR(4000),
    -- the built-in coverage score, out of 10 (null for problems without a rubric yet)
    score           NUMERIC(3, 1) CHECK (score BETWEEN 0 AND 10),
    structure_score NUMERIC(3, 1),
    -- indexes of the rubric points covered, e.g. "0,2,5"
    covered         VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT sd_answers_number UNIQUE (attempt_id, version_no),
    CONSTRAINT sd_answers_not_empty CHECK (
        coalesce(requirements, entities, api, high_level, deep_dives) IS NOT NULL)
);

CREATE INDEX sd_answers_attempt_idx ON sd_answers (attempt_id);
