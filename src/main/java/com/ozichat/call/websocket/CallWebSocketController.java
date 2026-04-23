package com.ozichat.call.websocket;

import com.ozichat.call.dto.signaling.*;
import com.ozichat.call.service.CallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for the WebRTC signaling plane.
 *
 * All endpoints are under the /app prefix (configured in WebSocketConfig).
 * JWT authentication is enforced at CONNECT time by JwtChannelInterceptor —
 * every message arriving here is already authenticated.
 *
 * Client destination                 → Method
 * ──────────────────────────────────────────────────
 * /app/call/initiate                 → initiateCall
 * /app/call/accept                   → acceptCall
 * /app/call/reject                   → rejectCall
 * /app/call/cancel                   → cancelCall
 * /app/call/end                      → endCall
 * /app/call/offer                    → relayOffer
 * /app/call/answer                   → relayAnswer
 * /app/call/ice-candidate            → relayIceCandidate
 *
 * Server pushes all events to: /user/{userId}/queue/call
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CallWebSocketController {

    private final CallService callService;

    // ── Call lifecycle ─────────────────────────────────────────────────────────

    @MessageMapping("/call/initiate")
    public void initiateCall(@Payload @Valid InitiateCallRequest request,
                             Principal principal) {
        Long callerId = extractUserId(principal);
        callService.initiateCall(callerId, request);
    }

    @MessageMapping("/call/accept")
    public void acceptCall(@Payload @Valid CallActionRequest request,
                           Principal principal) {
        Long calleeId = extractUserId(principal);
        callService.acceptCall(calleeId, request);
    }

    @MessageMapping("/call/reject")
    public void rejectCall(@Payload @Valid CallActionRequest request,
                           Principal principal) {
        Long calleeId = extractUserId(principal);
        callService.rejectCall(calleeId, request);
    }

    @MessageMapping("/call/cancel")
    public void cancelCall(@Payload @Valid CallActionRequest request,
                           Principal principal) {
        Long callerId = extractUserId(principal);
        callService.cancelCall(callerId, request);
    }

    @MessageMapping("/call/end")
    public void endCall(@Payload @Valid CallActionRequest request,
                        Principal principal) {
        Long userId = extractUserId(principal);
        callService.endCall(userId, request);
    }

    // ── WebRTC signaling relay ─────────────────────────────────────────────────

    /**
     * Caller → /app/call/offer
     * Backend validates membership and relays SDP offer to callee.
     */
    @MessageMapping("/call/offer")
    public void relayOffer(@Payload @Valid WebRtcSignal signal,
                           Principal principal) {
        Long senderId = extractUserId(principal);
        callService.relayOffer(senderId, signal);
    }

    /**
     * Callee → /app/call/answer
     * Backend validates membership and relays SDP answer to caller.
     */
    @MessageMapping("/call/answer")
    public void relayAnswer(@Payload @Valid WebRtcSignal signal,
                            Principal principal) {
        Long senderId = extractUserId(principal);
        callService.relayAnswer(senderId, signal);
    }

    /**
     * Either peer → /app/call/ice-candidate
     * ICE candidates are relayed as fast as possible (trickle ICE).
     */
    @MessageMapping("/call/ice-candidate")
    public void relayIceCandidate(@Payload @Valid IceCandidateSignal signal,
                                  Principal principal) {
        Long senderId = extractUserId(principal);
        callService.relayIceCandidate(senderId, signal);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private Long extractUserId(Principal principal) {
        if (principal == null) throw new AccessDeniedException("Not authenticated");
        return Long.parseLong(principal.getName());
    }
}
