package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One Remembered/Forgot answer on a review. Never updated, only inserted. */
@Entity
@Table(name = "revision_attempts")
public class RevisionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "attempted_on", nullable = false)
    private LocalDate attemptedOn;

    /** true = remembered, false = forgot. */
    @Column(nullable = false)
    private boolean solved;

    @Column(name = "interval_before")
    private Integer intervalBefore;

    @Column(name = "interval_after")
    private Integer intervalAfter;

    protected RevisionAttempt() {
        // for JPA
    }

    public RevisionAttempt(Long problemId, LocalDate attemptedOn, boolean solved,
                           int intervalBefore, int intervalAfter) {
        this.problemId = problemId;
        this.attemptedOn = attemptedOn;
        this.solved = solved;
        this.intervalBefore = intervalBefore;
        this.intervalAfter = intervalAfter;
    }

    public Long getId() { return id; }
    public Long getProblemId() { return problemId; }
    public LocalDate getAttemptedOn() { return attemptedOn; }
    public boolean isSolved() { return solved; }
}
