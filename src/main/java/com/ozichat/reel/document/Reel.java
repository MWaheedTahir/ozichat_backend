package com.ozichat.reel.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A short-form video posted by a user.
 *
 * Storage strategy:
 *  - Document lives in MongoDB (variable-length media metadata, hashtag arrays)
 *  - Likes tracked in MySQL (ReelLike) for fast EXISTS queries with referential integrity
 *  - Comments live here as a separate ReelComment collection (cursor-paginated)
 *  - View deduplication handled in Redis (TTL key per user+reel)
 *  - viewCount / likeCount / commentCount are denormalized counters updated atomically
 *    via MongoTemplate $inc to avoid aggregation scans on every feed load
 */
@Document(collection = "reels")
@CompoundIndexes({
    @CompoundIndex(name = "idx_reels_user_created",  def = "{'userId': 1, 'createdAt': -1, 'isDeleted': 1}"),
    @CompoundIndex(name = "idx_reels_feed",          def = "{'isDeleted': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_reels_hashtags",      def = "{'hashtags': 1, 'createdAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reel {

    @Id
    private String id;

    @Field("userId")
    private Long userId;

    // ── Video ─────────────────────────────────────

    @Field("videoUrl")
    private String videoUrl;           // public / CDN URL

    @Field("videoKey")
    private String videoKey;           // S3 object key (for deletion)

    @Field("thumbnailUrl")
    private String thumbnailUrl;       // public / CDN URL

    @Field("thumbnailKey")
    private String thumbnailKey;       // S3 object key

    @Field("duration")
    private Integer duration;          // seconds

    @Field("fileSize")
    private Long fileSize;             // bytes

    @Field("width")
    private Integer width;

    @Field("height")
    private Integer height;

    @Field("mimeType")
    @Builder.Default
    private String mimeType = "video/mp4";

    // ── Content ───────────────────────────────────

    @Field("caption")
    private String caption;            // up to 2200 chars (Instagram-style limit)

    @Field("hashtags")
    @Builder.Default
    private List<String> hashtags = new ArrayList<>();  // auto-extracted from caption

    // ── Counters (denormalized for O(1) feed reads) ───

    @Field("viewCount")
    @Builder.Default
    private Long viewCount = 0L;

    @Field("likeCount")
    @Builder.Default
    private Long likeCount = 0L;

    @Field("commentCount")
    @Builder.Default
    private Long commentCount = 0L;

    @Field("shareCount")
    @Builder.Default
    private Long shareCount = 0L;

    // ── Visibility / Lifecycle ─────────────────────

    @Field("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Indexed(sparse = true)
    @Field("deletedAt")
    private Instant deletedAt;

    @Field("createdAt")
    private Instant createdAt;

    @Field("updatedAt")
    private Instant updatedAt;
}
