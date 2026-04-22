package com.ozichat.reel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.reel.document.Reel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReelResponse {

    private String id;
    private Long userId;
    private String uploaderName;
    private String uploaderAvatarUrl;

    // Video metadata
    private String videoUrl;
    private String thumbnailUrl;
    private Integer duration;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String mimeType;

    // Content
    private String caption;
    private List<String> hashtags;

    // Engagement counters
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long shareCount;

    // Per-caller state (null when fetching other users' feeds in some contexts)
    private Boolean isLiked;

    private Instant createdAt;
    private Instant updatedAt;

    public static ReelResponse from(Reel reel, String uploaderName,
                                    String uploaderAvatarUrl, boolean isLiked) {
        return ReelResponse.builder()
                .id(reel.getId())
                .userId(reel.getUserId())
                .uploaderName(uploaderName)
                .uploaderAvatarUrl(uploaderAvatarUrl)
                .videoUrl(reel.getVideoUrl())
                .thumbnailUrl(reel.getThumbnailUrl())
                .duration(reel.getDuration())
                .fileSize(reel.getFileSize())
                .width(reel.getWidth())
                .height(reel.getHeight())
                .mimeType(reel.getMimeType())
                .caption(reel.getCaption())
                .hashtags(reel.getHashtags())
                .viewCount(reel.getViewCount())
                .likeCount(reel.getLikeCount())
                .commentCount(reel.getCommentCount())
                .shareCount(reel.getShareCount())
                .isLiked(isLiked)
                .createdAt(reel.getCreatedAt())
                .updatedAt(reel.getUpdatedAt())
                .build();
    }
}
