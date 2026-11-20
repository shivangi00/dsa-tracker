-- Your own answer for a saved version's complexity ("Your complexity"), separate from the
-- built-in estimate. When set, it's what the tracker compares with the best known approaches and
-- uses for the Improved tag; the estimate is only a second opinion. Can be set or corrected on any
-- day, unlike the code itself.
ALTER TABLE solution_versions
    ADD COLUMN your_time      VARCHAR(60),
    ADD COLUMN your_space     VARCHAR(60),
    ADD COLUMN your_set_at    TIMESTAMPTZ;
