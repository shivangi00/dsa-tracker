package dev.shivangi.dsatracker.sd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SdAttemptRepository extends JpaRepository<SdAttempt, Long> {

    List<SdAttempt> findByItemIdInOrderByItemIdAscRevisionAscTryNoAsc(Collection<Long> itemIds);

    List<SdAttempt> findByItemIdOrderByRevisionAscTryNoAsc(Long itemId);

    int countByItemIdAndRevision(Long itemId, int revision);

    @Query("""
            select a from SdAttempt a, SdItem i
            where a.id = :id and i.id = a.itemId and i.userId = :userId
            """)
    Optional<SdAttempt> findOwned(@Param("id") Long id, @Param("userId") Long userId);
}
