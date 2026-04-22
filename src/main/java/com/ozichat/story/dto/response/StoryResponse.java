package com.ozichat.story.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.story.document.Story;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryResponse {

    private String id;
    private Long userId;
    private String uploaderName;
    private String uploaderAvatarUrl;

    // ── Media ──────────────────────────────────────
    private String mediaUrl;
    private String thumbnailUrl;
    private String type;         // "IMAGE" | "VIDEO"
    private Integer duration;

    // ── Content ────────────────────────────────────
    private String caption;

    // ── Privacy ────────────────────────────────────
    private String privacy;      // "PUBLIC" | "PRIVATE"
    private List<Long> allowedViewerIds;

    // ── Engagement ─────────────────────────────────
    private Long viewCount;

    /**
     * True if the requesting user has already viewed this story.
     * Null when the caller is the owner (they always "know" their own story).
     */
    private Boolean viewed;

    // ── Lifecycle ──────────────────────────────────
    private Instant createdAt;
    private Instant expiresAt;

    public static StoryResponse from(Story story,
                                     String uploaderName,
                                     String uploaderAvatarUrl,
                                     Boolean viewed) {
        StoryResponseBuilder b = StoryResponse.builder()
                .id(story.getId())
                .userId(story.getUserId())
                .uploaderName(uploaderName)
                .uploaderAvatarUrl(uploaderAvatarUrl)
                .mediaUrl(story.getMediaUrl())
                .thumbnailUrl(story.getThumbnailUrl())
                .type(story.getType().name())
                .duration(story.getDuration())
                .caption(story.getCaption())
                .privacy(story.getPrivacy().name())
                .viewCount(story.getViewCount())
                .viewed(viewed)
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt());

        // Only expose allowedViewerIds to the story owner
        if (story.getPrivacy() == Story.StoryPrivacy.PRIVATE) {
            b.allowedViewerIds(story.getAllowedViewerIds());
        }

        return b.build();
    }
}
