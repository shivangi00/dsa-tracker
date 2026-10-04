-- Weekly tests, reworked: instead of a four-option quiz worded differently from what you study,
-- you mark one of the NeetCode roadmap categories (the patterns you study by), write what made
-- you recognise it, add your code, have it reviewed by the AI chatbot of your choice (the page
-- copies a prompt), and paste the feedback back here. Then you record how solving it went.
--
-- Questions answered the old way keep chosen_pattern_id and still display as before.

ALTER TABLE weekly_test_items
    ADD COLUMN chosen_category VARCHAR(40),
    ADD COLUMN recognition     VARCHAR(2000),
    ADD COLUMN code            TEXT CHECK (length(code) <= 10000),
    ADD COLUMN code_language   VARCHAR(20) CHECK (code_language IN ('JAVA', 'PYTHON', 'JAVASCRIPT', 'CPP')),
    ADD COLUMN feedback        TEXT CHECK (length(feedback) <= 10000);

-- The outcome used to require the old pattern answer; now either kind of answer will do.
ALTER TABLE weekly_test_items DROP CONSTRAINT IF EXISTS weekly_test_items_check;
ALTER TABLE weekly_test_items ADD CONSTRAINT weekly_test_items_answer_before_outcome
    CHECK (outcome IS NULL OR chosen_pattern_id IS NOT NULL OR chosen_category IS NOT NULL);
ALTER TABLE weekly_test_items ADD CONSTRAINT weekly_test_items_recognition_with_category
    CHECK (chosen_category IS NULL OR btrim(coalesce(recognition, '')) <> '');
