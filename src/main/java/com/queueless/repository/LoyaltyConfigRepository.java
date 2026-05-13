package com.queueless.repository;

import com.queueless.entity.LoyaltyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyConfigRepository extends JpaRepository<LoyaltyConfig, UUID> {
    Optional<LoyaltyConfig> findByShopId(UUID shopId);
}
