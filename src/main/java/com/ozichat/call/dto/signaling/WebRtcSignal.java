package com.ozichat.call.dto.signaling;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Carries a WebRTC SDP offer or answer between peers via the signaling server.
 * The backend validates membership then relays without parsing the SDP.
 *
 * Client sends to: /app/call/offer  OR  /app/call/answer
 */
@Data
public class WebRtcSignal {

    @NotBlank(message = "callId is required")
    private String callId;

    /**
     * Full SDP string produced by RTCPeerConnection.createOffer() or createAnswer().
     * Example: "v=0\r\no=- 46117317 2 IN IP4 127.0.0.1\r\n..."
     */
    @NotBlank(message = "sdp is required")
    private String sdp;

    /**
     * SDP type: "offer" or "answer".
     * Validated server-side against the endpoint used.
     */
    @NotBlank(message = "sdpType is required")
    private String sdpType;
}
