package com.ozichat;

import com.ozichat.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                "ozichat-super-secret-key-must-be-at-least-256-bits-long-for-hs512",
                900_000L,
                2_592_000_000L
        );
    }

    @Test
    void generateAccessToken_isValidAndExtractsUserId() {
        String token = provider.generateAccessToken(42L, "USER");

        assertThat(provider.isTokenValid(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.extractUserId(token)).isEqualTo(42L);
        assertThat(provider.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void generateRefreshToken_isValidButNotAccessToken() {
        String token = provider.generateRefreshToken(99L);

        assertThat(provider.isTokenValid(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isFalse();
        assertThat(provider.extractUserId(token)).isEqualTo(99L);
    }

    @Test
    void tampered_token_isInvalid() {
        String token = provider.generateAccessToken(1L, "USER");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(provider.isTokenValid(tampered)).isFalse();
    }

    @Test
    void expired_token_isInvalid() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "ozichat-super-secret-key-must-be-at-least-256-bits-long-for-hs512",
                1L,   // 1 ms expiry
                1L
        );
        String token = shortLived.generateAccessToken(5L, "USER");

        // Give it a moment to expire
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLived.isTokenValid(token)).isFalse();
    }
}
