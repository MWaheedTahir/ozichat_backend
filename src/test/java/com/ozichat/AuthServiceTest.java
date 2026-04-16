package com.ozichat;

import com.ozichat.security.JwtTokenProvider;
import com.ozichat.user.dto.request.LoginRequest;
import com.ozichat.user.dto.request.RegisterRequest;
import com.ozichat.user.dto.response.AuthResponse;
import com.ozichat.user.entity.User;
import com.ozichat.user.entity.UserPrivacySettings;
import com.ozichat.user.repository.UserPrivacySettingsRepository;
import com.ozichat.user.repository.UserRepository;
import com.ozichat.user.repository.UserSessionRepository;
import com.ozichat.user.service.AuthService;
import com.ozichat.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private UserPrivacySettingsRepository privacyRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // low cost for tests
        authService = new AuthServiceImpl(
                userRepository, sessionRepository, privacyRepository,
                jwtTokenProvider, passwordEncoder);
    }

    // ──────────────────────────────────────────────
    // Register
    // ──────────────────────────────────────────────

    @Test
    void register_withEmail_succeeds() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@ozichat.com");
        req.setPassword("securePass1!");
        req.setDisplayName("Alice");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);

        User savedUser = User.builder()
                .id(1L).email(req.getEmail())
                .displayName(req.getDisplayName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(User.Role.USER).isVerified(false)
                .createdAt(Instant.now()).build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(privacyRepository.save(any())).thenReturn(new UserPrivacySettings());
        when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh.token");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(sessionRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access.token");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@ozichat.com");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Alice");
        verify(userRepository).save(any(User.class));
        verify(privacyRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@ozichat.com");
        req.setPassword("securePass1!");
        req.setDisplayName("Alice");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .hasMessageContaining("Email is already registered");
    }

    @Test
    void register_noEmailOrPhone_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setPassword("securePass1!");
        req.setDisplayName("Alice");

        assertThatThrownBy(() -> authService.register(req))
                .hasMessageContaining("Email or phone number is required");
    }

    // ──────────────────────────────────────────────
    // Login
    // ──────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokens() {
        String rawPassword = "securePass1!";
        User user = User.builder()
                .id(2L).email("bob@ozichat.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName("Bob").role(User.Role.USER)
                .isVerified(true).createdAt(Instant.now()).build();

        LoginRequest req = new LoginRequest();
        req.setIdentifier("bob@ozichat.com");
        req.setPassword(rawPassword);

        when(userRepository.findByEmailAndDeletedAtIsNull("bob@ozichat.com"))
                .thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(2L, "USER")).thenReturn("access.bob");
        when(jwtTokenProvider.generateRefreshToken(2L)).thenReturn("refresh.bob");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(sessionRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.login(req, "127.0.0.1");

        assertThat(response.getAccessToken()).isEqualTo("access.bob");
        assertThat(response.getUser().getId()).isEqualTo(2L);
    }

    @Test
    void login_wrongPassword_throws() {
        User user = User.builder()
                .id(3L).email("charlie@ozichat.com")
                .passwordHash(passwordEncoder.encode("correctPass"))
                .displayName("Charlie").role(User.Role.USER)
                .isVerified(false).createdAt(Instant.now()).build();

        LoginRequest req = new LoginRequest();
        req.setIdentifier("charlie@ozichat.com");
        req.setPassword("wrongPass");

        when(userRepository.findByEmailAndDeletedAtIsNull("charlie@ozichat.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_unknownUser_throws() {
        LoginRequest req = new LoginRequest();
        req.setIdentifier("ghost@ozichat.com");
        req.setPassword("anything");

        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@ozichat.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPhoneAndDeletedAtIsNull("ghost@ozichat.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .hasMessageContaining("Invalid credentials");
    }
}
