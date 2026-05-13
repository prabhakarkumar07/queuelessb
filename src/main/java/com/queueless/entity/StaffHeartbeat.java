package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_heartbeats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_staff_heartbeat_provider_device", columnNames = {"provider_id", "device_id"})
}, indexes = {
        @Index(name = "idx_staff_heartbeats_shop_last_seen", columnList = "shop_id,last_seen_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ServiceProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "app_version", length = 80)
    private String appVersion;

    @Column(nullable = false)
    @Builder.Default
    private boolean online = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
