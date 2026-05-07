package com.ozichat.conversation.service.impl;

import com.ozichat.conversation.dto.response.ConversationListResponse;
import com.ozichat.conversation.dto.response.ConversationResponse;
import com.ozichat.conversation.entity.Conversation;
import com.ozichat.conversation.entity.ConversationMember;
import com.ozichat.conversation.repository.ConversationMemberRepository;
import com.ozichat.conversation.repository.ConversationRepository;
import com.ozichat.conversation.service.ConversationService;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.group.entity.GroupMetadata;
import com.ozichat.group.repository.GroupMetadataRepository;
import com.ozichat.message.document.Message;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupMetadataRepository groupRepo;

    @Override
    @Transactional
    public ConversationResponse getOrCreateDirect(Long requesterId, Long targetId) {
        // Validate target user exists
        userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetId));

        // Check if conversation already exists
        return conversationRepository.findDirectConversation(requesterId, targetId)
                .map(conv -> buildResponse(conv, requesterId))
                .orElseGet(() -> {
                    // Create new conversation
                    Conversation conv = conversationRepository.save(
                            Conversation.builder().type(Conversation.Type.DIRECT).build());

                    memberRepository.save(ConversationMember.builder()
                            .conversation(conv).userId(requesterId).role(ConversationMember.Role.MEMBER).build());
                    memberRepository.save(ConversationMember.builder()
                            .conversation(conv).userId(targetId).role(ConversationMember.Role.MEMBER).build());

                    log.info("Created DIRECT conversation {} between users {} and {}", conv.getId(), requesterId, targetId);
                    return buildResponse(conv, requesterId);
                });
    }

    @Override
    @Transactional
    public ConversationResponse getOrCreateDirectByEmail(Long requesterId, String targetEmail) {
        User target = userRepository.findByEmailAndDeletedAtIsNull(targetEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + targetEmail + " not found"));
        return getOrCreateDirect(requesterId, target.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationListResponse> getUserConversations(Long userId) {
        return conversationRepository.findAllByUserId(userId)
                .stream()
                .map(conv -> buildListResponse(conv, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        return buildResponse(conv, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long conversationId, Long userId) {
        return memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getOtherMemberIds(Long conversationId, Long userId) {
        return memberRepository.findOtherMemberIds(conversationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getUserConversationIds(Long userId) {
        return memberRepository.findConversationIdsByUserId(userId);
    }

    @Override
    @Transactional
    public void updateLastReadMessage(Long conversationId, Long userId, String lastReadMessageId) {
        memberRepository.findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .ifPresent(member -> {
                    member.setLastReadMessageId(lastReadMessageId);
                    member.setLastReadAt(Instant.now());
                    memberRepository.save(member);
                });
    }

    private ConversationResponse buildResponse(Conversation conv, Long requesterId) {
        List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(conv.getId());

        List<ConversationResponse.MemberInfo> memberInfos = members.stream()
                .map(m -> {
                    User user = userRepository.findByIdAndDeletedAtIsNull(m.getUserId()).orElse(null);
                    return ConversationResponse.MemberInfo.builder()
                            .userId(m.getUserId())
                            .displayName(user != null ? user.getDisplayName() : "Unknown")
                            .avatarUrl(user != null ? user.getAvatarUrl() : null)
                            .role(m.getRole().name())
                            .lastSeenAt(user != null ? user.getLastSeenAt() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType().name())
                .members(memberInfos)
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    private ConversationListResponse buildListResponse(Conversation conv, Long requesterId) {

        String displayName = null;
        String avatarUrl = null;

        if (conv.getType() == Conversation.Type.GROUP) {
            GroupMetadata group = groupRepo.findByConversationId(conv.getId()).orElse(null);
            if(group!=null){
                displayName = group.getGroupName();
                avatarUrl = group.getGroupAvatarUrl();
            }

        } else {

            ConversationMember otherMember = memberRepository
                    .findByConversationIdAndLeftAtIsNull(conv.getId())
                    .stream()
                    .filter(m -> !m.getUserId().equals(requesterId))
                    .findFirst()
                    .orElse(null);

            if (otherMember != null) {
                User user = userRepository
                        .findByIdAndDeletedAtIsNull(otherMember.getUserId())
                        .orElse(null);

                displayName = user != null ? user.getDisplayName() : "Unknown";
                avatarUrl = user != null ? user.getAvatarUrl() : null;
            }
        }

        return ConversationListResponse.builder()
                .conversationId(conv.getId())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .updatedAt(conv.getUpdatedAt())
                .type(conv.getType().name())
                .build();
    }
}
