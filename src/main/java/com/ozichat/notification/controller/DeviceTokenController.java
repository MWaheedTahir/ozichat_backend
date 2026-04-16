package com.ozichat.notification.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.notification.dto.request.RegisterDeviceTokenRequest;
import com.ozichat.notification.entity.DeviceToken;
import com.ozichat.notification.repository.DeviceTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/users/me/device-tokens")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Tokens", description = "FCM push notification device token management")
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Register (or refresh) a device token for push notifications.
     * Idempotent: if the token already exists for this user it's re-activated and last-used updated.
     */
    @PostMapping
    @Operation(summary = "Register FCM device token")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterDeviceTokenRequest request,
            @AuthenticationPrincipal Long userId) {

        deviceTokenRepository.findByUserIdAndToken(userId, request.getToken())
                .ifPresentOrElse(existing -> {
                    existing.setIsActive(true);
                    existing.setLastUsedAt(Instant.now());
                    if (request.getDeviceName() != null) {
                        existing.setDeviceName(request.getDeviceName());
                    }
                    deviceTokenRepository.save(existing);
                    log.debug("Device token refreshed for userId={}", userId);
                }, () -> {
                    DeviceToken.Platform platform;
                    try {
                        platform = DeviceToken.Platform.valueOf(
                                request.getPlatform().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        platform = DeviceToken.Platform.ANDROID;
                    }

                    deviceTokenRepository.save(DeviceToken.builder()
                            .userId(userId)
                            .token(request.getToken())
                            .platform(platform)
                            .deviceName(request.getDeviceName())
                            .build());
                    log.info("New device token registered for userId={} platform={}", userId, platform);
                });

        return ResponseEntity.ok(ApiResponse.success("Device token registered"));
    }

    /**
     * Deregister a device token (e.g. on logout or when the user disables notifications).
     */
    @DeleteMapping("/{token}")
    @Operation(summary = "Deregister FCM device token")
    public ResponseEntity<ApiResponse<Void>> deregister(
            @PathVariable String token,
            @AuthenticationPrincipal Long userId) {

        deviceTokenRepository.deactivateToken(userId, token);
        log.info("Device token deregistered for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("Device token removed"));
    }

    /**
     * Deregister all device tokens for the current user.
     * Should be called on "logout all devices".
     */
    @DeleteMapping
    @Operation(summary = "Deregister all FCM device tokens for the current user")
    public ResponseEntity<ApiResponse<Void>> deregisterAll(@AuthenticationPrincipal Long userId) {
        deviceTokenRepository.deactivateAllByUserId(userId);
        log.info("All device tokens deregistered for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("All device tokens removed"));
    }
}
