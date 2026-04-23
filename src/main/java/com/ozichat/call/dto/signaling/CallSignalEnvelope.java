package com.ozichat.call.dto.signaling;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Server → Client signal envelope.
 * All call events are delivered to the client via /user/queue/call
 * wrapped in this envelope so the client uses a single switch on {@code event}.
 *
 * Events:
 *   incoming_call   — callee receives this when someone calls them
 *   call_accepted   — caller receives this when callee picks up
 *   call_rejected   — caller receives this when callee declines
 *   call_cancelled  — callee receives this when caller hangs up before answer
 *   call_ended      — both receive this on normal hang-up
 *   call_missed     — callee receives this when call times out
 *   offer           — relay of WebRTC SDP offer
 *   answer          — relay of WebRTC SDP answer
 *   ice_candidate   — relay of ICE candidate
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSignalEnvelope {

    /** Event name — see Javadoc for valid values. */
    private String event;

    private String callId;

    /** The user who sent this signal. */
    private Long fromUserId;
    private String fromUserName;
    private String fromUserAvatarUrl;

    /** AUDIO or VIDEO. */
    private String callType;

    // ── WebRTC payloads (only set for offer / answer / ice_candidate) ────────

    private String sdp;
    private String sdpType;

    /** ICE candidate string — null signals end-of-candidates. */
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;

    /** Human-readable termination reason (optional). */
    private String reason;

    private Instant timestamp;
}
