package dev.dsatracker.dsa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionAttemptRepository extends JpaRepository<RevisionAttempt, Long> {
}
