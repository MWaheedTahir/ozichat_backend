package com.ozichat.call.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ozichat.call.document.CallSession;
import com.ozichat.call.dto.response.CallSessionResponse;
import com.ozichat.call.dto.signaling.*;
import com.ozichat.call.repository.CallSessionRepository;
import com.ozichat.call.service.CallService;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core call management service.
 *
 * Redis key schema:
 *   call:session:{callId}   → JSON-serialized CallActiveState   TTL = 2 h
 *   call:user:{userId}      → callId                            TTL = 2 h
 *   call:ringing  (ZSET)    → score = initiatedAt epoch ms, member = callId
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallServiceImpl implements CallService {

    private static final String   KEY_SESSION   = "call:session:";
    private static final String   KEY_USER      = "call:user:";
    private static final String   KEY_RINGING   = "call:ringing";
    private static final Duration SESSION_TTL   = Duration.ofHours(2);
    private static final String   CALL_QUEUE    = "/queue/call";
    private static final int      MAX_PAGE      = 50;

    private final CallSessionRepository callSessionRepository;
    private final UserRepository        userRepository;
    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate   redis;
    private final ObjectMapper          objectMapper;   // Spring Boot auto-configures this bean

    // ── Initiate ─────────────────────────────────────────────────────────────

    @Override
    public String initiateCall(Long callerId, InitiateCallRequest request) {
        Long calleeId = request.getCalleeId();

        if (callerId.equals(calleeId)) {
            throw new BusinessException("Cannot call yourself", HttpStatus.BAD_REQUEST);
        }
        if (redis.hasKey(KEY_USER + callerId)) {
            throw new BusinessException("You are already in an active call", HttpStatus.CONFLICT);
        }
        if (redis.hasKey(KEY_USER + calleeId)) {
            throw new BusinessException("User is already in an active call", HttpStatus.CONFLICT);
        }

        User callee = userRepository.findByIdAndDeletedAtIsNull(calleeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", calleeId));
        User caller = userRepository.findByIdAndDeletedAtIsNull(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", callerId));

        Instant now   = Instant.now();
        String callId = UUID.randomUUID().toString();

        // Persist to MongoDB
        callSessionRepository.save(CallSession.builder()
                .id(callId)
                .callerId(callerId)
                .calleeId(calleeId)
                .type(request.getType())
                .state(CallSession.CallState.RINGING)
                .initiatedAt(now)
                .ringingAt(now)
                .build());

        // Store active state in Redis
        CallActiveState active = new CallActiveState(
                callId, callerId, calleeId,
                request.getType().name(),
                CallSession.CallState.RINGING.name(),
                now.toEpochMilli());
        setActiveState(callId, active);
        redis.opsForValue().set(KEY_USER + callerId, callId, SESSION_TTL);
        redis.opsForValue().set(KEY_USER + calleeId, callId, SESSION_TTL);

        // Track in ringing ZSET for timeout scheduler
        redis.opsForZSet().add(KEY_RINGING, callId, (double) now.toEpochMilli());

        // Push incoming_call to callee
        sendToUser(calleeId, CallSignalEnvelope.builder()
                .event("incoming_call")
                .callId(callId)
                .fromUserId(callerId)
                .fromUserName(caller.getDisplayName())
                .fromUserAvatarUrl(caller.getAvatarUrl())
                .callType(request.getType().name())
                .timestamp(now)
                .build());

        // ACK to caller with callId + callee info
        sendToUser(callerId, CallSignalEnvelope.builder()
                .event("call_initiated")
                .callId(callId)
                .fromUserId(calleeId)
                .fromUserName(callee.getDisplayName())
                .fromUserAvatarUrl(callee.getAvatarUrl())
                .callType(request.getType().name())
                .timestamp(now)
                .build());

        log.info("Call initiated — callId={} caller={} callee={} type={}",
                callId, callerId, calleeId, request.getType());
        return callId;
    }

    // ── Accept ────────────────────────────────────────────────────────────────

    @Override
    public void acceptCall(Long calleeId, CallActionRequest request) {
        String callId          = request.getCallId();
        CallActiveState active = getActiveStateOrThrow(callId);
        assertParticipant(active, calleeId);
        assertState(active, CallSession.CallState.RINGING, "accept");

        Instant now = Instant.now();

        // Update MongoDB
        callSessionRepository.findById(callId).ifPresent(s -> {
            s.setState(CallSession.CallState.CONNECTED);
            s.setAnsweredAt(now);
            callSessionRepository.save(s);
        });

        // Update Redis state
        CallActiveState updated = new CallActiveState(
                active.getCallId(), active.getCallerId(), active.getCalleeId(),
                active.getType(), CallSession.CallState.CONNECTED.name(),
                active.getInitiatedAtEpoch());
        setActiveState(callId, updated);

        // Remove from ringing ZSET — no longer needs timeout tracking
        redis.opsForZSet().remove(KEY_RINGING, callId);

        User callee = getUser(calleeId);
        sendToUser(active.getCallerId(), CallSignalEnvelope.builder()
                .event("call_accepted")
                .callId(callId)
                .fromUserId(calleeId)
                .fromUserName(callee.getDisplayName())
                .callType(active.getType())
                .timestamp(now)
                .build());

        log.info("Call accepted — callId={} callee={}", callId, calleeId);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Override
    public void rejectCall(Long calleeId, CallActionRequest request) {
        String callId          = request.getCallId();
        CallActiveState active = getActiveStateOrThrow(callId);
        assertParticipant(active, calleeId);
        assertState(active, CallSession.CallState.RINGING, "reject");

        terminateCall(callId, active, CallSession.CallState.REJECTED,
                CallSession.EndReason.REJECTED, "CALLEE");

        sendToUser(active.getCallerId(), CallSignalEnvelope.builder()
                .event("call_rejected")
                .callId(callId)
                .fromUserId(calleeId)
                .timestamp(Instant.now())
                .build());

        log.info("Call rejected — callId={} callee={}", callId, calleeId);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Override
    public void cancelCall(Long callerId, CallActionRequest request) {
        String callId          = request.getCallId();
        CallActiveState active = getActiveStateOrThrow(callId);
        assertParticipant(active, callerId);
        assertState(active, CallSession.CallState.RINGING, "cancel");

        terminateCall(callId, active, CallSession.CallState.CANCELLED,
                CallSession.EndReason.CANCELLED, "CALLER");

        sendToUser(active.getCalleeId(), CallSignalEnvelope.builder()
                .event("call_cancelled")
                .callId(callId)
                .fromUserId(callerId)
                .timestamp(Instant.now())
                .build());

        log.info("Call cancelled — callId={} caller={}", callId, callerId);
    }

    // ── End ───────────────────────────────────────────────────────────────────

    @Override
    public void endCall(Long userId, CallActionRequest request) {
        String callId          = request.getCallId();
        CallActiveState active = getActiveStateOrThrow(callId);
        assertParticipant(active, userId);

        String endedBy = userId.equals(active.getCallerId()) ? "CALLER" : "CALLEE";
        Long   otherId = userId.equals(active.getCallerId()) ? active.getCalleeId() : active.getCallerId();

        // terminateCall computes durationSeconds when finalState == ENDED and answeredAt is set
        terminateCall(callId, active, CallSession.CallState.ENDED,
                CallSession.EndReason.NORMAL, endedBy);

        sendToUser(otherId, CallSignalEnvelope.builder()
                .event("call_ended")
                .callId(callId)
                .fromUserId(userId)
                .reason(endedBy)
                .timestamp(Instant.now())
                .build());

        log.info("Call ended — callId={} endedBy={}", callId, endedBy);
    }

    // ── Relay: Offer ──────────────────────────────────────────────────────────

    @Override
    public void relayOffer(Long senderId, WebRtcSignal signal) {
        CallActiveState active = getActiveStateOrThrow(signal.getCallId());
        assertParticipant(active, senderId);

        sendToUser(otherParty(active, senderId), CallSignalEnvelope.builder()
                .event("offer")
                .callId(signal.getCallId())
                .fromUserId(senderId)
                .sdp(signal.getSdp())
                .sdpType(signal.getSdpType())
                .timestamp(Instant.now())
                .build());

        log.debug("SDP offer relayed — callId={} sender={}", signal.getCallId(), senderId);
    }

    // ── Relay: Answer ─────────────────────────────────────────────────────────

    @Override
    public void relayAnswer(Long senderId, WebRtcSignal signal) {
        CallActiveState active = getActiveStateOrThrow(signal.getCallId());
        assertParticipant(active, senderId);

        sendToUser(otherParty(active, senderId), CallSignalEnvelope.builder()
                .event("answer")
                .callId(signal.getCallId())
                .fromUserId(senderId)
                .sdp(signal.getSdp())
                .sdpType(signal.getSdpType())
                .timestamp(Instant.now())
                .build());

        log.debug("SDP answer relayed — callId={} sender={}", signal.getCallId(), senderId);
    }

    // ── Relay: ICE Candidate ──────────────────────────────────────────────────

    @Override
    public void relayIceCandidate(Long senderId, IceCandidateSignal signal) {
        CallActiveState active = getActiveStateOrThrow(signal.getCallId());
        assertParticipant(active, senderId);

        sendToUser(otherParty(active, senderId), CallSignalEnvelope.builder()
                .event("ice_candidate")
                .callId(signal.getCallId())
                .fromUserId(senderId)
                .candidate(signal.getCandidate())
                .sdpMid(signal.getSdpMid())
                .sdpMLineIndex(signal.getSdpMLineIndex())
                .timestamp(Instant.now())
                .build());

        log.debug("ICE candidate relayed — callId={} sender={}", signal.getCallId(), senderId);
    }

    // ── Missed (called by timeout scheduler) ──────────────────────────────────

    @Override
    public void markCallMissed(String callId) {
        CallActiveState active = getActiveState(callId);
        if (active == null) return;
        if (!CallSession.CallState.RINGING.name().equals(active.getState())) return;

        terminateCall(callId, active, CallSession.CallState.MISSED,
                CallSession.EndReason.MISSED, "SYSTEM");

        sendToUser(active.getCalleeId(), CallSignalEnvelope.builder()
                .event("call_missed")
                .callId(callId)
                .fromUserId(active.getCallerId())
                .callType(active.getType())
                .timestamp(Instant.now())
                .build());

        sendToUser(active.getCallerId(), CallSignalEnvelope.builder()
                .event("call_ended")
                .callId(callId)
                .reason("MISSED")
                .timestamp(Instant.now())
                .build());

        log.info("Call marked MISSED — callId={}", callId);
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Override
    public List<CallSessionResponse> getCallHistory(Long userId, int page, int size) {
        return callSessionRepository
                .findByParticipant(userId,
                        PageRequest.of(page, Math.min(size, MAX_PAGE),
                                Sort.by(Sort.Direction.DESC, "initiatedAt")))
                .stream()
                .map(s -> CallSessionResponse.from(s, getUserName(s.getCallerId()), getUserName(s.getCalleeId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<CallSessionResponse> getMissedCalls(Long userId, int page, int size) {
        return callSessionRepository
                .findMissedCalls(userId,
                        PageRequest.of(page, Math.min(size, MAX_PAGE),
                                Sort.by(Sort.Direction.DESC, "initiatedAt")))
                .stream()
                .map(s -> CallSessionResponse.from(s, getUserName(s.getCallerId()), getUserName(s.getCalleeId())))
                .collect(Collectors.toList());
    }

    @Override
    public CallSession.CallState getActiveCallState(Long userId) {
        String callId = redis.opsForValue().get(KEY_USER + userId);
        if (callId == null) return null;
        CallActiveState active = getActiveState(callId);
        if (active == null) return null;
        return CallSession.CallState.valueOf(active.getState());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Persists the terminal state to MongoDB, clears all Redis keys,
     * and — for ENDED calls — computes call duration from answeredAt.
     */
    private void terminateCall(String callId, CallActiveState active,
                               CallSession.CallState finalState,
                               CallSession.EndReason reason,
                               String endedBy) {
        Instant now = Instant.now();

        callSessionRepository.findById(callId).ifPresent(s -> {
            s.setState(finalState);
            s.setEndedAt(now);
            s.setEndedBy(endedBy);
            s.setEndReason(reason);
            if (finalState == CallSession.CallState.ENDED && s.getAnsweredAt() != null) {
                long secs = Duration.between(s.getAnsweredAt(), now).getSeconds();
                s.setDurationSeconds(Math.max(0, secs));
            }
            callSessionRepository.save(s);
        });

        // Clear Redis
        redis.delete(KEY_SESSION + callId);
        redis.delete(KEY_USER + active.getCallerId().toString());
        redis.delete(KEY_USER + active.getCalleeId().toString());
        redis.opsForZSet().remove(KEY_RINGING, callId);
    }

    private void setActiveState(String callId, CallActiveState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redis.opsForValue().set(KEY_SESSION + callId, json, SESSION_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize call state for callId=" + callId, e);
        }
    }

    private CallActiveState getActiveState(String callId) {
        String json = redis.opsForValue().get(KEY_SESSION + callId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, CallActiveState.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize call state — callId={}", callId, e);
            return null;
        }
    }

    private CallActiveState getActiveStateOrThrow(String callId) {
        CallActiveState active = getActiveState(callId);
        if (active == null) {
            throw new ResourceNotFoundException("Call session not found or already terminated: " + callId);
        }
        return active;
    }

    private void assertParticipant(CallActiveState active, Long userId) {
        if (!active.getCallerId().equals(userId) && !active.getCalleeId().equals(userId)) {
            throw new BusinessException("You are not a participant in this call", HttpStatus.FORBIDDEN);
        }
    }

    private void assertState(CallActiveState active, CallSession.CallState expected, String action) {
        if (!expected.name().equals(active.getState())) {
            throw new BusinessException(
                    "Cannot " + action + " a call that is currently " + active.getState(),
                    HttpStatus.CONFLICT);
        }
    }

    private Long otherParty(CallActiveState active, Long userId) {
        return active.getCallerId().equals(userId) ? active.getCalleeId() : active.getCallerId();
    }

    private void sendToUser(Long userId, CallSignalEnvelope envelope) {
        messaging.convertAndSendToUser(userId.toString(), CALL_QUEUE, envelope);
    }

    private User getUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String getUserName(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(User::getDisplayName)
                .orElse("Unknown");
    }
}
