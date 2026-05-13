package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.ServiceProvider;
import com.queueless.entity.StaffHeartbeat;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.ServiceProviderRepository;
import com.queueless.repository.StaffHeartbeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffHeartbeatService {

    private final StaffHeartbeatRepository heartbeatRepository;
    private final ServiceProviderRepository providerRepository;

    @Transactional
    public StaffHeartbeatDto heartbeat(UUID shopId, UUID userId, StaffHeartbeatRequest request) {
        ServiceProvider provider = providerRepository.findByUserId(userId)
                .filter(ServiceProvider::isActive)
                .filter(p -> p.getShop().getId().equals(shopId))
                .orElseThrow(() -> new AccessDeniedException("Not assigned to this shop"));

        StaffHeartbeat heartbeat = heartbeatRepository
                .findByProviderIdAndDeviceId(provider.getId(), request.getDeviceId())
                .orElseGet(() -> StaffHeartbeat.builder()
                        .shop(provider.getShop())
                        .provider(provider)
                        .user(provider.getUser())
                        .deviceId(request.getDeviceId())
                        .build());
        heartbeat.setAppVersion(request.getAppVersion());
        heartbeat.setOnline(request.getOnline() == null || request.getOnline());
        heartbeat.setLastSeenAt(Instant.now());
        return toDto(heartbeatRepository.save(heartbeat));
    }

    @Transactional(readOnly = true)
    public List<StaffHeartbeatDto> getShopPresence(UUID shopId, UUID ownerId) {
        List<StaffHeartbeat> heartbeats = heartbeatRepository.findByShopIdOrderByLastSeenAtDesc(shopId);
        if (!heartbeats.isEmpty() && !heartbeats.get(0).getShop().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Not authorized");
        }
        if (heartbeats.isEmpty()) {
            providerRepository.findByShopIdAndActiveTrue(shopId).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No staff found for this shop"));
        }
        return heartbeats.stream().map(this::toDto).toList();
    }

    @Transactional
    public int markOfflineSince(Instant cutoff) {
        List<StaffHeartbeat> stale = heartbeatRepository.findByOnlineTrueAndLastSeenAtBefore(cutoff);
        stale.forEach(h -> h.setOnline(false));
        heartbeatRepository.saveAll(stale);
        return stale.size();
    }

    private StaffHeartbeatDto toDto(StaffHeartbeat heartbeat) {
        return StaffHeartbeatDto.builder()
                .id(heartbeat.getId())
                .shopId(heartbeat.getShop().getId())
                .providerId(heartbeat.getProvider().getId())
                .staffName(heartbeat.getUser().getName())
                .deviceId(heartbeat.getDeviceId())
                .appVersion(heartbeat.getAppVersion())
                .online(heartbeat.isOnline())
                .lastSeenAt(heartbeat.getLastSeenAt())
                .build();
    }
}
