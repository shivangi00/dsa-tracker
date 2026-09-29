package dev.shivangi.dsatracker.sd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/** One saved version of a written design, with its coverage score. Never changes once saved. */
@Entity
@Table(name = "sd_answers")
public class SdAnswer {

    public static final int MAX_PER_ATTEMPT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    private String requirements;
    private String entities;
    private String api;

    @Column(name = "high_level")
    private String highLevel;

    @Column(name = "deep_dives")
    private String deepDives;

    private BigDecimal score;

    @Column(name = "structure_score")
    private BigDecimal structureScore;

    private String covered;

    protected SdAnswer() {
        // for JPA
    }

    public SdAnswer(Long attemptId, int versionNo, DesignSections s, DesignScorer.Score score) {
        this.attemptId = attemptId;
        this.versionNo = versionNo;
        this.requirements = s.requirements();
        this.entities = s.entities();
        this.api = s.api();
        this.highLevel = s.highLevel();
        this.deepDives = s.deepDives();
        if (score != null) {
            this.score = BigDecimal.valueOf(score.total()).setScale(1, RoundingMode.HALF_UP);
            this.structureScore = BigDecimal.valueOf(score.structure()).setScale(1, RoundingMode.HALF_UP);
            this.covered = String.join(",", score.covered().stream().map(String::valueOf).toList());
        }
    }

    public DesignSections sections() {
        return new DesignSections(requirements, entities, api, highLevel, deepDives);
    }

    /** Indexes of the rubric points this version covered; empty if it wasn't scored. */
    public List<Integer> coveredPoints() {
        return covered == null || covered.isBlank() ? List.of()
                : Arrays.stream(covered.split(",")).map(Integer::valueOf).toList();
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public int getVersionNo() { return versionNo; }
    public Double getScore() { return score == null ? null : score.doubleValue(); }
    public Double getStructureScore() { return structureScore == null ? null : structureScore.doubleValue(); }
}
