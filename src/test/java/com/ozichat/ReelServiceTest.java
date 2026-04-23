package com.ozichat;

import com.ozichat.common.CursorPagedResponse;
import com.ozichat.conversation.service.ConversationService;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
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
import com.ozichat.reel.service.impl.ReelServiceImpl;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReelService unit tests")
class ReelServiceTest {

    @Mock private ReelRepository reelRepository;
    @Mock private ReelCommentRepository commentRepository;
    @Mock private ReelLikeRepository likeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConversationService conversationService;
    @Mock private MessageService messageService;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private ReelServiceImpl reelService;

    // Stable ObjectId string for tests
    private static final String REEL_ID  = new ObjectId().toHexString();
    private static final Long   USER_ID  = 1L;
    private static final Long   OTHER_ID = 2L;

    @BeforeEach
    void setUp() {
        reelService = new ReelServiceImpl(
                reelRepository, commentRepository, likeRepository,
                userRepository, conversationService, messageService,
                mongoTemplate, redisTemplate);
    }

    // ── Helpers ───────────────────────────────────────

    private Reel buildReel(Long ownerId) {
        return Reel.builder()
                .id(REEL_ID)
                .userId(ownerId)
                .videoUrl("https://cdn.example.com/video.mp4")
                .videoKey("reels/1/abc.mp4")
                .likeCount(0L).viewCount(0L).commentCount(0L).shareCount(0L)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setDisplayName("User " + id);
        return u;
    }

    // ── createReel ────────────────────────────────────

    @Test
    @DisplayName("createReel: saves reel and returns response with extracted hashtags")
    void createReel_success_extractsHashtags() {
        CreateReelRequest req = new CreateReelRequest();
        req.setVideoKey("reels/1/clip.mp4");
        req.setVideoUrl("https://s3.amazonaws.com/bucket/reels/1/clip.mp4");
        req.setCaption("Check this out! #travel #food #vibes");
        req.setDuration(30);

        Reel saved = buildReel(USER_ID);
        saved.setHashtags(List.of("travel", "food", "vibes"));
        saved.setCaption(req.getCaption());

        given(reelRepository.save(any(Reel.class))).willReturn(saved);
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(buildUser(USER_ID)));
        given(likeRepository.existsByReelIdAndUserId(REEL_ID, USER_ID)).willReturn(false);

        ReelResponse response = reelService.createReel(USER_ID, req);

        assertThat(response.getId()).isEqualTo(REEL_ID);
        assertThat(response.getHashtags()).containsExactly("travel", "food", "vibes");
        assertThat(response.getIsLiked()).isFalse();
        verify(reelRepository).save(any(Reel.class));
    }

    @Test
    @DisplayName("createReel: caption with no hashtags produces empty list")
    void createReel_noHashtags_emptyList() {
        CreateReelRequest req = new CreateReelRequest();
        req.setVideoKey("reels/1/clip.mp4");
        req.setVideoUrl("https://s3.amazonaws.com/bucket/reels/1/clip.mp4");
        req.setCaption("Just a plain caption");

        Reel saved = buildReel(USER_ID);
        saved.setCaption(req.getCaption());

        given(reelRepository.save(any(Reel.class))).willReturn(saved);
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(buildUser(USER_ID)));
        given(likeRepository.existsByReelIdAndUserId(REEL_ID, USER_ID)).willReturn(false);

        ReelResponse response = reelService.createReel(USER_ID, req);
        assertThat(response.getHashtags()).isEmpty();
    }

    // ── deleteReel ────────────────────────────────────

    @Test
    @DisplayName("deleteReel: owner can soft-delete their own reel")
    void deleteReel_ownerCanDelete() {
        Reel reel = buildReel(USER_ID);
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(reel));
        given(reelRepository.save(any(Reel.class))).willReturn(reel);

        reelService.deleteReel(REEL_ID, USER_ID);

        assertThat(reel.getIsDeleted()).isTrue();
        assertThat(reel.getDeletedAt()).isNotNull();
        verify(reelRepository).save(reel);
    }

    @Test
    @DisplayName("deleteReel: non-owner gets FORBIDDEN exception")
    void deleteReel_nonOwner_throwsForbidden() {
        Reel reel = buildReel(USER_ID); // owned by USER_ID
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(reel));

        assertThatThrownBy(() -> reelService.deleteReel(REEL_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own reels");
    }

    @Test
    @DisplayName("deleteReel: throws ResourceNotFoundException for missing reel")
    void deleteReel_notFound_throwsNotFound() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reelService.deleteReel(REEL_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── likeReel / unlikeReel ─────────────────────────

    @Test
    @DisplayName("likeReel: saves like and increments counter atomically")
    void likeReel_newLike_savesAndIncrements() {
        Reel reel = buildReel(OTHER_ID);
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(reel));
        given(likeRepository.existsByReelIdAndUserId(REEL_ID, USER_ID)).willReturn(false);
        given(likeRepository.save(any(ReelLike.class))).willReturn(new ReelLike());
        given(userRepository.findByIdAndDeletedAtIsNull(OTHER_ID)).willReturn(Optional.of(buildUser(OTHER_ID)));

        reelService.likeReel(REEL_ID, USER_ID);

        verify(likeRepository).save(any(ReelLike.class));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Reel.class));
    }

    @Test
    @DisplayName("likeReel: idempotent — does not create duplicate like")
    void likeReel_alreadyLiked_idempotentNoDuplicate() {
        Reel reel = buildReel(OTHER_ID);
        // findByIdAndIsDeletedFalse called twice (existence check + buildResponse)
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(reel));
        given(likeRepository.existsByReelIdAndUserId(REEL_ID, USER_ID)).willReturn(true);
        given(userRepository.findByIdAndDeletedAtIsNull(OTHER_ID)).willReturn(Optional.of(buildUser(OTHER_ID)));

        reelService.likeReel(REEL_ID, USER_ID);

        verify(likeRepository, never()).save(any(ReelLike.class));
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Reel.class));
    }

    @Test
    @DisplayName("unlikeReel: throws BusinessException when user has not liked the reel")
    void unlikeReel_notLiked_throwsConflict() {
        Reel reel = buildReel(OTHER_ID);
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(reel));
        given(likeRepository.existsByReelIdAndUserId(REEL_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> reelService.unlikeReel(REEL_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not liked");
    }

    // ── recordView ────────────────────────────────────

    @Test
    @DisplayName("recordView: new view — returns true and increments counter")
    void recordView_newView_returnsTrue() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(buildReel(OTHER_ID)));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any())).willReturn(Boolean.TRUE);

        boolean result = reelService.recordView(REEL_ID, USER_ID);

        assertThat(result).isTrue();
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Reel.class));
    }

    @Test
    @DisplayName("recordView: duplicate view within 24h — returns false, no counter increment")
    void recordView_duplicateWithin24h_returnsFalse() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(buildReel(OTHER_ID)));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any())).willReturn(Boolean.FALSE);

        boolean result = reelService.recordView(REEL_ID, USER_ID);

        assertThat(result).isFalse();
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Reel.class));
    }

    // ── Comments ──────────────────────────────────────

    @Test
    @DisplayName("addComment: saves comment and increments commentCount")
    void addComment_success() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(buildReel(OTHER_ID)));

        ReelComment saved = ReelComment.builder()
                .id(new ObjectId().toHexString())
                .reelId(REEL_ID)
                .userId(USER_ID)
                .content("Great reel!")
                .createdAt(Instant.now())
                .build();
        given(commentRepository.save(any(ReelComment.class))).willReturn(saved);
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(buildUser(USER_ID)));

        AddCommentRequest req = new AddCommentRequest();
        req.setContent("Great reel!");

        ReelCommentResponse response = reelService.addComment(REEL_ID, USER_ID, req);

        assertThat(response.getContent()).isEqualTo("Great reel!");
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Reel.class));
    }

    @Test
    @DisplayName("deleteComment: non-owner gets FORBIDDEN exception")
    void deleteComment_nonOwner_throwsForbidden() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(buildReel(OTHER_ID)));

        String commentId = new ObjectId().toHexString();
        ReelComment comment = ReelComment.builder()
                .id(commentId)
                .reelId(REEL_ID)
                .userId(USER_ID)  // owned by USER_ID
                .content("hello")
                .isDeleted(false)
                .build();
        given(commentRepository.findByIdAndIsDeletedFalse(commentId)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> reelService.deleteComment(REEL_ID, commentId, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own comments");
    }

    @Test
    @DisplayName("deleteComment: owner can delete and decrements commentCount")
    void deleteComment_owner_softDeletesAndDecrements() {
        given(reelRepository.findByIdAndIsDeletedFalse(REEL_ID)).willReturn(Optional.of(buildReel(OTHER_ID)));

        String commentId = new ObjectId().toHexString();
        ReelComment comment = ReelComment.builder()
                .id(commentId)
                .reelId(REEL_ID)
                .userId(USER_ID)
                .content("hello")
                .isDeleted(false)
                .build();
        given(commentRepository.findByIdAndIsDeletedFalse(commentId)).willReturn(Optional.of(comment));
        given(commentRepository.save(any(ReelComment.class))).willReturn(comment);

        reelService.deleteComment(REEL_ID, commentId, USER_ID);

        assertThat(comment.getIsDeleted()).isTrue();
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Reel.class));
    }
}
