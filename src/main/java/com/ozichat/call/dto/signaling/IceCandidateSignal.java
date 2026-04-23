package com.ozichat.call.dto.signaling;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Carries a single ICE candidate from one peer to the other.
 * ICE candidates are produced asynchronously by the browser's ICE agent
 * after createOffer()/createAnswer() and must be forwarded in real time.
 *
 * Client sends to: /app/call/ice-candidate
 */
@Data
public class IceCandidateSignal {

    @NotBlank(message = "callId is required")
    private String callId;

    /**
     * The ICE candidate string.
     * Example: "candidate:842163049 1 udp 1677729535 192.168.1.5 54400 typ srflx ..."
     * Null signals end-of-candidates (trickle ICE termination).
     */
    private String candidate;

    /** Media stream identification — e.g. "0", "1", "audio", "video". */
    private String sdpMid;

    /** Index of the media description in the SDP this candidate is associated with. */
    private Integer sdpMLineIndex;
}
