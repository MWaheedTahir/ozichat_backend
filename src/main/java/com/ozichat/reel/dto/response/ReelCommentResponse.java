package com.ozichat.reel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.reel.document.ReelComment;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReelCommentResponse {

    private String id;
    private String reelId;
    private Long userId;
    private String userDisplayName;
    private String userAvatarUrl;
    private String content;
    private Instant createdAt;

    public static ReelCommentResponse from(ReelComment comment,
                                           String userDisplayName,
                                           String userAvatarUrl) {
        return ReelCommentResponse.builder()
                .id(comment.getId())
                .reelId(comment.getReelId())
                .userId(comment.getUserId())
                .userDisplayName(userDisplayName)
                .userAvatarUrl(userAvatarUrl)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
