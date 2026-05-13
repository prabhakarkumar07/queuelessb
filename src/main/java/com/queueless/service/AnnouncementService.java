package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.exception.*;
import com.queueless.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages shop announcements — closure notices, break alerts, delay info, etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final ShopRepository shopRepository;
    private final ShopService shopService;

    @Transactional
    public AnnouncementDto create(UUID shopId, UUID actorId, CreateAnnouncementRequest request) {
        Shop shop = shopService.getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);

        Announcement announcement = Announcement.builder()
                .shop(shop)
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : Instant.now())
                .validTo(request.getValidTo())
                .build();

        announcement = announcementRepository.save(announcement);
        log.info("Announcement '{}' created for shop {}", announcement.getTitle(), shopId);
        return toDto(announcement);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> getActiveAnnouncements(UUID shopId) {
        return announcementRepository.findActiveByShopId(shopId, Instant.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> getAllAnnouncements(UUID shopId, UUID actorId) {
        Shop shop = shopService.getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);

        return announcementRepository.findByShopIdOrderByCreatedAtDesc(shopId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void delete(UUID announcementId, UUID actorId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));

        shopService.validateShopAccess(announcement.getShop(), actorId, StaffRole.MANAGER);

        announcementRepository.delete(announcement);
        log.info("Announcement {} deleted", announcementId);
    }

    private AnnouncementDto toDto(Announcement a) {
        Instant now = Instant.now();
        boolean active = !a.getValidFrom().isAfter(now)
                && (a.getValidTo() == null || !a.getValidTo().isBefore(now));
        return AnnouncementDto.builder()
                .id(a.getId())
                .shopId(a.getShop().getId())
                .shopName(a.getShop().getName())
                .title(a.getTitle())
                .message(a.getMessage())
                .type(a.getType().name())
                .active(active)
                .validFrom(a.getValidFrom())
                .validTo(a.getValidTo())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
