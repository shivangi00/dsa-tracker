package dev.shivangi.dsatracker.consistency;

import java.time.LocalDate;

/**
 * Motivation numbers that reward showing up without punishing a missed day.
 *
 * @param totalStudyDays days you've ever studied; it only ever goes up
 * @param weekNumber     current week of your plan (1 = the week starting on your start date), 0 before it
 * @param weekStart      first day of the current plan week
 * @param weekEnd        last day of the current plan week
 * @param daysThisWeek   days studied so far this plan week
 * @param weeklyTarget   flexible weekly goal (5 means two rest days are built in)
 * @param activeToday    whether you've studied today (the daily minimum: one review or one problem)
 * @param daysAway       whole days missed just before today (0 if you studied yesterday or started today)
 */
public record ConsistencySummary(
        int totalStudyDays,
        int weekNumber,
        LocalDate weekStart,
        LocalDate weekEnd,
        int daysThisWeek,
        int weeklyTarget,
        boolean activeToday,
        int daysAway) {
}
