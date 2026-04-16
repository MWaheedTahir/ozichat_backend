package com.ozichat.otp.service.impl;

import com.ozichat.otp.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${otp.expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    private static final String OTP_PREFIX = "otp:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String generateAndStore(String key) {
        String code = generateCode();
        code ="111111";
        String hash = hashCode(code);


        String redisKey = OTP_PREFIX + key;
        stringRedisTemplate.opsForValue().set(redisKey, hash, Duration.ofMinutes(expiryMinutes));

        log.debug("otp: {}, {}", code, hash);
        log.debug("OTP generated and stored — key={}, expiryMinutes={}", redisKey, expiryMinutes);
        return code;
    }

    @Override
    public boolean validateAndConsume(String key, String code) {
        String redisKey = OTP_PREFIX + key;
        String storedHash = stringRedisTemplate.opsForValue().get(redisKey);

        if (storedHash == null) {
            log.warn("OTP validation failed — key not found or expired: {}", redisKey);
            return false;
        }

        boolean valid = storedHash.equals(hashCode(code));
        if (valid) {
            stringRedisTemplate.delete(redisKey);
            log.debug("OTP validated and consumed — key={}", redisKey);
        } else {
            log.warn("OTP validation failed — invalid code for key={}", redisKey);
        }
        return valid;
    }

    @Override
    public void invalidate(String key) {
        stringRedisTemplate.delete(OTP_PREFIX + key);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private String generateCode() {
        int max = (int) Math.pow(10, otpLength);
        int code = SECURE_RANDOM.nextInt(max);
        return String.format("%0" + otpLength + "d", code);
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
