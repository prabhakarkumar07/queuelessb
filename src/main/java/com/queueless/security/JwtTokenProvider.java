package com.queueless.security;

import com.queueless.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT utility for token generation, parsing, and validation.
 * Uses HS256 with a configurable secret key.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    /**
     * Generates a short-lived JWT access token for authenticated users.
     *
     * @param user the authenticated user
     * @return signed JWT access token string
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("name", user.getName());
        claims.put("userId", user.getId().toString());

        return buildToken(claims, subjectFor(user), accessTokenExpiry);
    }

    /**
     * Generates a long-lived JWT refresh token.
     *
     * @param user the authenticated user
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("userId", user.getId().toString());
        return buildToken(claims, subjectFor(user), refreshTokenExpiry);
    }

    /**
     * Extracts the subject (phone number) from a JWT token.
     *
     * @param token JWT string
     * @return phone number embedded in the token subject
     */
    public String extractPhone(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the user ID embedded in the token claims.
     *
     * @param token JWT string
     * @return user UUID
     */
    public UUID extractUserId(String token) {
        String userId = extractAllClaims(token).get("userId", String.class);
        return userId != null ? UUID.fromString(userId) : null;
    }

    public Date extractIssuedAt(String token) {
        return extractAllClaims(token).getIssuedAt();
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Validates a JWT token against the user's identity.
     *
     * @param token JWT string
     * @param user  user to validate against
     * @return true if valid and not expired
     */
    public boolean isTokenValid(String token, User user) {
        final String subject = extractPhone(token);
        return (subject.equals(user.getId().toString()) || subject.equals(user.getPhone())) && !isTokenExpired(token);
    }

    /**
     * Checks whether a JWT token has expired.
     *
     * @param token JWT string
     * @return true if the token expiration date is before now
     */
    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiry) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String subjectFor(User user) {
        return user.getPhone() != null && !user.getPhone().isBlank()
                ? user.getPhone()
                : user.getId().toString();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
