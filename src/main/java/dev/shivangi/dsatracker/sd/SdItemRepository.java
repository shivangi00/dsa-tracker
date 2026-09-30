package dev.shivangi.dsatracker.sd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SdItemRepository extends JpaRepository<SdItem, Long> {

    List<SdItem> findByUserId(Long userId);

    /** Topics and problems due today or overdue (for the badge on the System design tab). */
    long countByUserIdAndNextDueOnLessThanEqual(Long userId, LocalDate today);

    Optional<SdItem> findByIdAndUserId(Long id, Long userId);

    Optional<SdItem> findByUserIdAndKindAndItemKey(Long userId, SdKind kind, String itemKey);

    /** How many system design reviews are due on each day in a range (for spreading the workload). */
    @Query("""
            select i.nextDueOn, count(i) from SdItem i
            where i.userId = :userId and i.nextDueOn between :from and :to
            group by i.nextDueOn
            """)
    List<Object[]> dueCountsBetween(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
