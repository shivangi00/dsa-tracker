package dev.shivangi.dsatracker.repetition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * The review schedule, as pure functions (no Spring, no database, no clock). Anki-style (SM-2)
 * gaps driven by your own ratings, fitted to a fixed plan: <b>three revisions per problem, all
 * finished by the last day of the plan</b>.
 *
 * <p><b>Marking a problem done</b> sets the first gap and the ease (how fast gaps grow):
 * <pre>
 *   Forgot (AGAIN)  1 day   ease 2.30
 *   Hard            2 days  ease 2.35
 *   Medium (GOOD)   3 days  ease 2.50
 *   Easy            5 days  ease 2.65
 * </pre>
 *
 * <p><b>Each revision</b> (d = days late, 0 if on time):
 * <ul>
 *   <li><b>Again:</b> the same revision again tomorrow (another try), ease −0.20. It doesn't count
 *       as one of the three.</li>
 *   <li><b>Hard:</b> gap = (gap + d/4) × 1.2, ease −0.15.</li>
 *   <li><b>Good:</b> gap = (gap + d/2) × ease; ease recovers by 0.05 towards 2.5 (no "ease hell").</li>
 *   <li><b>Easy:</b> gap = (gap + d) × ease × 1.3, ease +0.15.</li>
 * </ul>
 * Hard, Good or Easy at the third revision completes the problem: fully revised, nothing more due.
 *
 * <p><b>The deadline.</b> Every gap is capped at (days left in the plan) ÷ (revisions left), so the
 * remaining revisions always fit before the end. A Medium problem solved on day 85 of 100 with
 * three revisions to go: day 88, day 94 (6 days rather than 8), day 100. Problems solved early keep
 * their longer, natural gaps.
 *
 * <p><b>The workload.</b> {@link #spread} brings a review forward by up to a quarter of its gap
 * (at most 3 days) when {@value #MAX_REVIEWS_PER_DAY} reviews are already due that day. Never later,
 * so the deadline still holds.
 */
public final class SpacedRepetitionPolicy {

    public static final int REVISIONS = 3;
    public static final int MAX_REVIEWS_PER_DAY = 6;
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
    /** In {@link #previewGaps}: this rating completes the problem. */
    public static final int COMPLETES = 0;

    private static final Map<Rating, Integer> FIRST_GAP = Map.of(
            Rating.AGAIN, 1, Rating.HARD, 2, Rating.GOOD, 3, Rating.EASY, 5);

    /** First solve with no deadline (tests, or problems outside a plan). */
    public ScheduleState onFirstSolve(LocalDate day, Rating rating) {
        return onFirstSolve(day, rating, null);
    }

    /** First solve: the gap and ease follow how it went, capped so three revisions fit before {@code planEnd}. */
    public ScheduleState onFirstSolve(LocalDate day, Rating rating, LocalDate planEnd) {
        int gap = Math.min(FIRST_GAP.get(rating), cap(day, planEnd, REVISIONS));
        return new ScheduleState(gap, easeAfter(START_EASE, rating), 0, 0, day, day.plusDays(gap));
    }

    /** The first gap for each rating, for the page to show before you choose. */
    public Map<Rating, Integer> firstGaps(LocalDate today, LocalDate planEnd) {
        Map<Rating, Integer> gaps = new EnumMap<>(Rating.class);
        for (Rating r : Rating.values()) {
            gaps.put(r, onFirstSolve(today, r, planEnd).intervalDays());
        }
        return gaps;
    }

    public ScheduleState onReview(ScheduleState s, LocalDate today, Rating rating) {
        return onReview(s, today, rating, null);
    }

    /**
     * Applies a revision rating.
     *
     * @throws IllegalStateException if the revision isn't due yet, or the problem is fully revised
     */
    public ScheduleState onReview(ScheduleState s, LocalDate today, Rating rating, LocalDate planEnd) {
        if (s.nextDueOn() == null) {
            throw new IllegalStateException("This problem is fully revised: all " + REVISIONS + " revisions are done");
        }
        if (!s.isDueOn(today)) {
            throw new IllegalStateException("This review isn't due until " + s.nextDueOn());
        }
        long daysLate = ChronoUnit.DAYS.between(s.nextDueOn(), today);
        double ease = easeAfter(s.ease(), rating);
        if (rating == Rating.AGAIN) {
            return new ScheduleState(1, ease, s.reps(), s.lapses() + 1, today, today.plusDays(1));
        }
        int reps = s.reps() + 1;
        int natural = naturalGap(s, daysLate, rating);
        if (reps >= REVISIONS) {
            // Fully revised: nothing more is due. The gap still says how well it's known (memory stages).
            return new ScheduleState(natural, ease, reps, s.lapses(), today, null);
        }
        int gap = Math.min(natural, cap(today, planEnd, REVISIONS - reps));
        return new ScheduleState(gap, ease, reps, s.lapses(), today, today.plusDays(gap));
    }

    /**
     * The gap each rating would give if you answered today, so the buttons can say "Good · 8 days"
     * as Anki does. {@link #COMPLETES} means that rating finishes the problem. Empty if not due.
     */
    public Map<Rating, Integer> previewGaps(ScheduleState s, LocalDate today, LocalDate planEnd) {
        Map<Rating, Integer> gaps = new EnumMap<>(Rating.class);
        if (s.isDueOn(today)) {
            for (Rating r : Rating.values()) {
                ScheduleState next = onReview(s, today, r, planEnd);
                gaps.put(r, next.nextDueOn() == null ? COMPLETES : next.intervalDays());
            }
        }
        return gaps;
    }

    public Map<Rating, Integer> previewGaps(ScheduleState s, LocalDate today) {
        return previewGaps(s, today, null);
    }

    /**
     * Brings the next review forward when its day is already full ({@value #MAX_REVIEWS_PER_DAY}
     * reviews): up to a quarter of the gap, at most 3 days, never before tomorrow and never later.
     *
     * @param dueCounts reviews already due per day (not counting this problem)
     */
    public ScheduleState spread(ScheduleState s, LocalDate today, Map<LocalDate, Integer> dueCounts) {
        if (s.nextDueOn() == null) {
            return s;
        }
        int gap = (int) ChronoUnit.DAYS.between(today, s.nextDueOn());
        int maxShift = Math.min(3, gap / 4);
        for (int shift = 0; shift <= maxShift; shift++) {
            LocalDate day = s.nextDueOn().minusDays(shift);
            if (dueCounts.getOrDefault(day, 0) < MAX_REVIEWS_PER_DAY) {
                return shift == 0 ? s : new ScheduleState(gap - shift, s.ease(), s.reps(), s.lapses(), s.lastReviewedOn(), day);
            }
        }
        return s;   // every nearby day is full too: keep the natural date
    }

    private static int naturalGap(ScheduleState s, long daysLate, Rating rating) {
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

    /** The longest gap that still leaves room for {@code revisionsLeft} revisions before {@code planEnd}. */
    static int cap(LocalDate today, LocalDate planEnd, int revisionsLeft) {
        if (planEnd == null || revisionsLeft <= 0) {
            return Integer.MAX_VALUE;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, planEnd);
        return (int) Math.max(1, daysLeft / revisionsLeft);
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
     * Fully revised problems are left alone.
     */
    public ScheduleState pullForward(ScheduleState s, LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        if (s.nextDueOn() == null || !s.nextDueOn().isAfter(tomorrow)) {
            return s;
        }
        return new ScheduleState(s.intervalDays(), s.ease(), s.reps(), s.lapses(), s.lastReviewedOn(), tomorrow);
    }

    /** Days past the due date; 0 if not overdue or nothing is due. */
    public long overdueDays(ScheduleState s, LocalDate today) {
        return s.nextDueOn() != null && s.nextDueOn().isBefore(today) ? ChronoUnit.DAYS.between(s.nextDueOn(), today) : 0;
    }

    private static double round2(double x) {
        return Math.round(x * 100) / 100.0;   // keep 2.05, not 2.0499999
    }
}
