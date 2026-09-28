package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.consistency.ConsistencySummary;
import dev.shivangi.dsatracker.domain.Difficulty;
import dev.shivangi.dsatracker.repetition.Rating;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The JSON behind GET /api/dashboard, for the signed-in user. {@code firstGaps}: days until the first
 * review for each rating in the Mark as done window.
 */
public record DashboardView(
        String username,
        LocalDate today,
        LocalDate planStart,
        int planDays,
        int dayOfPlan,
        ConsistencySummary consistency,
        Memory memory,
        Recall recall,
        Counts counts,
        List<Day> activity,
        List<ProblemView> due,
        List<TestViews.Summary> weeklyTests,
        List<CatalogItem> catalog,
        List<ProblemView> earlierEntries,
        Map<Rating, Integer> firstGaps,
        Plan plan,
        Pace pace,
        List<Workload> workload) {

    /**
     * The plan's key dates: {@code lastNewDay} is the last day for new problems (day 85), {@code end}
     * the day every revision is done by (day 100). {@code revisions} per problem, and the daily
     * review cap the schedule spreads work to.
     */
    public record Plan(LocalDate end, LocalDate lastNewDay, int lastNewDayNumber, boolean newProblemsOpen,
                       int revisions, int maxReviewsPerDay) {
    }

    /**
     * Keeping up with new problems.
     *
     * @param left           NeetCode problems still to solve
     * @param daysLeft       days left for new problems, today included
     * @param neededPerDay   problems per day to finish by the last new-problem day (null if no days left)
     * @param yourPerDay     your average so far
     * @param projectedDay   the plan day you'd finish at your current pace (null if nothing done yet or all done)
     */
    public record Pace(int left, int daysLeft, Double neededPerDay, double yourPerDay, Integer projectedDay) {
    }

    /** Reviews due on a day (today's includes anything overdue). */
    public record Workload(LocalDate date, int reviews) {
    }

    /** How many solved problems sit at each memory stage, by their current review gap. */
    public record Memory(int learning, int strengthening, int longTerm) {
    }

    /** Reviews in the last {@code days} days, and how many you remembered. */
    public record Recall(int days, int reviews, int remembered) {
    }

    /**
     * {@code done} counts NeetCode problems marked done; {@code fullyRevised} those with all 3 revisions
     * done; {@code improved} those where a later version of your code beat an earlier one.
     */
    public record Counts(long done, long dueToday, long overdue, int target, long fullyRevised, long improved) {
    }

    public record Day(LocalDate date, int activities) {
    }

    /**
     * One NeetCode problem plus your progress on it.
     *
     * @param progress null while the problem is still "to do"
     */
    public record CatalogItem(int id, String category, String name, Difficulty difficulty,
                              String url, ProblemView progress) {
    }
}
