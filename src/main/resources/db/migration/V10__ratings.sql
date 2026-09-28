-- Anki-style ratings. You rate a problem when you mark it done (Forgot / Hard / Medium / Easy,
-- stored as AGAIN / HARD / GOOD / EASY) and at every review (Again / Hard / Good / Easy).
-- Older rows keep NULL: they were Remembered/Forgot answers, still in revision_attempts.solved.
-- Existing schedules are left as they are; the next rating takes over from there.

ALTER TABLE problems
    ADD COLUMN first_rating VARCHAR(5) CHECK (first_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'));

ALTER TABLE revision_attempts
    ADD COLUMN rating VARCHAR(5) CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'));
