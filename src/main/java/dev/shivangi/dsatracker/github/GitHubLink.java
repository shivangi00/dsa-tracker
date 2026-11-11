package dev.shivangi.dsatracker.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Your linked repository. The token is stored encrypted and is never sent to the browser. */
@Entity
@Table(name = "github_links")
public class GitHubLink {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repo;

    @Column(nullable = false)
    private String branch;

    @Column(name = "token_encrypted", nullable = false)
    private String tokenEncrypted;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "auto_push", nullable = false)
    private boolean autoPush;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_push_at")
    private Instant lastPushAt;

    protected GitHubLink() {
        // for JPA
    }

    public GitHubLink(Long userId, String owner, String repo, String branch, String tokenEncrypted, boolean isPrivate,
                      boolean autoPush, Instant connectedAt) {
        this.userId = userId;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.tokenEncrypted = tokenEncrypted;
        this.isPrivate = isPrivate;
        this.autoPush = autoPush;
        this.connectedAt = connectedAt;
    }

    public void pushed(Instant at) {
        this.lastPushAt = at;
    }

    public void setAutoPush(boolean autoPush) {
        this.autoPush = autoPush;
    }

    public String fullName() {
        return owner + "/" + repo;
    }

    public Long getUserId() { return userId; }
    public String getOwner() { return owner; }
    public String getRepo() { return repo; }
    public String getBranch() { return branch; }
    public String getTokenEncrypted() { return tokenEncrypted; }
    public boolean isPrivate() { return isPrivate; }
    public boolean isAutoPush() { return autoPush; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastPushAt() { return lastPushAt; }
}
