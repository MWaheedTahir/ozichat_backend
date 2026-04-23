package com.ozichat.call.service;

import com.ozichat.call.document.CallSession;
import com.ozichat.call.dto.response.CallSessionResponse;
import com.ozichat.call.dto.signaling.*;

import java.util.List;

public interface CallService {

    /**
     * Caller initiates a call. Creates a CallSession in MongoDB and Redis,
     * then pushes an incoming_call signal to the callee's personal queue.
     */
    String initiateCall(Long callerId, InitiateCallRequest request);

    /**
     * Callee accepts the call. Transitions state RINGING → CONNECTED.
     * Pushes call_accepted to the caller.
     */
    void acceptCall(Long calleeId, CallActionRequest request);

    /**
     * Callee rejects the call. Transitions state → REJECTED.
     * Pushes call_rejected to the caller.
     */
    void rejectCall(Long calleeId, CallActionRequest request);

    /**
     * Caller cancels before callee answers. Transitions state → CANCELLED.
     * Pushes call_cancelled to the callee.
     */
    void cancelCall(Long callerId, CallActionRequest request);

    /**
     * Either party ends the connected call. Transitions state → ENDED.
     * Pushes call_ended to the other party.
     */
    void endCall(Long userId, CallActionRequest request);

    /**
     * Relay a WebRTC SDP offer from caller to callee.
     * Backend validates membership; does NOT parse SDP content.
     */
    void relayOffer(Long senderId, WebRtcSignal signal);

    /**
     * Relay a WebRTC SDP answer from callee to caller.
     */
    void relayAnswer(Long senderId, WebRtcSignal signal);

    /**
     * Relay a single ICE candidate to the other peer.
     */
    void relayIceCandidate(Long senderId, IceCandidateSignal signal);

    /**
     * Called by the timeout scheduler to mark ringing calls as MISSED
     * when the callee never answered within the configured window.
     */
    void markCallMissed(String callId);

    /**
     * Paginated call history for a user (as caller or callee), newest first.
     */
    List<CallSessionResponse> getCallHistory(Long userId, int page, int size);

    /**
     * Missed calls for a user — useful for notification badges.
     */
    List<CallSessionResponse> getMissedCalls(Long userId, int page, int size);

    /** Active call state snapshot — null if the user is not in a call. */
    CallSession.CallState getActiveCallState(Long userId);
}
