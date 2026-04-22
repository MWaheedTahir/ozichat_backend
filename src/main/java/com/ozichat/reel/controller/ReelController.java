package com.ozichat.reel.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.common.CursorPagedResponse;
import com.ozichat.reel.dto.request.AddCommentRequest;
import com.ozichat.reel.dto.request.CreateReelRequest;
import com.ozichat.reel.dto.response.ReelCommentResponse;
import com.ozichat.reel.dto.response.ReelResponse;
import com.ozichat.reel.service.ReelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reels")
@RequiredArgsConstructor
@Tag(name = "Reels", description = "Short-form video reels — upload, feed, likes, comments and sharing")
@SecurityRequirement(name = "bearerAuth")
public class ReelController {

    private final ReelService reelService;

    // ── Create ──────────────────────────────────────

    /**
     * Step 1: Get a pre-signed PUT URL
     *   POST /api/v1/media/presign?fileName=clip.mp4&folder=reels
     *   POST /api/v1/media/presign?fileName=thumb.jpg&folder=reels-thumbs
     * Step 2: Client uploads directly to S3 using the PUT URL
     * Step 3: Call this endpoint with the returned s3Key and publicUrl fields
     */
    @PostMapping
    @Operation(summary = "Publish a reel (video must already be uploaded to S3 via pre-signed URL)")
    public ResponseEntity<ApiResponse<ReelResponse>> createReel(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateReelRequest request) {

        ReelResponse response = reelService.createReel(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reel published", response));
    }

    // ── Feed ────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get global reel feed (newest first, cursor-paginated)")
    public ResponseEntity<ApiResponse<CursorPagedResponse<ReelResponse>>> getFeed(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Cursor from previous page (last reel ID)") @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(reelService.getFeed(userId, cursor, limit)));
    }

    @GetMapping("/user/{targetUserId}")
    @Operation(summary = "Get reels posted by a specific user (newest first)")
    public ResponseEntity<ApiResponse<CursorPagedResponse<ReelResponse>>> getUserReels(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long targetUserId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                reelService.getUserReels(targetUserId, callerId, cursor, limit)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's own reels")
    public ResponseEntity<ApiResponse<CursorPagedResponse<ReelResponse>>> getMyReels(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                reelService.getUserReels(userId, userId, cursor, limit)));
    }

    @GetMapping("/{reelId}")
    @Operation(summary = "Get a single reel by ID")
    public ResponseEntity<ApiResponse<ReelResponse>> getById(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId) {

        return ResponseEntity.ok(ApiResponse.success(reelService.getById(reelId, userId)));
    }

    // ── Delete ──────────────────────────────────────

    @DeleteMapping("/{reelId}")
    @Operation(summary = "Delete (soft-delete) your own reel")
    public ResponseEntity<ApiResponse<Void>> deleteReel(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId) {

        reelService.deleteReel(reelId, userId);
        return ResponseEntity.ok(ApiResponse.success("Reel deleted"));
    }


    @PostMapping("/{reelId}/views")
    @Operation(summary = "Record a view (deduplicated per user per 24h window)")
    public ResponseEntity<ApiResponse<Boolean>> recordView(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId) {

        boolean isNew = reelService.recordView(reelId, userId);
        String message = isNew ? "View recorded" : "Already viewed";
        return ResponseEntity.ok(ApiResponse.success(message, isNew));
    }


    @PostMapping("/{reelId}/like")
    @Operation(summary = "Like a reel (idempotent)")
    public ResponseEntity<ApiResponse<ReelResponse>> likeReel(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId) {

        return ResponseEntity.ok(ApiResponse.success("Reel liked", reelService.likeReel(reelId, userId)));
    }

    @DeleteMapping("/{reelId}/like")
    @Operation(summary = "Unlike a reel")
    public ResponseEntity<ApiResponse<ReelResponse>> unlikeReel(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId) {

        return ResponseEntity.ok(ApiResponse.success("Reel unliked", reelService.unlikeReel(reelId, userId)));
    }


    @PostMapping("/{reelId}/comments")
    @Operation(summary = "Post a comment on a reel")
    public ResponseEntity<ApiResponse<ReelCommentResponse>> addComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId,
            @Valid @RequestBody AddCommentRequest request) {

        ReelCommentResponse comment = reelService.addComment(reelId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment posted", comment));
    }

    @GetMapping("/{reelId}/comments")
    @Operation(summary = "Get comments for a reel (newest first, cursor-paginated)")
    public ResponseEntity<ApiResponse<CursorPagedResponse<ReelCommentResponse>>> getComments(
            @PathVariable String reelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "15") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                reelService.getComments(reelId, cursor, limit)));
    }

    @DeleteMapping("/{reelId}/comments/{commentId}")
    @Operation(summary = "Delete your own comment")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId,
            @PathVariable String commentId) {

        reelService.deleteComment(reelId, commentId, userId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted"));
    }

    // ── Share ─────────────────────────────────────────

    @PostMapping("/{reelId}/share/{conversationId}")
    @Operation(summary = "Share a reel into a chat conversation")
    public ResponseEntity<ApiResponse<Void>> shareToConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String reelId,
            @PathVariable Long conversationId) {

        reelService.shareToConversation(reelId, userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Reel shared to conversation"));
    }
}
