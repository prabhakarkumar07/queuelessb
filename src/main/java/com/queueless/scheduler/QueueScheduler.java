package com.queueless.scheduler;

import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Scheduled tasks that run automatically to maintain queue data hygiene.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueScheduler {

    private final TokenRepository tokenRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Expires all WAITING tokens from previous days at 2:00 AM daily.
     * Tokens not served by end of day are automatically marked as EXPIRED.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireOldTokens() {
        int expired = tokenRepository.expireOldTokens(LocalDate.now());
        log.info("Daily token expiry job: {} tokens expired", expired);
    }

    /**
     * Persists daily and hourly queue summaries before midnight reset.
     */
    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void dailySummary() {
        LocalDate today = LocalDate.now();
        int dailyRows = jdbcTemplate.update("""
                INSERT INTO daily_shop_stats (
                    shop_id, stat_date, total_tokens, served_tokens, skipped_tokens, cancelled_tokens,
                    avg_wait_minutes, avg_service_minutes, updated_at
                )
                SELECT
                    t.shop_id,
                    t.date_issued,
                    COUNT(*),
                    COUNT(*) FILTER (WHERE t.status = 'SERVED'),
                    COUNT(*) FILTER (WHERE t.status = 'SKIPPED'),
                    COUNT(*) FILTER (WHERE t.status IN ('CANCELLED', 'EXPIRED')),
                    COALESCE(AVG(EXTRACT(EPOCH FROM (t.called_at - t.issued_at)) / 60)
                        FILTER (WHERE t.called_at IS NOT NULL), 0),
                    COALESCE(AVG(EXTRACT(EPOCH FROM (t.served_at - t.called_at)) / 60)
                        FILTER (WHERE t.called_at IS NOT NULL AND t.served_at IS NOT NULL), 0),
                    NOW()
                FROM tokens t
                WHERE t.date_issued = ?
                GROUP BY t.shop_id, t.date_issued
                ON CONFLICT (shop_id, stat_date) DO UPDATE SET
                    total_tokens = EXCLUDED.total_tokens,
                    served_tokens = EXCLUDED.served_tokens,
                    skipped_tokens = EXCLUDED.skipped_tokens,
                    cancelled_tokens = EXCLUDED.cancelled_tokens,
                    avg_wait_minutes = EXCLUDED.avg_wait_minutes,
                    avg_service_minutes = EXCLUDED.avg_service_minutes,
                    updated_at = NOW()
                """, today);

        int hourlyRows = jdbcTemplate.update("""
                INSERT INTO hourly_shop_stats (
                    shop_id, stat_date, stat_hour, total_tokens, served_tokens,
                    skipped_tokens, cancelled_tokens, updated_at
                )
                SELECT
                    t.shop_id,
                    t.date_issued,
                    EXTRACT(HOUR FROM t.issued_at)::SMALLINT,
                    COUNT(*),
                    COUNT(*) FILTER (WHERE t.status = 'SERVED'),
                    COUNT(*) FILTER (WHERE t.status = 'SKIPPED'),
                    COUNT(*) FILTER (WHERE t.status IN ('CANCELLED', 'EXPIRED')),
                    NOW()
                FROM tokens t
                WHERE t.date_issued = ?
                GROUP BY t.shop_id, t.date_issued, EXTRACT(HOUR FROM t.issued_at)::SMALLINT
                ON CONFLICT (shop_id, stat_date, stat_hour) DO UPDATE SET
                    total_tokens = EXCLUDED.total_tokens,
                    served_tokens = EXCLUDED.served_tokens,
                    skipped_tokens = EXCLUDED.skipped_tokens,
                    cancelled_tokens = EXCLUDED.cancelled_tokens,
                    updated_at = NOW()
                """, today);

        log.info("Daily queue summary job persisted {} daily rows and {} hourly rows for {}",
                dailyRows, hourlyRows, today);
    }
}
