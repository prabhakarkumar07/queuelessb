package com.queueless.controller;

import com.queueless.entity.User;
import com.queueless.service.AnalyticsService;
import com.queueless.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ShopService shopService;

    @GetMapping({"/shop/{shopId}", "/owner/shops/{shopId}/analytics"})
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN', 'SERVICE_PROVIDER')")
    public Map<String, Object> getShopAnalytics(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal User user) {
        
        // Ensure user owns the shop or is a MANAGER
        shopService.getShopWithAccessCheck(shopId, user.getId(), com.queueless.entity.StaffRole.MANAGER); 
        
        return analyticsService.getShopAnalytics(shopId, days);
    }
}
