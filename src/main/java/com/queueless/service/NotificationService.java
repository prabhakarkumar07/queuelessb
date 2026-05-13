package com.queueless.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueless.entity.*;
import com.queueless.repository.NotificationRepository;
import com.queueless.repository.ShopRepository;
import com.queueless.repository.TokenRepository;
import com.queueless.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notification service handling SMS via MSG91, push notifications, and in-app alerts.
 * All external API calls are async to avoid blocking the main queue flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final TokenRepository tokenRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${msg91.auth-key}")
    private String msg91AuthKey;

    @Value("${msg91.sender-id}")
    private String msg91SenderId;

    @Value("${msg91.template-id}")
    private String msg91TemplateId;

    @Value("${msg91.api-url}")
    private String msg91ApiUrl;

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.from-number:}")
    private String twilioFromNumber;

    @PostConstruct
    public void initTwilio() {
        if (twilioAccountSid != null && !twilioAccountSid.isBlank()) {
            Twilio.init(twilioAccountSid, twilioAuthToken);
            log.info("Twilio initialized for SMS fallback");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getTokenDataForSms(UUID tokenId) {
        return tokenRepository.findWithRelationsById(tokenId).map(token -> {
            Map<String, String> data = new HashMap<>();
            data.put("shopName", token.getShop().getName());
            data.put("tokenDisplay", token.getDisplayNumber());
            data.put("phone", token.getUser().getPhone());
            data.put("userId", token.getUser().getId().toString());
            data.put("shopId", token.getShop().getId().toString());
            return data;
        }).orElse(null);
    }

    /**
     * Sends an SMS alert to a customer via MSG91 when they are within threshold tokens of their turn.
     * This call is asynchronous and non-blocking.
     */
    @Async
    public void sendSmsAlert(UUID tokenId, int tokensAhead) {
        Map<String, String> data = getTokenDataForSms(tokenId);
        if (data == null) return;

        String phone = data.get("phone");
        String shopName = data.get("shopName");
        String tokenDisplay = data.get("tokenDisplay");
        UUID userId = UUID.fromString(data.get("userId"));
        UUID shopId = UUID.fromString(data.get("shopId"));

        String message = String.format(
                "QueueLess Alert: Your token %s at %s. Only %d customer(s) ahead! Please be ready. Track: https://app.queueless.in/track/%s",
                tokenDisplay, shopName, tokensAhead, tokenId
        );

        Notification notification = persistNotification(userId, shopId, tokenId, Notification.NotificationType.SMS, "Your Turn Is Near!", message);
        deliverSms(notification, phone);

        log.info("SMS alert sent to {} for token {}", phone, tokenDisplay);
    }

    @Transactional(readOnly = true)
    public String getUserPhone(UUID userId) {
        return userRepository.findById(userId).map(User::getPhone).orElse(null);
    }

    @Async
    public void sendOneTimePassword(UUID userId, String otp) {
        String phone = getUserPhone(userId);
        if (phone == null) return;

        String message = String.format("QueueLess OTP: %s. It expires in 5 minutes. Do not share it.", otp);
        Notification notification = persistNotification(userId, null, null, Notification.NotificationType.SMS, "QueueLess Login OTP", message);
        deliverSms(notification, phone);
    }

    /**
     * Sends a push notification to a user's device via FCM token.
     */
    @Async
    public void sendPushNotification(UUID userId, String fcmToken, String title, String message) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("No FCM token for user {}, skipping push", userId);
            return;
        }

        log.info("PUSH → UserID: {} | {} | {}", userId, title, message);
        persistNotification(userId, null, null, Notification.NotificationType.PUSH, title, message);
    }

    /**
     * Creates an in-app notification visible in the customer's notification feed.
     */
    public void sendInAppNotification(UUID userId, UUID shopId, UUID tokenId, String title, String message) {
        persistNotification(userId, shopId, tokenId, Notification.NotificationType.IN_APP, title, message);
    }

    public void retryNotification(Notification notification) {
        if (notification.getType() == Notification.NotificationType.SMS) {
            String phone = getUserPhone(notification.getUser().getId());
            if (phone != null) {
                deliverSms(notification, phone);
            }
        }
    }

    private void deliverSms(Notification notification, String mobile) {
        try {
            String message = notification.getMessage();
            notification.setDeliveryStatus(Notification.DeliveryStatus.PENDING);
            notification.setChannel("SMS");
            notificationRepository.save(notification);

            if (msg91AuthKey == null || msg91AuthKey.isBlank() || msg91TemplateId == null || msg91TemplateId.isBlank()) {
                deliverTwilioSms(notification, mobile);
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("flow_id", msg91TemplateId);
            body.put("sender", msg91SenderId);

            Map<String, String> recipient = new HashMap<>();
            recipient.put("mobiles", "91" + mobile);
            recipient.put("message", message);
            body.put("recipients", new Object[]{recipient});

            String requestBody = objectMapper.writeValueAsString(body);

            String response = webClientBuilder.build()
                    .post()
                    .uri(msg91ApiUrl)
                    .header("authkey", msg91AuthKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> Mono.error(new IllegalStateException(e.getMessage(), e)))
                    .block(java.time.Duration.ofSeconds(8));

            notification.setDeliveryStatus(Notification.DeliveryStatus.DELIVERED);
            notification.setDelivered(true);
            notification.setSentAt(Instant.now());
            notification.setLastError(null);
            notification.setNextAttemptAt(null);
            notification.setProviderMessageId(response != null && response.length() > 140 ? response.substring(0, 140) : response);
            notificationRepository.save(notification);
            log.debug("MSG91 response for {}: {}", mobile, response);

        } catch (Exception e) {
            log.warn("MSG91 failed for {}, falling back to Twilio: {}", mobile, e.getMessage());
            deliverTwilioSms(notification, mobile);
        }
    }

    private void deliverTwilioSms(Notification notification, String mobile) {
        if (twilioAccountSid == null || twilioAccountSid.isBlank()) {
            markFailed(notification, "Both MSG91 and Twilio are unconfigured");
            return;
        }

        try {
            String to = "+91" + mobile;
            String body = notification.getMessage();

            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilioFromNumber),
                    body
            ).create();

            notification.setDeliveryStatus(Notification.DeliveryStatus.DELIVERED);
            notification.setDelivered(true);
            notification.setSentAt(Instant.now());
            notification.setChannel("TWILIO_SMS");
            notification.setProviderMessageId(message.getSid());
            notificationRepository.save(notification);
            log.info("Twilio SMS delivered to {}: {}", to, message.getSid());
        } catch (Exception e) {
            markFailed(notification, "Twilio fallback failed: " + e.getMessage());
        }
    }

    private Notification persistNotification(UUID userId, UUID shopId, UUID tokenId,
                                      Notification.NotificationType type,
                                      String title, String message) {
        Notification notification = Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .shop(shopId != null ? shopRepository.getReferenceById(shopId) : null)
                .token(tokenId != null ? tokenRepository.getReferenceById(tokenId) : null)
                .type(type)
                .title(title)
                .message(message)
                .sentAt(Instant.now())
                .delivered(type == Notification.NotificationType.IN_APP)
                .deliveryStatus(type == Notification.NotificationType.IN_APP
                        ? Notification.DeliveryStatus.DELIVERED
                        : Notification.DeliveryStatus.PENDING)
                .channel(type.name())
                .build();
        return notificationRepository.save(notification);
    }

    private void markFailed(Notification notification, String error) {
        notification.setDeliveryStatus(Notification.DeliveryStatus.FAILED);
        notification.setDelivered(false);
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setLastError(error);
        notification.setNextAttemptAt(Instant.now().plusSeconds((long) Math.pow(2, notification.getRetryCount()) * 60));
        notificationRepository.save(notification);
        log.warn("Notification {} delivery failed: {}", notification.getId(), error);
    }
}
