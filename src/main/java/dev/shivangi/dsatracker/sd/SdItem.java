package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;

/** One topic studied or one design problem attempted, with its review schedule. */
@Entity
@Table(name = "sd_items")
public class SdItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SdKind kind;

    /** The topic or problem key in sd-topics.json / sd-problems.json. */
    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_rating", nullable = false)
    private Rating firstRating;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private double ease;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "last_reviewed_on", nullable = false)
    private LocalDate lastReviewedOn;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    protected SdItem() {
        // for JPA
    }

    public SdItem(Long userId, SdKind kind, String itemKey, LocalDate startedOn, Rating firstRating, ScheduleState s) {
        this.userId = userId;
        this.kind = kind;
        this.itemKey = itemKey;
        this.startedOn = startedOn;
        this.firstRating = firstRating;
        apply(s);
    }

    public ScheduleState schedule() {
        return new ScheduleState(intervalDays, ease, reps, lapses, lastReviewedOn, nextDueOn);
    }

    public void apply(ScheduleState s) {
        this.intervalDays = s.intervalDays();
        this.ease = s.ease();
        this.reps = s.reps();
        this.lapses = s.lapses();
        this.lastReviewedOn = s.lastReviewedOn();
        this.nextDueOn = s.nextDueOn();
    }

    public int revisionsDone() {
        return Math.min(reps, kind.revisions());
    }

    public boolean isFullyRevised() {
        return reps >= kind.revisions();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public SdKind getKind() { return kind; }
    public String getItemKey() { return itemKey; }
    public LocalDate getStartedOn() { return startedOn; }
    public Rating getFirstRating() { return firstRating; }
    public int getReps() { return reps; }
    public LocalDate getNextDueOn() { return nextDueOn; }
}
