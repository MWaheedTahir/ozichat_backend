package com.ozichat.user.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.user.dto.request.LoginRequest;
import com.ozichat.user.dto.request.RefreshTokenRequest;
import com.ozichat.user.dto.request.RegisterRequest;
import com.ozichat.user.dto.request.ResetPasswordRequest;
import com.ozichat.user.dto.request.SendOtpRequest;
import com.ozichat.user.dto.request.VerifyOtpRequest;
import com.ozichat.user.dto.response.AuthResponse;
import com.ozichat.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, OTP verification, and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (sends email verification OTP if email provided)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Check your email to verify your account.", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        AuthResponse response = authService.login(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout all sessions for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal Long userId) {
        authService.logoutAll(userId);
        return ResponseEntity.ok(ApiResponse.success("All sessions revoked"));
    }

    // ── OTP / Email Verification ──────────────────

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to email (purpose: EMAIL_VERIFICATION | PASSWORD_RESET)")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendEmailOtp(request.getEmail(), request.getPurpose());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to " + request.getEmail()));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify email OTP (purpose: EMAIL_VERIFICATION marks account as verified)")
    public ResponseEntity<ApiResponse<?>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        if(authService.verifyEmailOtp(request.getEmail(), request.getCode(), request.getPurpose())){
            return ResponseEntity.ok(ApiResponse.success("verified successful", authService.generateAuthResponse(request.getEmail())));

        }
        return ResponseEntity.ok(ApiResponse.success("Invalid OTP"));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Reset password using OTP from PASSWORD_RESET flow")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful. Please login with your new password."));
    }
}
