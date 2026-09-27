package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Only the newest link should work: clear older ones when a new one is sent or used. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
