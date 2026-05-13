package com.queueless.repository;

import com.queueless.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByShopIdAndVisibleTrueOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Page<Review> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Optional<Review> findByUserIdAndTokenId(UUID userId, UUID tokenId);

    Optional<Review> findByUserIdAndAppointmentId(UUID userId, UUID appointmentId);

    /** Avg rating and count for a shop. */
    @Query("""
        SELECT AVG(r.rating), COUNT(r)
        FROM Review r
        WHERE r.shop.id = :shopId
          AND r.visible = true
        """)
    Object[] getRatingSummary(@Param("shopId") UUID shopId);

    @Query("""
        SELECT AVG(r.rating), COUNT(r)
        FROM Review r
        WHERE r.shop.id = :shopId
        """)
    Object[] getOwnerRatingSummary(@Param("shopId") UUID shopId);

    /** Rating breakdown — count per star (1-5). */
    @Query("""
        SELECT r.rating, COUNT(r)
        FROM Review r
        WHERE r.shop.id = :shopId
          AND r.visible = true
        GROUP BY r.rating
        ORDER BY r.rating
        """)
    java.util.List<Object[]> getRatingBreakdown(@Param("shopId") UUID shopId);

    @Query("""
        SELECT r.rating, COUNT(r)
        FROM Review r
        WHERE r.shop.id = :shopId
        GROUP BY r.rating
        ORDER BY r.rating
        """)
    java.util.List<Object[]> getOwnerRatingBreakdown(@Param("shopId") UUID shopId);

    java.util.List<Review> findByModerationStatusOrderByCreatedAtDesc(Review.ModerationStatus moderationStatus);
}
