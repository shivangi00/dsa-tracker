package dev.shivangi.dsatracker.domain;

import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One problem a user has solved, with its review schedule. What happened at each sitting (notes,
 * code versions, analyses) lives in {@link Attempt}s: the solve day and the three revisions.
 */
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking: bumped on every update; a stale concurrent update fails with 409. */
    @Version
    private long version;

    /** The owner. Only null for rows logged before accounts existed and not yet claimed. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String link;

    /** Which NeetCode 150 problem this is; null for problems logged by hand before V3. */
    @Column(name = "catalog_id")
    private Integer catalogId;

    /** Only set on problems logged before V3, when the form still asked. */
    @Column(name = "solved_myself")
    private Boolean solvedMyself;

    @Column(name = "solved_on", nullable = false)
    private LocalDate solvedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_difficulty", nullable = false)
    private Difficulty difficulty;

    /** How the first solve went, in your judgement. Null for problems logged before ratings existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "first_rating")
    private Rating firstRating;

    // ---- review schedule (see ScheduleState) ----
    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private double ease;

    /** Successful revisions; 3 means fully revised. */
    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "last_reviewed_on", nullable = false)
    private LocalDate lastReviewedOn;

    /** Null once fully revised: nothing left to review. */
    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Problem() {
        // for JPA
    }

    /** A NeetCode problem marked done by {@code userId} on {@code solvedOn}. */
    public Problem(Long userId, CatalogProblem catalog, LocalDate solvedOn, Rating firstRating, ScheduleState schedule) {
        this.userId = userId;
        this.catalogId = catalog.getId();
        this.name = catalog.getName();
        this.link = catalog.getUrl();
        this.difficulty = catalog.getDifficulty();
        this.solvedOn = solvedOn;
        this.firstRating = firstRating;
        apply(schedule);
    }

    /** The schedule fields as an immutable value the policy can work on. */
    public ScheduleState schedule() {
        return new ScheduleState(intervalDays, ease, reps, lapses, lastReviewedOn, nextDueOn);
    }

    /** Copies a new schedule back onto the row. */
    public void apply(ScheduleState s) {
        this.intervalDays = s.intervalDays();
        this.ease = s.ease();
        this.reps = s.reps();
        this.lapses = s.lapses();
        this.lastReviewedOn = s.lastReviewedOn();
        this.nextDueOn = s.nextDueOn();
    }

    /** Revisions done so far, 0 to 3. */
    public int revisionsDone() {
        return Math.min(reps, SpacedRepetitionPolicy.REVISIONS);
    }

    public boolean isFullyRevised() {
        return reps >= SpacedRepetitionPolicy.REVISIONS;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getLink() { return link; }
    public Integer getCatalogId() { return catalogId; }
    public Boolean getSolvedMyself() { return solvedMyself; }
    public LocalDate getSolvedOn() { return solvedOn; }
    public Difficulty getDifficulty() { return difficulty; }
    public Rating getFirstRating() { return firstRating; }
    public int getIntervalDays() { return intervalDays; }
    public double getEase() { return ease; }
    public int getReps() { return reps; }
    public int getLapses() { return lapses; }
    public LocalDate getLastReviewedOn() { return lastReviewedOn; }
    public LocalDate getNextDueOn() { return nextDueOn; }
}
