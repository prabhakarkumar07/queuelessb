package com.queueless.service;

import com.queueless.dto.Dtos.QueueUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * WebSocket broadcast service. Pushes queue state changes to all connected clients
 * subscribed to a shop's topic channel.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcasts a queue update event to all clients watching a specific shop's queue.
     * Clients subscribe to: /topic/queue/{shopId}
     *
     * @param shopId the shop whose queue changed
     * @param event  the queue update payload containing new state
     */
    public void broadcastQueueUpdate(UUID shopId, QueueUpdateEvent event) {
        String destination = "/topic/queue/" + shopId;
        messagingTemplate.convertAndSend(destination, event);
        log.debug("WebSocket broadcast to {} — event: {}, waiting: {}",
                destination, event.getEventType(), event.getWaitingCount());
    }

    /**
     * Sends a personal notification directly to a specific user's WebSocket session.
     * Used for "Your turn!" alerts without polling.
     *
     * @param userPhone the user's phone (username in Spring Security context)
     * @param message   the notification payload
     */
    public void sendUserNotification(String userPhone, Object message) {
        messagingTemplate.convertAndSendToUser(userPhone, "/queue/notifications", message);
        log.debug("Personal WebSocket message sent to user {}", userPhone);
    }
}