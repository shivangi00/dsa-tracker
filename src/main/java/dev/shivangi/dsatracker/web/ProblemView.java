package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.domain.Difficulty;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;

import java.time.LocalDate;
import java.util.Map;

/** A problem you've marked done, as the API returns it. Keeps the JPA entity out of the JSON. */
public record ProblemView(
        long id,
        Integer catalogId,
        String name,
        String link,
        String learnings,
        String excalidrawUrl,
        LocalDate solvedOn,
        Difficulty difficulty,
        int intervalDays,
        double ease,
        int reps,
        int lapses,
        LocalDate lastReviewedOn,
        LocalDate nextDueOn,
        long overdueDays,
        boolean mature,
        String code,
        CodeLanguage codeLanguage,
        AnalysisView analysis,
        boolean editable,
        Rating firstRating,
        Map<Rating, Integer> reviewGaps) {

    /**
     * {@code editable}: true only on the day the problem was solved (see Problem#isEditableOn).
     * {@code analysis} carries a recommendation computed now, so improving the approach list
     * improves old analyses too. {@code reviewGaps}: for a review that's due, the gap in days each
     * rating would give (empty otherwise), so the buttons can show it.
     */
    public static ProblemView of(Problem p, long overdueDays, LocalDate today, ApproachRecommender approaches,
                                 SpacedRepetitionPolicy policy) {
        return new ProblemView(p.getId(), p.getCatalogId(), p.getName(), p.getLink(),
                p.getLearnings(), p.getExcalidrawUrl(), p.getSolvedOn(), p.getDifficulty(),
                p.getIntervalDays(), p.getEase(), p.getReps(), p.getLapses(),
                p.getLastReviewedOn(), p.getNextDueOn(), overdueDays,
                p.getIntervalDays() >= SpacedRepetitionPolicy.MATURE_DAYS,
                p.getCode(), p.getCodeLanguage(), AnalysisView.of(p.analysis(), p.getCatalogId(), approaches),
                p.isEditableOn(today), p.getFirstRating(), policy.previewGaps(p.schedule(), today));
    }
}
