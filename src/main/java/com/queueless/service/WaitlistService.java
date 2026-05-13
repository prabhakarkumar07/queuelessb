package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.exception.*;
import com.queueless.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the waitlist for shops whose queue is full or paused.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final ShopRepository shopRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Joins the waitlist for a shop. Throws if the user is already waiting.
     */
    @Transactional
    public WaitlistDto join(JoinWaitlistRequest request, UUID userId) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        waitlistRepository.findActiveEntryForUser(shop.getId(), userId)
                .ifPresent(w -> { throw new BusinessException("You are already on the waitlist for this shop"); });

        Serviceoffred service = null;
        if (request.getServiceId() != null) {
            service = serviceRepository.findById(request.getServiceId())
                    .filter(s -> s.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        }

        Waitlist entry = Waitlist.builder()
                .shop(shop)
                .user(user)
                .service(service)
                .build();

        entry = waitlistRepository.save(entry);
        log.info("User {} joined waitlist for shop {}", userId, shop.getId());
        return toDto(entry);
    }

    /**
     * Leaves the waitlist.
     */
    @Transactional
    public void leave(UUID waitlistId, UUID userId) {
        Waitlist entry = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found"));

        if (!entry.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your waitlist entry");
        }

        entry.setStatus(Waitlist.WaitlistStatus.EXPIRED);
        waitlistRepository.save(entry);
        log.info("User {} left waitlist entry {}", userId, waitlistId);
    }

    /**
     * Gets the authenticated user's active waitlist entries.
     */
    @Transactional(readOnly = true)
    public List<WaitlistDto> getMyWaitlist(UUID userId) {
        return waitlistRepository.findByUserIdOrderByJoinedAtDesc(userId)
                .stream()
                .filter(w -> w.getStatus() == Waitlist.WaitlistStatus.WAITING
                        || w.getStatus() == Waitlist.WaitlistStatus.NOTIFIED)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Notifies the top N waitlist members that a spot opened.
     * Called by TokenService after each token is SERVED or CANCELLED.
     */
    @Transactional
    public void notifyTopWaiting(UUID shopId, int spotsAvailable) {
        waitlistRepository.findTopWaiting(shopId, spotsAvailable).forEach(entry -> {
            entry.setStatus(Waitlist.WaitlistStatus.NOTIFIED);
            entry.setNotifiedAt(java.time.Instant.now());
            waitlistRepository.save(entry);
            notificationService.sendPushNotification(
                    entry.getUser().getId(),
                    entry.getUser().getFcmToken(),
                    "Queue Spot Open!",
                    "A spot just opened at " + entry.getShop().getName() +
                            ". Join now before it fills up!"
            );
        });
    }

    private WaitlistDto toDto(Waitlist w) {
        long ahead = waitlistRepository.countAhead(w.getShop().getId(), w.getJoinedAt());
        return WaitlistDto.builder()
                .id(w.getId())
                .shopId(w.getShop().getId())
                .shopName(w.getShop().getName())
                .serviceId(w.getService() != null ? w.getService().getId() : null)
                .serviceName(w.getService() != null ? w.getService().getName() : null)
                .joinedAt(w.getJoinedAt())
                .status(w.getStatus().name())
                .positionAhead(ahead)
                .build();
    }
}
