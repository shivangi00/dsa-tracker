package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
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
     * One question. What the tracker files the problem under (category, pattern, idea, and the
     * NeetCode problem it came from) is only filled in once you've finished the question, so the
     * page can't give it away. Old questions ({@code legacy}) were a four-option quiz: they keep
     * their {@code options} and reveal as soon as a pattern was picked.
     */
    public record Item(
            long id,
            int position,
            PracticeRef problem,
            boolean legacy,
            List<Option> options,
            Integer chosenPatternId,
            String chosenCategory,
            String recognition,
            String code,
            CodeLanguage codeLanguage,
            String feedback,
            Integer correctPatternId,
            String correctPatternName,
            String correctCategory,
            String idea,
            String anchorName,
            TestOutcome outcome) {
    }

    /** @param categories the NeetCode roadmap categories, in roadmap order: the patterns to choose from */
    public record Test(long id, int weekNumber, LocalDate weekStart, LocalDate weekEnd,
                       boolean completed, List<String> categories, List<Item> items) {
    }
}
