package dev.shivangi.dsatracker.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProblemPushRepository extends JpaRepository<ProblemPush, Long> {

    List<ProblemPush> findByProblemIdIn(Collection<Long> problemIds);

    /** Forget what was pushed (after linking a different repository, or disconnecting). */
    @Modifying
    @Query("delete from ProblemPush p where p.problemId in (select q.id from Problem q where q.userId = :userId)")
    void deleteForUser(@Param("userId") Long userId);
}
