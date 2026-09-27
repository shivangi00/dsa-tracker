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

    /** Only on accounts made before V9, when sign-up asked for it. */
    private String email;

    /** A BCrypt hash. The password itself is never stored. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** BCrypt hash of the one-time recovery code; null until one is made. */
    @Column(name = "recovery_code_hash")
    private String recoveryCodeHash;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String passwordHash, LocalDate startDate) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.startDate = startDate;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    /** Replaces the recovery code: the old one stops working. */
    public void replaceRecoveryCodeHash(String newHash) {
        this.recoveryCodeHash = newHash;
    }

    public boolean hasRecoveryCode() {
        return recoveryCodeHash != null;
    }

    public void changeStartDate(LocalDate newStart) {
        this.startDate = newStart;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDate getStartDate() { return startDate; }
    public String getRecoveryCodeHash() { return recoveryCodeHash; }
}
