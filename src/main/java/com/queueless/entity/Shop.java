package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

/**
 * Shop entity representing a business registered on QueueLess.
 */
@Entity
@Table(name = "shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_account_id")
    private BusinessAccount businessAccount;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    @Builder.Default
    private String primaryColor = "#f97316";

    @Column(name = "business_registration_number", length = 100)
    private String businessRegistrationNumber;

    @Column(name = "branch_code", length = 50)
    private String branchCode;

    @Column(unique = true, length = 150)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "queue_paused", nullable = false)
    @Builder.Default
    private boolean queuePaused = false;

    @Column(name = "open_time", nullable = false)
    @Builder.Default
    private LocalTime openTime = LocalTime.of(9, 0);

    @Column(name = "close_time", nullable = false)
    @Builder.Default
    private LocalTime closeTime = LocalTime.of(18, 0);

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "shop_closed_days", joinColumns = @JoinColumn(name = "shop_id"))
    @Column(name = "day_of_week", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<DayOfWeek> closedDays = new LinkedHashSet<>();

    @Column(name = "avg_service_mins", nullable = false)
    @Builder.Default
    private int avgServiceMins = 10;

    @Column(name = "max_queue_size", nullable = false)
    @Builder.Default
    private int maxQueueSize = 100;

    @Column(name = "no_show_grace_mins", nullable = false)
    @Builder.Default
    private int noShowGraceMins = 5;

    @Column(name = "rejoin_window_mins", nullable = false)
    @Builder.Default
    private int rejoinWindowMins = 15;

    @Column(name = "max_rejoins", nullable = false)
    @Builder.Default
    private int maxRejoins = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_status", length = 50)
    private IncidentStatus incidentStatus;

    @Column(name = "incident_message", length = 500)
    private String incidentMessage;

    @Column(name = "stop_tokens_before_closing_mins", nullable = false, columnDefinition = "integer DEFAULT 0")
    @Builder.Default
    private int stopTokensBeforeClosingMins = 0;

    @Column(name = "max_tokens_per_day")
    private Integer maxTokensPerDay;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Serviceoffred> services = new ArrayList<>();

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Token> tokens = new ArrayList<>();

    /** Shop category types. */
    public enum Category {
        CLINIC, SALON, BANK, GOVERNMENT, RESTAURANT,OTHER
    }

    public enum VerificationStatus {
        PENDING, SUBMITTED, VERIFIED, REJECTED
    }

    public enum IncidentStatus {
        NORMAL, DELAYED, EMERGENCY_CLOSURE, SYSTEM_DOWN
    }
}
