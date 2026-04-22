package com.ozichat.message.service.impl;

import com.ozichat.common.CursorPagedResponse;
import com.ozichat.conversation.repository.ConversationMemberRepository;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.message.document.Message;
import com.ozichat.message.dto.request.MediaAttachmentRequest;
import com.ozichat.message.dto.request.SendMessageRequest;
import com.ozichat.message.dto.response.MessageResponse;
import com.ozichat.message.repository.MessageRepository;
import com.ozichat.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository memberRepository;

    @Override
    public Message saveMessage(Long conversationId, Long senderId, SendMessageRequest request) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(request.getContent())
                .type(request.getType() != null ? request.getType() : Message.MessageType.TEXT)
                .status(Message.MessageStatus.SENT)
                .tempId(request.getTempId())
                .replyTo(request.getReplyTo())
                .media(toMediaAttachment(request.getMedia()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        log.debug("Message {} saved in conversation {} by sender {} type={}",
                saved.getId(), conversationId, senderId, saved.getType());
        return saved;
    }

    /**
     * Maps the incoming {@link MediaAttachmentRequest} DTO to the persisted
     * {@link Message.MediaAttachment} embedded document.
     * Returns {@code null} for plain TEXT messages that carry no attachment.
     */
    private Message.MediaAttachment toMediaAttachment(MediaAttachmentRequest req) {
        if (req == null) return null;
        return Message.MediaAttachment.builder()
                .s3Key(req.getS3Key())
                .url(req.getUrl())
                .thumbnailUrl(req.getThumbnailUrl())
                .mimeType(req.getMimeType())
                .fileSize(req.getFileSize())
                .fileName(req.getFileName())
                .duration(req.getDuration())
                .width(req.getWidth())
                .height(req.getHeight())
                .build();
    }

    @Override
    public CursorPagedResponse<MessageResponse> getMessageHistory(Long conversationId, Long userId,
                                                                   String cursor, int limit, String direction) {
        // Verify membership
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new BusinessException("Not a member of this conversation", HttpStatus.FORBIDDEN);
        }

        int fetchLimit = Math.min(limit, 100);
        List<Message> messages;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if (cursor == null || cursor.isBlank()) {
            // First page: most recent messages
            messages = messageRepository
                    .findByConversationIdAndIsDeletedForEveryoneFalse(conversationId, sort)
                    .stream().limit(fetchLimit + 1).toList();
        } else {
            ObjectId cursorId = new ObjectId(cursor);
            if ("AFTER".equalsIgnoreCase(direction)) {
                messages = messageRepository.findAfterCursor(conversationId, cursorId,
                        Sort.by(Sort.Direction.ASC, "createdAt"))
                        .stream().limit(fetchLimit + 1).toList();
            } else {
                messages = messageRepository.findBeforeCursor(conversationId, cursorId, sort)
                        .stream().limit(fetchLimit + 1).toList();
            }
        }

        boolean hasMore = messages.size() > fetchLimit;
        List<Message> page = hasMore ? messages.subList(0, fetchLimit) : messages;

        String nextCursor = (hasMore && !page.isEmpty()) ? page.get(page.size() - 1).getId() : null;

        return CursorPagedResponse.<MessageResponse>builder()
                .content(page.stream().map(MessageResponse::from).toList())
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(fetchLimit)
                .build();
    }

    @Override
    public List<MessageResponse> getMissedMessages(Long userId, Instant since) {
        List<Long> conversationIds = memberRepository.findConversationIdsByUserId(userId);
        if (conversationIds.isEmpty()) return List.of();

        return messageRepository.findMissedMessages(conversationIds, since,
                Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Override
    public MessageResponse editMessage(String messageId, Long userId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!message.getSenderId().equals(userId)) {
            throw new BusinessException("You can only edit your own messages", HttpStatus.FORBIDDEN);
        }

        // 15-minute edit window
        Instant editDeadline = message.getCreatedAt().plusSeconds(900);
        if (Instant.now().isAfter(editDeadline)) {
            throw new BusinessException("Edit window has expired (15 minutes)", HttpStatus.BAD_REQUEST);
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        message.setEditedAt(Instant.now());
        message.setUpdatedAt(Instant.now());

        return MessageResponse.from(messageRepository.save(message));
    }

    @Override
    public void deleteMessage(String messageId, Long userId, String scope) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if ("FOR_EVERYONE".equalsIgnoreCase(scope)) {
            if (!message.getSenderId().equals(userId)) {
                throw new BusinessException("Only the sender can delete for everyone", HttpStatus.FORBIDDEN);
            }
            message.setIsDeletedForEveryone(true);
            message.setContent(null);
            message.setMedia(null);
        }
        // FOR_ME: handled client-side; we do nothing server-side for now
        message.setUpdatedAt(Instant.now());
        messageRepository.save(message);
    }
}
