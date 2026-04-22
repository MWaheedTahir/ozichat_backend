package com.ozichat.reel.service;

import com.ozichat.common.CursorPagedResponse;
import com.ozichat.reel.dto.request.AddCommentRequest;
import com.ozichat.reel.dto.request.CreateReelRequest;
import com.ozichat.reel.dto.response.ReelCommentResponse;
import com.ozichat.reel.dto.response.ReelResponse;

public interface ReelService {

    /** Register a reel whose video was already uploaded to S3 via pre-signed URL. */
    ReelResponse createReel(Long userId, CreateReelRequest request);

    /** Global feed — newest first, cursor-based. Limit max 20. */
    CursorPagedResponse<ReelResponse> getFeed(Long callerId, String cursor, int limit);

    /** Reels posted by a specific user, newest first. */
    CursorPagedResponse<ReelResponse> getUserReels(Long targetUserId, Long callerId,
                                                    String cursor, int limit);

    /** Single reel detail. */
    ReelResponse getById(String reelId, Long callerId);

    /** Soft-delete. Only the owner can delete their own reel. */
    void deleteReel(String reelId, Long userId);

    // ── Engagement ─────────────────────────────────

    /** Idempotent like. */
    ReelResponse likeReel(String reelId, Long userId);

    /** Unlike. Throws if not liked. */
    ReelResponse unlikeReel(String reelId, Long userId);

    /**
     * Record a view.
     * Uses Redis SET NX with 24h TTL to deduplicate repeat views within a day.
     * Returns true if this was a new unique view.
     */
    boolean recordView(String reelId, Long userId);

    // ── Comments ───────────────────────────────────

    ReelCommentResponse addComment(String reelId, Long userId, AddCommentRequest request);

    CursorPagedResponse<ReelCommentResponse> getComments(String reelId, String cursor, int limit);

    /** Only the comment owner can delete their own comment. */
    void deleteComment(String reelId, String commentId, Long userId);

    // ── Share ──────────────────────────────────────

    /**
     * Share a reel link into a chat conversation.
     * Creates a TEXT message in the conversation containing the reel URL.
     */
    void shareToConversation(String reelId, Long userId, Long conversationId);
}
