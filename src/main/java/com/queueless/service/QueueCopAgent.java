package com.queueless.service;

import com.queueless.entity.Token;
import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Autonomous AI Agent responsible for monitoring active queues and penalizing highly probable no-shows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueCopAgent {

    private final TokenRepository tokenRepository;
    private final TokenService tokenService;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void enforceNoShowPolicy() {
        // Find high-risk tokens (noShowProbability > 0.85)
        List<Token> highRiskTokens = tokenRepository.findTokensWithHighNoShow(0.85);

        for (Token token : highRiskTokens) {
            try {
                // If the probability is extremely high, push them back automatically
                if (token.getNoShowProbability() > 0.95) {
                    log.info("Queue Cop: Auto-snoozing token {} due to extremely high no-show probability ({})", 
                            token.getDisplayNumber(), token.getNoShowProbability());
                    
                    // Auto-snooze the token (pushes them back)
                    tokenService.snoozeToken(token.getId(), token.getShop().getOwner().getId());
                    
                    notificationService.sendPushNotification(
                            token.getUser().getId(),
                            token.getUser().getFcmToken(),
                            "You've been pushed back",
                            "Queue Cop noticed you might not make it in time. We've pushed you back in the queue."
                    );
                } 
                // If just high risk, send a warning
                else if (!token.isReminderSent()) {
                    log.info("Queue Cop: Warning token {} about high no-show probability ({})", 
                            token.getDisplayNumber(), token.getNoShowProbability());
                    
                    notificationService.sendPushNotification(
                            token.getUser().getId(),
                            token.getUser().getFcmToken(),
                            "Are you still coming?",
                            "Your token is coming up soon. Please confirm you are on your way."
                    );
                    token.setReminderSent(true);
                    tokenRepository.save(token);
                }
            } catch (Exception e) {
                log.error("Queue Cop encountered error for token {}: {}", token.getId(), e.getMessage());
            }
        }
    }
}
