package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One of the NeetCode 150 problems. Reference data: loaded by migration V3 and never
 * changed by the app, so there are no setters and no generated id.
 */
@Entity
@Table(name = "catalog_problems")
public class CatalogProblem {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String category;

    @Column(name = "category_order", nullable = false)
    private int categoryOrder;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String url;

    /** The technique this problem teaches (migration V5). */
    @Column(name = "pattern_id", nullable = false)
    private Integer patternId;

    protected CatalogProblem() {
        // for JPA
    }

    public Integer getId() { return id; }
    public String getCategory() { return category; }
    public int getCategoryOrder() { return categoryOrder; }
    public String getName() { return name; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getUrl() { return url; }
    public Integer getPatternId() { return patternId; }
}
