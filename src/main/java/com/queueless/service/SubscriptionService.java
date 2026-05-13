package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.Shop;
import com.queueless.entity.ShopSubscription;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.ShopRepository;
import com.queueless.repository.ShopSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    @Transactional
    public ShopSubscriptionDto changePlan(UUID shopId, UUID ownerId, UpdateSubscriptionRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (!shop.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Not authorized");
        }
        Instant now = Instant.now();
        ShopSubscription subscription = ShopSubscription.builder()
                .shop(shop)
                .plan(request.getPlan())
                .status(request.getPlan() == ShopSubscription.Plan.FREE
                        ? ShopSubscription.Status.ACTIVE
                        : ShopSubscription.Status.TRIALING)
                .amount(amountFor(request.getPlan()))
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build();
        return toDto(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public ShopSubscriptionDto current(UUID shopId, UUID ownerId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (!shop.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Not authorized");
        }
        return subscriptionRepository.findFirstByShopIdOrderByCreatedAtDesc(shopId)
                .map(this::toDto)
                .orElseGet(() -> ShopSubscriptionDto.builder()
                        .shopId(shop.getId())
                        .shopName(shop.getName())
                        .plan(ShopSubscription.Plan.FREE)
                        .status(ShopSubscription.Status.ACTIVE)
                        .amount(BigDecimal.ZERO)
                        .build());
    }

    private BigDecimal amountFor(ShopSubscription.Plan plan) {
        return switch (plan) {
            case FREE -> BigDecimal.ZERO;
            case STARTER -> BigDecimal.valueOf(499);
            case GROWTH -> BigDecimal.valueOf(1499);
            case PRO -> BigDecimal.valueOf(3999);
        };
    }

    private ShopSubscriptionDto toDto(ShopSubscription subscription) {
        return ShopSubscriptionDto.builder()
                .id(subscription.getId())
                .shopId(subscription.getShop().getId())
                .shopName(subscription.getShop().getName())
                .plan(subscription.getPlan())
                .status(subscription.getStatus())
                .amount(subscription.getAmount())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .build();
    }
}
