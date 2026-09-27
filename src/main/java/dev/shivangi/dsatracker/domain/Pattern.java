package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A problem-solving technique (e.g. "Monotonic stack"). Reference data from migration V5. */
@Entity
@Table(name = "patterns")
public class Pattern {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    /** One-line explanation shown after you answer a test question. */
    @Column(nullable = false)
    private String idea;

    protected Pattern() {
        // for JPA
    }

    public Integer getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getIdea() { return idea; }
}
