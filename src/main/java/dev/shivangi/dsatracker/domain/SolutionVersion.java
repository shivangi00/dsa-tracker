package dev.shivangi.dsatracker.domain;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * One saved version of your solution within an attempt ("Save as new version"). The code never
 * changes once saved; its analysis is kept separately and can be added later that day.
 */
@Entity
@Table(name = "solution_versions")
public class SolutionVersion {

    public static final int MAX_PER_ATTEMPT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_language", nullable = false)
    private CodeLanguage codeLanguage;

    @Column(name = "time_complexity")
    private String timeComplexity;

    @Column(name = "space_complexity")
    private String spaceComplexity;

    /** The analysis steps, one per line. */
    @Column(name = "analysis_reasons")
    private String analysisReasons;

    @Column(name = "analysis_confidence")
    private String analysisConfidence;

    @Column(name = "analysis_source")
    private String analysisSource;

    @Column(name = "analysed_at")
    private Instant analysedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected SolutionVersion() {
        // for JPA
    }

    public SolutionVersion(Long attemptId, int versionNo, String code, CodeLanguage codeLanguage) {
        this.attemptId = attemptId;
        this.versionNo = versionNo;
        this.code = code;
        this.codeLanguage = codeLanguage;
    }

    public void recordAnalysis(ComplexityAnalysis analysis, Instant at) {
        this.timeComplexity = analysis.time();
        this.spaceComplexity = analysis.space();
        this.analysisReasons = String.join("\n", analysis.reasons());
        this.analysisConfidence = analysis.confidence();
        this.analysisSource = analysis.source();
        this.analysedAt = at;
    }

    /** The analysis, or null if this version hasn't been analysed. */
    public ComplexityAnalysis analysis() {
        if (analysedAt == null) {
            return null;
        }
        List<String> reasons = analysisReasons == null || analysisReasons.isEmpty()
                ? List.of() : Arrays.asList(analysisReasons.split("\n"));
        return new ComplexityAnalysis(timeComplexity, spaceComplexity, reasons, analysisConfidence, analysisSource);
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public int getVersionNo() { return versionNo; }
    public String getCode() { return code; }
    public CodeLanguage getCodeLanguage() { return codeLanguage; }
    public Instant getCreatedAt() { return createdAt; }
}
