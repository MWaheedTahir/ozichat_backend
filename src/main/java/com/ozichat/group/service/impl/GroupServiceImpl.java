package com.ozichat.group.service.impl;

import com.ozichat.common.PagedResponse;
import com.ozichat.conversation.dto.response.MemberResponse;
import com.ozichat.conversation.dto.response.PinnedMessageResponse;
import com.ozichat.conversation.entity.Conversation;
import com.ozichat.conversation.entity.ConversationMember;
import com.ozichat.conversation.entity.PinnedMessage;
import com.ozichat.conversation.repository.ConversationMemberRepository;
import com.ozichat.conversation.repository.ConversationRepository;
import com.ozichat.conversation.repository.PinnedMessageRepository;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.DuplicateResourceException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.group.dto.request.CreateGroupRequest;
import com.ozichat.group.dto.request.SetAnnouncementRequest;
import com.ozichat.group.dto.request.UpdateGroupRequest;
import com.ozichat.group.dto.response.GroupResponse;
import com.ozichat.group.entity.GroupInviteLink;
import com.ozichat.group.entity.GroupMetadata;
import com.ozichat.group.repository.GroupInviteLinkRepository;
import com.ozichat.group.repository.GroupMetadataRepository;
import com.ozichat.group.service.GroupService;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {

    private static final int MAX_PINNED_MESSAGES = 5;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final GroupMetadataRepository groupMetadataRepository;
    private final GroupInviteLinkRepository inviteLinkRepository;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public GroupResponse createGroup(Long creatorId, CreateGroupRequest request) {
        Conversation conversation = conversationRepository.save(
                Conversation.builder().type(Conversation.Type.GROUP).build()
        );

        memberRepository.save(ConversationMember.builder()
                .conversation(conversation)
                .userId(creatorId)
                .role(ConversationMember.Role.OWNER)
                .build());

        for (Long memberId : request.getMemberIds()) {
            if (!memberId.equals(creatorId)) {
                userRepository.findByIdAndDeletedAtIsNull(memberId)
                        .ifPresent(u -> memberRepository.save(ConversationMember.builder()
                                .conversation(conversation)
                                .userId(memberId)
                                .role(ConversationMember.Role.MEMBER)
                                .build()));
            }
        }

        GroupMetadata metadata = groupMetadataRepository.save(GroupMetadata.builder()
                .conversationId(conversation.getId())
                .groupName(request.getGroupName())
                .groupDescription(request.getGroupDescription())
                .groupAvatarUrl(request.getGroupAvatarUrl())
                .createdBy(creatorId)
                .build());

        log.info("Group '{}' created — conversationId={}, by userId={}", request.getGroupName(), conversation.getId(), creatorId);
        return buildResponse(metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long conversationId, Long requesterId) {
        assertMember(conversationId, requesterId);
        return buildResponse(getMetadataOrThrow(conversationId));
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(Long conversationId, Long requesterId, UpdateGroupRequest request) {
        assertAdminOrOwner(conversationId, requesterId);
        GroupMetadata metadata = getMetadataOrThrow(conversationId);

        if (request.getGroupName() != null) metadata.setGroupName(request.getGroupName());
        if (request.getGroupDescription() != null) metadata.setGroupDescription(request.getGroupDescription());
        if (request.getGroupAvatarUrl() != null) metadata.setGroupAvatarUrl(request.getGroupAvatarUrl());
        if (request.getOnlyAdminsCanSend() != null) metadata.setOnlyAdminsCanSend(request.getOnlyAdminsCanSend());
        if (request.getOnlyAdminsCanEditInfo() != null) metadata.setOnlyAdminsCanEditInfo(request.getOnlyAdminsCanEditInfo());

        return buildResponse(groupMetadataRepository.save(metadata));
    }

    @Override
    @Transactional
    public void addMembers(Long conversationId, Long requesterId, List<Long> userIds) {
        assertAdminOrOwner(conversationId, requesterId);

        GroupMetadata metadata = getMetadataOrThrow(conversationId);
        long currentCount = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).size();

        if (currentCount + userIds.size() > metadata.getMaxMembers()) {
            throw new BusinessException("Adding these members would exceed the group limit of " + metadata.getMaxMembers());
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        for (Long userId : userIds) {
            boolean alreadyMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
            if (!alreadyMember) {
                userRepository.findByIdAndDeletedAtIsNull(userId).ifPresent(u ->
                        memberRepository.save(ConversationMember.builder()
                                .conversation(conversation)
                                .userId(userId)
                                .role(ConversationMember.Role.MEMBER)
                                .build()));
            }
        }
    }

    @Override
    @Transactional
    public void removeMember(Long conversationId, Long requesterId, Long targetUserId) {
        if (!requesterId.equals(targetUserId)) {
            assertAdminOrOwner(conversationId, requesterId);
        }

        ConversationMember target = memberRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

        if (target.getRole() == ConversationMember.Role.OWNER && !requesterId.equals(targetUserId)) {
            throw new BusinessException("Cannot remove the group owner");
        }

        target.setLeftAt(Instant.now());
        memberRepository.save(target);
        log.info("User {} removed from group {}", targetUserId, conversationId);
    }

    @Override
    @Transactional
    public void promoteMember(Long conversationId, Long requesterId, Long targetUserId, String role) {
        assertOwner(conversationId, requesterId);

        ConversationMember target = memberRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        ConversationMember.Role newRole;
        try {
            newRole = ConversationMember.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid role. Use ADMIN or MEMBER");
        }

        if (newRole == ConversationMember.Role.OWNER) {
            throw new BusinessException("Cannot assign OWNER role via this endpoint");
        }

        target.setRole(newRole);
        memberRepository.save(target);
    }

    @Override
    @Transactional
    public String generateInviteLink(Long conversationId, Long requesterId) {
        assertAdminOrOwner(conversationId, requesterId);

        inviteLinkRepository.findByConversationIdAndIsRevokedFalse(conversationId)
                .ifPresent(link -> {
                    link.setIsRevoked(true);
                    inviteLinkRepository.save(link);
                });

        String token = UUID.randomUUID().toString().replace("-", "");
        inviteLinkRepository.save(GroupInviteLink.builder()
                .conversationId(conversationId)
                .token(token)
                .createdBy(requesterId)
                .build());

        return token;
    }

    @Override
    @Transactional
    public GroupResponse joinViaInviteLink(Long userId, String token) {
        GroupInviteLink link = inviteLinkRepository.findByTokenAndIsRevokedFalse(token)
                .orElseThrow(() -> new BusinessException("Invalid or expired invite link", HttpStatus.NOT_FOUND));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("This invite link has expired");
        }
        if (link.getMaxUses() != null && link.getUseCount() >= link.getMaxUses()) {
            throw new BusinessException("This invite link has reached its usage limit");
        }

        Long conversationId = link.getConversationId();
        boolean alreadyMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
        if (!alreadyMember) {
            GroupMetadata metadata = getMetadataOrThrow(conversationId);
            long currentCount = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).size();
            if (currentCount >= metadata.getMaxMembers()) {
                throw new BusinessException("This group is full");
            }

            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
            memberRepository.save(ConversationMember.builder()
                    .conversation(conversation)
                    .userId(userId)
                    .role(ConversationMember.Role.MEMBER)
                    .build());

            link.setUseCount(link.getUseCount() + 1);
            inviteLinkRepository.save(link);
        }

        return buildResponse(getMetadataOrThrow(conversationId));
    }

    @Override
    @Transactional
    public void revokeInviteLink(Long conversationId, Long requesterId) {
        assertAdminOrOwner(conversationId, requesterId);
        inviteLinkRepository.findByConversationIdAndIsRevokedFalse(conversationId)
                .ifPresent(link -> {
                    link.setIsRevoked(true);
                    inviteLinkRepository.save(link);
                });
    }

    // ── Sprint 3: Announcement ────────────────────

    @Override
    @Transactional
    public GroupResponse setAnnouncement(Long conversationId, Long requesterId, SetAnnouncementRequest request) {
        assertAdminOrOwner(conversationId, requesterId);
        GroupMetadata metadata = getMetadataOrThrow(conversationId);

        String text = request.getText();
        if (text == null || text.isBlank()) {
            // Clear announcement
            metadata.setAnnouncementText(null);
            metadata.setAnnouncementAt(null);
            metadata.setAnnouncementBy(null);
        } else {
            metadata.setAnnouncementText(text.trim());
            metadata.setAnnouncementAt(Instant.now());
            metadata.setAnnouncementBy(requesterId);
        }

        return buildResponse(groupMetadataRepository.save(metadata));
    }

    // ── Sprint 3: Pinned Messages ─────────────────

    @Override
    @Transactional
    public PinnedMessageResponse pinMessage(Long conversationId, Long requesterId, String messageId) {
        assertAdminOrOwner(conversationId, requesterId);

        if (pinnedMessageRepository.existsByConversationIdAndMessageId(conversationId, messageId)) {
            throw new DuplicateResourceException("Message is already pinned");
        }

        long pinnedCount = pinnedMessageRepository.countByConversationId(conversationId);
        if (pinnedCount >= MAX_PINNED_MESSAGES) {
            throw new BusinessException("Cannot pin more than " + MAX_PINNED_MESSAGES + " messages. Unpin one first.");
        }

        PinnedMessage pinned = pinnedMessageRepository.save(PinnedMessage.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .pinnedBy(requesterId)
                .build());

        log.info("Message {} pinned in conversation {} by user {}", messageId, conversationId, requesterId);
        return PinnedMessageResponse.from(pinned);
    }

    @Override
    @Transactional
    public void unpinMessage(Long conversationId, Long requesterId, String messageId) {
        assertAdminOrOwner(conversationId, requesterId);

        if (!pinnedMessageRepository.existsByConversationIdAndMessageId(conversationId, messageId)) {
            throw new ResourceNotFoundException("Pinned message not found");
        }

        pinnedMessageRepository.deleteByConversationIdAndMessageId(conversationId, messageId);
        log.info("Message {} unpinned from conversation {} by user {}", messageId, conversationId, requesterId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PinnedMessageResponse> getPinnedMessages(Long conversationId, Long requesterId) {
        assertMember(conversationId, requesterId);
        return pinnedMessageRepository.findByConversationIdOrderByPinnedAtDesc(conversationId)
                .stream()
                .map(PinnedMessageResponse::from)
                .collect(Collectors.toList());
    }

    // ── Sprint 3: Paginated Member List ──────────

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MemberResponse> getMembers(Long conversationId, Long requesterId, int page, int size) {
        assertMember(conversationId, requesterId);

        int safeSize = Math.min(size, 50);
        PageRequest pageRequest = PageRequest.of(page, safeSize,
                Sort.by(Sort.Direction.ASC, "joinedAt"));

        Page<ConversationMember> memberPage = memberRepository
                .findByConversationIdAndLeftAtIsNull(conversationId, pageRequest);

        List<MemberResponse> content = memberPage.getContent().stream().map(m -> {
            User user = userRepository.findByIdAndDeletedAtIsNull(m.getUserId()).orElse(null);
            return MemberResponse.from(m,
                    user != null ? user.getDisplayName() : "Unknown",
                    user != null ? user.getAvatarUrl() : null);
        }).collect(Collectors.toList());

        return PagedResponse.<MemberResponse>builder()
                .content(content)
                .page(memberPage.getNumber())
                .size(memberPage.getSize())
                .totalElements(memberPage.getTotalElements())
                .totalPages(memberPage.getTotalPages())
                .hasNext(memberPage.hasNext())
                .hasPrevious(memberPage.hasPrevious())
                .build();
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private GroupMetadata getMetadataOrThrow(Long conversationId) {
        return groupMetadataRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", conversationId));
    }

    private void assertMember(Long conversationId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new BusinessException("Not a member of this group", HttpStatus.FORBIDDEN);
        }
    }

    private void assertAdminOrOwner(Long conversationId, Long userId) {
        ConversationMember member = memberRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException("Not a member of this group", HttpStatus.FORBIDDEN));
        if (member.getRole() == ConversationMember.Role.MEMBER) {
            throw new BusinessException("Admin or Owner access required", HttpStatus.FORBIDDEN);
        }
    }

    private void assertOwner(Long conversationId, Long userId) {
        ConversationMember member = memberRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException("Not a member of this group", HttpStatus.FORBIDDEN));
        if (member.getRole() != ConversationMember.Role.OWNER) {
            throw new BusinessException("Owner access required", HttpStatus.FORBIDDEN);
        }
    }

    private GroupResponse buildResponse(GroupMetadata metadata) {
        List<ConversationMember> members = memberRepository
                .findByConversationIdAndLeftAtIsNull(metadata.getConversationId());

        List<GroupResponse.MemberInfo> memberInfos = members.stream().map(m -> {
            User user = userRepository.findByIdAndDeletedAtIsNull(m.getUserId()).orElse(null);
            return GroupResponse.MemberInfo.builder()
                    .userId(m.getUserId())
                    .displayName(user != null ? user.getDisplayName() : "Unknown")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .role(m.getRole().name())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).collect(Collectors.toList());

        return GroupResponse.builder()
                .conversationId(metadata.getConversationId())
                .groupName(metadata.getGroupName())
                .groupDescription(metadata.getGroupDescription())
                .groupAvatarUrl(metadata.getGroupAvatarUrl())
                .maxMembers(metadata.getMaxMembers())
                .currentMemberCount(members.size())
                .onlyAdminsCanSend(metadata.getOnlyAdminsCanSend())
                .onlyAdminsCanEditInfo(metadata.getOnlyAdminsCanEditInfo())
                .announcementText(metadata.getAnnouncementText())
                .announcementAt(metadata.getAnnouncementAt())
                .announcementBy(metadata.getAnnouncementBy())
                .createdBy(metadata.getCreatedBy())
                .members(memberInfos)
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .build();
    }
}
