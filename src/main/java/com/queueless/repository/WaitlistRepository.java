package com.queueless.repository;

import com.queueless.entity.Waitlist;
import com.queueless.entity.Waitlist.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {

    /** Find the first N waiting customers (oldest first) to notify. */
    @Query("""
        SELECT w FROM Waitlist w
        WHERE w.shop.id = :shopId
          AND w.status = 'WAITING'
        ORDER BY w.joinedAt ASC
        LIMIT :limit
        """)
    List<Waitlist> findTopWaiting(@Param("shopId") UUID shopId, @Param("limit") int limit);

    /** Find existing WAITING entry for a user in a shop. */
    @Query("""
        SELECT w FROM Waitlist w
        WHERE w.shop.id = :shopId
          AND w.user.id = :userId
          AND w.status = 'WAITING'
        """)
    Optional<Waitlist> findActiveEntryForUser(@Param("shopId") UUID shopId, @Param("userId") UUID userId);

    /** Count how many are ahead of this user in the waitlist. */
    @Query("""
        SELECT COUNT(w) FROM Waitlist w
        WHERE w.shop.id = :shopId
          AND w.status = 'WAITING'
          AND w.joinedAt < :joinedAt
        """)
    long countAhead(@Param("shopId") UUID shopId,
                    @Param("joinedAt") java.time.Instant joinedAt);

    List<Waitlist> findByUserIdOrderByJoinedAtDesc(UUID userId);

    long countByShopIdAndStatus(UUID shopId, WaitlistStatus status);
}
