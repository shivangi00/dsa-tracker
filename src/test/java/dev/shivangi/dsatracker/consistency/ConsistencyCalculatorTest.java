package dev.shivangi.dsatracker.consistency;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyCalculatorTest {

    private final ConsistencyCalculator calc = new ConsistencyCalculator();
    private final LocalDate start = LocalDate.of(2026, 10, 1);   // day 1 of the plan

    private LocalDate day(int n) {
        return start.plusDays(n - 1);
    }

    @Test
    void totalStudyDaysNeverDropsAfterAMissedDay() {
        List<LocalDate> active = List.of(day(1), day(2), day(3));
        assertEquals(3, calc.summarise(active, day(4), start, 5).totalStudyDays());
        assertEquals(3, calc.summarise(active, day(9), start, 5).totalStudyDays());   // a week off: still 3
    }

    @Test
    void weeksRunFromTheStartDate() {
        ConsistencySummary s = calc.summarise(List.of(), day(8), start, 5);
        assertEquals(2, s.weekNumber());
        assertEquals(day(8), s.weekStart());
        assertEquals(day(14), s.weekEnd());
    }

    @Test
    void daysThisWeekCountOnlyTheCurrentPlanWeek() {
        List<LocalDate> active = List.of(day(5), day(6), day(8), day(10));
        ConsistencySummary s = calc.summarise(active, day(10), start, 5);
        assertEquals(2, s.daysThisWeek());                                     // days 8 and 10
        assertTrue(s.activeToday());
    }

    @Test
    void missingOneDayGivesANudgeNotAPenalty() {
        ConsistencySummary s = calc.summarise(List.of(day(1), day(2)), day(4), start, 5);
        assertEquals(1, s.daysAway());                                         // missed day 3
        assertFalse(s.activeToday());
        assertEquals(2, s.totalStudyDays());
    }

    @Test
    void daysAwayCountFromTheStartDateOnly() {
        LocalDate lateStart = day(10);
        ConsistencySummary s = calc.summarise(List.of(day(1)), day(12), lateStart, 5);
        assertEquals(2, s.daysAway());                                         // days 10 and 11, not 2–11
    }

    @Test
    void beforeTheStartDateThereIsNoWeekAndNothingMissed() {
        ConsistencySummary s = calc.summarise(List.of(), start.minusDays(3), start, 5);
        assertEquals(0, s.weekNumber());
        assertEquals(0, s.daysAway());
    }

    @Test
    void studyingTodayMeansNotAway() {
        ConsistencySummary s = calc.summarise(List.of(day(3), day(4)), day(4), start, 5);
        assertEquals(0, s.daysAway());
        assertTrue(s.activeToday());
    }
}
