package com.ozichat;

import com.ozichat.conversation.entity.ConversationMember;
import com.ozichat.conversation.entity.PinnedMessage;
import com.ozichat.conversation.repository.ConversationMemberRepository;
import com.ozichat.conversation.repository.ConversationRepository;
import com.ozichat.conversation.repository.PinnedMessageRepository;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.DuplicateResourceException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.group.dto.request.SetAnnouncementRequest;
import com.ozichat.group.dto.response.GroupResponse;
import com.ozichat.group.entity.GroupMetadata;
import com.ozichat.group.repository.GroupInviteLinkRepository;
import com.ozichat.group.repository.GroupMetadataRepository;
import com.ozichat.group.service.impl.GroupServiceImpl;
import com.ozichat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Group pinned messages and announcement tests")
class GroupPinAnnouncementTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMemberRepository memberRepository;
    @Mock private GroupMetadataRepository groupMetadataRepository;
    @Mock private GroupInviteLinkRepository inviteLinkRepository;
    @Mock private PinnedMessageRepository pinnedMessageRepository;
    @Mock private UserRepository userRepository;

    private GroupServiceImpl groupService;

    private static final Long CONV_ID  = 10L;
    private static final Long ADMIN_ID = 1L;
    private static final Long MSG_ID_STR = null; // used as String "abc123"

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
                conversationRepository, memberRepository,
                groupMetadataRepository, inviteLinkRepository,
                pinnedMessageRepository, userRepository);
    }

    // ─── Helper stubs ─────────────────────────────

    private void stubAdminMember() {
        ConversationMember admin = new ConversationMember();
        admin.setUserId(ADMIN_ID);
        admin.setRole(ConversationMember.Role.ADMIN);
        given(memberRepository.findByConversationIdAndUserIdAndLeftAtIsNull(CONV_ID, ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(CONV_ID, ADMIN_ID))
                .willReturn(true);
    }

    private GroupMetadata stubMetadata() {
        GroupMetadata meta = GroupMetadata.builder()
                .conversationId(CONV_ID)
                .groupName("Test Group")
                .maxMembers(1024)
                .onlyAdminsCanSend(false)
                .onlyAdminsCanEditInfo(true)
                .createdBy(ADMIN_ID)
                .build();
        given(groupMetadataRepository.findById(CONV_ID)).willReturn(Optional.of(meta));
        given(groupMetadataRepository.save(any(GroupMetadata.class))).willAnswer(inv -> inv.getArgument(0));
        return meta;
    }

    // ─── Announcement tests ───────────────────────

    @Test
    @DisplayName("setAnnouncement: sets announcement text and timestamp")
    void setAnnouncement_setsTextAndTimestamp() {
        stubAdminMember();
        stubMetadata();
        given(memberRepository.findByConversationIdAndLeftAtIsNull(CONV_ID))
                .willReturn(java.util.List.of());

        SetAnnouncementRequest req = new SetAnnouncementRequest();
        req.setText("Welcome to the group! Please read the rules.");

        GroupResponse response = groupService.setAnnouncement(CONV_ID, ADMIN_ID, req);

        assertThat(response.getAnnouncementText()).isEqualTo("Welcome to the group! Please read the rules.");
        assertThat(response.getAnnouncementBy()).isEqualTo(ADMIN_ID);
        assertThat(response.getAnnouncementAt()).isNotNull();
    }

    @Test
    @DisplayName("setAnnouncement: clears announcement when text is blank")
    void setAnnouncement_blankText_clearsAnnouncement() {
        stubAdminMember();
        stubMetadata();
        given(memberRepository.findByConversationIdAndLeftAtIsNull(CONV_ID))
                .willReturn(java.util.List.of());

        SetAnnouncementRequest req = new SetAnnouncementRequest();
        req.setText("  "); // blank

        GroupResponse response = groupService.setAnnouncement(CONV_ID, ADMIN_ID, req);

        assertThat(response.getAnnouncementText()).isNull();
        assertThat(response.getAnnouncementAt()).isNull();
        assertThat(response.getAnnouncementBy()).isNull();
    }

    // ─── Pin message tests ────────────────────────

    @Test
    @DisplayName("pinMessage: successfully pins a message")
    void pinMessage_success() {
        stubAdminMember();
        stubMetadata();

        String messageId = "64b9f1a2c3d4e5f600001234";
        given(pinnedMessageRepository.existsByConversationIdAndMessageId(CONV_ID, messageId))
                .willReturn(false);
        given(pinnedMessageRepository.countByConversationId(CONV_ID)).willReturn(0L);
        given(pinnedMessageRepository.save(any(PinnedMessage.class))).willAnswer(inv -> {
            PinnedMessage p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        var response = groupService.pinMessage(CONV_ID, ADMIN_ID, messageId);

        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getPinnedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("pinMessage: throws DuplicateResourceException when already pinned")
    void pinMessage_alreadyPinned_throwsDuplicateException() {
        stubAdminMember();
        stubMetadata();

        String messageId = "64b9f1a2c3d4e5f600001234";
        given(pinnedMessageRepository.existsByConversationIdAndMessageId(CONV_ID, messageId))
                .willReturn(true);

        assertThatThrownBy(() -> groupService.pinMessage(CONV_ID, ADMIN_ID, messageId))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already pinned");
    }

    @Test
    @DisplayName("pinMessage: throws BusinessException when max 5 messages already pinned")
    void pinMessage_maxPinnedReached_throwsBusinessException() {
        stubAdminMember();
        stubMetadata();

        String messageId = "64b9f1a2c3d4e5f600001234";
        given(pinnedMessageRepository.existsByConversationIdAndMessageId(CONV_ID, messageId))
                .willReturn(false);
        given(pinnedMessageRepository.countByConversationId(CONV_ID)).willReturn(5L);

        assertThatThrownBy(() -> groupService.pinMessage(CONV_ID, ADMIN_ID, messageId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot pin more than 5");
    }

    @Test
    @DisplayName("unpinMessage: throws ResourceNotFoundException when message not pinned")
    void unpinMessage_notPinned_throwsNotFound() {
        stubAdminMember();
        stubMetadata();

        given(pinnedMessageRepository.existsByConversationIdAndMessageId(CONV_ID, "nonexistent"))
                .willReturn(false);

        assertThatThrownBy(() -> groupService.unpinMessage(CONV_ID, ADMIN_ID, "nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("unpinMessage: successfully unpins a message")
    void unpinMessage_success() {
        stubAdminMember();
        stubMetadata();

        String messageId = "64b9f1a2c3d4e5f600001234";
        given(pinnedMessageRepository.existsByConversationIdAndMessageId(CONV_ID, messageId))
                .willReturn(true);

        groupService.unpinMessage(CONV_ID, ADMIN_ID, messageId);

        verify(pinnedMessageRepository).deleteByConversationIdAndMessageId(CONV_ID, messageId);
    }
}
