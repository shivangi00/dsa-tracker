package dev.shivangi.dsatracker.domain;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One question in a weekly test: a practice problem and your answers. You mark the pattern (a
 * NeetCode category), say what made you recognise it, add your code, paste an AI review of it,
 * and record how solving it went. Questions from before V14 have a four-option pattern answer instead.
 */
@Entity
@Table(name = "weekly_test_items")
public class WeeklyTestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking: bumped on every update; a stale concurrent update fails with 409. */
    @Version
    private long version;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(nullable = false)
    private int position;

    @Column(name = "practice_problem_id", nullable = false)
    private Integer practiceProblemId;

    /** The right answer. */
    @Column(name = "pattern_id", nullable = false)
    private Integer patternId;

    /** The NeetCode problem you solved that week with the same pattern. */
    @Column(name = "anchor_catalog_id", nullable = false)
    private Integer anchorCatalogId;

    /** Pattern ids in display order, e.g. "7,1,12,3". */
    @Column(name = "option_pattern_ids", nullable = false)
    private String optionPatternIds;

    /** Old-style answer (a four-option quiz); null for questions answered since V14. */
    @Column(name = "chosen_pattern_id")
    private Integer chosenPatternId;

    /** The NeetCode category you marked, e.g. "Sliding Window". */
    @Column(name = "chosen_category")
    private String chosenCategory;

    /** What made you recognise the pattern, in your words. */
    private String recognition;

    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_language")
    private CodeLanguage codeLanguage;

    /** The AI chatbot's review, pasted back by you. */
    private String feedback;

    @Enumerated(EnumType.STRING)
    private TestOutcome outcome;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected WeeklyTestItem() {
        // for JPA
    }

    public WeeklyTestItem(Long testId, int position, int practiceProblemId, int patternId,
                          int anchorCatalogId, List<Integer> options) {
        this.testId = testId;
        this.position = position;
        this.practiceProblemId = practiceProblemId;
        this.patternId = patternId;
        this.anchorCatalogId = anchorCatalogId;
        this.optionPatternIds = options.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public List<Integer> options() {
        return Arrays.stream(optionPatternIds.split(",")).map(Integer::valueOf).toList();
    }

    public void choosePattern(int chosen) {
        this.chosenPatternId = chosen;
    }

    /** Your answer: the category, why, and your code. Can be changed until the outcome is recorded. */
    public void answer(String category, String recognition, String code, CodeLanguage language) {
        if (outcome != null) {
            throw new IllegalStateException("This question is finished");
        }
        this.chosenCategory = category;
        this.recognition = recognition;
        this.code = code;
        this.codeLanguage = code == null ? null : language;
    }

    public void recordFeedback(String feedback) {
        this.feedback = feedback;
    }

    public void recordOutcome(TestOutcome result, Instant now) {
        this.outcome = result;
        this.answeredAt = now;
    }

    /** Answered either way: the old pattern quiz, or a marked category with your reasoning. */
    public boolean patternAnswered() {
        return chosenPatternId != null || chosenCategory != null;
    }

    public boolean isLegacy() {
        return chosenPatternId != null && chosenCategory == null;
    }

    /**
     * Whether you recognised the pattern: the exact pattern for old answers, the category for new ones
     * ({@code correctCategory} is the category of {@link #getPatternId()}).
     */
    public boolean patternCorrect(String correctCategory) {
        return chosenCategory != null ? chosenCategory.equals(correctCategory) : patternId.equals(chosenPatternId);
    }

    public Long getId() { return id; }
    public Long getTestId() { return testId; }
    public int getPosition() { return position; }
    public Integer getPracticeProblemId() { return practiceProblemId; }
    public Integer getPatternId() { return patternId; }
    public Integer getAnchorCatalogId() { return anchorCatalogId; }
    public Integer getChosenPatternId() { return chosenPatternId; }
    public TestOutcome getOutcome() { return outcome; }
    public String getChosenCategory() { return chosenCategory; }
    public String getRecognition() { return recognition; }
    public String getCode() { return code; }
    public CodeLanguage getCodeLanguage() { return codeLanguage; }
    public String getFeedback() { return feedback; }
}
