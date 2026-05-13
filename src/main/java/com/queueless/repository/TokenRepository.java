package com.queueless.repository;

import com.queueless.entity.Token;
import com.queueless.entity.Token.TokenStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for Token entity — core queue management queries. */
@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {

    @EntityGraph(attributePaths = {"shop", "user"})
    Optional<Token> findWithRelationsById(UUID id);

    @Query("""
        SELECT t FROM Token t
        WHERE t.shop.id = :shopId
          AND t.dateIssued = :date
          AND t.status IN ('WAITING', 'CALLED', 'ARRIVED', 'SERVING')
        ORDER BY (t.tokenNumber + t.sortPenalty) ASC
        """)
    List<Token> findActiveQueueByShopAndDate(@Param("shopId") UUID shopId, @Param("date") LocalDate date);

    @Query(value = """
        SELECT * FROM tokens
        WHERE shop_id = :shopId
          AND date_issued = :date
          AND status = 'WAITING'
        ORDER BY
            CASE priority
                WHEN 'EMERGENCY' THEN 1
                WHEN 'VIP'       THEN 2
                WHEN 'SENIOR'    THEN 3
                WHEN 'PREGNANT'  THEN 4
                ELSE 5
            END ASC,
            (token_number + sort_penalty) ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Token> findNextWaitingToken(@Param("shopId") UUID shopId, @Param("date") LocalDate date);

    @Query(value = """
        SELECT * FROM tokens
        WHERE shop_id = :shopId
          AND date_issued = :date
          AND status = 'WAITING'
          AND (provider_id IS NULL OR provider_id = :providerId)
        ORDER BY
            CASE priority
                WHEN 'EMERGENCY' THEN 1
                WHEN 'VIP'       THEN 2
                WHEN 'SENIOR'    THEN 3
                WHEN 'PREGNANT'  THEN 4
                ELSE 5
            END ASC,
            (token_number + sort_penalty) ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Token> findNextWaitingTokenForProvider(@Param("shopId") UUID shopId, @Param("date") LocalDate date, @Param("providerId") UUID providerId);

    @Query("""
        SELECT COUNT(t) FROM Token t
        WHERE t.shop.id = :shopId
          AND t.dateIssued = :date
          AND t.status = 'WAITING'
          AND (t.tokenNumber + t.sortPenalty) < :sortValue
        """)
    long countTokensAhead(@Param("shopId") UUID shopId, @Param("date") LocalDate date, @Param("sortValue") int sortValue);

    Page<Token> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
        SELECT t FROM Token t
        WHERE t.user.id = :userId
          AND t.shop.id = :shopId
          AND t.dateIssued = :date
          AND t.status IN ('WAITING', 'CALLED', 'ARRIVED', 'SERVING')
        """)
    Optional<Token> findActiveTokenForUserAtShop(@Param("userId") UUID userId, @Param("shopId") UUID shopId, @Param("date") LocalDate date);

    @Query("""
        SELECT COALESCE(MAX(t.tokenNumber), 0) FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued = :date
        """)
    int findMaxTokenNumberForShopToday(@Param("shopId") UUID shopId, @Param("date") LocalDate date);

    @Query("""
        SELECT t.status, COUNT(t) FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued = :date
        GROUP BY t.status
        """)
    List<Object[]> countByStatusForShopToday(@Param("shopId") UUID shopId, @Param("date") LocalDate date);

    @Query("""
        SELECT t FROM Token t
        WHERE t.shop.id = :shopId
          AND t.dateIssued = :date
          AND t.status = 'WAITING'
          AND t.smsSent = false
          AND t.tokenNumber > :currentTokenNumber
          AND t.tokenNumber <= :currentTokenNumber + :threshold
        """)
    List<Token> findTokensNeedingSmsAlert(@Param("shopId") UUID shopId, @Param("date") LocalDate date, @Param("currentTokenNumber") int currentTokenNumber, @Param("threshold") int threshold);

    @Query("SELECT t FROM Token t WHERE t.shop.id = :shopId AND t.status = 'SERVED' ORDER BY t.servedAt DESC")
    Page<Token> findRecentServedTokens(@Param("shopId") UUID shopId, Pageable pageable);

    @Modifying
    @Query(value = "TRUNCATE TABLE token_sequences", nativeQuery = true)
    void resetDailySequences();

    @Modifying
    @Query("UPDATE Token t SET t.status = 'EXPIRED' WHERE t.status = 'WAITING' AND t.dateIssued < :today")
    int expireOldTokens(@Param("today") LocalDate today);

    List<Token> findByShopIdAndDateIssuedAndStatus(UUID shopId, LocalDate date, TokenStatus status);

    // ===== ANALYTICS QUERIES =====

    @Query(value = """
        SELECT EXTRACT(HOUR FROM issued_at) AS hour, COUNT(*)
        FROM tokens
        WHERE shop_id = :shopId AND date_issued >= :fromDate
        GROUP BY 1 ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> findHourlyHeatmap(@Param("shopId") UUID shopId, @Param("fromDate") LocalDate fromDate);

    @Query("""
        SELECT SUM(CASE WHEN t.status = :skippedStatus THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.status IN :relevantStatuses THEN 1 ELSE 0 END)
        FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued >= :fromDate
        """)
    List<Object[]> findNoShowStats(
            @Param("shopId") UUID shopId, 
            @Param("fromDate") LocalDate fromDate,
            @Param("skippedStatus") TokenStatus skippedStatus,
            @Param("relevantStatuses") List<TokenStatus> relevantStatuses);

    @Query("""
        SELECT t.service.name, COUNT(t)
        FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued >= :fromDate AND t.service IS NOT NULL
        GROUP BY t.service.name ORDER BY COUNT(t) DESC
        """)
    List<Object[]> findServicePopularity(@Param("shopId") UUID shopId, @Param("fromDate") LocalDate fromDate);

    @Query("""
        SELECT t.serviceProvider.user.name, COUNT(t),
               SUM(CASE WHEN t.status = :servedStatus THEN 1 ELSE 0 END)
        FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued >= :fromDate AND t.serviceProvider IS NOT NULL
        GROUP BY t.serviceProvider.id, t.serviceProvider.user.name ORDER BY COUNT(t) DESC
        """)
    List<Object[]> findProviderUtilization(
            @Param("shopId") UUID shopId, 
            @Param("fromDate") LocalDate fromDate,
            @Param("servedStatus") TokenStatus servedStatus);

    @Query("""
        SELECT t.dateIssued, COUNT(t)
        FROM Token t
        WHERE t.shop.id = :shopId AND t.dateIssued >= :fromDate
        GROUP BY t.dateIssued ORDER BY t.dateIssued ASC
        """)
    List<Object[]> findDailyTraffic(@Param("shopId") UUID shopId, @Param("fromDate") LocalDate fromDate);

    List<Token> findAllByStatusAndReminderSentFalse(Token.TokenStatus status);

    @Query("SELECT t FROM Token t WHERE t.status = 'WAITING' AND t.noShowProbability > :threshold")
    List<Token> findTokensWithHighNoShow(@Param("threshold") double threshold);

    @Query("SELECT t FROM Token t WHERE t.status = 'WAITING' AND t.dateIssued = CURRENT_DATE")
    List<Token> findWaitingTokensForRebalancing();
}
