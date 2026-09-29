package dev.shivangi.dsatracker.sd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SdAnswerRepository extends JpaRepository<SdAnswer, Long> {

    List<SdAnswer> findByAttemptIdInOrderByAttemptIdAscVersionNoAsc(Collection<Long> attemptIds);

    int countByAttemptId(Long attemptId);

    @Query("select coalesce(max(a.versionNo), 0) from SdAnswer a where a.attemptId = :attemptId")
    int maxVersionNo(@Param("attemptId") Long attemptId);

    @Query("""
            select a from SdAnswer a, SdAttempt t, SdItem i
            where a.id = :id and t.id = a.attemptId and i.id = t.itemId and i.userId = :userId
            """)
    Optional<SdAnswer> findOwned(@Param("id") Long id, @Param("userId") Long userId);
}
