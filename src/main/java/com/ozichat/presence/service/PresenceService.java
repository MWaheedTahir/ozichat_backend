package com.ozichat.presence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final StringRedisTemplate redis;

    private static final String PRESENCE_KEY = "presence";
    private static final String TYPING_PREFIX = "typing:";
    private static final String SESSION_PREFIX = "ws:session:";

    // ──────────────────────────────────────────────
    // Presence
    // ──────────────────────────────────────────────

    public void setOnline(Long userId) {
        redis.opsForHash().put(PRESENCE_KEY, userId.toString(), Instant.now().toString());
        log.debug("User {} is now ONLINE", userId);
    }

    public void setOffline(Long userId) {
        redis.opsForHash().delete(PRESENCE_KEY, userId.toString());
        log.debug("User {} is now OFFLINE", userId);
    }

    public boolean isOnline(Long userId) {
        return redis.opsForHash().hasKey(PRESENCE_KEY, userId.toString());
    }

    // ──────────────────────────────────────────────
    // Typing
    // ──────────────────────────────────────────────

    public void setTyping(Long conversationId, Long userId) {
        String key = TYPING_PREFIX + conversationId + ":" + userId;
        redis.opsForValue().set(key, "1", Duration.ofSeconds(4));
    }

    public boolean isTyping(Long conversationId, Long userId) {
        String key = TYPING_PREFIX + conversationId + ":" + userId;
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    public void clearTyping(Long conversationId, Long userId) {
        String key = TYPING_PREFIX + conversationId + ":" + userId;
        redis.delete(key);
    }

    // ──────────────────────────────────────────────
    // WebSocket Session Mapping
    // ──────────────────────────────────────────────

    public void storeSession(Long userId, String sessionId) {
        String key = SESSION_PREFIX + userId;
        redis.opsForValue().set(key, sessionId, Duration.ofDays(1));
    }

    public void removeSession(Long userId) {
        redis.delete(SESSION_PREFIX + userId);
    }

    public String getSession(Long userId) {
        return redis.opsForValue().get(SESSION_PREFIX + userId);
    }
}
