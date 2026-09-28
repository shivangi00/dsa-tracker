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

import java.time.LocalDate;

/** One rating given at a review. Never updated, only inserted. */
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

    /** false for Again (or Forgot, before ratings existed); kept for the recall rate. */
    @Column(nullable = false)
    private boolean solved;

    /** Again / Hard / Good / Easy. Null for answers given before ratings existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rating")
    private Rating rating;

    @Column(name = "interval_before")
    private Integer intervalBefore;

    @Column(name = "interval_after")
    private Integer intervalAfter;

    protected RevisionAttempt() {
        // for JPA
    }

    public RevisionAttempt(Long problemId, LocalDate attemptedOn, Rating rating,
                           int intervalBefore, int intervalAfter) {
        this.problemId = problemId;
        this.attemptedOn = attemptedOn;
        this.rating = rating;
        this.solved = rating.solved();
        this.intervalBefore = intervalBefore;
        this.intervalAfter = intervalAfter;
    }

    public Long getId() { return id; }
    public Long getProblemId() { return problemId; }
    public LocalDate getAttemptedOn() { return attemptedOn; }
    public boolean isSolved() { return solved; }
    public Rating getRating() { return rating; }
}
