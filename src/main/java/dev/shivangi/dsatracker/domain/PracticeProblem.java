package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A LeetCode problem outside the NeetCode 150 that uses a known pattern; used in weekly tests. */
@Entity
@Table(name = "practice_problems")
public class PracticeProblem {

    @Id
    private Integer id;

    @Column(name = "pattern_id", nullable = false)
    private Integer patternId;

    @Column(nullable = false)
    private String name;

    @Column(name = "leetcode_number")
    private Integer leetcodeNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String url;

    protected PracticeProblem() {
        // for JPA
    }

    public Integer getId() { return id; }
    public Integer getPatternId() { return patternId; }
    public String getName() { return name; }
    public Integer getLeetcodeNumber() { return leetcodeNumber; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getUrl() { return url; }
}
