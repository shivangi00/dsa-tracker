package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.domain.Difficulty;
import dev.shivangi.dsatracker.domain.TestOutcome;

import java.time.LocalDate;
import java.util.List;

/** JSON shapes for weekly tests. */
public final class TestViews {

    private TestViews() {
    }

    /** A row in the dashboard's "Weekly tests" list. */
    public record Summary(
            int weekNumber,
            LocalDate weekStart,
            LocalDate weekEnd,
            String status,              // UPCOMING, AVAILABLE, IN_PROGRESS, COMPLETED
            int problemsDone,           // problems marked done that week
            Long testId,
            int items,
            int answered,
            int patternsRight,
            int solved,                 // solved without help
            int withHint) {
    }

    public record Option(int id, String name) {
    }

    public record PracticeRef(String name, Integer leetcodeNumber, Difficulty difficulty, String url) {
    }

    /**
     * One question. The right pattern, its explanation and the NeetCode problem it came from
     * are only filled in after you've picked a pattern, so the page can't give the answer away.
     */
    public record Item(
            long id,
            int position,
            PracticeRef problem,
            List<Option> options,
            Integer chosenPatternId,
            Integer correctPatternId,
            String correctPatternName,
            String idea,
            String anchorName,
            TestOutcome outcome) {
    }

    public record Test(long id, int weekNumber, LocalDate weekStart, LocalDate weekEnd,
                       boolean completed, List<Item> items) {
    }
}
