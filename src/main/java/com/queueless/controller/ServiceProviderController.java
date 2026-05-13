package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.ServiceProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for managing service providers (staff).
 */
@RestController
@RequestMapping("/api/shops/{shopId}/providers")
@RequiredArgsConstructor
public class ServiceProviderController {

    private final ServiceProviderService providerService;

    /**
     * Creates a new service provider for a shop.
     * POST /api/shops/{shopId}/providers
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ServiceProviderDto> createProvider(
            @PathVariable UUID shopId,
            @Valid @RequestBody CreateServiceProviderRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerService.createProvider(shopId, user.getId(), request));
    }

    /**
     * Gets all active providers for a shop.
     * GET /api/shops/{shopId}/providers
     */
    @GetMapping
    public ResponseEntity<List<ServiceProviderDto>> getProviders(@PathVariable UUID shopId) {
        return ResponseEntity.ok(providerService.getProvidersByShop(shopId));
    }

    /**
     * Deletes a provider.
     * DELETE /api/shops/{shopId}/providers/{providerId}
     */
    @DeleteMapping("/{providerId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<Void> deleteProvider(
            @PathVariable UUID shopId,
            @PathVariable UUID providerId,
            @AuthenticationPrincipal User user) {
        providerService.deleteProvider(shopId, providerId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates a provider's availability.
     */
    @PatchMapping("/{providerId}/availability")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<ServiceProviderDto> updateAvailability(
            @PathVariable UUID shopId,
            @PathVariable UUID providerId,
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                providerService.updateAvailability(shopId, providerId, user.getId(), request.getAvailable()));
    }

    /**
     * Updates the current provider's own availability.
     */
    @PatchMapping("/me/availability")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<ServiceProviderDto> updateMyAvailability(
            @PathVariable UUID shopId,
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                providerService.updateMyAvailability(shopId, user.getId(), request.getAvailable()));
    }
}
