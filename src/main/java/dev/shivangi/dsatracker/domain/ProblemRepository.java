package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Every read and write is scoped to one user, so nobody can see or touch another's data. */
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCatalogId(Long userId, Integer catalogId);

    List<Problem> findByUserIdOrderBySolvedOnDescIdDesc(Long userId);

    /** Problems marked done in a date range (a plan week), for weekly tests. */
    List<Problem> findByUserIdAndSolvedOnBetween(Long userId, LocalDate from, LocalDate to);

    Optional<Problem> findByUserIdAndCatalogId(Long userId, Integer catalogId);

    /** Due today or overdue, most overdue first. Uses the (user_id, next_due_on) index. */
    @Query("""
            select p from Problem p
            where p.userId = :userId and p.nextDueOn <= :today
            order by p.nextDueOn, p.id
            """)
    List<Problem> findDue(@Param("userId") Long userId, @Param("today") LocalDate today);

    /** Gives rows from before accounts existed to the first account. */
    @Modifying
    @Query("update Problem p set p.userId = :userId where p.userId is null")
    int claimUnowned(@Param("userId") Long userId);
}
