package com.ozichat.reel.service.impl;

import com.ozichat.common.CursorPagedResponse;
import com.ozichat.conversation.service.ConversationService;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.message.dto.request.SendMessageRequest;
import com.ozichat.message.service.MessageService;
import com.ozichat.reel.document.Reel;
import com.ozichat.reel.document.ReelComment;
import com.ozichat.reel.dto.request.AddCommentRequest;
import com.ozichat.reel.dto.request.CreateReelRequest;
import com.ozichat.reel.dto.response.ReelCommentResponse;
import com.ozichat.reel.dto.response.ReelResponse;
import com.ozichat.reel.entity.ReelLike;
import com.ozichat.reel.repository.ReelCommentRepository;
import com.ozichat.reel.repository.ReelLikeRepository;
import com.ozichat.reel.repository.ReelRepository;
import com.ozichat.reel.service.ReelService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReelServiceImpl implements ReelService {

    private final ReelRepository reelRepository;
    private final ReelCommentRepository commentRepository;
    private final ReelLikeRepository likeRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    // Redis key: reel:view:{reelId}:{userId}  — TTL 24h (one view per user per day)
    private static final String VIEW_KEY_PREFIX   = "reel:view:";
    private static final Duration VIEW_TTL        = Duration.ofHours(24);
    private static final int MAX_FEED_LIMIT       = 20;
    private static final Pattern HASHTAG_PATTERN  = Pattern.compile("#(\\w+)");

    // ── Create ──────────────────────────────────────

    @Override
    @Transactional
    public ReelResponse createReel(Long userId, CreateReelRequest request) {
        List<String> hashtags = extractHashtags(request.getCaption());

        Reel reel = Reel.builder()
                .userId(userId)
                .videoUrl(request.getVideoUrl())
                .videoKey(request.getVideoKey())
                .thumbnailUrl(request.getThumbnailUrl())
                .thumbnailKey(request.getThumbnailKey())
                .caption(request.getCaption())
                .hashtags(hashtags)
                .duration(request.getDuration())
                .fileSize(request.getFileSize())
                .width(request.getWidth())
                .height(request.getHeight())
                .mimeType(StringUtils.hasText(request.getMimeType())
                        ? request.getMimeType() : "video/mp4")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Reel saved = reelRepository.save(reel);
        log.info("Reel created — id={} by userId={}", saved.getId(), userId);

        return buildResponse(saved, userId, false);
    }

    // ── Feed ────────────────────────────────────────

    @Override
    public CursorPagedResponse<ReelResponse> getFeed(Long callerId, String cursor, int limit) {
        int safeLimit = Math.min(limit, MAX_FEED_LIMIT);
        int fetchLimit = safeLimit + 1; // fetch one extra to determine hasMore

        PageRequest pageRequest = PageRequest.of(0, fetchLimit, Sort.by(Sort.Direction.DESC, "_id"));

        List<Reel> reels;
        if (StringUtils.hasText(cursor)) {
            reels = reelRepository.findFeedBeforeCursor(toObjectId(cursor), pageRequest);
        } else {
            reels = reelRepository.findFeed(pageRequest);
        }

        boolean hasMore = reels.size() > safeLimit;
        List<Reel> page = hasMore ? reels.subList(0, safeLimit) : reels;

        List<ReelResponse> content = page.stream()
                .map(r -> buildResponse(r, callerId,
                        callerId != null && likeRepository.existsByReelIdAndUserId(r.getId(), callerId)))
                .collect(Collectors.toList());

        String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return CursorPagedResponse.<ReelResponse>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(safeLimit)
                .build();
    }

    @Override
    public CursorPagedResponse<ReelResponse> getUserReels(Long targetUserId, Long callerId,
                                                          String cursor, int limit) {
        int safeLimit = Math.min(limit, MAX_FEED_LIMIT);
        PageRequest pageRequest = PageRequest.of(0, safeLimit + 1,
                Sort.by(Sort.Direction.DESC, "_id"));

        List<Reel> reels;
        if (StringUtils.hasText(cursor)) {
            reels = reelRepository.findByUserIdBeforeCursor(targetUserId, toObjectId(cursor), pageRequest);
        } else {
            reels = reelRepository.findByUserIdInitial(targetUserId, pageRequest);
        }

        boolean hasMore = reels.size() > safeLimit;
        List<Reel> page = hasMore ? reels.subList(0, safeLimit) : reels;

        List<ReelResponse> content = page.stream()
                .map(r -> buildResponse(r, callerId,
                        callerId != null && likeRepository.existsByReelIdAndUserId(r.getId(), callerId)))
                .collect(Collectors.toList());

        String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return CursorPagedResponse.<ReelResponse>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(safeLimit)
                .build();
    }

    @Override
    public ReelResponse getById(String reelId, Long callerId) {
        Reel reel = findActiveReel(reelId);
        boolean isLiked = callerId != null && likeRepository.existsByReelIdAndUserId(reelId, callerId);
        return buildResponse(reel, callerId, isLiked);
    }

    // ── Delete ──────────────────────────────────────

    @Override
    @Transactional
    public void deleteReel(String reelId, Long userId) {
        Reel reel = findActiveReel(reelId);
        assertOwner(reel, userId);

        reel.setIsDeleted(true);
        reel.setDeletedAt(Instant.now());
        reel.setUpdatedAt(Instant.now());
        reelRepository.save(reel);

        log.info("Reel soft-deleted — id={} by userId={}", reelId, userId);
    }

    // ── Likes ───────────────────────────────────────

    @Override
    @Transactional
    public ReelResponse likeReel(String reelId, Long userId) {
        findActiveReel(reelId); // existence check

        if (likeRepository.existsByReelIdAndUserId(reelId, userId)) {
            // Idempotent — just return current state
            return buildResponse(findActiveReel(reelId), userId,true);
        }

        likeRepository.save(ReelLike.builder().reelId(reelId).userId(userId).build());

        // Atomically increment counter in MongoDB (no full document reload needed)
        incrementCounter(reelId, "likeCount", 1);

        log.debug("Reel liked — reelId={} userId={}", reelId, userId);
        return buildResponse(findActiveReel(reelId), userId, true);
    }

    @Override
    @Transactional
    public ReelResponse unlikeReel(String reelId, Long userId) {
        findActiveReel(reelId); // existence check

        if (!likeRepository.existsByReelIdAndUserId(reelId, userId)) {
            throw new BusinessException("You have not liked this reel", HttpStatus.CONFLICT);
        }

        likeRepository.deleteByReelIdAndUserId(reelId, userId);
        incrementCounter(reelId, "likeCount", -1);

        log.debug("Reel unliked — reelId={} userId={}", reelId, userId);
        return buildResponse(findActiveReel(reelId), userId, false);
    }

    // ── Views ───────────────────────────────────────

    @Override
    public boolean recordView(String reelId, Long userId) {
        findActiveReel(reelId); // existence check

        String key = VIEW_KEY_PREFIX + reelId + ":" + userId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", VIEW_TTL);

        if (Boolean.TRUE.equals(isNew)) {
            incrementCounter(reelId, "viewCount", 1);
            log.debug("New view recorded — reelId={} userId={}", reelId, userId);
            return true;
        }

        return false; // duplicate view within 24h window
    }

    // ── Comments ────────────────────────────────────

    @Override
    @Transactional
    public ReelCommentResponse addComment(String reelId, Long userId, AddCommentRequest request) {
        findActiveReel(reelId); // existence check

        ReelComment comment = ReelComment.builder()
                .reelId(reelId)
                .userId(userId)
                .content(request.getContent().trim())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReelComment saved = commentRepository.save(comment);
        incrementCounter(reelId, "commentCount", 1);

        log.debug("Comment added — reelId={} commentId={} userId={}", reelId, saved.getId(), userId);
        return buildCommentResponse(saved);
    }

    @Override
    public CursorPagedResponse<ReelCommentResponse> getComments(String reelId, String cursor, int limit) {
        findActiveReel(reelId); // existence check

        int safeLimit = Math.min(limit, 30);
        PageRequest pageRequest = PageRequest.of(0, safeLimit + 1,
                Sort.by(Sort.Direction.DESC, "_id"));

        List<ReelComment> comments;
        if (StringUtils.hasText(cursor)) {
            comments = commentRepository.findByReelIdBeforeCursor(reelId, toObjectId(cursor), pageRequest);
        } else {
            comments = commentRepository.findByReelIdInitial(reelId, pageRequest);
        }

        boolean hasMore = comments.size() > safeLimit;
        List<ReelComment> page = hasMore ? comments.subList(0, safeLimit) : comments;

        List<ReelCommentResponse> content = page.stream()
                .map(this::buildCommentResponse)
                .collect(Collectors.toList());

        String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return CursorPagedResponse.<ReelCommentResponse>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(safeLimit)
                .build();
    }

    @Override
    @Transactional
    public void deleteComment(String reelId, String commentId, Long userId) {
        findActiveReel(reelId); // existence check

        ReelComment comment = commentRepository.findByIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getReelId().equals(reelId)) {
            throw new BusinessException("Comment does not belong to this reel", HttpStatus.BAD_REQUEST);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("You can only delete your own comments", HttpStatus.FORBIDDEN);
        }

        comment.setIsDeleted(true);
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);

        incrementCounter(reelId, "commentCount", -1);
        log.debug("Comment deleted — commentId={} by userId={}", commentId, userId);
    }

    // ── Share ────────────────────────────────────────

    @Override
    @Transactional
    public void shareToConversation(String reelId, Long userId, Long conversationId) {
        Reel reel = findActiveReel(reelId);

        if (!conversationService.isMember(conversationId, userId)) {
            throw new BusinessException("Not a member of this conversation", HttpStatus.FORBIDDEN);
        }

        // Build a text message containing the reel URL and caption preview
        String shareText = buildShareText(reel);
        SendMessageRequest msgRequest = new SendMessageRequest();
        msgRequest.setConversationId(conversationId);
        msgRequest.setContent(shareText);
        msgRequest.setType(com.ozichat.message.document.Message.MessageType.TEXT);

        messageService.saveMessage(conversationId, userId, msgRequest);

        // Increment share counter
        incrementCounter(reelId, "shareCount", 1);

        log.info("Reel {} shared to conversation {} by userId={}", reelId, conversationId, userId);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private Reel findActiveReel(String reelId) {
        return reelRepository.findByIdAndIsDeletedFalse(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", reelId));
    }

    private void assertOwner(Reel reel, Long userId) {
        if (!reel.getUserId().equals(userId)) {
            throw new BusinessException("You can only modify your own reels", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Atomic $inc on a counter field — avoids reloading and saving the full document.
     */
    private void incrementCounter(String reelId, String field, long delta) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(new ObjectId(reelId))),
                new Update().inc(field, delta),
                Reel.class
        );
    }

    private ReelResponse buildResponse(Reel reel, Long callerId, boolean isLiked) {
        User uploader = userRepository.findByIdAndDeletedAtIsNull(reel.getUserId()).orElse(null);
        String name   = uploader != null ? uploader.getDisplayName() : "Unknown";
        String avatar = uploader != null ? uploader.getAvatarUrl() : null;
        return ReelResponse.from(reel, name, avatar, isLiked);
    }

    private ReelCommentResponse buildCommentResponse(ReelComment comment) {
        User user = userRepository.findByIdAndDeletedAtIsNull(comment.getUserId()).orElse(null);
        return ReelCommentResponse.from(comment,
                user != null ? user.getDisplayName() : "Unknown",
                user != null ? user.getAvatarUrl() : null);
    }

    /**
     * Extract all #hashtags from the caption, lowercased and de-duplicated.
     */
    private List<String> extractHashtags(String caption) {
        if (!StringUtils.hasText(caption)) return new ArrayList<>();

        Matcher matcher = HASHTAG_PATTERN.matcher(caption);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String buildShareText(Reel reel) {
        StringBuilder sb = new StringBuilder("🎬 Shared a reel");
        if (StringUtils.hasText(reel.getCaption())) {
            String preview = reel.getCaption().length() > 100
                    ? reel.getCaption().substring(0, 100) + "…"
                    : reel.getCaption();
            sb.append(": ").append(preview);
        }
        sb.append("\n").append(reel.getVideoUrl());
        return sb.toString();
    }

    private ObjectId toObjectId(String id) {
        try {
            return new ObjectId(id);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid cursor format", HttpStatus.BAD_REQUEST);
        }
    }
}
