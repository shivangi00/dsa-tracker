package dev.shivangi.dsatracker.repetition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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

    /** Solve on day 1, then remember on time every time. */
    private ScheduleState onTimeUpTo(int reviews) {
        ScheduleState s = policy.onFirstSolve(day1);
        for (int i = 0; i < reviews; i++) {
            s = policy.onReview(s, s.nextDueOn(), true);
        }
        return s;
    }

    @Test
    void theAvoirExampleOnTime_1_4_9_20() {
        ScheduleState s = policy.onFirstSolve(day1);
        assertEquals(day(2), s.nextDueOn());

        s = policy.onReview(s, day(2), true);
        assertEquals(4, s.intervalDays());
        assertEquals(day(6), s.nextDueOn());

        s = policy.onReview(s, day(6), true);
        assertEquals(9, s.intervalDays());
        assertEquals(day(15), s.nextDueOn());

        s = policy.onReview(s, day(15), true);
        assertEquals(20, s.intervalDays());
        assertEquals(day(35), s.nextDueOn());
        assertEquals(3, s.reps());
    }

    @Test
    void rememberingLateEarnsABiggerGap() {
        ScheduleState s = onTimeUpTo(3);                                   // gap 20, due day 35
        ScheduleState late = policy.onReview(s, day(51), true);            // 16 days late
        assertEquals(53, late.intervalDays());                             // 20 × 2.25 + 16 / 2
        assertEquals(day(51).plusDays(53), late.nextDueOn());

        ScheduleState onTime = policy.onReview(s, day(35), true);
        assertEquals(45, onTime.intervalDays());                           // 20 × 2.25
    }

    @Test
    void forgettingDropsToASafetyGapNotBackToDayOne() {
        ScheduleState s = onTimeUpTo(3);                                   // gap 20
        ScheduleState lapsed = policy.onReview(s, day(51), false);
        assertEquals(4, lapsed.intervalDays());                            // 20 × 0.2, within 3–7
        assertEquals(1, lapsed.lapses());
        assertEquals(3, lapsed.reps());                                    // earlier successes still count
        assertEquals(2.05, lapsed.ease(), 1e-9);                           // 2.25 − 0.2
    }

    @Test
    void safetyGapIsClampedBetween3And7Days() {
        ScheduleState young = policy.onFirstSolve(day1);                   // gap 1
        assertEquals(3, policy.onReview(young, day(2), false).intervalDays());

        ScheduleState old = new ScheduleState(120, 2.25, 6, 0, day1, day1.plusDays(120));
        assertEquals(7, policy.onReview(old, old.nextDueOn(), false).intervalDays());
    }

    @Test
    void relearningIsFasterThanLearning() {
        ScheduleState s = policy.onReview(onTimeUpTo(3), day(51), false);  // gap 4 after the lapse
        s = policy.onReview(s, s.nextDueOn(), true);
        assertEquals(8, s.intervalDays());                                 // 4 × 2.05
        s = policy.onReview(s, s.nextDueOn(), true);
        assertEquals(16, s.intervalDays());                                // 8 × 2.05
        s = policy.onReview(s, s.nextDueOn(), true);
        assertTrue(s.isMature());                                          // back above 21 days in 3 steps
    }

    @Test
    void easeNeverFallsBelowTheFloor() {
        ScheduleState s = policy.onFirstSolve(day1);
        for (int i = 0; i < 10; i++) {
            s = policy.onReview(s, s.nextDueOn(), false);
        }
        assertEquals(SpacedRepetitionPolicy.MIN_EASE, s.ease(), 1e-9);
        assertEquals(10, s.lapses());
    }

    @Test
    void gapIsCappedAtAYear() {
        ScheduleState s = new ScheduleState(300, 2.25, 8, 0, day1, day1.plusDays(300));
        assertEquals(365, policy.onReview(s, s.nextDueOn(), true).intervalDays());
    }

    @Test
    void cannotReviewBeforeItIsDue() {
        ScheduleState s = policy.onFirstSolve(day1);
        assertThrows(IllegalStateException.class, () -> policy.onReview(s, day1, true));
    }

    @Test
    void overdueDaysAndMaturity() {
        ScheduleState s = onTimeUpTo(3);                                   // due day 35, gap 20
        assertEquals(0, policy.overdueDays(s, day(35)));
        assertEquals(16, policy.overdueDays(s, day(51)));
        assertFalse(s.isMature());
        assertTrue(policy.onReview(s, day(35), true).isMature());          // gap 45
    }

    @Test
    void pullForwardBringsALaterReviewToTomorrowOnly() {
        ScheduleState s = onTimeUpTo(3);                                   // due day 35, gap 20
        ScheduleState pulled = policy.pullForward(s, day(20));
        assertEquals(day(21), pulled.nextDueOn());
        assertEquals(20, pulled.intervalDays());                           // gap untouched

        ScheduleState dueSoon = policy.onFirstSolve(day(20));              // due day 21 already
        assertEquals(dueSoon, policy.pullForward(dueSoon, day(20)));
    }
}
