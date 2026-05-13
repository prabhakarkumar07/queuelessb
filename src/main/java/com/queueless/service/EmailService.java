package com.queueless.service;

import com.queueless.config.EmailConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Email service using MailerSend API.
 * Supports transactional emails (welcome, booking confirmation, password reset, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send a plain text email.
     */
    @Async
    public void sendText(String toEmail, String toName, String subject, String textBody) {
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.debug("Email disabled or API key not set. Skipping email to {}", toEmail);
            return;
        }

        Map<String, Object> payload = Map.of(
                "from", Map.of("email", config.getFromEmail(), "name", config.getFromName()),
                "to", List.of(Map.of("email", toEmail, "name", toName != null ? toName : toEmail)),
                "subject", subject,
                "text", textBody
        );

        sendRequest(payload);
    }

    /**
     * Send an HTML email.
     */
    @Async
    public void sendHtml(String toEmail, String toName, String subject, String htmlBody) {
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.debug("Email disabled or API key not set. Skipping email to {}", toEmail);
            return;
        }

        Map<String, Object> payload = Map.of(
                "from", Map.of("email", config.getFromEmail(), "name", config.getFromName()),
                "to", List.of(Map.of("email", toEmail, "name", toName != null ? toName : toEmail)),
                "subject", subject,
                "html", htmlBody
        );

        sendRequest(payload);
    }

    /**
     * Send a welcome email to a new user.
     */
    @Async
    public void sendWelcome(String toEmail, String userName) {
        String subject = "Welcome to QueueLess, " + userName + "!";
        String html = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 560px; margin: 0 auto; padding: 32px 24px;">
                  <div style="background: #0f172a; border-radius: 8px; padding: 16px 20px; margin-bottom: 24px;">
                    <span style="color: #fcd34d; font-weight: 900; font-size: 14px;">QL</span>
                    <span style="color: #ffffff; font-weight: 700; font-size: 14px; margin-left: 8px;">QueueLess</span>
                  </div>
                  <h1 style="color: #0f172a; font-size: 22px; font-weight: 700; margin: 0 0 12px;">Welcome aboard, %s!</h1>
                  <p style="color: #64748b; font-size: 14px; line-height: 1.6; margin: 0 0 20px;">
                    Your account is ready. You can now discover nearby shops, join queues remotely, book appointments, and track your turn in real time.
                  </p>
                  <a href="https://queueless.in" style="display: inline-block; background: #0f172a; color: #ffffff; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 600; font-size: 14px;">
                    Open QueueLess
                  </a>
                  <p style="color: #94a3b8; font-size: 12px; margin-top: 32px; border-top: 1px solid #e2e8f0; padding-top: 16px;">
                    © 2026 QueueLess. All rights reserved.
                  </p>
                </div>
                """.formatted(userName);

        sendHtml(toEmail, userName, subject, html);
    }

    /**
     * Send appointment confirmation email.
     */
    @Async
    public void sendAppointmentConfirmation(String toEmail, String userName, String shopName, String dateTime, String serviceName) {
        String subject = "Appointment Confirmed — " + shopName;
        String html = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 560px; margin: 0 auto; padding: 32px 24px;">
                  <div style="background: #0f172a; border-radius: 8px; padding: 16px 20px; margin-bottom: 24px;">
                    <span style="color: #fcd34d; font-weight: 900; font-size: 14px;">QL</span>
                    <span style="color: #ffffff; font-weight: 700; font-size: 14px; margin-left: 8px;">QueueLess</span>
                  </div>
                  <h1 style="color: #0f172a; font-size: 20px; font-weight: 700; margin: 0 0 12px;">Appointment Confirmed</h1>
                  <p style="color: #64748b; font-size: 14px; line-height: 1.6; margin: 0 0 16px;">Hi %s, your appointment is confirmed:</p>
                  <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin-bottom: 20px;">
                    <p style="margin: 0 0 8px; color: #0f172a; font-weight: 600;">%s</p>
                    <p style="margin: 0 0 4px; color: #64748b; font-size: 13px;">Service: %s</p>
                    <p style="margin: 0; color: #64748b; font-size: 13px;">Date & Time: %s</p>
                  </div>
                  <p style="color: #64748b; font-size: 13px;">You'll receive a reminder 30 minutes before your appointment.</p>
                  <p style="color: #94a3b8; font-size: 12px; margin-top: 32px; border-top: 1px solid #e2e8f0; padding-top: 16px;">
                    © 2026 QueueLess. All rights reserved.
                  </p>
                </div>
                """.formatted(userName, shopName, serviceName, dateTime);

        sendHtml(toEmail, userName, subject, html);
    }

    private void sendRequest(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl() + "/email",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully to {}", ((List<Map<String, String>>) payload.get("to")).get(0).get("email"));
            } else {
                log.warn("Email send returned status {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }
}
