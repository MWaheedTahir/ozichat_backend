package com.ozichat.email.service;

public interface EmailService {

    /**
     * Send a verification OTP email to a newly registered / unverified user.
     */
    void sendEmailVerificationOtp(String toEmail, String displayName, String otp);

    /**
     * Send a password-reset OTP email.
     */
    void sendPasswordResetOtp(String toEmail, String displayName, String otp);

    /**
     * Send a generic OTP email with a custom subject.
     */
    void sendOtp(String toEmail, String displayName, String subject, String otp, int expiryMinutes);
}
