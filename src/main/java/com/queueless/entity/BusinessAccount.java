package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_accounts", indexes = {
        @Index(name = "idx_business_accounts_owner", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "billing_email", length = 200)
    private String billingEmail;

    @Column(length = 30)
    private String gstin;

    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPercent = BigDecimal.valueOf(18);

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    @Builder.Default
    private String invoicePrefix = "QL";

    @Column(name = "razorpay_key_id", length = 150)
    private String razorpayKeyId;

    @Column(name = "razorpay_key_secret", length = 255)
    private String razorpayKeySecret;

    @Column(name = "stripe_publishable_key", length = 200)
    private String stripePublishableKey;

    @Column(name = "payout_enabled", nullable = false)
    @Builder.Default
    private boolean payoutEnabled = false;

    @Column(name = "settlement_frequency", length = 50)
    @Builder.Default
    private String settlementFrequency = "DAILY";

    @Column(name = "payout_account_name", length = 200)
    private String payoutAccountName;

    @Column(name = "payout_account_number_masked", length = 40)
    private String payoutAccountNumberMasked;

    @Column(name = "payout_ifsc", length = 20)
    private String payoutIfsc;

    @Column(name = "sms_sender_id", length = 20)
    private String smsSenderId;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
