package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyTestRepository extends JpaRepository<WeeklyTest, Long> {

    Optional<WeeklyTest> findByIdAndUserId(Long id, Long userId);

    Optional<WeeklyTest> findByUserIdAndWeekNumber(Long userId, int weekNumber);

    List<WeeklyTest> findByUserId(Long userId);
}
