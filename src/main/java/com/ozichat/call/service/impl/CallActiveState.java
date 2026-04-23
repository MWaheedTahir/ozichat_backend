package com.ozichat.call.service.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight call state stored in Redis as JSON.
 * Kept in a dedicated class so Jackson can reliably serialize/deserialize it
 * (inner records and non-public nested types can be problematic with Jackson).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallActiveState {
    private String callId;
    private Long   callerId;
    private Long   calleeId;
    private String type;            // CallSession.CallType name
    private String state;           // CallSession.CallState name
    private long   initiatedAtEpoch; // Instant.toEpochMilli()
}
