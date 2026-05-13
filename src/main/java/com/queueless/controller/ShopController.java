package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shop management controller — CRUD, queue control, services, stats.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final com.queueless.service.RateLimitService rateLimitService;
    private final com.queueless.service.AnalyticsService analyticsService;

    private final jakarta.servlet.http.HttpServletRequest httpRequest;


    // ——— Public endpoints ———

    /** Returns shop details — publicly accessible for customer app. */
    @GetMapping("/shops/public/{shopId}")
    public ResponseEntity<ShopDto> getShopPublic(@PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.getShopById(shopId));
    }

    /** Returns nearby shops sorted by distance. */
    @GetMapping("/shops/public/nearby")
    public ResponseEntity<List<ShopDto>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        rateLimitService.consumePublicSearch(clientIp(httpRequest));
        return ResponseEntity.ok(shopService.getNearbyShops(lat, lng, radiusKm));
    }

    /** Searches active shops globally by name, category, city, state, or address. */
    @GetMapping("/shops/public/search")
    public ResponseEntity<List<ShopDto>> searchPublicShops(@RequestParam String q) {
        rateLimitService.consumePublicSearch(clientIp(httpRequest));
        return ResponseEntity.ok(shopService.searchPublicShops(q));
    }

    /** Returns popular shops using discovery events, queue usage, and recent reviews. */
    @GetMapping("/shops/public/popular")
    public ResponseEntity<List<ShopDto>> getPopular(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(shopService.getPopularShops(category, lat, lng, limit));
    }

    /** Returns shops frequently surfaced by recent customer search and detail views. */
    @GetMapping("/shops/public/trending")
    public ResponseEntity<List<ShopDto>> getTrending(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(shopService.getTrendingShops(category, lat, lng, limit));
    }

    /** Looks up a shop by its human-readable slug (e.g. /api/shops/public/slug/star-salon). */
    @GetMapping("/shops/public/slug/{slug}")
    public ResponseEntity<ShopDto> getShopBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(shopService.getShopBySlug(slug));
    }

    /** Returns services for a shop. */
    @GetMapping("/shops/public/{shopId}/services")
    public ResponseEntity<List<ServiceDto>> getServices(@PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.getShopServices(shopId));
    }

    // ——— Owner endpoints ———

    /** Creates a new shop. */
    @PostMapping("/owner/shops")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<ShopDto> createShop(@Valid @RequestBody CreateShopRequest request,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.createShop(request, user));
    }

    /** Returns all shops owned by the authenticated user. */
    @GetMapping("/owner/shops")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<List<ShopDto>> getMyShops(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.getOwnerShops(user.getId()));
    }

    /** Returns all shops accessible to the current dashboard user. */
    @GetMapping("/shops/my")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<List<ShopDto>> getAccessibleShops(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.getAccessibleShops(user));
    }

    /** Updates a shop's details. */
    @PutMapping("/owner/shops/{shopId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ShopDto> updateShop(@PathVariable UUID shopId,
                                               @Valid @RequestBody CreateShopRequest request,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.updateShop(shopId, request, user.getId()));
    }

    /** Pauses the queue for a shop. */
    @PostMapping("/owner/shops/{shopId}/pause")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ShopDto> pauseQueue(@PathVariable UUID shopId,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.toggleQueuePause(shopId, true, user.getId()));
    }

    /** Resumes the queue for a shop. */
    @PostMapping("/owner/shops/{shopId}/resume")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ShopDto> resumeQueue(@PathVariable UUID shopId,
                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.toggleQueuePause(shopId, false, user.getId()));
    }

    // NOTE: The canonical pause/resume API uses POST /owner/shops/{shopId}/pause and /resume above.
    // The duplicate PUT /owner/shops/{id}/pause has been removed to avoid conflicting contracts.

    @PutMapping("/owner/shops/{id}/incident")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ShopDto updateIncident(@PathVariable UUID id, @Valid @RequestBody UpdateIncidentRequest request, @AuthenticationPrincipal User user) {
        return shopService.updateIncident(id, request, user.getId());
    }

    /** Rapidly opens a new branch by cloning an existing shop. */
    @PostMapping("/owner/shops/{shopId}/clone")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<ShopDto> cloneShop(@PathVariable UUID shopId,
                                              @Valid @RequestBody CloneShopRequest request,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shopService.cloneShop(shopId, request.getNewName(), request.getBranchCode(), request.getNewAddress(), user.getId()));
    }

    /** Returns a printable QR poster payload for a shop/counter. */
    @GetMapping("/owner/shops/{shopId}/qr-poster")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<QrPosterDto> getQrPoster(@PathVariable UUID shopId,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.getQrPoster(shopId, user.getId()));
    }

    /** Returns today's stats for the shop dashboard. */
    @GetMapping("/owner/shops/{shopId}/stats")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ShopStatsDto> getStats(@PathVariable UUID shopId,
                                                  @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(shopService.getShopStats(shopId, user.getId()));
    }

    /** Adds a new service to a shop. */
    @PostMapping("/owner/shops/{shopId}/services")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ServiceDto> addService(@PathVariable UUID shopId,
                                                  @Valid @RequestBody CreateServiceRequest request,
                                                  @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shopService.createService(shopId, request, user.getId()));
    }

    /** Soft-deletes a service. */
    @DeleteMapping("/owner/services/{serviceId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<Void> deleteService(@PathVariable UUID serviceId,
                                               @AuthenticationPrincipal User user) {
        shopService.deleteService(serviceId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ——— Shop Status ———

    /** Returns the real-time status of a shop (OPEN, CLOSED, BREAK, CLOSES_SOON, HOLIDAY). */
    @GetMapping("/shops/public/{shopId}/status")
    public ResponseEntity<ShopStatusDto> getShopStatus(@PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.getShopStatus(shopId));
    }

    // ——— Holiday Management ———

    @PostMapping("/owner/shops/{shopId}/holidays")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<HolidayDto> addHoliday(@PathVariable UUID shopId,
                                                   @Valid @RequestBody CreateHolidayRequest request,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shopService.addHoliday(shopId, user.getId(), request));
    }

    @GetMapping("/shops/public/{shopId}/holidays")
    public ResponseEntity<List<HolidayDto>> getHolidays(@PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.getUpcomingHolidays(shopId));
    }

    @DeleteMapping("/owner/holidays/{holidayId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID holidayId,
                                               @AuthenticationPrincipal User user) {
        shopService.deleteHoliday(holidayId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ——— Analytics ———

    @GetMapping("/owner/shops/{shopId}/analytics")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN', 'SERVICE_PROVIDER')")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal User user) {
        // Access check
        shopService.getShopWithAccessCheck(shopId, user.getId(), com.queueless.entity.StaffRole.MANAGER);
        return ResponseEntity.ok(analyticsService.getShopAnalytics(shopId, days));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
