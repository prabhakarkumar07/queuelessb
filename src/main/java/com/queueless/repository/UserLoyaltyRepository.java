package com.queueless.repository;

import com.queueless.entity.UserLoyalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLoyaltyRepository extends JpaRepository<UserLoyalty, UUID> {
    Optional<UserLoyalty> findByUserIdAndShopId(UUID userId, UUID shopId);

    /** Returns all loyalty records for a user across all shops (for the Rewards screen). */
    List<UserLoyalty> findByUserId(UUID userId);
}
