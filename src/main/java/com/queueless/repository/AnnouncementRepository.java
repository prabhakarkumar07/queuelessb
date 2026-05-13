package com.queueless.repository;

import com.queueless.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    /** Returns all currently active announcements for a shop. */
    @Query("""
        SELECT a FROM Announcement a
        WHERE a.shop.id = :shopId
          AND a.validFrom <= :now
          AND (a.validTo IS NULL OR a.validTo >= :now)
        ORDER BY a.createdAt DESC
        """)
    List<Announcement> findActiveByShopId(@Param("shopId") UUID shopId, @Param("now") Instant now);

    /** All announcements for a shop (for management view). */
    List<Announcement> findByShopIdOrderByCreatedAtDesc(UUID shopId);
}
