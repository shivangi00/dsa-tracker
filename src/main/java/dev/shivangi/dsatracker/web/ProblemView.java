package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.domain.Difficulty;
import dev.shivangi.dsatracker.repetition.Rating;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A problem you've marked done, as the API returns it: its schedule and its full attempt history.
 *
 * @param nextDueOn     the next revision, or null once fully revised
 * @param revisionsDone 0 to 3; the table's Revision 1–3 columns
 * @param reviewGaps    for a revision that's due: the gap in days each rating would give
 *                      (0 = that rating completes the problem); empty otherwise
 * @param attempts      the solve day first, then each revision try, oldest first
 */
public record ProblemView(
        long id,
        Integer catalogId,
        String name,
        String link,
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
        int revisionsDone,
        boolean fullyRevised,
        Rating firstRating,
        Map<Rating, Integer> reviewGaps,
        List<AttemptView> attempts,
        List<String> interviewTips) {
}
