package com.queueless.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes", indexes = {
        @Index(name = "idx_otp_codes_phone_purpose", columnList = "phone,purpose,expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "otp_hash", nullable = false, length = 128)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Purpose purpose;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now()) && attempts < 5;
    }

    public enum Purpose {
        CUSTOMER_LOGIN
    }
}
