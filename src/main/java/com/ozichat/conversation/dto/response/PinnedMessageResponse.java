package com.ozichat.conversation.dto.response;

import com.ozichat.conversation.entity.PinnedMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PinnedMessageResponse {
    private Long id;
    private Long conversationId;
    private String messageId;
    private Long pinnedBy;
    private Instant pinnedAt;

    public static PinnedMessageResponse from(PinnedMessage p) {
        return PinnedMessageResponse.builder()
                .id(p.getId())
                .conversationId(p.getConversationId())
                .messageId(p.getMessageId())
                .pinnedBy(p.getPinnedBy())
                .pinnedAt(p.getPinnedAt())
                .build();
    }
}
