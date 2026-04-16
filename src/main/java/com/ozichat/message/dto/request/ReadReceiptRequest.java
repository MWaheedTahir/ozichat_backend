package com.ozichat.message.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReadReceiptRequest {
    @NotNull
    private Long conversationId;
    @NotNull
    private String lastReadMessageId;
}
