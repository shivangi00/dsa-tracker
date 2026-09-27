-- Your solution's code and its complexity analysis, stored with each solved problem.
-- Notes, code and analysis can be changed only on the day the problem was solved (enforced in Java,
-- because "today" depends on the app's time zone); after that they're frozen.
ALTER TABLE problems
    ADD COLUMN code                TEXT,
    ADD COLUMN code_language       VARCHAR(20),
    ADD COLUMN time_complexity     VARCHAR(40),
    ADD COLUMN space_complexity    VARCHAR(40),
    ADD COLUMN analysis_reasons    TEXT,
    ADD COLUMN analysis_confidence VARCHAR(10),
    ADD COLUMN analysis_source     VARCHAR(20),
    ADD COLUMN analysed_at         TIMESTAMPTZ,
    ADD CONSTRAINT problems_code_size CHECK (code IS NULL OR length(code) <= 10000),
    ADD CONSTRAINT problems_code_language CHECK (code_language IS NULL OR code_language IN ('JAVA', 'PYTHON', 'JAVASCRIPT', 'CPP')),
    ADD CONSTRAINT problems_analysis_complete CHECK ((time_complexity IS NULL) = (analysed_at IS NULL));
