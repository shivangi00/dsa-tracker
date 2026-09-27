package dev.shivangi.dsatracker.domain;

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
 * One question in a weekly test: a practice problem, the pattern options, and your answers.
 * Answered in two steps: first which pattern fits, then how solving it went.
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

    @Column(name = "chosen_pattern_id")
    private Integer chosenPatternId;

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

    public void recordOutcome(TestOutcome result, Instant now) {
        this.outcome = result;
        this.answeredAt = now;
    }

    public boolean patternAnswered() {
        return chosenPatternId != null;
    }

    public boolean patternCorrect() {
        return patternId.equals(chosenPatternId);
    }

    public Long getId() { return id; }
    public Long getTestId() { return testId; }
    public int getPosition() { return position; }
    public Integer getPracticeProblemId() { return practiceProblemId; }
    public Integer getPatternId() { return patternId; }
    public Integer getAnchorCatalogId() { return anchorCatalogId; }
    public Integer getChosenPatternId() { return chosenPatternId; }
    public TestOutcome getOutcome() { return outcome; }
}
