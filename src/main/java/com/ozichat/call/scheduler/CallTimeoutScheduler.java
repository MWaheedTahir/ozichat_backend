package com.ozichat.call.scheduler;

import com.ozichat.call.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Polls the Redis sorted set {@code call:ringing} every 10 seconds.
 * Any call whose score (= initiatedAt epoch) is older than the configured
 * ring timeout is marked as MISSED and both parties are notified.
 *
 * Production scaling note:
 *   In a multi-instance deployment, add a distributed lock (e.g. Redisson
 *   RLOCK) around this method so only one instance processes timeouts at a time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallTimeoutScheduler {

    private static final String KEY_RINGING = "call:ringing";

    private final CallService         callService;
    private final StringRedisTemplate redis;

    /** How long (seconds) to wait for callee to answer before marking MISSED. */
    @Value("${call.ring-timeout-seconds:30}")
    private int ringTimeoutSeconds;

    @Scheduled(fixedDelay = 10_000)   // every 10 seconds
    public void processRingingTimeouts() {
        double cutoff = (double) Instant.now()
                .minusSeconds(ringTimeoutSeconds)
                .toEpochMilli();

        // ZRANGEBYSCORE returns callIds with initiatedAt < cutoff (i.e., timed out)
        Set<String> timedOut = redis.opsForZSet()
                .rangeByScore(KEY_RINGING, 0, cutoff);

        if (timedOut == null || timedOut.isEmpty()) return;

        log.info("Call timeout check: {} ringing call(s) timed out", timedOut.size());

        for (String callId : timedOut) {
            try {
                callService.markCallMissed(callId);
                // markCallMissed removes the entry from the ZSET
            } catch (Exception e) {
                log.error("Error marking call {} as missed", callId, e);
                // Still remove from ZSET to prevent infinite retries
                redis.opsForZSet().remove(KEY_RINGING, callId);
            }
        }
    }
}
