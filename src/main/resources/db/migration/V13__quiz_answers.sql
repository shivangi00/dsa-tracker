-- Keep the answer you chose for each quiz question (one per line, in the same order as
-- quiz_detail), so a past quiz can be shown again with your answers next to the right ones.
-- Quizzes taken before this keep only right/wrong per question.
ALTER TABLE sd_attempts ADD COLUMN quiz_choices TEXT CHECK (length(quiz_choices) <= 4000);
