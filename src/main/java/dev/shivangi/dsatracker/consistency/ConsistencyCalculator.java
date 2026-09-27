package dev.shivangi.dsatracker.consistency;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces the streak. Streaks punish a single missed day by dropping to zero, which is when
 * many people quit. These numbers never collapse:
 *
 * <ul>
 *   <li>Total study days only goes up.</li>
 *   <li>The week counts days against a flexible target (e.g. 5 of 7), so rest days are allowed.</li>
 *   <li>{@code daysAway} powers a gentle "never miss twice" nudge, not a penalty.</li>
 * </ul>
 *
 * Pure Java: no Spring, no database.
 */
public final class ConsistencyCalculator {

    /**
     * @param activeDays   distinct days with any activity, up to and including today
     * @param today        the current date in the app's time zone
     * @param startDate    day 1 of the user's plan
     * @param weeklyTarget study days per plan week to aim for
     */
    public ConsistencySummary summarise(List<LocalDate> activeDays, LocalDate today,
                                        LocalDate startDate, int weeklyTarget) {
        Set<LocalDate> active = new HashSet<>(activeDays);

        int weekNumber = today.isBefore(startDate) ? 0 : (int) (days(startDate, today) / 7) + 1;
        LocalDate weekStart = weekNumber == 0 ? startDate : startDate.plusDays((weekNumber - 1) * 7L);
        LocalDate weekEnd = weekStart.plusDays(6);

        int daysThisWeek = 0;
        for (LocalDate d = weekStart; !d.isAfter(today); d = d.plusDays(1)) {
            if (active.contains(d)) {
                daysThisWeek++;
            }
        }

        // Days missed just before today, counted only from the start date.
        int daysAway = 0;
        for (LocalDate d = today.minusDays(1); !d.isBefore(startDate) && !active.contains(d); d = d.minusDays(1)) {
            daysAway++;
        }

        return new ConsistencySummary(active.size(), weekNumber, weekStart, weekEnd,
                daysThisWeek, weeklyTarget, active.contains(today), daysAway);
    }

    private static long days(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }
}
