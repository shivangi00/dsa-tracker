package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WeeklyTestItemRepository extends JpaRepository<WeeklyTestItem, Long> {

    List<WeeklyTestItem> findByTestIdOrderByPosition(Long testId);

    List<WeeklyTestItem> findByTestIdIn(Collection<Long> testIds);

    /** Practice problems this user has already seen, so a test never repeats one. */
    @Query("""
            select i.practiceProblemId from WeeklyTestItem i, WeeklyTest t
            where t.id = i.testId and t.userId = :userId
            """)
    List<Integer> usedPracticeIds(@Param("userId") Long userId);
}
