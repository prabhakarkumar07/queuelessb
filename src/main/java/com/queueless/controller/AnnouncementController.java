package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for shop announcements.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** Create an announcement (owner only). */
    @PostMapping("/owner/shops/{shopId}/announcements")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<AnnouncementDto> create(
            @PathVariable UUID shopId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(announcementService.create(shopId, user.getId(), request));
    }

    /** Get all active announcements for a shop (public). */
    @GetMapping("/shops/public/{shopId}/announcements")
    public ResponseEntity<List<AnnouncementDto>> getActive(@PathVariable UUID shopId) {
        return ResponseEntity.ok(announcementService.getActiveAnnouncements(shopId));
    }

    /** Get all announcements (owner view). */
    @GetMapping("/owner/shops/{shopId}/announcements")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<List<AnnouncementDto>> getAll(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(announcementService.getAllAnnouncements(shopId, user.getId()));
    }

    /** Delete an announcement. */
    @DeleteMapping("/owner/announcements/{announcementId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal User user) {
        announcementService.delete(announcementId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
