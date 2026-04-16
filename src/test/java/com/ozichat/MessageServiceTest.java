package com.ozichat;

import com.ozichat.conversation.repository.ConversationMemberRepository;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.message.document.Message;
import com.ozichat.message.dto.request.SendMessageRequest;
import com.ozichat.message.dto.response.MessageResponse;
import com.ozichat.message.repository.MessageRepository;
import com.ozichat.message.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ConversationMemberRepository memberRepository;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageServiceImpl(messageRepository, memberRepository);
    }

    @Test
    void saveMessage_persistsAndReturnsMessage() {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(10L);
        req.setContent("Hello!");
        req.setTempId("temp-uuid-001");

        Message saved = Message.builder()
                .id("mongo-id-001")
                .conversationId(10L)
                .senderId(1L)
                .content("Hello!")
                .type(Message.MessageType.TEXT)
                .status(Message.MessageStatus.SENT)
                .tempId("temp-uuid-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(saved);

        Message result = messageService.saveMessage(10L, 1L, req);

        assertThat(result.getId()).isEqualTo("mongo-id-001");
        assertThat(result.getContent()).isEqualTo("Hello!");
        assertThat(result.getSenderId()).isEqualTo(1L);
        assertThat(result.getTempId()).isEqualTo("temp-uuid-001");
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void editMessage_byNonSender_throws() {
        Message msg = Message.builder()
                .id("msg-1").senderId(1L).content("original")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .isEdited(false).isDeletedForEveryone(false).build();

        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> messageService.editMessage("msg-1", 99L, "hacked"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only edit your own messages");
    }

    @Test
    void editMessage_afterWindow_throws() {
        Message msg = Message.builder()
                .id("msg-2").senderId(5L).content("old")
                // created 20 minutes ago — outside 15-minute window
                .createdAt(Instant.now().minusSeconds(1200))
                .updatedAt(Instant.now().minusSeconds(1200))
                .isEdited(false).isDeletedForEveryone(false).build();

        when(messageRepository.findById("msg-2")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> messageService.editMessage("msg-2", 5L, "new content"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Edit window");
    }

    @Test
    void editMessage_bySenderWithinWindow_succeeds() {
        Message msg = Message.builder()
                .id("msg-3").senderId(7L).content("original")
                .createdAt(Instant.now().minusSeconds(60)) // 1 minute ago — within window
                .updatedAt(Instant.now().minusSeconds(60))
                .isEdited(false).isDeletedForEveryone(false).build();

        when(messageRepository.findById("msg-3")).thenReturn(Optional.of(msg));
        when(messageRepository.save(any())).thenReturn(msg);

        MessageResponse result = messageService.editMessage("msg-3", 7L, "updated content");
        assertThat(result).isNotNull();
        verify(messageRepository).save(any());
    }

    @Test
    void deleteForEveryone_byNonSender_throws() {
        Message msg = Message.builder()
                .id("msg-4").senderId(1L).content("hi")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .isEdited(false).isDeletedForEveryone(false).build();

        when(messageRepository.findById("msg-4")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> messageService.deleteMessage("msg-4", 99L, "FOR_EVERYONE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only the sender");
    }

    @Test
    void deleteMessage_notFound_throws() {
        when(messageRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.deleteMessage("ghost", 1L, "FOR_ME"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
