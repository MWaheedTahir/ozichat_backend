package com.ozichat.message.websocket;

import com.ozichat.conversation.service.ConversationService;
import com.ozichat.message.document.Message;
import com.ozichat.message.dto.request.ReadReceiptRequest;
import com.ozichat.message.dto.request.SendMessageRequest;
import com.ozichat.message.dto.request.TypingRequest;
import com.ozichat.message.dto.response.MessageResponse;
import com.ozichat.message.dto.response.NotificationResponse;
import com.ozichat.message.service.MessageService;
import com.ozichat.notification.service.PushNotificationService;
import com.ozichat.presence.service.PresenceService;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────
    // SEND MESSAGE
    // Client → /app/chat/send
    // ──────────────────────────────────────────────
    @MessageMapping("/chat/send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        Long senderId = extractUserId(principal);

        // 1. Validate membership — NEVER trust the client's userId
        if (!conversationService.isMember(request.getConversationId(), senderId)) {
            throw new AccessDeniedException("Not a member of this conversation");
        }

        // 2. Save message to MongoDB
        Message saved = messageService.saveMessage(request.getConversationId(), senderId, request);

        // 3. Resolve sender display name (used for push notification title)
        String senderName = userRepository.findByIdAndDeletedAtIsNull(senderId)
                .map(User::getDisplayName)
                .orElse("Someone");

        // 4. Build response and ACK to sender (swap tempId → real id)
        MessageResponse ack = MessageResponse.from(saved);
        ack.setTempId(request.getTempId());
        messagingTemplate.convertAndSendToUser(
                senderId.toString(), "/queue/messages", ack
        );

        // 5. Fan-out to other participants
        List<Long> recipientIds = conversationService.getOtherMemberIds(request.getConversationId(), senderId);
        MessageResponse delivery = MessageResponse.from(saved);

        for (Long recipientId : recipientIds) {
            // Deliver via WebSocket
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(), "/queue/messages", delivery
            );

            if (presenceService.isOnline(recipientId)) {
                // 5a. Recipient is online → send DELIVERED receipt back to sender
                messagingTemplate.convertAndSendToUser(
                        senderId.toString(), "/queue/notifications",
                        NotificationResponse.delivered(saved.getId(), recipientId, request.getConversationId())
                );
            } else {
                // 5b. Recipient is offline → fire push notification (FCM/APNs)
                pushNotificationService.sendMessageNotification(recipientId, saved, senderName);
            }
        }

        log.info("Message {} sent in conversation {} by user {}", saved.getId(), request.getConversationId(), senderId);
    }

    // ──────────────────────────────────────────────
    // READ RECEIPT
    // Client → /app/chat/read
    // ──────────────────────────────────────────────
    @MessageMapping("/chat/read")
    public void markAsRead(@Payload ReadReceiptRequest request, Principal principal) {
        Long userId = extractUserId(principal);

        if (!conversationService.isMember(request.getConversationId(), userId)) {
            throw new AccessDeniedException("Not a member of this conversation");
        }

        // Update cursor in MySQL
        conversationService.updateLastReadMessage(
                request.getConversationId(), userId, request.getLastReadMessageId()
        );

        // Notify all other participants about the read event (blue tick)
        List<Long> otherMembers = conversationService.getOtherMemberIds(request.getConversationId(), userId);
        for (Long memberId : otherMembers) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(), "/queue/notifications",
                    NotificationResponse.read(request.getLastReadMessageId(), userId, request.getConversationId())
            );
        }

        log.debug("Read receipt: user {} read up to message {} in conversation {}",
                userId, request.getLastReadMessageId(), request.getConversationId());
    }

    // ──────────────────────────────────────────────
    // TYPING INDICATOR
    // Client → /app/chat/typing
    // ──────────────────────────────────────────────
    @MessageMapping("/chat/typing")
    public void typingIndicator(@Payload TypingRequest request, Principal principal) {
        Long userId = extractUserId(principal);

        if (!conversationService.isMember(request.getConversationId(), userId)) {
            return; // Silently reject
        }

        // Store in Redis with 4s TTL — never in database
        if (request.isTyping()) {
            presenceService.setTyping(request.getConversationId(), userId);
        } else {
            presenceService.clearTyping(request.getConversationId(), userId);
        }

        // Notify other participants
        List<Long> otherMembers = conversationService.getOtherMemberIds(request.getConversationId(), userId);
        for (Long memberId : otherMembers) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(), "/queue/notifications",
                    NotificationResponse.typing(request.getConversationId(), userId, request.isTyping())
            );
        }
    }

    private Long extractUserId(Principal principal) {
        if (principal == null) throw new AccessDeniedException("Not authenticated");
        return Long.parseLong(principal.getName());
    }
}
