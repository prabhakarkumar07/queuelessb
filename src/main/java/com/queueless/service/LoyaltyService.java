package com.queueless.service;

import com.queueless.dto.Dtos;
import com.queueless.entity.Shop;
import com.queueless.entity.User;
import com.queueless.entity.UserLoyalty;
import com.queueless.entity.LoyaltyConfig;
import com.queueless.repository.UserLoyaltyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final UserLoyaltyRepository loyaltyRepository;
    private final LoyaltyConfigService configService;

    @Transactional
    public void awardPoints(User user, Shop shop) {
        LoyaltyConfig config = configService.getOrCreateConfigEntity(shop.getId());
        int pointsToAward = config.getPointsPerVisit();

        UserLoyalty loyalty = loyaltyRepository.findByUserIdAndShopId(user.getId(), shop.getId())
                .orElseGet(() -> UserLoyalty.builder()
                        .user(user)
                        .shop(shop)
                        .points(0)
                        .totalVisits(0)
                        .tier(UserLoyalty.LoyaltyTier.BRONZE)
                        .build());

        loyalty.setPoints(loyalty.getPoints() + pointsToAward);
        loyalty.setTotalVisits(loyalty.getTotalVisits() + 1);

        updateTier(loyalty, config);

        loyaltyRepository.save(loyalty);
        log.info("Awarded {} points to user {} at shop {}. Total points: {}",
                pointsToAward, user.getId(), shop.getId(), loyalty.getPoints());
    }

    private void updateTier(UserLoyalty loyalty, LoyaltyConfig config) {
        int points = loyalty.getPoints();
        if (points >= config.getGoldThreshold()) {
            loyalty.setTier(UserLoyalty.LoyaltyTier.GOLD);
        } else if (points >= config.getSilverThreshold()) {
            loyalty.setTier(UserLoyalty.LoyaltyTier.SILVER);
        } else {
            loyalty.setTier(UserLoyalty.LoyaltyTier.BRONZE);
        }
    }

    public UserLoyalty getLoyalty(UUID userId, UUID shopId) {
        return loyaltyRepository.findByUserIdAndShopId(userId, shopId)
                .orElse(null);
    }

    /**
     * Returns all loyalty entries for a user across all shops, including config thresholds.
     * B-14: Used by GET /api/loyalty/my to power the mobile Rewards screen without the
     * broken workaround of calling shopApi.getNearby(0, 0, 9999).
     */
    @Transactional(readOnly = true)
    public List<Dtos.UserLoyaltyDto> getAllLoyaltyForUser(UUID userId) {
        return loyaltyRepository.findByUserId(userId).stream()
                .filter(l -> l.getPoints() > 0) // Only include shops where the user has earned points
                .map(l -> {
                    LoyaltyConfig config = configService.getOrCreateConfigEntity(l.getShop().getId());
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
                })
                .collect(Collectors.toList());
    }
}
