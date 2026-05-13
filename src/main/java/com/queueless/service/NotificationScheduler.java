package com.queueless.service;

import com.queueless.entity.Appointment;
import com.queueless.entity.Notification;
import com.queueless.entity.Token;
import com.queueless.repository.AppointmentRepository;
import com.queueless.repository.NotificationRepository;
import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final AppointmentRepository appointmentRepository;
    private final TokenRepository tokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final StaffHeartbeatService staffHeartbeatService;

    /**
     * Runs every 5 minutes to send reminders for upcoming appointments.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void sendAppointmentReminders() {
        Instant now = Instant.now();
        Instant targetTime = now.plus(2, ChronoUnit.HOURS);

        // Find confirmed appointments in the next 2 hours that haven't had a reminder sent
        List<Appointment> upcoming = appointmentRepository.findAllByStatusAndScheduledAtBetweenAndReminderSentFalse(
                Appointment.AppointmentStatus.CONFIRMED, now, targetTime);

        for (Appointment appt : upcoming) {
            notificationService.sendInAppNotification(
                    appt.getUser().getId(), appt.getShop().getId(), null,
                    "Appointment Reminder",
                    String.format("Reminder: You have an appointment at %s in less than 2 hours.", appt.getShop().getName())
            );
            appt.setReminderSent(true);
            appointmentRepository.save(appt);
            log.info("Sent appointment reminder for {}", appt.getId());
        }
    }

    /**
     * Runs every minute to check if someone is "almost up" in the queue.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sendQueueProximityReminders() {
        // This logic is slightly more complex as it depends on the current state of each shop's queue.
        // For simplicity, we can check for tokens where queuePosition is small (e.g., 2 or 3).
        // A better way would be to check tokens relative to the currently called token.
        
        // Find all WAITING tokens with reminderSent = false
        List<Token> waiting = tokenRepository.findAllByStatusAndReminderSentFalse(Token.TokenStatus.WAITING);
        
        for (Token token : waiting) {
            // Check if this token is within top 3 of its shop's current active queue
            // This is a basic heuristic for Phase 2.
            if (token.getQueuePosition() != null && token.getQueuePosition() <= 3) {
                notificationService.sendInAppNotification(
                        token.getUser().getId(), token.getShop().getId(), token.getId(),
                        "You're almost up!",
                        String.format("Get ready! You are currently position #%d in the queue at %s.", 
                                token.getQueuePosition(), token.getShop().getName())
                );
                token.setReminderSent(true);
                tokenRepository.save(token);
                log.info("Sent queue proximity reminder for token {}", token.getId());
            }
        }
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> retryable = notificationRepository
                .findByDeliveryStatusAndNextAttemptAtLessThanEqualAndRetryCountLessThan(
                        Notification.DeliveryStatus.FAILED, Instant.now(), 3);
        retryable.forEach(notificationService::retryNotification);
        if (!retryable.isEmpty()) {
            log.info("Retried {} failed notifications", retryable.size());
        }
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void markOfflineStaff() {
        int offline = staffHeartbeatService.markOfflineSince(Instant.now().minus(90, ChronoUnit.SECONDS));
        if (offline > 0) {
            log.info("Marked {} staff counter sessions offline", offline);
        }
    }
}
