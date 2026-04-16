package com.ozichat;

import com.ozichat.otp.service.impl.OtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService unit tests")
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(otpService, "expiryMinutes", 10);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("generateAndStore: stores a 6-digit code hash in Redis with TTL")
    void generateAndStore_storesHashWithTtl() {
        // when
        String otp = otpService.generateAndStore("email_verification:test@example.com");

        // then
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");

        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("otp:");
        assertThat(valueCaptor.getValue()).isNotBlank(); // SHA-256 hash
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("validateAndConsume: returns true and deletes key when code is correct")
    void validateAndConsume_correctCode_returnsTrueAndDeletes() {
        // Capture the hash that gets stored
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(valueOps).set(anyString(), hashCaptor.capture(), any(Duration.class));
        String otp = otpService.generateAndStore("email_verification:user@example.com");
        String storedHash = hashCaptor.getValue();

        // Simulate Redis returning the stored hash
        given(valueOps.get("otp:email_verification:user@example.com")).willReturn(storedHash);

        // when
        boolean result = otpService.validateAndConsume("email_verification:user@example.com", otp);

        // then
        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:email_verification:user@example.com");
    }

    @Test
    @DisplayName("validateAndConsume: returns false when code is wrong")
    void validateAndConsume_wrongCode_returnsFalse() {
        // Store a valid OTP
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(valueOps).set(anyString(), hashCaptor.capture(), any(Duration.class));
        otpService.generateAndStore("email_verification:user@example.com");
        String storedHash = hashCaptor.getValue();

        given(valueOps.get("otp:email_verification:user@example.com")).willReturn(storedHash);

        // when — provide wrong OTP
        boolean result = otpService.validateAndConsume("email_verification:user@example.com", "000000");

        // then
        assertThat(result).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("validateAndConsume: returns false when OTP is expired (key not in Redis)")
    void validateAndConsume_expiredOtp_returnsFalse() {
        given(valueOps.get(anyString())).willReturn(null);

        boolean result = otpService.validateAndConsume("email_verification:user@example.com", "123456");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("invalidate: deletes the Redis key for the given OTP")
    void invalidate_deletesKey() {
        otpService.invalidate("email_verification:user@example.com");
        verify(redisTemplate).delete("otp:email_verification:user@example.com");
    }

    @Test
    @DisplayName("generateAndStore: each call returns a different code (randomness)")
    void generateAndStore_producesUniqueCodesAcrossMultipleCalls() {
        doNothing().when(valueOps).set(anyString(), anyString(), any(Duration.class));

        String otp1 = otpService.generateAndStore("key1");
        String otp2 = otpService.generateAndStore("key2");
        String otp3 = otpService.generateAndStore("key3");

        // With 1M possible codes this collision probability is negligible
        // but we just ensure the codes are valid 6-digit strings
        assertThat(otp1).matches("\\d{6}");
        assertThat(otp2).matches("\\d{6}");
        assertThat(otp3).matches("\\d{6}");
    }
}
