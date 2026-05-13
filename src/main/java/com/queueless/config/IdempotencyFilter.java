package com.queueless.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Idempotency filter for critical state-changing API endpoints.
 *
 * If a client sends an `Idempotency-Key` header on a POST request to a
 * protected endpoint, the filter will:
 *   1. On the FIRST call: allow it through and cache the response body/status.
 *   2. On SUBSEQUENT calls with the same key: replay the cached response directly,
 *      without hitting the service layer again.
 *
 * This prevents double-bookings and duplicate token issuance on network retries.
 * Keys expire after 24 hours.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String CACHE_PREFIX = "idempotency:";
    private static final Duration KEY_TTL = Duration.ofHours(24);

    // Only these endpoints need idempotency protection
    private static final String[] PROTECTED_PATHS = {
        "/api/tokens",
        "/api/tokens/shops/",
        "/api/appointments"
    };

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        // Only enforce on POST requests to protected paths that provide the header
        if (!HttpMethod.POST.name().equals(request.getMethod())
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || !isProtectedPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String cacheKey = CACHE_PREFIX + idempotencyKey;

        try {
            // Check if we've seen this key before
            String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);

            if (cachedResponse != null) {
                // Replay the cached response
                log.info("Idempotency key {} already processed — replaying cached response", idempotencyKey);
                response.setStatus(HttpStatus.OK.value());
                response.setContentType("application/json");
                response.setHeader("X-Idempotent-Replayed", "true");
                response.getWriter().write(cachedResponse);
                return;
            }

            // Key is new — wrap the response to capture its body
            CachingResponseWrapper cachedResponseWrapper = new CachingResponseWrapper(response);
            chain.doFilter(request, cachedResponseWrapper);

            // Only cache successful responses (2xx)
            int status = cachedResponseWrapper.getStatus();
            String body = cachedResponseWrapper.getCapturedBody();

            if (status >= 200 && status < 300 && body != null && !body.isBlank()) {
                redisTemplate.opsForValue().set(cacheKey, body, KEY_TTL);
                log.debug("Cached idempotency response for key {} (status: {})", idempotencyKey, status);
            }

            // Write the actual response through
            cachedResponseWrapper.copyBodyToResponse();

        } catch (Exception e) {
            log.warn("Idempotency filter error for key {}: {} — passing through", idempotencyKey, e.getMessage());
            chain.doFilter(request, response);
        }
    }

    private boolean isProtectedPath(String uri) {
        for (String path : PROTECTED_PATHS) {
            if (uri != null && uri.startsWith(path)) return true;
        }
        return false;
    }
}
