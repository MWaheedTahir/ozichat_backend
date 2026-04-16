package com.ozichat.message.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.message.document.Message;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse {

    private String id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private String type;
    private String status;
    private String replyTo;
    private String tempId;
    private Boolean isEdited;
    private Instant editedAt;
    private Boolean isDeletedForEveryone;
    private Instant readAt;
    private Message.MediaAttachment media;
    private Instant createdAt;
    private Instant updatedAt;

    public static MessageResponse from(Message msg) {
        return MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .content(msg.getIsDeletedForEveryone() ? null : msg.getContent())
                .type(msg.getType().name())
                .status(msg.getStatus().name())
                .replyTo(msg.getReplyTo())
                .isEdited(msg.getIsEdited())
                .editedAt(msg.getEditedAt())
                .isDeletedForEveryone(msg.getIsDeletedForEveryone())
                .readAt(msg.getReadAt())
                .media(msg.getIsDeletedForEveryone() ? null : msg.getMedia())
                .createdAt(msg.getCreatedAt())
                .updatedAt(msg.getUpdatedAt())
                .build();
    }
}
