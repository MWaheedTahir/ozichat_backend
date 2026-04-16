package com.ozichat.conversation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationResponse {
    private Long id;
    private String type;
    private List<MemberInfo> members;
    private LastMessageInfo lastMessage;
    private Integer unreadCount;
    private Instant createdAt;
    private Instant updatedAt;

    // Group-specific fields (null for DIRECT)
    private String groupName;
    private String groupAvatarUrl;

    @Data
    @Builder
    public static class MemberInfo {
        private Long userId;
        private String displayName;
        private String avatarUrl;
        private String role;
        private Instant lastSeenAt;
    }

    @Data
    @Builder
    public static class LastMessageInfo {
        private String messageId;
        private Long senderId;
        private String contentPreview;
        private String type;
        private Instant sentAt;
    }
}
