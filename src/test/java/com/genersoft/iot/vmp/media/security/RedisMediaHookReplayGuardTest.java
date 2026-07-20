package com.genersoft.iot.vmp.media.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMediaHookReplayGuardTest {

    private static final Instant NOW =
            Instant.parse("2026-07-15T08:00:00Z");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisMediaHookReplayGuard replayGuard;
    private MediaNodePrincipal principal;

    @BeforeEach
    void setUp() {
        MediaAccessProperties properties = new MediaAccessProperties(
                false,
                Instant.parse("2026-10-01T00:00:00Z"),
                "0123456789abcdef0123456789abcdef",
                Duration.ofSeconds(2),
                Duration.ofMinutes(2),
                Duration.ofMinutes(5));
        replayGuard = new RedisMediaHookReplayGuard(
                redisTemplate,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        principal = new MediaNodePrincipal(
                "zlm-1", MediaNodeType.ZLM, 3L);
    }

    @Test
    void timestampAndNonceCanOnlyBeReservedOnce() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(), eq("1"), eq(Duration.ofMinutes(5))))
                .thenReturn(true, false);

        assertTrue(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_play",
                "{}".getBytes(StandardCharsets.UTF_8),
                Long.toString(NOW.getEpochSecond()),
                "nonce-12345678"));
        assertFalse(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_play",
                "{}".getBytes(StandardCharsets.UTF_8),
                Long.toString(NOW.getEpochSecond()),
                "nonce-12345678"));
    }

    @Test
    void stateChangingBodyFingerprintUsesShortReplayWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(), eq("1"), eq(Duration.ofSeconds(2))))
                .thenReturn(true, false);
        byte[] body = "{\"mediaServerId\":\"zlm-1\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_publish",
                body,
                null,
                null));
        assertFalse(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_publish",
                body,
                null,
                null));
    }

    @Test
    void staleOrIncompleteTimestampPairIsRejectedWithoutRedisCall() {
        assertFalse(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_play",
                new byte[0],
                Long.toString(NOW.minusSeconds(121).getEpochSecond()),
                "nonce-12345678"));
        assertFalse(replayGuard.accept(
                principal,
                "/index/hook/zlm/on_play",
                new byte[0],
                Long.toString(NOW.getEpochSecond()),
                null));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void redisFailureFailsClosed() {
        when(redisTemplate.opsForValue())
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> replayGuard.accept(
                        principal,
                        "/index/hook/zlm/on_publish",
                        new byte[0],
                        null,
                        null));
    }
}
