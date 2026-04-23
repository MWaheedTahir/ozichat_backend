package com.ozichat.call.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.call.document.CallSession;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSessionResponse {

    private String id;
    private Long callerId;
    private String callerName;
    private Long calleeId;
    private String calleeName;
    private String type;
    private String state;
    private Instant initiatedAt;
    private Instant answeredAt;
    private Instant endedAt;
    private Long durationSeconds;
    private String endedBy;
    private String endReason;

    public static CallSessionResponse from(CallSession s,
                                           String callerName,
                                           String calleeName) {
        return CallSessionResponse.builder()
                .id(s.getId())
                .callerId(s.getCallerId())
                .callerName(callerName)
                .calleeId(s.getCalleeId())
                .calleeName(calleeName)
                .type(s.getType().name())
                .state(s.getState().name())
                .initiatedAt(s.getInitiatedAt())
                .answeredAt(s.getAnsweredAt())
                .endedAt(s.getEndedAt())
                .durationSeconds(s.getDurationSeconds())
                .endedBy(s.getEndedBy())
                .endReason(s.getEndReason() != null ? s.getEndReason().name() : null)
                .build();
    }
}
