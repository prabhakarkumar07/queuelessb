package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.exception.*;
import com.queueless.entity.RefreshSession;
import com.queueless.entity.OtpCode;
import com.queueless.repository.OtpCodeRepository;
import com.queueless.repository.RefreshSessionRepository;
import com.queueless.repository.UserRepository;
import com.queueless.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Authentication service for registration, login, token refresh, and logout.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshSessionRepository refreshSessionRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Registers a new user (customer, shop owner, or admin).
     * Validates uniqueness of phone and email before persisting.
     *
     * @param request registration data including phone, password, and role
     * @return AuthResponse containing access token, refresh token, and user profile
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (request.getRole() != User.Role.CUSTOMER && request.getRole() != User.Role.SHOP_OWNER) {
            throw new AccessDeniedException("Only customer and shop owner accounts can be self-registered");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("Phone number already registered: " + request.getPhone());
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshSession(user, ipAddress, userAgent);
        log.info("User registered: {} ({})", user.getPhone(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    /**
     * Authenticates a user with phone and password credentials.
     *
     * @param request login credentials
     * @return AuthResponse with JWT tokens and user profile
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getPhone(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException("Invalid phone or password");
        } catch (DisabledException e) {
            throw new BusinessException("Account is deactivated");
        }

        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshSession(user, ipAddress, userAgent);

        log.info("User logged in: {}", user.getPhone());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    /**
     * Issues a new access token using a valid refresh token.
     *
     * @param request containing the refresh token
     * @return AuthResponse with new access token (refresh token unchanged)
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw new BusinessException("Refresh token expired. Please login again.");
        }

        UUID userId = jwtTokenProvider.extractUserId(refreshToken);
        String subject = jwtTokenProvider.extractPhone(refreshToken);
        User user = userId != null
                ? userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"))
                : userRepository.findByPhone(subject)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshSession session = refreshSessionRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (!session.isActive() || !session.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Invalid refresh token");
        }

        if (user.getPasswordChangedAt() != null
                && jwtTokenProvider.extractIssuedAt(refreshToken).toInstant().isBefore(user.getPasswordChangedAt())) {
            session.setRevokedAt(Instant.now());
            refreshSessionRepository.save(session);
            throw new BusinessException("Password changed. Please login again.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String rotatedRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        session.setTokenHash(hashToken(rotatedRefreshToken));
        session.setExpiresAt(jwtTokenProvider.extractExpiration(rotatedRefreshToken).toInstant());
        session.setLastUsedAt(Instant.now());
        refreshSessionRepository.save(session);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(rotatedRefreshToken)
                .user(toUserDto(user))
                .build();
    }

    @Transactional
    public void requestCustomerOtp(OtpRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .orElseThrow(() -> new BusinessException("Customer account not found for this phone number"));
        if (!user.isActive()) {
            throw new BusinessException("Account is deactivated");
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpCodeRepository.consumeOpenCodes(user.getPhone(), OtpCode.Purpose.CUSTOMER_LOGIN, Instant.now());
        otpCodeRepository.save(OtpCode.builder()
                .phone(user.getPhone())
                .purpose(OtpCode.Purpose.CUSTOMER_LOGIN)
                .otpHash(passwordEncoder.encode(user.getPhone() + ":" + otp))
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        notificationService.sendOneTimePassword(user.getId(), otp);
        log.info("Customer OTP issued for {}", user.getPhone());
    }

    @Transactional
    public AuthResponse verifyCustomerOtp(OtpVerifyRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByPhone(request.getPhone())
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .orElseThrow(() -> new BusinessException("Customer account not found"));

        OtpCode code = otpCodeRepository
                .findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        request.getPhone(), OtpCode.Purpose.CUSTOMER_LOGIN)
                .orElseThrow(() -> new BusinessException("OTP expired or not requested"));

        if (!code.isUsable()) {
            throw new BusinessException("OTP expired. Please request a new OTP.");
        }

        code.setAttempts(code.getAttempts() + 1);
        if (!passwordEncoder.matches(request.getPhone() + ":" + request.getOtp(), code.getOtpHash())) {
            otpCodeRepository.save(code);
            throw new BusinessException("Invalid OTP");
        }

        code.setConsumedAt(Instant.now());
        otpCodeRepository.save(code);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshSession(user, ipAddress, userAgent);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    /**
     * Invalidates the refresh token (logout).
     *
     * @param userId the user to log out
     */
    @Transactional
    public void logout(java.util.UUID userId) {
        refreshSessionRepository.revokeAllForUser(userId, Instant.now());
        userRepository.findById(userId).ifPresent(user -> log.info("User logged out: {}", user.getPhone()));
    }

    /**
     * Updates the FCM device token for push notifications.
     *
     * @param userId   user to update
     * @param fcmToken the new FCM token from the device
     */
    @Transactional
    public void updateFcmToken(java.util.UUID userId, String fcmToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        });
    }

    @Transactional
    public AuthResponse oauthLogin(User user, String ipAddress, String userAgent) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshSession(user, ipAddress, userAgent);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    private String issueRefreshSession(User user, String ipAddress, String userAgent) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        refreshSessionRepository.save(RefreshSession.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .expiresAt(jwtTokenProvider.extractExpiration(refreshToken).toInstant())
                .lastUsedAt(Instant.now())
                .build());
        return refreshToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
