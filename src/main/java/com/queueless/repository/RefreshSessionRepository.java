package com.queueless.repository;

import com.queueless.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshSession s SET s.revokedAt = :revokedAt WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :now OR s.revokedAt IS NOT NULL")
    int deleteExpiredOrRevoked(@Param("now") Instant now);
}
