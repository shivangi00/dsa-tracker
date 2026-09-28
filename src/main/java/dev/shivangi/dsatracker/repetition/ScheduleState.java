package dev.shivangi.dsatracker.repetition;

import java.time.LocalDate;

/**
 * The review schedule of one problem. Immutable: every rule takes a state and returns
 * a new one, which keeps the rules easy to test without a database.
 *
 * @param intervalDays   the gap that was scheduled after the last review (how long the
 *                       memory is expected to last)
 * @param ease           growth factor applied to the interval on each Good review; set by your
 *                       first rating (2.3–2.65), lowered by Again and Hard, raised by Easy
 * @param reps           successful reviews so far (the first solve doesn't count)
 * @param lapses         times you pressed Again on a review
 * @param lastReviewedOn the day of the last review, or of the first solve
 * @param nextDueOn      the day the next review is due; null once fully revised
 */
public record ScheduleState(
        int intervalDays,
        double ease,
        int reps,
        int lapses,
        LocalDate lastReviewedOn,
        LocalDate nextDueOn) {

    /** True if the problem can be reviewed today (due today or overdue). */
    public boolean isDueOn(LocalDate today) {
        return nextDueOn != null && !nextDueOn.isAfter(today);
    }

    /** "Mature" = the memory is expected to last three weeks or more (Anki's threshold). */
    public boolean isMature() {
        return intervalDays >= SpacedRepetitionPolicy.MATURE_DAYS;
    }
}
