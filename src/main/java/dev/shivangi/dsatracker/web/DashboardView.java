package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.consistency.ConsistencySummary;
import dev.shivangi.dsatracker.domain.Difficulty;

import java.time.LocalDate;
import java.util.List;

/** The JSON behind GET /api/dashboard, for the signed-in user. */
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
        List<ProblemView> earlierEntries) {

    /** How many solved problems sit at each memory stage, by their current review gap. */
    public record Memory(int learning, int strengthening, int longTerm) {
    }

    /** Reviews in the last {@code days} days, and how many you remembered. */
    public record Recall(int days, int reviews, int remembered) {
    }

    /** {@code done} counts NeetCode problems marked done. */
    public record Counts(long done, long dueToday, long overdue, int target) {
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
