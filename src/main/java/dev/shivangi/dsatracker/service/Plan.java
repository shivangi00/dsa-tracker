package dev.shivangi.dsatracker.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One user's plan: {@code days} days from {@code start}. New problems can be marked done up to
 * {@code lastNewDay}; everything after that is for revisions, which all finish by {@code end}.
 */
public record Plan(LocalDate start, LocalDate end, LocalDate lastNewDay) {

    public static Plan of(LocalDate start, int days, int lastNewProblemDay) {
        return new Plan(start, start.plusDays(days - 1L), start.plusDays(lastNewProblemDay - 1L));
    }

    /** 1 on the start date; can be below 1 (not started) or above the plan length (finished). */
    public int dayOf(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(start, date) + 1;
    }

    public boolean newProblemsOpen(LocalDate today) {
        return !today.isAfter(lastNewDay);
    }
}
