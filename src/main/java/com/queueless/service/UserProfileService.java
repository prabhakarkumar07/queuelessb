package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.exception.BusinessException;
import com.queueless.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for user profile operations: view, update, and change password.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Returns a safe DTO for the authenticated user. */
    public UserDto getProfile(User user) {
        return toDto(user);
    }

    /**
     * Updates the user's name and/or email.
     * Validates that the new email is not already taken by another account.
     */
    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Check email uniqueness only if a different email is provided
        String newEmail = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail().trim().toLowerCase()
                : null;

        if (newEmail != null && !newEmail.equals(user.getEmail())) {
            boolean taken = userRepository.existsByEmailAndIdNot(newEmail, userId);
            if (taken) {
                throw new BusinessException("This email address is already in use");
            }
            user.setEmail(newEmail);
        }

        user.setName(request.getName().trim());
        User saved = userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return toDto(saved);
    }

    /**
     * Changes the user's password after verifying the current one.
     * Rejects OAuth2 (Google) users who have no password hash.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException("Password change is not available for accounts signed in with Google.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        log.info("Password changed for user {}", userId);
    }

    private UserDto toDto(User user) {
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
