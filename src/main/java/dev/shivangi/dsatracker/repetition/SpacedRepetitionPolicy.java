package dev.shivangi.dsatracker.repetition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * An Anki-style (SM-2) spaced-repetition schedule driven by your own ratings, as pure functions
 * (no Spring, no database, no clock). The same problem can be easy for one person and hard for
 * another, so the schedule follows how it went for you, not the problem's official difficulty.
 *
 * <p><b>Marking a problem done</b> sets the first gap and the ease (how fast gaps grow):
 * <pre>
 *   Forgot (AGAIN)  1 day   ease 2.30
 *   Hard            2 days  ease 2.35
 *   Medium (GOOD)   3 days  ease 2.50
 *   Easy            5 days  ease 2.65
 * </pre>
 * Pressing Good at every review after "Medium": 3 → 8 → 20 → 50 → 125 days.
 *
 * <p><b>Each review</b> (d = days late, 0 if on time):
 * <ul>
 *   <li><b>Again:</b> back tomorrow, ease −0.20. Relearning starts over, but the ease
 *       remembers it was hard, so gaps grow more slowly afterwards.</li>
 *   <li><b>Hard:</b> gap = (gap + d/4) × 1.2, ease −0.15.</li>
 *   <li><b>Good:</b> gap = (gap + d/2) × ease. If the ease is below 2.5 it recovers by 0.05, so
 *       a few bad days don't slow a problem down forever (SM-2's "ease hell").</li>
 *   <li><b>Easy:</b> gap = (gap + d) × ease × 1.3, ease +0.15.</li>
 * </ul>
 * Remembering after a delay proves the memory outlasted the schedule, hence the late bonus.
 * Every successful answer grows the gap by at least a day, and Hard &lt; Good &lt; Easy always.
 * Ease never drops below 1.3; gaps are capped at a year. Missing days does nothing by itself:
 * a review simply stays due until you do it.
 */
public final class SpacedRepetitionPolicy {

    public static final double START_EASE = 2.5;
    public static final double MIN_EASE = 1.3;
    public static final double AGAIN_PENALTY = 0.20;
    public static final double HARD_PENALTY = 0.15;
    public static final double EASY_BONUS = 0.15;
    public static final double EASE_RECOVERY = 0.05;   // per Good, while below START_EASE
    public static final double HARD_FACTOR = 1.2;
    public static final double EASY_FACTOR = 1.3;
    public static final int MAX_GAP = 365;
    public static final int STRENGTHENING_DAYS = 7;   // gap of a week or more
    public static final int MATURE_DAYS = 21;         // gap of three weeks or more: long-term memory

    private static final Map<Rating, Integer> FIRST_GAP = Map.of(
            Rating.AGAIN, 1, Rating.HARD, 2, Rating.GOOD, 3, Rating.EASY, 5);

    /** First solve: the gap and ease follow how it went. */
    public ScheduleState onFirstSolve(LocalDate day, Rating rating) {
        int gap = FIRST_GAP.get(rating);
        return new ScheduleState(gap, easeAfter(START_EASE, rating), 0, 0, day, day.plusDays(gap));
    }

    /** The first gap for each rating, for the page to show before you choose. */
    public Map<Rating, Integer> firstGaps() {
        return new EnumMap<>(FIRST_GAP);
    }

    /**
     * Applies a review rating.
     *
     * @throws IllegalStateException if the review isn't due yet
     */
    public ScheduleState onReview(ScheduleState s, LocalDate today, Rating rating) {
        if (!s.isDueOn(today)) {
            throw new IllegalStateException("This review isn't due until " + s.nextDueOn());
        }
        int gap = nextGap(s, ChronoUnit.DAYS.between(s.nextDueOn(), today), rating);
        double ease = easeAfter(s.ease(), rating);
        if (rating == Rating.AGAIN) {
            return new ScheduleState(gap, ease, s.reps(), s.lapses() + 1, today, today.plusDays(gap));
        }
        return new ScheduleState(gap, ease, s.reps() + 1, s.lapses(), today, today.plusDays(gap));
    }

    /**
     * The gap each rating would give if you answered today, so the buttons can say
     * "Good · 8 days" as Anki does. Empty if the review isn't due.
     */
    public Map<Rating, Integer> previewGaps(ScheduleState s, LocalDate today) {
        Map<Rating, Integer> gaps = new EnumMap<>(Rating.class);
        if (s.isDueOn(today)) {
            long daysLate = ChronoUnit.DAYS.between(s.nextDueOn(), today);
            for (Rating r : Rating.values()) {
                gaps.put(r, nextGap(s, daysLate, r));
            }
        }
        return gaps;
    }

    private static int nextGap(ScheduleState s, long daysLate, Rating rating) {
        int gap = s.intervalDays();
        int hard = Math.max(gap + 1, (int) Math.round((gap + daysLate / 4.0) * HARD_FACTOR));
        int good = Math.max(hard + 1, (int) Math.round((gap + daysLate / 2.0) * s.ease()));
        int easy = Math.max(good + 1, (int) Math.round((gap + daysLate) * s.ease() * EASY_FACTOR));
        return switch (rating) {
            case AGAIN -> 1;
            case HARD -> Math.min(MAX_GAP, hard);
            case GOOD -> Math.min(MAX_GAP, good);
            case EASY -> Math.min(MAX_GAP, easy);
        };
    }

    private static double easeAfter(double ease, Rating rating) {
        double next = switch (rating) {
            case AGAIN -> ease - AGAIN_PENALTY;
            case HARD -> ease - HARD_PENALTY;
            case GOOD -> ease < START_EASE ? Math.min(START_EASE, ease + EASE_RECOVERY) : ease;
            case EASY -> ease + EASY_BONUS;
        };
        return Math.max(MIN_EASE, round2(next));
    }

    /**
     * Brings a review forward to tomorrow (if it was due later). Used when you couldn't solve a
     * weekly-test problem with the same pattern: a sign the memory is weaker than scheduled.
     * The gap and ease are unchanged; the next review's rating adjusts them as usual.
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
