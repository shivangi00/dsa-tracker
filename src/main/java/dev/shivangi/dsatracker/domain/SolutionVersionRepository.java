package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolutionVersionRepository extends JpaRepository<SolutionVersion, Long> {

    List<SolutionVersion> findByAttemptIdInOrderByAttemptIdAscVersionNoAsc(Collection<Long> attemptIds);

    int countByAttemptId(Long attemptId);

    @Query("select coalesce(max(v.versionNo), 0) from SolutionVersion v where v.attemptId = :attemptId")
    int maxVersionNo(@Param("attemptId") Long attemptId);

    /** A version, but only if it belongs to {@code userId}. */
    @Query("""
            select v from SolutionVersion v, Attempt a, Problem p
            where v.id = :id and a.id = v.attemptId and p.id = a.problemId and p.userId = :userId
            """)
    Optional<SolutionVersion> findOwned(@Param("id") Long id, @Param("userId") Long userId);
}
