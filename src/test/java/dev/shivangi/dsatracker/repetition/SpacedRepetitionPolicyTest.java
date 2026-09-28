package dev.shivangi.dsatracker.repetition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.shivangi.dsatracker.repetition.Rating.AGAIN;
import static dev.shivangi.dsatracker.repetition.Rating.EASY;
import static dev.shivangi.dsatracker.repetition.Rating.GOOD;
import static dev.shivangi.dsatracker.repetition.Rating.HARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacedRepetitionPolicyTest {

    private final SpacedRepetitionPolicy policy = new SpacedRepetitionPolicy();
    private final LocalDate day1 = LocalDate.of(2026, 10, 1);

    private LocalDate day(int n) {
        return day1.plusDays(n - 1);
    }

    /** Gaps after the first solve, then after each on-time review with the same rating. */
    private List<Integer> gaps(Rating first, Rating review, int reviews) {
        ScheduleState s = policy.onFirstSolve(day1, first);
        List<Integer> gaps = new ArrayList<>(List.of(s.intervalDays()));
        for (int i = 0; i < reviews; i++) {
            s = policy.onReview(s, s.nextDueOn(), review);
            gaps.add(s.intervalDays());
        }
        return gaps;
    }

    @Test
    void yourFirstRatingSetsTheFirstGap() {
        assertEquals(1, policy.onFirstSolve(day1, AGAIN).intervalDays());
        assertEquals(2, policy.onFirstSolve(day1, HARD).intervalDays());
        assertEquals(3, policy.onFirstSolve(day1, GOOD).intervalDays());
        assertEquals(5, policy.onFirstSolve(day1, EASY).intervalDays());
        assertEquals(day(4), policy.onFirstSolve(day1, GOOD).nextDueOn());
        assertEquals(Map.of(AGAIN, 1, HARD, 2, GOOD, 3, EASY, 5), policy.firstGaps());
    }

    @Test
    void goodEveryTimeGrowsByTheEase() {
        assertEquals(List.of(3, 8, 20, 50, 125), gaps(GOOD, GOOD, 4));
        assertEquals(List.of(5, 13, 34, 90), gaps(EASY, GOOD, 3));
    }

    @Test
    void aHarderStartGrowsMoreSlowlyButCatchesUp() {
        List<Integer> hard = gaps(HARD, GOOD, 4);
        List<Integer> forgot = gaps(AGAIN, GOOD, 4);
        assertEquals(List.of(2, 5, 12, 29, 73), hard);
        assertEquals(List.of(1, 3, 7, 17, 42), forgot);
        // the ease has recovered to the normal 2.5 by then
        ScheduleState s = policy.onFirstSolve(day1, AGAIN);
        for (int i = 0; i < 4; i++) {
            s = policy.onReview(s, s.nextDueOn(), GOOD);
        }
        assertEquals(2.5, s.ease(), 1e-9);
    }

    @Test
    void theFourButtonsAreAlwaysInOrder() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);          // gap 3, due day 4
        Map<Rating, Integer> g = policy.previewGaps(s, day(4));
        assertEquals(1, (int) g.get(AGAIN));
        assertEquals(4, (int) g.get(HARD));                               // 3 × 1.2 = 3.6
        assertEquals(8, (int) g.get(GOOD));                               // 3 × 2.5 = 7.5
        assertEquals(10, (int) g.get(EASY));                              // 3 × 2.5 × 1.3 = 9.75
        assertTrue(g.get(AGAIN) < g.get(HARD) && g.get(HARD) < g.get(GOOD) && g.get(GOOD) < g.get(EASY));
        assertEquals((int) g.get(GOOD), policy.onReview(s, day(4), GOOD).intervalDays());   // preview matches
    }

    @Test
    void hardStillGrowsEvenFromOneDay() {
        ScheduleState s = policy.onFirstSolve(day1, AGAIN);         // gap 1
        assertEquals(2, policy.onReview(s, s.nextDueOn(), HARD).intervalDays());
    }

    @Test
    void againComesBackTomorrowAndSlowsGrowth() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);
        s = policy.onReview(s, s.nextDueOn(), GOOD);                // gap 8
        ScheduleState lapsed = policy.onReview(s, s.nextDueOn(), AGAIN);
        assertEquals(1, lapsed.intervalDays());
        assertEquals(2.3, lapsed.ease(), 1e-9);
        assertEquals(1, lapsed.lapses());
        assertEquals(1, lapsed.reps());                             // successful reviews unchanged
    }

    @Test
    void easyGrowsFasterAndRaisesTheEase() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);
        ScheduleState easy = policy.onReview(s, s.nextDueOn(), EASY);
        assertEquals(10, easy.intervalDays());
        assertEquals(2.65, easy.ease(), 1e-9);
    }

    @Test
    void easeNeverDropsBelowTheFloor() {
        ScheduleState s = policy.onFirstSolve(day1, AGAIN);
        for (int i = 0; i < 20; i++) {
            s = policy.onReview(s, s.nextDueOn(), AGAIN);
        }
        assertEquals(SpacedRepetitionPolicy.MIN_EASE, s.ease(), 1e-9);
        assertEquals(1, s.intervalDays());
    }

    @Test
    void rememberingLateEarnsABonus() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);          // gap 3, due day 4
        assertEquals(8, policy.onReview(s, day(4), GOOD).intervalDays());
        assertEquals(20, policy.onReview(s, day(14), GOOD).intervalDays());   // 10 late: (3 + 5) × 2.5
        assertEquals(1, policy.onReview(s, day(14), AGAIN).intervalDays());   // no bonus for forgetting
    }

    @Test
    void gapsAreCappedAtAYear() {
        ScheduleState s = new ScheduleState(300, 2.5, 6, 0, day1, day(301));
        assertEquals(365, policy.onReview(s, s.nextDueOn(), EASY).intervalDays());
        assertEquals(365, policy.onReview(s, s.nextDueOn(), GOOD).intervalDays());
    }

    @Test
    void cannotReviewBeforeItIsDue() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);
        assertThrows(IllegalStateException.class, () -> policy.onReview(s, day1, GOOD));
        assertTrue(policy.previewGaps(s, day1).isEmpty());
    }

    @Test
    void overdueDaysAndMaturity() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);
        s = policy.onReview(s, s.nextDueOn(), GOOD);                // gap 8, due day 12
        assertEquals(0, policy.overdueDays(s, day(12)));
        assertEquals(5, policy.overdueDays(s, day(17)));
        assertFalse(s.isMature());
        assertFalse(policy.onReview(s, day(12), GOOD).isMature());  // gap 20, just under three weeks
        assertTrue(policy.onReview(s, day(12), EASY).isMature());   // gap 26
    }

    @Test
    void pullForwardBringsALaterReviewToTomorrowOnly() {
        ScheduleState s = policy.onFirstSolve(day1, EASY);          // due day 6
        ScheduleState pulled = policy.pullForward(s, day(2));
        assertEquals(day(3), pulled.nextDueOn());
        assertEquals(5, pulled.intervalDays());                     // gap untouched

        ScheduleState dueSoon = policy.onFirstSolve(day(20), AGAIN);   // due day 21 already
        assertEquals(dueSoon, policy.pullForward(dueSoon, day(20)));
    }
}
