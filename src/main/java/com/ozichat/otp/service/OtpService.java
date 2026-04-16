package com.ozichat.otp.service;

public interface OtpService {

    /**
     * Generate a 6-digit OTP, store its hash in Redis with TTL, and return the raw code.
     *
     * @param key     unique key, e.g. "email:verify:{email}" or "email:reset:{email}"
     * @return        raw 6-digit OTP to include in the email body
     */
    String generateAndStore(String key);

    /**
     * Validate the provided code against the stored hash.
     * Deletes the entry on success (one-time use).
     *
     * @param key  same key used in generateAndStore
     * @param code raw code entered by the user
     * @return     true if code is valid and not expired
     */
    boolean validateAndConsume(String key, String code);

    /**
     * Delete the OTP entry without validating (e.g. after resend).
     */
    void invalidate(String key);
}
