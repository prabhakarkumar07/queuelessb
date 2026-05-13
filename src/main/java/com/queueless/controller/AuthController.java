package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.AuthService;
import com.queueless.service.RateLimitService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller: register, login, refresh token, logout, FCM token update.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    /** Registers a new user and returns JWT tokens. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        rateLimitService.consumeAuth(clientKey(httpRequest, request.getPhone()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    /** Authenticates an existing user with phone + password. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        rateLimitService.consumeAuth(clientKey(httpRequest, request.getPhone()));
        return ResponseEntity.ok(authService.login(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    /** Sends a 6-digit OTP to a customer for passwordless login. */
    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest request,
                                           HttpServletRequest httpRequest) {
        rateLimitService.consumeAuth("otp-request:" + clientKey(httpRequest, request.getPhone()));
        authService.requestCustomerOtp(request);
        return ResponseEntity.accepted().build();
    }

    /** Verifies customer OTP and returns JWT tokens. */
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                                  HttpServletRequest httpRequest) {
        rateLimitService.consumeAuth("otp-verify:" + clientKey(httpRequest, request.getPhone()));
        return ResponseEntity.ok(authService.verifyCustomerOtp(
                request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    /** Issues a new access token from a valid refresh token. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest httpRequest) {
        rateLimitService.consumeAuth("refresh:" + clientIp(httpRequest));
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /** Invalidates the refresh token (logout). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        authService.logout(user.getId());
        return ResponseEntity.noContent().build();
    }

    /** Updates the FCM push notification device token. */
    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal User user,
                                                @RequestParam String fcmToken) {
        authService.updateFcmToken(user.getId(), fcmToken);
        return ResponseEntity.ok().build();
    }

    /** Returns the currently authenticated user's profile. */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build());
    }

    private String clientKey(HttpServletRequest request, String phone) {
        return clientIp(request) + ":" + phone;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
