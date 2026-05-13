package com.queueless.service;

import com.queueless.dto.Dtos;
import com.queueless.entity.LoyaltyConfig;
import com.queueless.entity.Shop;
import com.queueless.entity.User;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.LoyaltyConfigRepository;
import com.queueless.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyConfigService {

    private final LoyaltyConfigRepository configRepository;
    private final ShopRepository shopRepository;

    @Transactional
    public Dtos.LoyaltyConfigDto getConfig(UUID shopId) {
        LoyaltyConfig config = configRepository.findByShopId(shopId)
                .orElseGet(() -> createDefaultConfig(shopId));
        return toDto(config);
    }

    @Transactional
    public Dtos.LoyaltyConfigDto getConfigForOwner(UUID shopId, User actor) {
        validateShopOwnerOrAdmin(shopId, actor);
        return getConfig(shopId);
    }

    @Transactional
    public Dtos.LoyaltyConfigDto updateConfig(UUID shopId, Dtos.UpdateLoyaltyConfigRequest request, User actor) {
        validateShopOwnerOrAdmin(shopId, actor);
        LoyaltyConfig config = configRepository.findByShopId(shopId)
                .orElseGet(() -> createDefaultConfig(shopId));

        config.setPointsPerVisit(request.getPointsPerVisit());
        config.setBronzeThreshold(request.getBronzeThreshold());
        config.setSilverThreshold(request.getSilverThreshold());
        config.setGoldThreshold(request.getGoldThreshold());

        return toDto(configRepository.save(config));
    }

    private void validateShopOwnerOrAdmin(UUID shopId, User actor) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }
        if (!shop.getOwner().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Not authorized to manage loyalty for this shop");
        }
    }

    @Transactional
    public LoyaltyConfig getOrCreateConfigEntity(UUID shopId) {
        return configRepository.findByShopId(shopId)
                .orElseGet(() -> createDefaultConfig(shopId));
    }

    private LoyaltyConfig createDefaultConfig(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        LoyaltyConfig config = LoyaltyConfig.builder()
                .shop(shop)
                .pointsPerVisit(10)
                .bronzeThreshold(50)
                .silverThreshold(200)
                .goldThreshold(500)
                .build();
        return configRepository.save(config);
    }

    private Dtos.LoyaltyConfigDto toDto(LoyaltyConfig config) {
        return Dtos.LoyaltyConfigDto.builder()
                .id(config.getId())
                .shopId(config.getShop().getId())
                .pointsPerVisit(config.getPointsPerVisit())
                .bronzeThreshold(config.getBronzeThreshold())
                .silverThreshold(config.getSilverThreshold())
                .goldThreshold(config.getGoldThreshold())
                .build();
    }
}
