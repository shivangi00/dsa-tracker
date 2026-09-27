package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/** One user's test for one week of their plan. */
@Entity
@Table(name = "weekly_tests")
public class WeeklyTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking: bumped on every update; a stale concurrent update fails with 409. */
    @Version
    private long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WeeklyTest() {
        // for JPA
    }

    public WeeklyTest(Long userId, int weekNumber, LocalDate weekStart, LocalDate weekEnd) {
        this.userId = userId;
        this.weekNumber = weekNumber;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
    }

    public void markCompleted(Instant now) {
        this.completedAt = now;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getWeekNumber() { return weekNumber; }
    public LocalDate getWeekStart() { return weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; }
}
