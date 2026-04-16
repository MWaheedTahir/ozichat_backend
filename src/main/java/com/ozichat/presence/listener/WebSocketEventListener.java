package com.ozichat.presence.listener;

import com.ozichat.conversation.service.ConversationService;
import com.ozichat.message.dto.response.NotificationResponse;
import com.ozichat.presence.service.PresenceService;
import com.ozichat.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final ConversationService conversationService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() == null) return;

        Long userId = Long.parseLong(accessor.getUser().getName());
        String sessionId = accessor.getSessionId();

        // 1. Mark user as online in Redis
        presenceService.setOnline(userId);
        presenceService.storeSession(userId, sessionId);

        // 2. Notify contacts asynchronously — scoped to shared conversations only
        notifyPresenceToContacts(userId, "ONLINE", null);

        log.info("WebSocket CONNECTED — userId={}, sessionId={}", userId, sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() == null) return;

        Long userId = Long.parseLong(accessor.getUser().getName());

        // 1. Remove from Redis presence
        presenceService.setOffline(userId);
        presenceService.removeSession(userId);

        // 2. Persist last_seen_at in MySQL
        Instant lastSeen = Instant.now();
        userService.updateLastSeen(userId);

        // 3. Notify contacts — scoped to conversations only, NOT global broadcast
        notifyPresenceToContacts(userId, "OFFLINE", lastSeen);

        log.info("WebSocket DISCONNECTED — userId={}", userId);
    }

    /**
     * Emit presence update ONLY to users who share a conversation with this user.
     * Never use io.emit() / global broadcast — that causes O(n) write storms.
     */
    @Async
    protected void notifyPresenceToContacts(Long userId, String status, Instant lastSeenAt) {
        try {
            List<Long> conversationIds = conversationService.getUserConversationIds(userId);
            for (Long convId : conversationIds) {
                List<Long> otherMembers = conversationService.getOtherMemberIds(convId, userId);
                NotificationResponse notification = NotificationResponse.presence(userId, status, lastSeenAt);
                for (Long memberId : otherMembers) {
                    messagingTemplate.convertAndSendToUser(
                            memberId.toString(), "/queue/notifications", notification
                    );
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to broadcast presence for userId={}: {}", userId, ex.getMessage());
        }
    }
}
