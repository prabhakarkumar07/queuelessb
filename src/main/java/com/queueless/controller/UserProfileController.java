package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authenticated user profile operations.
 * All endpoints require a valid Bearer token.
 *
 * GET    /api/users/profile          → view own profile
 * PUT    /api/users/profile          → update name / email
 * PUT    /api/users/profile/password → change password
 */
@RestController
@RequestMapping("/api/users/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /** Returns the authenticated user's profile. */
    @GetMapping
    public ResponseEntity<UserDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userProfileService.getProfile(user));
    }

    /** Updates the user's display name and optional email. */
    @PutMapping
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(user.getId(), request));
    }

    /** Changes the user's password after verifying the current one. */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(user.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
