package com.ozichat.call.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persistent call history record stored in MongoDB.
 * Created when a call is initiated; updated at each state transition.
 * The hot/active state lives in Redis (CallActiveState).
 */
@Document(collection = "call_sessions")
@CompoundIndexes({
    @CompoundIndex(name = "idx_call_caller",  def = "{'callerId': 1,  'initiatedAt': -1}"),
    @CompoundIndex(name = "idx_call_callee",  def = "{'calleeId': 1,  'initiatedAt': -1}"),
    @CompoundIndex(name = "idx_call_parties", def = "{'callerId': 1, 'calleeId': 1, 'initiatedAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallSession {

    @Id
    private String id;

    @Field("callerId")
    private Long callerId;

    @Field("calleeId")
    private Long calleeId;

    @Field("type")
    @Builder.Default
    private CallType type = CallType.AUDIO;

    @Field("state")
    @Builder.Default
    private CallState state = CallState.INITIATING;

    // ── Timestamps ──────────────────────────────────────────────────────────

    @Field("initiatedAt")
    private Instant initiatedAt;

    @Field("ringingAt")
    private Instant ringingAt;

    @Field("answeredAt")
    private Instant answeredAt;

    @Field("endedAt")
    private Instant endedAt;

    // ── Result ──────────────────────────────────────────────────────────────

    /** Actual duration in seconds — null until the call ends normally. */
    @Field("durationSeconds")
    private Long durationSeconds;

    /** Who terminated the call: CALLER, CALLEE, or SYSTEM (timeout). */
    @Field("endedBy")
    private String endedBy;

    @Field("endReason")
    private EndReason endReason;


    public enum CallType  { AUDIO, VIDEO }

    public enum CallState {
        INITIATING,   // caller sent initiate; not yet delivered
        RINGING,      // callee received notification
        CONNECTED,    // both peers exchanged SDP; media flowing
        ENDED,        // normal hangup
        REJECTED,     // callee explicitly rejected
        MISSED,       // callee never answered within timeout
        CANCELLED     // caller hung up before callee answered
    }

    public enum EndReason { NORMAL, REJECTED, MISSED, CANCELLED, ERROR }
}
