-- Rate-limit counters, shared by every running copy of the app (Vercel starts several when busy).
-- One row per rule + client. "bucket" is a SHA-256 hash, so raw IP addresses and emails are never stored.
CREATE TABLE rate_limits (
    bucket       CHAR(64)    PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    hits         INTEGER     NOT NULL CHECK (hits > 0)
);

-- For deleting old rows.
CREATE INDEX rate_limits_window_start_idx ON rate_limits (window_start);
