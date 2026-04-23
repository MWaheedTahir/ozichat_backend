package com.ozichat.call.controller;

import com.ozichat.call.document.CallSession;
import com.ozichat.call.dto.response.CallSessionResponse;
import com.ozichat.call.dto.response.IceServerConfig;
import com.ozichat.call.service.CallService;
import com.ozichat.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "Calls", description = "WebRTC calling — ICE server config, call history")
@SecurityRequirement(name = "bearerAuth")
public class CallController {

    private final CallService callService;

    // ── TURN/STUN configuration ──────────────────────────────────────────────

    @Value("${call.stun.urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}")
    private String stunUrls;

    @Value("${call.turn.url:}")
    private String turnUrl;

    @Value("${call.turn.username:}")
    private String turnUsername;

    @Value("${call.turn.credential:}")
    private String turnCredential;

    /**
     * Returns ICE server configuration for WebRTC.
     * The client passes this directly to:
     *   new RTCPeerConnection({ iceServers: response.data })
     *
     * Must be called before initiating or accepting a call so the client
     * has the current TURN credentials (rotate credentials regularly in prod).
     */
    @GetMapping("/ice-servers")
    @Operation(summary = "Get ICE server configuration (STUN + TURN) for WebRTC peer connections")
    public ResponseEntity<ApiResponse<List<IceServerConfig>>> getIceServers() {
        List<IceServerConfig> servers = new ArrayList<>();

        // STUN — Google public servers (no auth required)
        servers.add(IceServerConfig.builder()
                .urls(List.of(stunUrls.split(",")))
                .build());

        // TURN — self-hosted coturn (only added if configured)
        if (!turnUrl.isBlank()) {
            servers.add(IceServerConfig.builder()
                    .urls(List.of(turnUrl))
                    .username(turnUsername)
                    .credential(turnCredential)
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.success(servers));
    }

    // ── Call history ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    @Operation(summary = "Get call history for the current user (as caller or callee)")
    public ResponseEntity<ApiResponse<List<CallSessionResponse>>> getCallHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                callService.getCallHistory(userId, page, size)));
    }

    @GetMapping("/missed")
    @Operation(summary = "Get missed calls for the current user")
    public ResponseEntity<ApiResponse<List<CallSessionResponse>>> getMissedCalls(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                callService.getMissedCalls(userId, page, size)));
    }

    // ── Active call status ────────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Check if the current user is in an active call and the call state")
    public ResponseEntity<ApiResponse<?>> getCallStatus(
            @AuthenticationPrincipal Long userId) {

        CallSession.CallState state = callService.getActiveCallState(userId);
        String status = state != null ? state.name() : "IDLE";

        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
