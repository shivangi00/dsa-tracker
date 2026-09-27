package dev.shivangi.dsatracker.repetition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An adaptive spaced-repetition schedule, as pure functions (no Spring, no database, no clock).
 *
 * <p>Worked example, always remembering and always on time:
 * solve on day 1 → review day 2 (gap 1) → day 6 (gap 4) → day 15 (gap 9) → day 35 (gap 20)
 * → gap 45, and so on, growing by {@link #START_EASE} each time.
 *
 * <ul>
 *   <li><b>Remembered, even if late:</b> the gap grows, plus a bonus of half the days you were
 *       late. Recalling something after a long gap proves the memory is stronger than the
 *       schedule assumed. Gap 20, reviewed 16 days late: 20 × 2.25 + 16 / 2 = 53 days.</li>
 *   <li><b>Forgot:</b> not back to day 1. The gap drops to a short safety interval of
 *       3–7 days (a fifth of the old gap), because relearning is faster than learning.
 *       The ease drops by 0.2 (never below 1.3), so a problem you keep forgetting grows
 *       more slowly.</li>
 *   <li>Missing days does nothing by itself. A review simply stays due until you do it.</li>
 * </ul>
 */
public final class SpacedRepetitionPolicy {

    public static final double START_EASE = 2.25;
    public static final double MIN_EASE = 1.3;
    public static final double EASE_PENALTY = 0.2;
    public static final int FIRST_GAP = 1;          // solve → first review
    public static final int SECOND_GAP = 4;         // first successful review → second
    public static final double LATE_BONUS = 0.5;    // share of the days late added on success
    public static final int MIN_LAPSE_GAP = 3;
    public static final int MAX_LAPSE_GAP = 7;
    public static final double LAPSE_FACTOR = 0.2;
    public static final int MAX_GAP = 365;
    public static final int STRENGTHENING_DAYS = 7;   // gap of a week or more
    public static final int MATURE_DAYS = 21;         // gap of three weeks or more: long-term memory

    /** First solve: review tomorrow. */
    public ScheduleState onFirstSolve(LocalDate day) {
        return new ScheduleState(FIRST_GAP, START_EASE, 0, 0, day, day.plusDays(FIRST_GAP));
    }

    /**
     * Applies a review answer.
     *
     * @throws IllegalStateException if the review isn't due yet
     */
    public ScheduleState onReview(ScheduleState s, LocalDate today, boolean remembered) {
        if (!s.isDueOn(today)) {
            throw new IllegalStateException("This review isn't due until " + s.nextDueOn());
        }
        long daysLate = ChronoUnit.DAYS.between(s.nextDueOn(), today);

        if (remembered) {
            double base = s.reps() == 0 ? SECOND_GAP : s.intervalDays() * s.ease();
            int gap = (int) Math.round(base + LATE_BONUS * daysLate);
            gap = Math.min(MAX_GAP, Math.max(gap, s.intervalDays() + 1));   // always grows
            return new ScheduleState(gap, s.ease(), s.reps() + 1, s.lapses(), today, today.plusDays(gap));
        }

        int gap = (int) Math.round(s.intervalDays() * LAPSE_FACTOR);
        gap = Math.max(MIN_LAPSE_GAP, Math.min(MAX_LAPSE_GAP, gap));
        double ease = Math.max(MIN_EASE, round2(s.ease() - EASE_PENALTY));
        return new ScheduleState(gap, ease, s.reps(), s.lapses() + 1, today, today.plusDays(gap));
    }

    /**
     * Brings a review forward to tomorrow (if it was due later). Used when you couldn't solve a
     * weekly-test problem with the same pattern: a sign the memory is weaker than scheduled.
     * The gap and ease are unchanged; the next review's answer adjusts them as usual.
     */
    public ScheduleState pullForward(ScheduleState s, LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        if (!s.nextDueOn().isAfter(tomorrow)) {
            return s;
        }
        return new ScheduleState(s.intervalDays(), s.ease(), s.reps(), s.lapses(), s.lastReviewedOn(), tomorrow);
    }

    /** Days past the due date; 0 if not overdue. */
    public long overdueDays(ScheduleState s, LocalDate today) {
        return s.nextDueOn().isBefore(today) ? ChronoUnit.DAYS.between(s.nextDueOn(), today) : 0;
    }

    private static double round2(double x) {
        return Math.round(x * 100) / 100.0;   // keep 2.05, not 2.0499999
    }
}
