package com.queueless.service;

import com.queueless.entity.Token;
import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autonomous AI Agent responsible for dynamically rebalancing queues 
 * to prevent customer starvation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartSorterAgent {

    private final TokenRepository tokenRepository;
    private final TokenService tokenService;

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    @Transactional
    public void rebalanceQueue() {
        List<Token> waitingTokens = tokenRepository.findWaitingTokensForRebalancing();
        Instant now = Instant.now();
        boolean changed = false;
        
        Set<java.util.UUID> affectedShops = new java.util.HashSet<>();

        for (Token token : waitingTokens) {
            long minutesWaiting = Duration.between(token.getIssuedAt(), now).toMinutes();
            
            // Rebalance condition: Waiting > 60 minutes and hasn't already been massively prioritized
            if (minutesWaiting > 60 && token.getSortPenalty() >= -2) {
                log.info("Smart Sorter: Booting token {} priority. Waiting for {} mins", 
                        token.getDisplayNumber(), minutesWaiting);
                
                token.setSortPenalty(token.getSortPenalty() - 1);
                tokenRepository.save(token);
                changed = true;
                affectedShops.add(token.getShop().getId());
            }
        }
        
        if (changed) {
            // Broadcast live updates to UI for any shop that got rebalanced
            for (java.util.UUID shopId : affectedShops) {
                tokenService.broadcastQueueUpdate(shopId, "QUEUE_REBALANCED");
            }
        }
    }
}
