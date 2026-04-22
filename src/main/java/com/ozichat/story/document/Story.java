package com.ozichat.story.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * A Story is a time-limited (24 h) photo or video post visible to either
 * everyone (PUBLIC) or a specific list of users (PRIVATE).
 *
 * MongoDB TTL index on {@code expiresAt} automatically removes documents
 * 7 days after they have expired, keeping the collection lean without
 * any scheduled job.
 */
@Document(collection = "stories")
@CompoundIndexes({
    // Per-user active stories (my stories / profile view)
    @CompoundIndex(name = "idx_stories_user_active",
            def = "{'userId': 1, 'isDeleted': 1, 'expiresAt': -1}"),
    // Global public feed
    @CompoundIndex(name = "idx_stories_public_feed",
            def = "{'privacy': 1, 'isDeleted': 1, 'expiresAt': -1, 'createdAt': -1}"),
    // Private stories where a viewer is allowed
    @CompoundIndex(name = "idx_stories_private_viewer",
            def = "{'allowedViewerIds': 1, 'isDeleted': 1, 'expiresAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    private String id;

    /** Owner of the story. */
    @Field("userId")
    private Long userId;

    // ── Media ──────────────────────────────────────

    /** Public CDN/S3 URL of the photo or video. */
    @Field("mediaUrl")
    private String mediaUrl;

    /** S3 object key (kept for deletion). */
    @Field("mediaKey")
    private String mediaKey;

    /** Optional thumbnail URL (required for VIDEO type). */
    @Field("thumbnailUrl")
    private String thumbnailUrl;

    @Field("thumbnailKey")
    private String thumbnailKey;

    /** IMAGE or VIDEO. */
    @Field("type")
    @Builder.Default
    private StoryType type = StoryType.IMAGE;

    /** Duration in seconds — for video stories. */
    @Field("duration")
    private Integer duration;

    // ── Content ────────────────────────────────────

    /** Optional caption (max 500 chars). */
    @Field("caption")
    private String caption;

    // ── Privacy ────────────────────────────────────

    /**
     * PUBLIC  — visible to all authenticated users.
     * PRIVATE — visible only to the owner + users in {@code allowedViewerIds}.
     */
    @Field("privacy")
    @Builder.Default
    private StoryPrivacy privacy = StoryPrivacy.PUBLIC;

    /**
     * Populated only for PRIVATE stories.
     * Contains the user IDs that are allowed to see this story.
     */
    @Field("allowedViewerIds")
    private List<Long> allowedViewerIds;

    // ── Counters ───────────────────────────────────

    @Field("viewCount")
    @Builder.Default
    private Long viewCount = 0L;

    // ── Lifecycle ──────────────────────────────────

    @Field("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Field("deletedAt")
    private Instant deletedAt;

    @Field("createdAt")
    private Instant createdAt;

    /**
     * Stories expire 24 hours after creation.
     * A sparse TTL index removes documents 7 days after this timestamp
     * so that view history queries remain fast.
     */
    @Indexed(name = "ttl_stories_expires", expireAfterSeconds = 604800 /* 7 days */)
    @Field("expiresAt")
    private Instant expiresAt;

    // ── Enums ──────────────────────────────────────

    public enum StoryType {
        IMAGE, VIDEO
    }

    public enum StoryPrivacy {
        PUBLIC, PRIVATE
    }
}
