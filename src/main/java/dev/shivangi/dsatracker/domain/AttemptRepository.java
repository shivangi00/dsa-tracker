package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByProblemIdInOrderByProblemIdAscRevisionAscTryNoAsc(Collection<Long> problemIds);

    List<Attempt> findByProblemIdOrderByRevisionAscTryNoAsc(Long problemId);

    int countByProblemIdAndRevision(Long problemId, int revision);

    /** An attempt, but only if its problem belongs to {@code userId}. */
    @Query("""
            select a from Attempt a, Problem p
            where a.id = :id and p.id = a.problemId and p.userId = :userId
            """)
    Optional<Attempt> findOwned(@Param("id") Long id, @Param("userId") Long userId);
}
