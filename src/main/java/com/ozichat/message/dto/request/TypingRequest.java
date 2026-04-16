package com.ozichat.message.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TypingRequest {
    @NotNull
    private Long conversationId;
    private boolean isTyping = true;
}
