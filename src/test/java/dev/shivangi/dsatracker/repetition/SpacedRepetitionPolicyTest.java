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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        for (int i = 0; i < reviews && s.nextDueOn() != null; i++) {
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
        assertEquals(Map.of(AGAIN, 1, HARD, 2, GOOD, 3, EASY, 5), policy.firstGaps(day1, null));
    }

    @Test
    void goodEveryTimeGrowsByTheEase() {
        assertEquals(List.of(3, 8, 20, 50), gaps(GOOD, GOOD, 4));   // the third revision completes it
        assertEquals(List.of(5, 13, 34, 90), gaps(EASY, GOOD, 3));
    }

    @Test
    void aHarderStartGrowsMoreSlowly() {
        assertEquals(List.of(2, 5, 12, 29), gaps(HARD, GOOD, 3));
        assertEquals(List.of(1, 3, 7, 17), gaps(AGAIN, GOOD, 3));
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
        ScheduleState s = new ScheduleState(300, 2.5, 1, 0, day1, day(301));
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

    // ---------- three revisions, finished by the end of the plan

    @Test
    void theThirdSuccessfulRevisionCompletesIt() {
        ScheduleState s = policy.onFirstSolve(day1, GOOD);
        s = policy.onReview(s, s.nextDueOn(), GOOD);
        s = policy.onReview(s, s.nextDueOn(), AGAIN);                // doesn't count
        assertEquals(1, s.reps());
        s = policy.onReview(s, s.nextDueOn(), HARD);
        assertEquals(2, s.reps());
        ScheduleState done = policy.onReview(s, s.nextDueOn(), GOOD);
        assertEquals(3, done.reps());
        assertNull(done.nextDueOn());
        assertFalse(done.isDueOn(day(400)));
        assertEquals(0, policy.overdueDays(done, day(400)));
        assertThrows(IllegalStateException.class, () -> policy.onReview(done, day(400), GOOD));
        assertEquals(done, policy.pullForward(done, day(400)));
    }

    @Test
    void theLastRevisionsButtonsSayTheyComplete() {
        ScheduleState s = new ScheduleState(8, 2.5, 2, 0, day1, day(9));
        Map<Rating, Integer> g = policy.previewGaps(s, day(9));
        assertEquals(1, (int) g.get(AGAIN));
        assertEquals(SpacedRepetitionPolicy.COMPLETES, (int) g.get(HARD));
        assertEquals(SpacedRepetitionPolicy.COMPLETES, (int) g.get(EASY));
    }

    @Test
    void lateProblemsAreSqueezedToFinishByTheEndOfThePlan() {
        LocalDate planEnd = day(100);
        ScheduleState s = policy.onFirstSolve(day(85), GOOD, planEnd);  // 15 days left, 3 revisions: gaps ≤ 5
        assertEquals(day(88), s.nextDueOn());
        s = policy.onReview(s, day(88), GOOD, planEnd);                 // natural 8, but 12 days / 2 left = 6
        assertEquals(day(94), s.nextDueOn());
        s = policy.onReview(s, day(94), GOOD, planEnd);                 // natural 15, but 6 days / 1 left = 6
        assertEquals(day(100), s.nextDueOn());
        assertNull(policy.onReview(s, day(100), GOOD, planEnd).nextDueOn());
    }

    @Test
    void earlyProblemsKeepTheirNaturalGaps() {
        LocalDate planEnd = day(100);
        ScheduleState s = policy.onFirstSolve(day1, GOOD, planEnd);
        s = policy.onReview(s, s.nextDueOn(), GOOD, planEnd);
        assertEquals(8, s.intervalDays());
        s = policy.onReview(s, s.nextDueOn(), GOOD, planEnd);
        assertEquals(20, s.intervalDays());
    }

    @Test
    void theDeadlineCapNeverGoesBelowOneDay() {
        ScheduleState s = policy.onFirstSolve(day(99), EASY, day(100));
        assertEquals(1, s.intervalDays());
        ScheduleState late = policy.onFirstSolve(day(120), EASY, day(100));   // past the end: still tomorrow
        assertEquals(1, late.intervalDays());
    }

    @Test
    void firstGapsFollowTheDeadlineToo() {
        assertEquals(Map.of(AGAIN, 1, HARD, 2, GOOD, 3, EASY, 5), policy.firstGaps(day1, day(100)));
        assertEquals(Map.of(AGAIN, 1, HARD, 2, GOOD, 3, EASY, 3), policy.firstGaps(day(90), day(100)));  // 10 / 3
    }

    // ---------- spreading the workload

    @Test
    void aFullDayPushesTheReviewEarlier() {
        ScheduleState s = new ScheduleState(20, 2.5, 1, 0, day1, day(21));
        ScheduleState spread = policy.spread(s, day1, Map.of(day(21), 6));
        assertEquals(day(20), spread.nextDueOn());
        assertEquals(19, spread.intervalDays());
    }

    @Test
    void spreadingNeverMovesAReviewLaterOrTooFar() {
        ScheduleState s = new ScheduleState(20, 2.5, 1, 0, day1, day(21));
        Map<LocalDate, Integer> full = Map.of(day(21), 6, day(20), 6, day(19), 6, day(18), 6);
        assertEquals(s, policy.spread(s, day1, full));                  // at most 3 days earlier: keep the date
        ScheduleState shortGap = new ScheduleState(3, 2.5, 0, 0, day1, day(4));
        assertEquals(shortGap, policy.spread(shortGap, day1, Map.of(day(4), 9)));   // gap 3: no room to move
        assertEquals(s, policy.spread(s, day1, Map.of(day(21), 5)));    // under the cap: unchanged
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

    @Test
    void twoRevisionPolicyCompletesAfterTheSecond() {
        SpacedRepetitionPolicy two = new SpacedRepetitionPolicy(2);
        assertEquals(2, two.revisions());
        ScheduleState s = two.onFirstSolve(day1, GOOD, day(100));
        s = two.onReview(s, s.nextDueOn(), GOOD, day(100));
        assertEquals(1, s.reps());
        assertTrue(s.nextDueOn() != null);
        s = two.onReview(s, s.nextDueOn(), GOOD, day(100));
        assertEquals(2, s.reps());
        assertNull(s.nextDueOn());
        ScheduleState done = s;
        assertThrows(IllegalStateException.class, () -> two.onReview(done, day(100), GOOD, day(100)));
    }

    @Test
    void twoRevisionDeadlineSplitsTheDaysLeftInTwo() {
        SpacedRepetitionPolicy two = new SpacedRepetitionPolicy(2);
        // Day 90 of 100: 10 days left, 2 revisions, so no gap longer than 5; Easy (5) fits exactly.
        assertEquals(5, two.onFirstSolve(day(90), EASY, day(100)).intervalDays());
        // With three revisions the same day allows only 3.
        assertEquals(3, policy.onFirstSolve(day(90), EASY, day(100)).intervalDays());
    }

    @Test
    void atLeastOneRevision() {
        assertThrows(IllegalArgumentException.class, () -> new SpacedRepetitionPolicy(0));
    }
}
