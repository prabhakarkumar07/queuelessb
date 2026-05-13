package com.queueless.controller;

import com.queueless.dto.Dtos;
import com.queueless.dto.Dtos.LoyaltyConfigDto;
import com.queueless.entity.User;
import com.queueless.entity.UserLoyalty;
import com.queueless.service.LoyaltyService;
import com.queueless.service.LoyaltyConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final LoyaltyConfigService loyaltyConfigService;

    /**
     * Returns the loyalty config for a shop (owner/admin only).
     */
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    @GetMapping("/owner/shop/{shopId}/config")
    public ResponseEntity<Dtos.LoyaltyConfigDto> getConfig(@PathVariable UUID shopId,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loyaltyConfigService.getConfigForOwner(shopId, user));
    }

    /**
     * Updates loyalty config for a shop (owner/admin only).
     */
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    @PutMapping("/owner/shop/{shopId}/config")
    public ResponseEntity<Dtos.LoyaltyConfigDto> updateConfig(@PathVariable UUID shopId,
                                                              @Valid @RequestBody Dtos.UpdateLoyaltyConfigRequest request,
                                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loyaltyConfigService.updateConfig(shopId, request, user));
    }

    /**
     * Returns this customer's loyalty data at a specific shop.
     * B-06: Added @PreAuthorize — previously unguarded, which would NPE for non-customer roles.
     */
    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Dtos.UserLoyaltyDto> getMyLoyalty(@PathVariable UUID shopId,
                                                             @AuthenticationPrincipal User user) {
        LoyaltyConfigDto config = loyaltyConfigService.getConfig(shopId);
        UserLoyalty loyalty = loyaltyService.getLoyalty(user.getId(), shopId);
        if (loyalty == null) {
            return ResponseEntity.ok(Dtos.UserLoyaltyDto.builder()
                    .shopId(shopId)
                    .points(0)
                    .totalVisits(0)
                    .tier("NONE")
                    .bronzeThreshold(config.getBronzeThreshold())
                    .silverThreshold(config.getSilverThreshold())
                    .goldThreshold(config.getGoldThreshold())
                    .build());
        }
        return ResponseEntity.ok(toDto(loyalty, config));
    }

    /**
     * Returns ALL loyalty entries for the authenticated customer across all shops.
     * B-14: Replaces the broken RewardsScreen workaround of shopApi.getNearby(0, 0, 9999).
     * GET /api/loyalty/my
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Dtos.UserLoyaltyDto>> getMyAllLoyalty(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loyaltyService.getAllLoyaltyForUser(user.getId()));
    }

    private Dtos.UserLoyaltyDto toDto(UserLoyalty l, LoyaltyConfigDto config) {
        return Dtos.UserLoyaltyDto.builder()
                .id(l.getId())
                .shopId(l.getShop().getId())
                .shopName(l.getShop().getName())
                .points(l.getPoints())
                .totalVisits(l.getTotalVisits())
                .tier(l.getTier().name())
                .bronzeThreshold(config.getBronzeThreshold())
                .silverThreshold(config.getSilverThreshold())
                .goldThreshold(config.getGoldThreshold())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
