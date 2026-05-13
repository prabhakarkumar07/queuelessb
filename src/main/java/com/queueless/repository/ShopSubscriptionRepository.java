package com.queueless.repository;

import com.queueless.entity.ShopSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, UUID> {
    Optional<ShopSubscription> findFirstByShopIdOrderByCreatedAtDesc(UUID shopId);
}
