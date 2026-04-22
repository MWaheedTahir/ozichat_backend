package com.ozichat.story.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.story.dto.request.CreateStoryRequest;
import com.ozichat.story.dto.response.StoryResponse;
import com.ozichat.story.dto.response.StoryViewerResponse;
import com.ozichat.story.dto.response.UserStoriesResponse;
import com.ozichat.story.service.StoryService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
@Tag(name = "Stories", description = "24-hour stories — public and private, with view tracking")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService storyService;

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Upload flow:
     *   Step 1: POST /api/v1/media/presign?fileName=photo.jpg&folder=stories
     *   Step 2: Client PUTs binary directly to S3
     *   Step 3: Call this endpoint with the returned s3Key and publicUrl
     */
    @PostMapping
    @Operation(summary = "Publish a story (media must already be uploaded to S3 via pre-signed URL)")
    public ResponseEntity<ApiResponse<StoryResponse>> createStory(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateStoryRequest request) {

        StoryResponse response = storyService.createStory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Story published", response));
    }

    // ── Feed ─────────────────────────────────────────────────────────────

    @GetMapping("/feed")
    @Operation(summary = "Get the story feed — all visible stories grouped by user (own stories first)")
    public ResponseEntity<ApiResponse<List<UserStoriesResponse>>> getFeed(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(storyService.getFeed(userId)));
    }

    // ── Own Stories ───────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the current user's own active stories (all privacies)")
    public ResponseEntity<ApiResponse<List<StoryResponse>>> getMyStories(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(storyService.getMyStories(userId)));
    }

    // ── User Stories ──────────────────────────────────────────────────────

    @GetMapping("/user/{targetUserId}")
    @Operation(summary = "Get active stories posted by a specific user (privacy-filtered)")
    public ResponseEntity<ApiResponse<List<StoryResponse>>> getUserStories(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long targetUserId) {

        return ResponseEntity.ok(ApiResponse.success(
                storyService.getUserStories(targetUserId, callerId)));
    }

    // ── Single Story ──────────────────────────────────────────────────────

    @GetMapping("/{storyId}")
    @Operation(summary = "Get a single story by ID")
    public ResponseEntity<ApiResponse<StoryResponse>> getById(
            @AuthenticationPrincipal Long userId,
            @PathVariable String storyId) {

        return ResponseEntity.ok(ApiResponse.success(storyService.getById(storyId, userId)));
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @DeleteMapping("/{storyId}")
    @Operation(summary = "Delete (soft-delete) your own story")
    public ResponseEntity<ApiResponse<Void>> deleteStory(
            @AuthenticationPrincipal Long userId,
            @PathVariable String storyId) {

        storyService.deleteStory(storyId, userId);
        return ResponseEntity.ok(ApiResponse.success("Story deleted"));
    }

    // ── View ──────────────────────────────────────────────────────────────

    @PostMapping("/{storyId}/view")
    @Operation(summary = "Record a view on a story (idempotent — safe to call multiple times)")
    public ResponseEntity<ApiResponse<Void>> recordView(
            @AuthenticationPrincipal Long userId,
            @PathVariable String storyId) {

        storyService.recordView(storyId, userId);
        return ResponseEntity.ok(ApiResponse.success("View recorded"));
    }

    // ── Viewers ───────────────────────────────────────────────────────────

    @GetMapping("/{storyId}/viewers")
    @Operation(summary = "Get list of viewers for your own story (owner only)")
    public ResponseEntity<ApiResponse<List<StoryViewerResponse>>> getViewers(
            @AuthenticationPrincipal Long userId,
            @PathVariable String storyId,
            @Parameter(description = "0-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Max 50") @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                storyService.getViewers(storyId, userId, page, size)));
    }
}
