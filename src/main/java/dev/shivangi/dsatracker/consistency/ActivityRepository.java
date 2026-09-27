package dev.shivangi.dsatracker.consistency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only SQL over one user's history. An "activity" is a problem marked done or a review
 * answered; a day with at least one activity is a study day.
 */
@Repository
public class ActivityRepository {

    /** Both placeholders are the user id. Reviews are joined to their problem to find the owner. */
    private static final String ACTIVITY = """
            SELECT p.solved_on AS day FROM problems p WHERE p.user_id = ?
            UNION ALL
            SELECT a.attempted_on AS day
            FROM revision_attempts a JOIN problems p ON p.id = a.problem_id
            WHERE p.user_id = ?
            """;

    private static final String ACTIVE_DAYS = """
            SELECT DISTINCT day FROM (%s) a WHERE day <= ? ORDER BY day
            """.formatted(ACTIVITY);

    private static final String COUNTS_PER_DAY = """
            SELECT day, COUNT(*) AS activities
            FROM (%s) a
            WHERE day BETWEEN ? AND ?
            GROUP BY day
            ORDER BY day
            """.formatted(ACTIVITY);

    /** FILTER counts only the rows that match, in the same pass. */
    private static final String REVIEW_STATS = """
            SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE a.solved) AS remembered
            FROM revision_attempts a JOIN problems p ON p.id = a.problem_id
            WHERE p.user_id = ? AND a.attempted_on >= ?
            """;

    private final JdbcTemplate jdbc;

    public ActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every distinct study day up to and including {@code today}, oldest first. */
    public List<LocalDate> activeDays(Long userId, LocalDate today) {
        return jdbc.query(ACTIVE_DAYS, (rs, i) -> rs.getObject("day", LocalDate.class), userId, userId, today);
    }

    public List<DayActivity> countsBetween(Long userId, LocalDate from, LocalDate to) {
        return jdbc.query(COUNTS_PER_DAY, (rs, i) -> new DayActivity(
                rs.getObject("day", LocalDate.class),
                rs.getInt("activities")), userId, userId, from, to);
    }

    /** How many reviews since {@code since}, and how many of them were remembered. */
    public ReviewStats reviewStats(Long userId, LocalDate since) {
        List<ReviewStats> rows = jdbc.query(REVIEW_STATS, (rs, i) -> new ReviewStats(
                rs.getInt("total"), rs.getInt("remembered")), userId, since);
        return rows.isEmpty() ? new ReviewStats(0, 0) : rows.get(0);
    }

    public record DayActivity(LocalDate day, int activities) {
    }

    public record ReviewStats(int total, int remembered) {
    }
}
