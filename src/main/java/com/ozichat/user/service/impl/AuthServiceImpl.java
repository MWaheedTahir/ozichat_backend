package com.ozichat.user.service.impl;

import com.ozichat.email.service.EmailService;
import com.ozichat.exception.BusinessException;
import com.ozichat.exception.DuplicateResourceException;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.otp.service.OtpService;
import com.ozichat.security.JwtTokenProvider;
import com.ozichat.user.dto.request.LoginRequest;
import com.ozichat.user.dto.request.RefreshTokenRequest;
import com.ozichat.user.dto.request.RegisterRequest;
import com.ozichat.user.dto.request.ResetPasswordRequest;
import com.ozichat.user.dto.response.AuthResponse;
import com.ozichat.user.dto.response.UserResponse;
import com.ozichat.user.entity.User;
import com.ozichat.user.entity.UserPrivacySettings;
import com.ozichat.user.entity.UserSession;
import com.ozichat.user.repository.UserPrivacySettingsRepository;
import com.ozichat.user.repository.UserRepository;
import com.ozichat.user.repository.UserSessionRepository;
import com.ozichat.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final UserPrivacySettingsRepository privacyRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;

    // OTP key format: {purpose}:{email}
    private static final String OTP_PURPOSE_VERIFY  = "EMAIL_VERIFICATION";
    private static final String OTP_PURPOSE_RESET   = "PASSWORD_RESET";

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null && request.getPhone() == null) {
            throw new BusinessException("Email or phone number is required");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .role(User.Role.USER)
                .isVerified(false)
                .build();

        user = userRepository.save(user);

        // Create default privacy settings
        privacyRepository.save(UserPrivacySettings.builder().userId(user.getId()).build());

        // Send verification OTP if email was provided
        if (user.getEmail() != null) {
            String otp = otpService.generateAndStore(otpKey(OTP_PURPOSE_VERIFY, user.getEmail()));
//            emailService.sendEmailVerificationOtp(user.getEmail(), user.getDisplayName(), otp);
            log.info("Verification OTP sent to email={}", user.getEmail());
        }

        log.info("User registered successfully: userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getIdentifier())
                .or(() -> userRepository.findByPhoneAndDeletedAtIsNull(request.getIdentifier()))
                .orElseThrow(() -> new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!user.getIsVerified()) {
            throw new BusinessException("Not verified", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        log.info("User logged in: userId={}", user.getId());
        return buildAuthResponse(user, request.getPlatform(), request.getDeviceFingerprint(), ipAddress);
    }


    @Override
    @Transactional
    public AuthResponse generateAuthResponse(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .or(() -> userRepository.findByPhoneAndDeletedAtIsNull(email))
                .orElseThrow(() -> new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!user.getIsVerified()) {
            throw new BusinessException("Not verified", HttpStatus.UNAUTHORIZED);
        }


        log.info("User logged in: userId={}", user.getId());
        return buildAuthResponse(user, null, null, null);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        UserSession session = sessionRepository.findByRefreshTokenHashAndIsRevokedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setIsRevoked(true);
            sessionRepository.save(session);
            throw new BusinessException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: revoke old session, create new one
        session.setIsRevoked(true);
        sessionRepository.save(session);

        User user = userRepository.findByIdAndDeletedAtIsNull(session.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", session.getUserId()));

        return buildAuthResponse(user, session.getPlatform() != null ? session.getPlatform().name() : null,
                session.getDeviceFingerprint(), null);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        sessionRepository.findByRefreshTokenHashAndIsRevokedFalse(tokenHash)
                .ifPresent(session -> {
                    session.setIsRevoked(true);
                    sessionRepository.save(session);
                });
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        sessionRepository.revokeAllByUserId(userId);
        log.info("All sessions revoked for userId={}", userId);
    }

    // ── OTP / Email Verification ──────────────────

    @Override
    public void sendEmailOtp(String email, String purpose) {
        validatePurpose(purpose);

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        // Invalidate any existing OTP for this key before generating a new one
        otpService.invalidate(otpKey(purpose, email));

        String otp = otpService.generateAndStore(otpKey(purpose, email));

        if (OTP_PURPOSE_VERIFY.equalsIgnoreCase(purpose)) {
            if (Boolean.TRUE.equals(user.getIsVerified())) {
                throw new BusinessException("Email is already verified");
            }
            emailService.sendEmailVerificationOtp(email, user.getDisplayName(), otp);
        } else {
            emailService.sendPasswordResetOtp(email, user.getDisplayName(), otp);
        }

        log.info("OTP sent — email={} purpose={}", email, purpose);
    }

    @Override
    @Transactional
    public boolean verifyEmailOtp(String email, String code, String purpose) {
        validatePurpose(purpose);

        boolean valid = otpService.validateAndConsume(otpKey(purpose, email), code);
        if (!valid) {
            throw new BusinessException("Invalid or expired OTP", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (OTP_PURPOSE_VERIFY.equalsIgnoreCase(purpose)) {
            userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
                user.setIsVerified(true);
                userRepository.save(user);
                log.info("Email verified for userId={}", user.getId());
            });
        }

        return true;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        boolean valid = otpService.validateAndConsume(
                otpKey(OTP_PURPOSE_RESET, request.getEmail()), request.getCode());

        if (!valid) {
            throw new BusinessException("Invalid or expired OTP", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing sessions for security
        sessionRepository.revokeAllByUserId(user.getId());
        log.info("Password reset for userId={} — all sessions revoked", user.getId());
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, null, null, null);
    }

    private AuthResponse buildAuthResponse(User user, String platform, String deviceFingerprint, String ipAddress) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        String refreshTokenHash = hashToken(refreshToken);

        UserSession.Platform platformEnum = null;
        try {
            if (platform != null) platformEnum = UserSession.Platform.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        sessionRepository.save(UserSession.builder()
                .userId(user.getId())
                .refreshTokenHash(refreshTokenHash)
                .platform(platformEnum)
                .deviceFingerprint(deviceFingerprint)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getAccessTokenExpiryMs() * 2016L))
                .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiryMs() / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String otpKey(String purpose, String email) {
        return purpose.toLowerCase() + ":" + email.toLowerCase();
    }

    private void validatePurpose(String purpose) {
        if (!OTP_PURPOSE_VERIFY.equalsIgnoreCase(purpose) && !OTP_PURPOSE_RESET.equalsIgnoreCase(purpose)) {
            throw new BusinessException("Invalid purpose. Use EMAIL_VERIFICATION or PASSWORD_RESET");
        }
    }
}
