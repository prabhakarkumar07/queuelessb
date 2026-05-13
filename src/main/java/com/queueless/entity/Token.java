package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Token entity representing a customer's place in a shop queue.
 * Each token has a unique display number (e.g., A042) per shop per day.
 */
@Entity
@Table(name = "tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Serviceoffred service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private ServiceProvider serviceProvider;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    /** Human-readable token display, e.g., "A042". */
    @Column(name = "display_number", nullable = false, length = 10)
    private String displayNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TokenStatus status = TokenStatus.WAITING;

    /** Priority lane — set by the shop operator at the counter or via the dashboard. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'NORMAL'")
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "reminder_sent", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean reminderSent = false;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private Instant issuedAt = Instant.now();

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "served_at")
    private Instant servedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "rejoin_count", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int rejoinCount = 0;

    @Column(name = "snooze_count", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int snoozeCount = 0;

    @Column(name = "sort_penalty", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int sortPenalty = 0;

    @Column(name = "sms_sent", nullable = false)
    @Builder.Default
    private boolean smsSent = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "date_issued", nullable = false)
    @Builder.Default
    private LocalDate dateIssued = LocalDate.now();

    @Column(name = "no_show_probability")
    @Builder.Default
    private Double noShowProbability = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** All possible statuses for a token in the queue lifecycle. */
    public enum TokenStatus {
        WAITING,    // In queue, not yet called
        CALLED,     // Shop called this token
        ARRIVED,    // Customer checked in at the counter
        SERVING,    // Currently being served
        SERVED,     // Service completed
        SKIPPED,    // Customer missed their call
        SNOOZED,    // Token pushed back in queue
        CANCELLED,  // Customer or shop cancelled
        EXPIRED     // Token expired (e.g., end of day)
    }

    /** Priority lane for special categories of customers. */
    public enum Priority {
        NORMAL,
        SENIOR,
        PREGNANT,
        VIP,
        EMERGENCY  // Highest priority — served immediately
    }
}
