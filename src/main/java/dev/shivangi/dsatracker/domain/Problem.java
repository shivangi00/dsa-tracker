package dev.shivangi.dsatracker.domain;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** One problem a user has solved, with its review schedule. */
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

    /** What you learned, written when you marked it done. */
    @Column(nullable = false)
    private String learnings;

    /** Optional link to your Excalidraw drawing. */
    @Column(name = "excalidraw_url")
    private String excalidrawUrl;

    /** Only set on problems logged before V3, when the form still asked. */
    @Column(name = "solved_myself")
    private Boolean solvedMyself;

    @Column(name = "solved_on", nullable = false)
    private LocalDate solvedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_difficulty", nullable = false)
    private Difficulty difficulty;

    // ---- review schedule (see ScheduleState) ----
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

    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;

    // ---- your code and its analysis (V8) ----
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_language")
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

    protected Problem() {
        // for JPA
    }

    /** A NeetCode problem marked done by {@code userId} on {@code solvedOn}. */
    public Problem(Long userId, CatalogProblem catalog, String learnings, String excalidrawUrl,
                   LocalDate solvedOn, ScheduleState schedule) {
        this.userId = userId;
        this.catalogId = catalog.getId();
        this.name = catalog.getName();
        this.link = catalog.getUrl();
        this.difficulty = catalog.getDifficulty();
        this.learnings = learnings;
        this.excalidrawUrl = excalidrawUrl;
        this.solvedOn = solvedOn;
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

    /**
     * Notes, code and analysis can change only on the day the problem was solved. After that they
     * are frozen: a record of what you understood at the time, which reviews then test.
     */
    public boolean isEditableOn(LocalDate today) {
        return solvedOn.equals(today);
    }

    /**
     * Replaces the notes and code. Changing the code (or its language) clears the old analysis,
     * because it described different code.
     *
     * @throws IllegalStateException if the day the problem was solved is over
     */
    public void editNotes(String learnings, String excalidrawUrl, String code, CodeLanguage language, LocalDate today) {
        requireEditable(today);
        boolean codeChanged = !Objects.equals(this.code, code) || this.codeLanguage != language;
        this.learnings = learnings;
        this.excalidrawUrl = excalidrawUrl;
        this.code = code;
        this.codeLanguage = language;
        if (codeChanged) {
            clearAnalysis();
        }
    }

    /** Stores the analysis of the current code. */
    public void recordAnalysis(ComplexityAnalysis analysis, Instant at, LocalDate today) {
        requireEditable(today);
        this.timeComplexity = analysis.time();
        this.spaceComplexity = analysis.space();
        this.analysisReasons = String.join("\n", analysis.reasons());
        this.analysisConfidence = analysis.confidence();
        this.analysisSource = analysis.source();
        this.analysedAt = at;
    }

    /** The stored analysis, or null if the current code hasn't been analysed. */
    public ComplexityAnalysis analysis() {
        if (analysedAt == null) {
            return null;
        }
        List<String> reasons = analysisReasons == null || analysisReasons.isEmpty()
                ? List.of() : Arrays.asList(analysisReasons.split("\n"));
        return new ComplexityAnalysis(timeComplexity, spaceComplexity, reasons, analysisConfidence, analysisSource);
    }

    private void clearAnalysis() {
        this.timeComplexity = null;
        this.spaceComplexity = null;
        this.analysisReasons = null;
        this.analysisConfidence = null;
        this.analysisSource = null;
        this.analysedAt = null;
    }

    private void requireEditable(LocalDate today) {
        if (!isEditableOn(today)) {
            throw new IllegalStateException("Notes are frozen: they could only be changed on "
                    + solvedOn + ", the day you solved this problem");
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getLink() { return link; }
    public Integer getCatalogId() { return catalogId; }
    public String getLearnings() { return learnings; }
    public String getExcalidrawUrl() { return excalidrawUrl; }
    public Boolean getSolvedMyself() { return solvedMyself; }
    public LocalDate getSolvedOn() { return solvedOn; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getIntervalDays() { return intervalDays; }
    public double getEase() { return ease; }
    public int getReps() { return reps; }
    public int getLapses() { return lapses; }
    public LocalDate getLastReviewedOn() { return lastReviewedOn; }
    public LocalDate getNextDueOn() { return nextDueOn; }
    public String getCode() { return code; }
    public CodeLanguage getCodeLanguage() { return codeLanguage; }
    public Instant getAnalysedAt() { return analysedAt; }
}
