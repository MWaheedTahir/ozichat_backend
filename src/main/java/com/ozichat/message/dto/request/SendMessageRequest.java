package com.ozichat.message.dto.request;

import com.ozichat.message.document.Message;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "conversationId is required")
    private Long conversationId;

    private String content;

    private Message.MessageType type = Message.MessageType.TEXT;

    private String tempId;

    private String replyTo;
}
