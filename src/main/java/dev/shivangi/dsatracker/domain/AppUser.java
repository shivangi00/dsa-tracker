package dev.shivangi.dsatracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;

/** An account. Called AppUser because "User" clashes with Spring Security's own User class. */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking: bumped on every update; a stale concurrent update fails with 409. */
    @Version
    private long version;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    /** A BCrypt hash. The password itself is never stored. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String email, String passwordHash, LocalDate startDate) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.startDate = startDate;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void changeStartDate(LocalDate newStart) {
        this.startDate = newStart;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDate getStartDate() { return startDate; }
}
