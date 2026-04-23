package com.ozichat.call.dto.signaling;

import com.ozichat.call.document.CallSession;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Sent by the caller over WebSocket to start a call.
 * Client sends to: /app/call/initiate
 */
@Data
public class InitiateCallRequest {

    @NotNull(message = "calleeId is required")
    private Long calleeId;

    @NotNull(message = "type is required (AUDIO or VIDEO)")
    private CallSession.CallType type;
}
