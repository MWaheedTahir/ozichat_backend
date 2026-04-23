package com.ozichat.call.dto.signaling;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Generic action on an existing call — accept, reject, cancel, or end.
 * Client sends to: /app/call/accept | /app/call/reject | /app/call/cancel | /app/call/end
 */
@Data
public class CallActionRequest {

    @NotBlank(message = "callId is required")
    private String callId;
}
