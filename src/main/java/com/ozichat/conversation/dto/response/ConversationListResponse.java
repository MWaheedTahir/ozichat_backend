package com.ozichat.conversation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationListResponse {
    private Long conversationId;
    private String displayName;
    private String avatarUrl;
    private Instant updatedAt;
    private String type;
}