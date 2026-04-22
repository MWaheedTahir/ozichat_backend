package com.ozichat.story.service;

import com.ozichat.story.dto.request.CreateStoryRequest;
import com.ozichat.story.dto.response.StoryResponse;
import com.ozichat.story.dto.response.StoryViewerResponse;
import com.ozichat.story.dto.response.UserStoriesResponse;

import java.util.List;

public interface StoryService {

    /**
     * Publish a new story.
     * Media must already be uploaded to S3 via the pre-signed URL flow.
     */
    StoryResponse createStory(Long userId, CreateStoryRequest request);

    /**
     * Global story feed — all stories the caller is allowed to see,
     * grouped by user (newest story per user determines group order).
     * Caller's own stories always appear first.
     */
    List<UserStoriesResponse> getFeed(Long callerId);

    /**
     * The caller's own active stories (all privacies).
     */
    List<StoryResponse> getMyStories(Long userId);

    /**
     * Active stories posted by {@code targetUserId} that {@code callerId} can see.
     * Owners always see all their own stories regardless of privacy.
     */
    List<StoryResponse> getUserStories(Long targetUserId, Long callerId);

    /**
     * Fetch a single story by ID.
     * Throws {@link com.ozichat.exception.ResourceNotFoundException} if not found.
     * Throws {@link com.ozichat.exception.BusinessException} (403) if caller has no access.
     */
    StoryResponse getById(String storyId, Long callerId);

    /**
     * Soft-delete a story. Only the owner can delete.
     */
    void deleteStory(String storyId, Long userId);

    /**
     * Record that {@code viewerId} viewed a story.
     * Idempotent — duplicate calls are silently ignored.
     * The owner viewing their own story is also ignored.
     */
    void recordView(String storyId, Long viewerId);

    /**
     * List of users who have viewed a story — owner-only.
     * Paginated: {@code page} is 0-based, max 50 per page.
     */
    List<StoryViewerResponse> getViewers(String storyId, Long ownerId, int page, int size);
}
