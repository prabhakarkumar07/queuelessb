package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a holiday/closure day for a shop.
 * On this date the shop will not accept new tokens or appointments.
 */
@Entity
@Table(name = "shop_holidays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
