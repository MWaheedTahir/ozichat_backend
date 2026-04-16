package com.ozichat.user.service;

import com.ozichat.user.dto.request.LoginRequest;
import com.ozichat.user.dto.request.RefreshTokenRequest;
import com.ozichat.user.dto.request.RegisterRequest;
import com.ozichat.user.dto.request.ResetPasswordRequest;
import com.ozichat.user.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request, String ipAddress);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String refreshToken);
    void logoutAll(Long userId);

    // ── OTP / Email Verification ──
    void sendEmailOtp(String email, String purpose);
    boolean verifyEmailOtp(String email, String code, String purpose);
    void resetPassword(ResetPasswordRequest request);
    AuthResponse generateAuthResponse(String email);
}
