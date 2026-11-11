package dev.shivangi.dsatracker.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** What was last pushed for a problem: a fingerprint of its files, to tell if anything changed since. */
@Entity
@Table(name = "github_pushes")
public class ProblemPush {

    @Id
    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "pushed_at", nullable = false)
    private Instant pushedAt;

    @Column(name = "commit_url")
    private String commitUrl;

    protected ProblemPush() {
        // for JPA
    }

    public ProblemPush(Long problemId, String contentHash, Instant pushedAt, String commitUrl) {
        this.problemId = problemId;
        this.contentHash = contentHash;
        this.pushedAt = pushedAt;
        this.commitUrl = commitUrl;
    }

    public void update(String contentHash, Instant pushedAt, String commitUrl) {
        this.contentHash = contentHash;
        this.pushedAt = pushedAt;
        if (commitUrl != null) {
            this.commitUrl = commitUrl;
        }
    }

    public Long getProblemId() { return problemId; }
    public String getContentHash() { return contentHash; }
    public Instant getPushedAt() { return pushedAt; }
    public String getCommitUrl() { return commitUrl; }
}
