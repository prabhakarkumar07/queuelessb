package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * UserLoyalty tracks points and visits per user per shop.
 */
@Entity
@Table(name = "user_loyalty", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "shop_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoyalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false)
    @Builder.Default
    private int points = 0;

    @Column(name = "total_visits", nullable = false)
    @Builder.Default
    private int totalVisits = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoyaltyTier tier = LoyaltyTier.BRONZE;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum LoyaltyTier {
        BRONZE, SILVER, GOLD
    }
}
