package com.ozichat.story.service.impl;

import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.story.document.Story;
import com.ozichat.story.document.StoryView;
import com.ozichat.story.dto.request.CreateStoryRequest;
import com.ozichat.story.dto.response.StoryResponse;
import com.ozichat.story.dto.response.StoryViewerResponse;
import com.ozichat.story.dto.response.UserStoriesResponse;
import com.ozichat.story.repository.StoryRepository;
import com.ozichat.story.repository.StoryViewRepository;
import com.ozichat.story.service.StoryService;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryServiceImpl implements StoryService {

    private static final Duration STORY_TTL        = Duration.ofHours(24);
    private static final Sort     NEWEST_FIRST      = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final int      MAX_VIEWERS_PAGE  = 50;

    private final StoryRepository     storyRepository;
    private final StoryViewRepository viewRepository;
    private final UserRepository      userRepository;
    private final MongoTemplate       mongoTemplate;

    // ── Create ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StoryResponse createStory(Long userId, CreateStoryRequest request) {
        Instant now      = Instant.now();
        Instant expiry   = now.plus(STORY_TTL);

        Story story = Story.builder()
                .userId(userId)
                .mediaUrl(request.getMediaUrl())
                .mediaKey(request.getMediaKey())
                .thumbnailUrl(request.getThumbnailUrl())
                .thumbnailKey(request.getThumbnailKey())
                .type(request.getType())
                .duration(request.getDuration())
                .caption(request.getCaption())
                .privacy(request.getPrivacy())
                .allowedViewerIds(
                        request.getPrivacy() == Story.StoryPrivacy.PRIVATE
                                ? request.getAllowedViewerIds()
                                : null
                )
                .viewCount(0L)
                .isDeleted(false)
                .createdAt(now)
                .expiresAt(expiry)
                .build();

        Story saved = storyRepository.save(story);
        log.info("Story created — id={} userId={} privacy={}", saved.getId(), userId, saved.getPrivacy());
        return buildStoryResponse(saved, userId, null);
    }

    // ── Feed ────────────────────────────────────────────────────────────────

    @Override
    public List<UserStoriesResponse> getFeed(Long callerId) {
        Instant now = Instant.now();
        List<Story> stories = storyRepository.findFeedStories(callerId, now, NEWEST_FIRST);

        // Also include caller's own stories (all privacies)
        List<Story> myStories = storyRepository.findActiveByUserId(callerId, now, NEWEST_FIRST);

        // Merge, deduplicate by ID
        Map<String, Story> deduplicated = new LinkedHashMap<>();
        myStories.forEach(s -> deduplicated.put(s.getId(), s));
        stories.forEach(s -> deduplicated.putIfAbsent(s.getId(), s));

        // Group by userId
        Map<Long, List<Story>> byUser = deduplicated.values().stream()
                .collect(Collectors.groupingBy(Story::getUserId, LinkedHashMap::new, Collectors.toList()));

        // Pre-load all user profiles in one batch
        Set<Long> userIds = byUser.keySet();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Pre-fetch viewed story IDs for this caller
        Set<String> allStoryIds = deduplicated.keySet();
        Set<String> viewedIds = allStoryIds.stream()
                .filter(sid -> viewRepository.existsByStoryIdAndViewerId(sid, callerId))
                .collect(Collectors.toSet());

        // Build response — caller's own stories first, then others sorted by most recent story
        List<UserStoriesResponse> result = new ArrayList<>();

        // Own stories first
        if (byUser.containsKey(callerId)) {
            result.add(buildUserStoriesResponse(
                    callerId, byUser.get(callerId), userMap, viewedIds, callerId));
        }

        // Other users sorted by the timestamp of their most recent story
        byUser.entrySet().stream()
                .filter(e -> !e.getKey().equals(callerId))
                .sorted((a, b) -> {
                    Instant aLatest = a.getValue().get(0).getCreatedAt();
                    Instant bLatest = b.getValue().get(0).getCreatedAt();
                    return bLatest.compareTo(aLatest);
                })
                .forEach(e -> result.add(
                        buildUserStoriesResponse(e.getKey(), e.getValue(), userMap, viewedIds, callerId)));

        return result;
    }

    // ── My Stories ──────────────────────────────────────────────────────────

    @Override
    public List<StoryResponse> getMyStories(Long userId) {
        return storyRepository.findActiveByUserId(userId, Instant.now(), NEWEST_FIRST)
                .stream()
                .map(s -> buildStoryResponse(s, userId, null)) // owner always sees all
                .collect(Collectors.toList());
    }

    // ── User Stories ────────────────────────────────────────────────────────

    @Override
    public List<StoryResponse> getUserStories(Long targetUserId, Long callerId) {
        Instant now = Instant.now();
        List<Story> stories;

        if (targetUserId.equals(callerId)) {
            // Owner sees everything regardless of privacy
            stories = storyRepository.findActiveByUserId(targetUserId, now, NEWEST_FIRST);
        } else {
            stories = storyRepository.findVisibleByUserId(targetUserId, callerId, now, NEWEST_FIRST);
        }

        return stories.stream()
                .map(s -> {
                    boolean viewed = viewRepository.existsByStoryIdAndViewerId(s.getId(), callerId);
                    return buildStoryResponse(s, callerId, viewed);
                })
                .collect(Collectors.toList());
    }

    // ── Get By ID ───────────────────────────────────────────────────────────

    @Override
    public StoryResponse getById(String storyId, Long callerId) {
        Story story = findActiveStory(storyId);
        assertCanView(story, callerId);
        boolean viewed = !story.getUserId().equals(callerId)
                && viewRepository.existsByStoryIdAndViewerId(storyId, callerId);
        return buildStoryResponse(story, callerId, viewed);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteStory(String storyId, Long userId) {
        Story story = findActiveStory(storyId);
        assertOwner(story, userId);

        story.setIsDeleted(true);
        story.setDeletedAt(Instant.now());
        storyRepository.save(story);
        log.info("Story soft-deleted — id={} by userId={}", storyId, userId);
    }

    // ── Record View ─────────────────────────────────────────────────────────

    @Override
    public void recordView(String storyId, Long viewerId) {
        Story story = findActiveStory(storyId);

        // Owner viewing their own story — skip
        if (story.getUserId().equals(viewerId)) return;

        // Verify access
        assertCanView(story, viewerId);

        // Idempotent — ignore duplicate views
        if (viewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) return;

        viewRepository.save(StoryView.builder()
                .storyId(storyId)
                .viewerId(viewerId)
                .viewedAt(Instant.now())
                .build());

        // Atomically increment viewCount
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(new ObjectId(storyId))),
                new Update().inc("viewCount", 1),
                Story.class
        );

        log.debug("Story viewed — storyId={} viewerId={}", storyId, viewerId);
    }

    // ── Viewers ─────────────────────────────────────────────────────────────

    @Override
    public List<StoryViewerResponse> getViewers(String storyId, Long ownerId, int page, int size) {
        Story story = findActiveStory(storyId);
        assertOwner(story, ownerId);

        int safeSize = Math.min(size, MAX_VIEWERS_PAGE);
        List<StoryView> views = viewRepository.findByStoryIdOrderByViewedAtDesc(
                storyId, PageRequest.of(page, safeSize));

        return views.stream()
                .map(v -> {
                    User viewer = userRepository.findByIdAndDeletedAtIsNull(v.getViewerId()).orElse(null);
                    return StoryViewerResponse.from(v,
                            viewer != null ? viewer.getDisplayName() : "Unknown",
                            viewer != null ? viewer.getAvatarUrl() : null);
                })
                .collect(Collectors.toList());
    }

    // ── Private Helpers ─────────────────────────────────────────────────────

    private Story findActiveStory(String storyId) {
        Story story = storyRepository.findByIdAndIsDeletedFalse(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story", storyId));

        if (Instant.now().isAfter(story.getExpiresAt())) {
            throw new ResourceNotFoundException("Story", storyId);  // treat expired as not found
        }
        return story;
    }

    private void assertOwner(Story story, Long userId) {
        if (!story.getUserId().equals(userId)) {
            throw new BusinessException("You can only modify your own stories", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCanView(Story story, Long callerId) {
        if (story.getUserId().equals(callerId)) return; // owner always has access
        if (story.getPrivacy() == Story.StoryPrivacy.PUBLIC) return;
        // PRIVATE — check allowedViewerIds
        List<Long> allowed = story.getAllowedViewerIds();
        if (allowed == null || !allowed.contains(callerId)) {
            throw new BusinessException("You are not allowed to view this story", HttpStatus.FORBIDDEN);
        }
    }

    private StoryResponse buildStoryResponse(Story story, Long callerId, Boolean viewed) {
        User uploader = userRepository.findByIdAndDeletedAtIsNull(story.getUserId()).orElse(null);
        return StoryResponse.from(
                story,
                uploader != null ? uploader.getDisplayName() : "Unknown",
                uploader != null ? uploader.getAvatarUrl() : null,
                story.getUserId().equals(callerId) ? null : viewed  // null for owner
        );
    }

    private UserStoriesResponse buildUserStoriesResponse(Long userId,
                                                          List<Story> stories,
                                                          Map<Long, User> userMap,
                                                          Set<String> viewedIds,
                                                          Long callerId) {
        User user = userMap.get(userId);
        boolean hasUnviewed = stories.stream()
                .anyMatch(s -> !viewedIds.contains(s.getId()));

        List<StoryResponse> storyResponses = stories.stream()
                .map(s -> {
                    Boolean viewed = userId.equals(callerId) ? null : viewedIds.contains(s.getId());
                    return StoryResponse.from(s,
                            user != null ? user.getDisplayName() : "Unknown",
                            user != null ? user.getAvatarUrl() : null,
                            viewed);
                })
                .collect(Collectors.toList());

        return UserStoriesResponse.builder()
                .userId(userId)
                .userName(user != null ? user.getDisplayName() : "Unknown")
                .userAvatarUrl(user != null ? user.getAvatarUrl() : null)
                .hasUnviewed(hasUnviewed)
                .stories(storyResponses)
                .build();
    }
}
