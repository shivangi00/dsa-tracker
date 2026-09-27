-- V4: accounts, password reset, per-user data, and the adaptive review schedule.

-- ---------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------
CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(30)  NOT NULL CHECK (username ~ '^[A-Za-z0-9_.-]{3,30}$'),
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,        -- BCrypt hash, never the password itself
    start_date     DATE         NOT NULL,        -- the day this person's plan starts
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- "Shivangi" and "shivangi" are the same username; the same goes for emails.
CREATE UNIQUE INDEX users_username_unique ON users (lower(username));
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));

-- One row per "forgot password" request. Only a SHA-256 hash of the token is stored,
-- so someone who reads this table still can't use a link.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------
-- Every problem now belongs to a user.
-- Rows logged before accounts existed keep user_id = NULL until the first account
-- is created, which takes them over (see AuthService.signUp).
-- ---------------------------------------------------------------
ALTER TABLE problems ADD COLUMN user_id BIGINT REFERENCES users (id) ON DELETE CASCADE;

DROP INDEX problems_name_unique;
CREATE UNIQUE INDEX problems_user_name_unique ON problems (user_id, lower(name));
DROP INDEX problems_catalog_unique;
CREATE UNIQUE INDEX problems_user_catalog_unique ON problems (user_id, catalog_id) WHERE catalog_id IS NOT NULL;

-- ---------------------------------------------------------------
-- Adaptive schedule: interval + ease replace levels and fixed revision counts.
-- ---------------------------------------------------------------
ALTER TABLE problems
    ADD COLUMN interval_days    INT,
    ADD COLUMN ease             DOUBLE PRECISION,
    ADD COLUMN reps             INT,
    ADD COLUMN lapses           INT,
    ADD COLUMN last_reviewed_on DATE;

-- The old rule "a mastered problem has no due date" must go before mastered
-- problems are given one below.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_check;

-- Convert existing schedules so nobody loses progress.
UPDATE problems p SET
    last_reviewed_on = COALESCE(
        (SELECT max(a.attempted_on) FROM revision_attempts a WHERE a.problem_id = p.id), p.solved_on),
    reps   = p.completed_revisions,
    lapses = (SELECT count(*) FROM revision_attempts a WHERE a.problem_id = p.id AND NOT a.solved),
    ease   = 2.25;

-- Problems that were "mastered" had no due date; give them a 30-day check-in.
UPDATE problems SET next_due_on = last_reviewed_on + 30 WHERE next_due_on IS NULL;
UPDATE problems SET interval_days = GREATEST(1, next_due_on - last_reviewed_on);

ALTER TABLE problems
    ALTER COLUMN interval_days    SET NOT NULL,
    ALTER COLUMN ease             SET NOT NULL,
    ALTER COLUMN reps             SET NOT NULL,
    ALTER COLUMN lapses           SET NOT NULL,
    ALTER COLUMN last_reviewed_on SET NOT NULL,
    ALTER COLUMN next_due_on      SET NOT NULL,
    ADD CONSTRAINT problems_interval_positive CHECK (interval_days BETWEEN 1 AND 365),
    ADD CONSTRAINT problems_ease_floor        CHECK (ease >= 1.3),
    ADD CONSTRAINT problems_counts_positive   CHECK (reps >= 0 AND lapses >= 0);

-- The old model's columns (and their constraints and index) go.
DROP INDEX IF EXISTS problems_due_idx;
ALTER TABLE problems
    DROP COLUMN status,
    DROP COLUMN required_revisions,
    DROP COLUMN completed_revisions,
    DROP COLUMN current_difficulty,
    DROP COLUMN last_reset_on;

-- "What's due today for this user?"
CREATE INDEX problems_user_due_idx ON problems (user_id, next_due_on);

-- Review history now records gaps instead of levels. Old rows keep their levels.
ALTER TABLE revision_attempts
    ALTER COLUMN difficulty_before DROP NOT NULL,
    ALTER COLUMN difficulty_after  DROP NOT NULL,
    ADD COLUMN interval_before INT,
    ADD COLUMN interval_after  INT;
