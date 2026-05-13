package com.queueless.repository;

import com.queueless.entity.StaffHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffHeartbeatRepository extends JpaRepository<StaffHeartbeat, UUID> {
    Optional<StaffHeartbeat> findByProviderIdAndDeviceId(UUID providerId, String deviceId);
    List<StaffHeartbeat> findByShopIdOrderByLastSeenAtDesc(UUID shopId);
    List<StaffHeartbeat> findByOnlineTrueAndLastSeenAtBefore(Instant cutoff);
}
