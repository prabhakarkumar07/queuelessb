package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/shops/{shopId}/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<ShopSubscriptionDto> current(@PathVariable UUID shopId,
                                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionService.current(shopId, user.getId()));
    }

    @PutMapping
    public ResponseEntity<ShopSubscriptionDto> update(@PathVariable UUID shopId,
                                                      @Valid @RequestBody UpdateSubscriptionRequest request,
                                                      @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionService.changePlan(shopId, user.getId(), request));
    }
}
