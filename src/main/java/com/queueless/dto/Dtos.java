package com.queueless.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.queueless.entity.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Container class for all DTO definitions used in QueueLess API. */
public class Dtos {

    // ========== AUTH DTOs ==========

    @Data
    public static class RegisterRequest {
        @NotBlank @Size(min = 2, max = 100)
        private String name;

        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        private String phone;

        @Email
        private String email;

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotNull
        private User.Role role;
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String phone;

        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private UserDto user;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class OtpRequest {
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        private String phone;
    }

    @Data
    public static class OtpVerifyRequest {
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        private String phone;

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        private String otp;
    }

    // ========== USER DTOs ==========

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserDto {
        private UUID id;
        private String name;
        private String phone;
        private String email;
        private String avatarUrl;
        private User.Role role;
        private boolean active;
        private Instant createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class MediaUploadResponse {
        private String url;
    }

    @Data
    public static class UpdateProfileRequest {
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
        private String name;

        @Email(message = "Enter a valid email address")
        @Size(max = 255)
        private String email;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;
    }

    // ========== SHOP DTOs ==========

    @Data
    public static class CreateShopRequest {
        @NotBlank @Size(max = 200)
        private String name;

        @NotNull
        private Shop.Category category;

        private String description;

        @NotBlank
        private String address;

        @NotBlank
        private String city;

        @NotBlank
        private String state;

        @NotBlank @Pattern(regexp = "\\d{6}")
        private String pincode;

        private BigDecimal latitude;
        private BigDecimal longitude;

        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$")
        private String phone;

        private String openTime;
        private String closeTime;
        private String breakStartTime;
        private String breakEndTime;
        private List<DayOfWeek> closedDays;
        private String logoUrl;
        private String primaryColor;
        private String businessRegistrationNumber;
        private UUID businessAccountId;
        private String branchCode;

        @Min(1) @Max(120)
        private Integer avgServiceMins;

        @Min(10) @Max(500)
        private Integer maxQueueSize;

        @Min(1) @Max(60)
        private Integer noShowGraceMins;

        @Min(1) @Max(120)
        private Integer rejoinWindowMins;

        @Min(0) @Max(5)
        private Integer maxRejoins;

        @Min(0) @Max(120)
        private Integer stopTokensBeforeClosingMins;

        @Min(1)
        private Integer maxTokensPerDay;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShopDto {
        private UUID id;
        private UUID ownerId;
        private String ownerName;
        private String name;
        private Shop.Category category;
        private String description;
        private String address;
        private String city;
        private String state;
        private String pincode;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String phone;
        private String logoUrl;
        private String primaryColor;
        private String businessRegistrationNumber;
        private UUID businessAccountId;
        private String businessAccountName;
        private String branchCode;
        private String slug;
        private Shop.VerificationStatus verificationStatus;
        private Shop.IncidentStatus incidentStatus;
        private String incidentMessage;
        private int stopTokensBeforeClosingMins;
        private Integer maxTokensPerDay;
        private boolean active;
        private boolean queuePaused;
        private String openTime;
        private String closeTime;
        private String breakStartTime;
        private String breakEndTime;
        private List<DayOfWeek> closedDays;
        private int avgServiceMins;
        private int maxQueueSize;
        private int noShowGraceMins;
        private int rejoinWindowMins;
        private int maxRejoins;
        private Integer currentQueueSize;
        private Integer estimatedWaitMins;
        private Double distanceKm;
        private Instant createdAt;
        private com.queueless.entity.StaffRole myStaffRole;
    }

    @Data
    public static class CloneShopRequest {
        @NotBlank @Size(max = 200)
        private String newName;

        @NotBlank
        private String newAddress;

        @NotBlank @Size(max = 50)
        private String branchCode;

        private int stopTokensBeforeClosingMins;
        private Integer maxTokensPerDay;
    }

    @Data
    public static class UpdateIncidentRequest {
        @NotNull
        private Shop.IncidentStatus status;
        @Size(max = 500)
        private String message;
    }

    // ========== SERVICE DTOs ==========

    @Data
    public static class CreateServiceRequest {
        @NotBlank @Size(max = 200)
        private String name;

        private String description;

        @Min(5) @Max(480)
        private int durationMins = 15;

        @DecimalMin("0.0")
        private BigDecimal price = BigDecimal.ZERO;
    }

    @Data
    @Builder
    public static class ServiceDto {
        private UUID id;
        private UUID shopId;
        private String name;
        private String description;
        private int durationMins;
        private BigDecimal price;
        private boolean active;
    }

    // ========== SERVICE PROVIDER DTOs ==========

    @Data
    public static class CreateServiceProviderRequest {
        @NotBlank @Size(max = 100)
        private String name;

        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$")
        private String phone;

        @NotBlank @Size(min = 8)
        private String password;

        @NotBlank @Size(max = 100)
        private String title;

        private StaffRole staffRole;

        private List<UUID> serviceIds;
    }

    @Data
    public static class UpdateAvailabilityRequest {
        @NotNull
        private Boolean available;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceProviderDto {
        private UUID id;
        private UUID shopId;
        private UUID userId;
        private String name;
        private String phone;
        private String title;
        private StaffRole staffRole;
        private List<UUID> serviceIds;
        private List<String> serviceNames;
        private boolean active;
        private boolean available;
    }

    // ========== TOKEN DTOs ==========

    @Data
    public static class GetTokenRequest {
        @NotNull
        private UUID shopId;

        private UUID serviceId;

        private UUID providerId;

        private String notes;
    }

    @Data
    public static class SetPriorityRequest {
        @NotNull
        private Token.Priority priority;
    }

    @Data
    public static class CreateWalkInTokenRequest {
        @NotNull
        private UUID shopId;

        @Size(max = 100)
        private String customerName;

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        private String customerPhone;

        private UUID serviceId;

        private UUID providerId;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    public static class TransferTokenRequest {
        private UUID serviceId;

        private UUID providerId;

        @Size(max = 500)
        private String reason;
    }

    @Data
    public static class TokenActionReasonRequest {
        @Size(max = 500)
        private String reason;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private UUID userId;
        private String userName;
        private String userPhone;
        private UUID serviceId;
        private String serviceName;
        private UUID providerId;
        private String providerName;
        private int tokenNumber;
        private String displayNumber;
        private Token.TokenStatus status;
        private Token.Priority priority;
        private Integer queuePosition;
        private Integer tokensAhead;
        private Integer estimatedWaitMins;
        private Instant issuedAt;
        private Instant calledAt;
        private Instant servedAt;
        private LocalDate dateIssued;
        private Integer rejoinCount;
        private Integer snoozeCount;
        private Double noShowProbability;
        private Instant skippedAt;
    }

    // ========== QUEUE DTOs ==========

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveQueueDto {
        private UUID shopId;
        private String shopName;
        private boolean queuePaused;
        private int totalWaiting;
        private int totalServedToday;
        private int avgServiceMins;
        private String currentTokenDisplay;
        private List<TokenDto> waitingTokens;
        private Instant lastUpdated;
    }

    @Data
    @Builder
    public static class QueueUpdateEvent {
        private UUID shopId;
        private String eventType;
        private String currentToken;
        private int waitingCount;
        private List<TokenDto> waitingTokens;
        private Instant timestamp;
    }

    // ========== APPOINTMENT DTOs ==========

    @Data
    public static class BookAppointmentRequest {
        @NotNull
        private UUID shopId;

        @NotNull
        private UUID serviceId;

        private UUID providerId;

        @NotNull
        private Instant scheduledAt;

        private String notes;
    }

    @Data
    public static class RescheduleRequest {
        @NotNull
        private Instant newScheduledAt;

        private String reason;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppointmentDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private UUID serviceId;
        private String serviceName;
        private UUID providerId;
        private String providerName;
        private Instant scheduledAt;
        private int durationMins;
        private Appointment.AppointmentStatus status;
        private Appointment.PaymentStatus paymentStatus;
        private BigDecimal amount;
        private String razorpayOrderId;
        private String notes;
        private Instant createdAt;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QrPosterDto {
        private UUID shopId;
        private String shopName;
        private String branchCode;
        private String qrPayload;
        private String qrSvg;
        private String posterTitle;
        private String posterSubtitle;
    }

    // ========== PAYMENT DTOs ==========

    @Data
    @Builder
    public static class PaymentOrderDto {
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String keyId;
    }

    @Data
    public static class PaymentVerifyRequest {
        @NotBlank
        private String razorpayOrderId;

        @NotBlank
        private String razorpayPaymentId;

        @NotBlank
        private String razorpaySignature;
    }

    // ========== NOTIFICATION DTOs ==========

    @Data
    @Builder
    public static class NotificationDto {
        private UUID id;
        private String type;
        private String title;
        private String message;
        private boolean read;
        private Instant createdAt;
    }

    // ========== STATS DTOs ==========

    @Data
    @Builder
    public static class ShopStatsDto {
        private long totalTokensToday;
        private long servedToday;
        private long waitingNow;
        private long cancelledToday;
        private double avgWaitMinutes;
        private long totalAppointments;
        private BigDecimal revenueToday;
    }

    // ========== ANNOUNCEMENT DTOs ==========

    @Data
    public static class CreateAnnouncementRequest {
        @NotBlank @Size(max = 200)
        private String title;

        @NotBlank
        private String message;

        @NotNull
        private Announcement.AnnouncementType type;

        private Instant validFrom;

        /** null means never expires */
        private Instant validTo;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnnouncementDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private String title;
        private String message;
        private String type;
        private boolean active;
        private Instant validFrom;
        private Instant validTo;
        private Instant createdAt;
    }

    // ========== REVIEW DTOs ==========

    @Data
    public static class CreateReviewRequest {
        @NotNull
        private UUID shopId;

        private UUID tokenId;

        private UUID appointmentId;

        @NotNull @Min(1) @Max(5)
        private int rating;

        @Size(max = 1000)
        private String comment;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReviewDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private UUID userId;
        private String userName;
        private UUID tokenId;
        private UUID appointmentId;
        private int rating;
        private String comment;
        private boolean visible;
        private String moderationStatus;
        private String moderationReason;
        private Instant createdAt;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReviewSummaryDto {
        private double avgRating;
        private long totalReviews;
        private Map<Integer, Long> breakdown;
    }

    // ========== WAITLIST DTOs ==========

    @Data
    public static class JoinWaitlistRequest {
        @NotNull
        private UUID shopId;

        private UUID serviceId;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WaitlistDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private UUID serviceId;
        private String serviceName;
        private Instant joinedAt;
        private String status;
        private long positionAhead;
    }

    // ========== SHOP STATUS DTO ==========

    @Data
    @Builder
    public static class ShopStatusDto {
        private String status;         // OPEN, CLOSED, BREAK, CLOSES_SOON, HOLIDAY
        private String reason;
        private Instant nextChangeAt;
    }

    // ========== HOLIDAY DTOs ==========

    @Data
    public static class CreateHolidayRequest {
        @NotNull
        private LocalDate date;

        @Size(max = 500)
        private String reason;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HolidayDto {
        private UUID id;
        private LocalDate date;
        private String reason;
    }

    // ========== ANALYTICS DTO ==========

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnalyticsDto {
        private List<HourlyBucket> hourlyHeatmap;
        private double noShowRate;
        private List<ServicePopularity> servicePopularity;
        private List<ProviderUtilization> providerUtilization;
        private List<DailyTrafficDto> last7DaysTraffic;

        @Data
        @Builder
        public static class HourlyBucket {
            private int hour;
            private long count;
        }

        @Data
        @Builder
        public static class ServicePopularity {
            private String serviceName;
            private long tokenCount;
        }

        @Data
        @Builder
        public static class ProviderUtilization {
            private String providerName;
            private long totalTokens;
            private long servedTokens;
        }
    }

    // ========== PAGINATION ==========

    @Data
    @Builder
    public static class PageResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;
    }

    // ========== ERROR ==========

    @Data
    @Builder
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private String path;
        private Instant timestamp;
    }

    // ========== ATTACHMENT DTOs ==========

    @Data
    public static class CreateAttachmentRequest {
        @NotNull
        private UUID targetId;

        @NotNull
        private Attachment.TargetType targetType;

        @NotBlank
        private String fileUrl;

        private String description;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AttachmentDto {
        private UUID id;
        private UUID targetId;
        private String targetType;
        private String fileUrl;
        private String description;
        private Instant createdAt;
    }

    // ========== LOYALTY DTOs ==========

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserLoyaltyDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private int points;
        private int totalVisits;
        private String tier;
        private int bronzeThreshold;
        private int silverThreshold;
        private int goldThreshold;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoyaltyConfigDto {
        private UUID id;
        private UUID shopId;
        private int pointsPerVisit;
        private int bronzeThreshold;
        private int silverThreshold;
        private int goldThreshold;
    }

    @Data
    public static class UpdateLoyaltyConfigRequest {
        @Min(1)
        private int pointsPerVisit;
        @Min(0)
        private int bronzeThreshold;
        @Min(1)
        private int silverThreshold;
        @Min(2)
        private int goldThreshold;
    }

    @Data
    @Builder
    public static class DailyTrafficDto {
        private LocalDate date;
        private long tokenCount;
    }

    // ========== ROADMAP DTOs ==========

    @Data
    public static class CreateBusinessAccountRequest {
        @NotBlank @Size(max = 200)
        private String name;
        @Email @Size(max = 200)
        private String billingEmail;
        @Size(max = 30)
        private String gstin;
    }

    @Data
    public static class UpdateBusinessAccountRequest {
        @NotBlank @Size(max = 200)
        private String name;
        @Email @Size(max = 200)
        private String billingEmail;
        @Size(max = 30)
        private String gstin;
        private BigDecimal taxPercent;
        private String invoicePrefix;
        private String razorpayKeyId;
        private String razorpayKeySecret;
        private String settlementFrequency;
        private String payoutAccountName;
        private String payoutAccountNumber;
        private String payoutIfsc;
        private String smsSenderId;
        private String whatsappNumber;
    }

    @Data
    @Builder
    public static class BusinessAccountDto {
        private UUID id;
        private String name;
        private String billingEmail;
        private String gstin;
        private BigDecimal taxPercent;
        private String invoicePrefix;
        private String razorpayKeyId;
        private boolean payoutEnabled;
        private String settlementFrequency;
        private String payoutAccountName;
        private String payoutAccountNumberMasked;
        private String payoutIfsc;
        private String smsSenderId;
        private String whatsappNumber;
        private boolean active;
        private Instant createdAt;
    }

    @Data
    public static class UpdateSubscriptionRequest {
        @NotNull
        private ShopSubscription.Plan plan;
    }

    @Data
    @Builder
    public static class ShopSubscriptionDto {
        private UUID id;
        private UUID shopId;
        private String shopName;
        private ShopSubscription.Plan plan;
        private ShopSubscription.Status status;
        private BigDecimal amount;
        private Instant currentPeriodStart;
        private Instant currentPeriodEnd;
    }

    @Data
    public static class ModerateReviewRequest {
        @NotNull
        private Review.ModerationStatus status;

        @Size(max = 1000)
        private String reason;
    }

    @Data
    public static class StaffHeartbeatRequest {
        @NotBlank @Size(max = 120)
        private String deviceId;

        @Size(max = 80)
        private String appVersion;

        private Boolean online;
    }

    @Data
    @Builder
    public static class StaffHeartbeatDto {
        private UUID id;
        private UUID shopId;
        private UUID providerId;
        private String staffName;
        private String deviceId;
        private String appVersion;
        private boolean online;
        private Instant lastSeenAt;
    }
}
