package com.ozichat.conversation.dto.response;

import com.ozichat.conversation.entity.ConversationMember;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class MemberResponse {
    private Long userId;
    private String displayName;
    private String avatarUrl;
    private String role;
    private Instant joinedAt;
    private boolean muted;

    public static MemberResponse from(ConversationMember member,
                                      String displayName, String avatarUrl) {
        return MemberResponse.builder()
                .userId(member.getUserId())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .muted(Boolean.TRUE.equals(member.getIsMuted()))
                .build();
    }
}
