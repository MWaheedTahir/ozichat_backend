package com.ozichat.call.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ICE server configuration returned to the client.
 * Clients pass this directly to {@code new RTCPeerConnection({ iceServers })}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IceServerConfig {

    /** One or more STUN/TURN URLs. */
    private List<String> urls;

    /** TURN username — null for STUN-only entries. */
    private String username;

    /** TURN credential — null for STUN-only entries. */
    private String credential;
}
