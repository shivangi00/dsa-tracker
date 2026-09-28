package dev.shivangi.dsatracker.domain;

import dev.shivangi.dsatracker.repetition.Rating;
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

/**
 * One sitting with a problem: the day you solved it (revision 0) or one of its three revisions.
 * "Again" at a revision repeats it, as another try of the same revision. Editable on its own day,
 * frozen after: a record of what you understood at the time.
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    public static final int SOLVE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    /** 0 = solve day, 1..3 = revision. */
    @Column(nullable = false)
    private int revision;

    @Column(name = "try_no", nullable = false)
    private int tryNo;

    @Column(name = "attempted_on", nullable = false)
    private LocalDate attemptedOn;

    @Enumerated(EnumType.STRING)
    private Rating rating;

    /** Required on the solve day, optional at revisions. */
    private String learnings;

    @Column(name = "excalidraw_url")
    private String excalidrawUrl;

    protected Attempt() {
        // for JPA
    }

    public Attempt(Long problemId, int revision, int tryNo, LocalDate attemptedOn, Rating rating,
                   String learnings, String excalidrawUrl) {
        this.problemId = problemId;
        this.revision = revision;
        this.tryNo = tryNo;
        this.attemptedOn = attemptedOn;
        this.rating = rating;
        this.learnings = learnings;
        this.excalidrawUrl = excalidrawUrl;
    }

    public boolean isEditableOn(LocalDate today) {
        return attemptedOn.equals(today);
    }

    /** @throws IllegalStateException once the day is over */
    public void requireEditable(LocalDate today) {
        if (!isEditableOn(today)) {
            throw new IllegalStateException("Frozen: this " + (revision == SOLVE ? "solve" : "revision")
                    + " could only be changed on " + attemptedOn + ", the day you did it");
        }
    }

    public void editNotes(String learnings, String excalidrawUrl, LocalDate today) {
        requireEditable(today);
        this.learnings = learnings;
        this.excalidrawUrl = excalidrawUrl;
    }

    public boolean isSolve() {
        return revision == SOLVE;
    }

    public Long getId() { return id; }
    public Long getProblemId() { return problemId; }
    public int getRevision() { return revision; }
    public int getTryNo() { return tryNo; }
    public LocalDate getAttemptedOn() { return attemptedOn; }
    public Rating getRating() { return rating; }
    public String getLearnings() { return learnings; }
    public String getExcalidrawUrl() { return excalidrawUrl; }
}
