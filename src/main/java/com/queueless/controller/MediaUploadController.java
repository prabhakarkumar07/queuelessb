package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.Shop;
import com.queueless.entity.User;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.ShopRepository;
import com.queueless.repository.UserRepository;
import com.queueless.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Media upload endpoints for user avatars and shop logos.
 *
 * POST   /api/media/avatar                   — upload user profile photo
 * DELETE /api/media/avatar                   — remove user profile photo
 * POST   /api/media/shops/{shopId}/logo      — upload shop logo (owner/admin)
 * DELETE /api/media/shops/{shopId}/logo      — remove shop logo (owner/admin)
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
public class MediaUploadController {

    private static final String AVATAR_FOLDER = "avatars/";
    private static final String LOGO_FOLDER = "logos/";

    private final MinioService minioService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    // ── User Avatar ──────────────────────────────────────────────────────────

    /**
     * Upload or replace the authenticated user's profile photo.
     * Accepts: multipart/form-data, field name "file"
     */
    @PostMapping("/avatar")
    public ResponseEntity<MediaUploadResponse> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {

        String url = minioService.uploadImage(file, AVATAR_FOLDER);

        // Delete old avatar if present
        if (user.getAvatarUrl() != null) {
            minioService.deleteByUrl(user.getAvatarUrl());
        }

        user.setAvatarUrl(url);
        userRepository.save(user);
        log.info("Avatar updated for user {}", user.getId());

        return ResponseEntity.ok(new MediaUploadResponse(url));
    }

    /**
     * Remove the authenticated user's profile photo.
     */
    @DeleteMapping("/avatar")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal User user) {
        if (user.getAvatarUrl() != null) {
            minioService.deleteByUrl(user.getAvatarUrl());
            user.setAvatarUrl(null);
            userRepository.save(user);
        }
        return ResponseEntity.noContent().build();
    }

    // ── Shop Logo ────────────────────────────────────────────────────────────

    /**
     * Upload or replace a shop's logo.
     * Only the shop owner or ADMIN may call this.
     */
    @PostMapping("/shops/{shopId}/logo")
    public ResponseEntity<MediaUploadResponse> uploadShopLogo(
            @AuthenticationPrincipal User user,
            @PathVariable UUID shopId,
            @RequestParam("file") MultipartFile file) {

        Shop shop = findShopAndAuthorize(shopId, user);
        String url = minioService.uploadImage(file, LOGO_FOLDER);

        // Delete old logo if stored in MinIO
        if (shop.getLogoUrl() != null && shop.getLogoUrl().startsWith("http")) {
            minioService.deleteByUrl(shop.getLogoUrl());
        }

        shop.setLogoUrl(url);
        shopRepository.save(shop);
        log.info("Logo updated for shop {}", shopId);

        return ResponseEntity.ok(new MediaUploadResponse(url));
    }

    /**
     * Remove a shop's logo.
     */
    @DeleteMapping("/shops/{shopId}/logo")
    public ResponseEntity<Void> removeShopLogo(
            @AuthenticationPrincipal User user,
            @PathVariable UUID shopId) {

        Shop shop = findShopAndAuthorize(shopId, user);
        if (shop.getLogoUrl() != null) {
            minioService.deleteByUrl(shop.getLogoUrl());
            shop.setLogoUrl(null);
            shopRepository.save(shop);
        }
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Shop findShopAndAuthorize(UUID shopId, User user) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        boolean isOwner = shop.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == com.queueless.entity.User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Not authorized to manage this shop's media");
        }
        return shop;
    }
}
