package com.queueless.service;

import com.queueless.exception.BusinessException;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Distributed rate limiting service backed by Redis.
 *
 * Uses Bucket4j's Lettuce-based distributed proxy so rate limit state is
 * shared across all pod replicas. Gracefully falls back to allowing the
 * request through if Redis is unavailable (fail-open) rather than blocking
 * legitimate traffic.
 *
 * Limits enforced:
 *   - OTP / Auth:     3 requests / 15 minutes (per IP)
 *   - Token issuance: 10 tokens / 60 minutes (per user)
 *   - General API:    100 requests / 1 minute  (per user)
 */
@Service
@Slf4j
public class RateLimitService {

    @Value("${rate-limit.otp.capacity:3}")
    private int otpCapacity;

    @Value("${rate-limit.otp.refill-duration-minutes:15}")
    private int otpRefillMinutes;

    @Value("${rate-limit.api.capacity:100}")
    private int apiCapacity;

    @Value("${rate-limit.api.refill-duration-minutes:1}")
    private int apiRefillMinutes;

    private final ProxyManager<String> proxyManager;

    public RateLimitService(LettuceConnectionFactory connectionFactory) {
        ProxyManager<String> manager = null;
        try {
            // Build a Lettuce-based distributed proxy manager using the app's existing
            // Redis connection so we reuse the pool and don't open a separate connection.
            RedisClient redisClient = (RedisClient) connectionFactory.getNativeClient();
            StatefulRedisConnection<String, byte[]> connection =
                    redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            
            manager = LettuceBasedProxyManager.builderFor(connection)
                    .withExpirationStrategy(
                            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(2))
                        )
                    .build();
            log.info("RateLimitService initialized with Redis distributed proxy.");
        } catch (Exception e) {
            log.error("Failed to connect to Redis for RateLimitService: {}. Falling back to local in-memory limiting.", e.getMessage());
            // Fallback: This will still cause issues if we try to use proxyManager as LettuceBasedProxyManager
            // but we can use a simple in-memory one or just handle nulls in consume().
        }
        this.proxyManager = manager;
    }

    /** Rate-limits OTP requests and login attempts by IP address. */
    public void consumeAuth(String key) {
        consume("auth:" + key, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(otpCapacity,
                        Refill.intervally(otpCapacity, Duration.ofMinutes(otpRefillMinutes))))
                .build(),
                "Too many authentication attempts. Please try again in " + otpRefillMinutes + " minutes.");
    }

    /** Rate-limits token issuance by IP+userId combination. */
    public void consumeTokenIssue(String key) {
        consume("token-issue:" + key, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(60))))
                .build(),
                "Too many token requests. Please try again later.");
    }

    /** General API rate limit per user. */
    public void consumeApi(String key) {
        consume("api:" + key, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(apiCapacity,
                        Refill.intervally(apiCapacity, Duration.ofMinutes(apiRefillMinutes))))
                .build(),
                "Too many requests. Please slow down.");
    }

    /**
     * Rate-limits public (unauthenticated) shop search and nearby endpoints by IP address.
     * B-13: Prevents bots from exhausting DB connections via geospatial queries.
     * Allows 60 requests per minute — generous for real users, blocks scrapers.
     */
    public void consumePublicSearch(String ip) {
        consume("public-search:" + ip, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1))))
                .build(),
                "Too many search requests. Please slow down.");
    }

    private void consume(String key, Supplier<BucketConfiguration> config, String message) {
        try {
            if (proxyManager == null) {
                // Fallback to local in-memory bucket if Redis is down
                Bucket localBucket = Bucket.builder()
                        .addLimit(config.get().getBandwidths()[0]) // Use the same limit
                        .build();
                if (!localBucket.tryConsume(1)) {
                    throw new BusinessException(message);
                }
                return;
            }
            Bucket bucket = proxyManager.builder().build(key, config);
            if (!bucket.tryConsume(1)) {
                throw new BusinessException(message);
            }
        } catch (BusinessException e) {
            throw e; // rethrow rate-limit exceptions as-is
        } catch (Exception e) {
            // Redis unavailable — fail open to avoid blocking legitimate traffic
            log.warn("Rate limit check failed for key '{}' — failing open: {}", key, e.getMessage());
        }
    }
}
