package com.queueless.repository;

import com.queueless.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {

    List<Shop> findByOwnerIdAndActiveTrue(UUID ownerId);

    Optional<Shop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Page<Shop> findByActiveTrue(Pageable pageable);

    Page<Shop> findByCityAndActiveTrue(String city, Pageable pageable);

    Page<Shop> findByCategoryAndActiveTrue(Shop.Category category, Pageable pageable);

    List<Shop> findByActiveTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();

    List<Shop> findByActiveTrue();

    @Query(value = """
            SELECT *
            FROM shops
            WHERE is_active = true
              AND (
                    LOWER(name) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(address, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(city, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(state, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(CAST(category AS TEXT)) LIKE CONCAT('%', LOWER(:query), '%')
              )
            ORDER BY
              CASE WHEN LOWER(name) LIKE CONCAT(LOWER(:query), '%') THEN 0 ELSE 1 END,
              name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Shop> searchActiveShops(@Param("query") String query, @Param("limit") int limit);

    @Query(value = """
            SELECT *
            FROM shops
            WHERE is_active = true
              AND latitude IS NOT NULL
              AND longitude IS NOT NULL
              AND (
                    6371 * acos(LEAST(1,
                        cos(radians(:lat)) * cos(radians(latitude)) *
                        cos(radians(longitude) - radians(:lng)) +
                        sin(radians(:lat)) * sin(radians(latitude))
                    ))
              ) <= :radiusKm
            ORDER BY (
                    6371 * acos(LEAST(1,
                        cos(radians(:lat)) * cos(radians(latitude)) *
                        cos(radians(longitude) - radians(:lng)) +
                        sin(radians(:lat)) * sin(radians(latitude))
                    ))
              ) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Shop> findNearbyActiveShops(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radiusKm") double radiusKm,
                                     @Param("limit") int limit);

    @Query(value = """
            SELECT s.*
            FROM shops s
            LEFT JOIN (
                SELECT shop_id, COUNT(*) AS event_score
                FROM shop_discovery_events
                WHERE shop_id IS NOT NULL
                  AND created_at >= NOW() - INTERVAL '30 days'
                GROUP BY shop_id
            ) e ON e.shop_id = s.id
            LEFT JOIN (
                SELECT shop_id, COUNT(*) AS token_score
                FROM tokens
                WHERE created_at >= NOW() - INTERVAL '30 days'
                GROUP BY shop_id
            ) t ON t.shop_id = s.id
            LEFT JOIN (
                SELECT shop_id, COUNT(*) AS review_score
                FROM reviews
                WHERE created_at >= NOW() - INTERVAL '90 days'
                GROUP BY shop_id
            ) r ON r.shop_id = s.id
            WHERE s.is_active = true
              AND (:category IS NULL OR CAST(s.category AS TEXT) = :category)
            ORDER BY
              (COALESCE(e.event_score, 0) * 5 + COALESCE(t.token_score, 0) * 2 + COALESCE(r.review_score, 0)) DESC,
              s.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Shop> findPopularActiveShops(@Param("category") String category, @Param("limit") int limit);

    @Query(value = """
            SELECT s.*
            FROM shops s
            JOIN (
                SELECT shop_id, COUNT(*) AS event_score
                FROM shop_discovery_events
                WHERE shop_id IS NOT NULL
                  AND event_type IN ('SEARCH_RESULT', 'VIEW')
                  AND created_at >= NOW() - INTERVAL '7 days'
                GROUP BY shop_id
            ) e ON e.shop_id = s.id
            WHERE s.is_active = true
              AND (:category IS NULL OR CAST(s.category AS TEXT) = :category)
            ORDER BY e.event_score DESC, s.name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Shop> findTrendingActiveShops(@Param("category") String category, @Param("limit") int limit);

    @Query("SELECT COUNT(s) FROM Shop s WHERE s.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") UUID ownerId);

    List<Shop> findByVerificationStatusOrderByCreatedAtDesc(Shop.VerificationStatus verificationStatus);
}
