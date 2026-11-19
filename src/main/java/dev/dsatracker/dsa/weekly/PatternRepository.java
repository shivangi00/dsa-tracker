package dev.dsatracker.dsa.weekly;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternRepository extends JpaRepository<Pattern, Integer> {
}
