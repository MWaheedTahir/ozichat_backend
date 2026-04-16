package com.ozichat.message.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationResponse {

    private String type;  // DELIVERED, READ, TYPING, PRESENCE

    // For DELIVERED / READ
    private String messageId;
    private Long byUserId;
    private Long conversationId;

    // For TYPING
    private Boolean isTyping;

    // For PRESENCE
    private String status;  // ONLINE, OFFLINE
    private Instant lastSeenAt;

    public static NotificationResponse delivered(String messageId, Long byUserId, Long conversationId) {
        return NotificationResponse.builder()
                .type("DELIVERED")
                .messageId(messageId)
                .byUserId(byUserId)
                .conversationId(conversationId)
                .build();
    }

    public static NotificationResponse read(String messageId, Long byUserId, Long conversationId) {
        return NotificationResponse.builder()
                .type("READ")
                .messageId(messageId)
                .byUserId(byUserId)
                .conversationId(conversationId)
                .build();
    }

    public static NotificationResponse typing(Long conversationId, Long userId, boolean isTyping) {
        return NotificationResponse.builder()
                .type("TYPING")
                .conversationId(conversationId)
                .byUserId(userId)
                .isTyping(isTyping)
                .build();
    }

    public static NotificationResponse presence(Long userId, String status, Instant lastSeenAt) {
        return NotificationResponse.builder()
                .type("PRESENCE")
                .byUserId(userId)
                .status(status)
                .lastSeenAt(lastSeenAt)
                .build();
    }
}
