package com.genersoft.iot.vmp.media.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class RedisMediaHookReplayGuard implements MediaHookReplayGuard {

    private static final String KEY_PREFIX = "wvp:media:hook-replay:";
    private static final Pattern NONCE_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{8,128}");

    private final StringRedisTemplate redisTemplate;
    private final MediaAccessProperties properties;
    private final Clock clock;

    @Autowired
    public RedisMediaHookReplayGuard(
            StringRedisTemplate redisTemplate,
            MediaAccessProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RedisMediaHookReplayGuard(
            StringRedisTemplate redisTemplate,
            MediaAccessProperties properties,
            Clock clock) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
        this.properties = Objects.requireNonNull(
                properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean accept(
            MediaNodePrincipal principal,
            String requestPath,
            byte[] requestBody,
            String timestamp,
            String nonce) {
        Objects.requireNonNull(principal, "principal must not be null");
        boolean hasTimestamp = hasText(timestamp);
        boolean hasNonce = hasText(nonce);
        if (hasTimestamp != hasNonce) {
            return false;
        }
        if (hasTimestamp) {
            Instant requestTime = parseTimestamp(timestamp);
            if (requestTime == null
                    || durationBetween(requestTime, clock.instant())
                    .compareTo(properties.hookTimestampSkew()) > 0
                    || !NONCE_PATTERN.matcher(nonce.trim()).matches()
                    || !reserve("nonce:" + digest(
                            principal.getName().getBytes(StandardCharsets.UTF_8),
                            nonce.trim().getBytes(StandardCharsets.UTF_8)),
                            properties.hookNonceTtl())) {
                return false;
            }
        }

        if (!requiresBodyFingerprint(requestPath)) {
            return true;
        }
        byte[] path = requestPath == null
                ? new byte[0]
                : requestPath.getBytes(StandardCharsets.UTF_8);
        return reserve(
                "body:" + digest(
                        principal.getName().getBytes(StandardCharsets.UTF_8),
                        path,
                        requestBody == null ? new byte[0] : requestBody),
                properties.hookReplayWindow());
    }

    private boolean reserve(String keySuffix, Duration ttl) {
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + keySuffix, "1", ttl);
        return Boolean.TRUE.equals(stored);
    }

    private boolean requiresBodyFingerprint(String requestPath) {
        if (requestPath == null) {
            return true;
        }
        return !(requestPath.endsWith("/on_play")
                || requestPath.endsWith("/on_server_keepalive")
                || requestPath.endsWith("/on_stream_none_reader")
                || requestPath.endsWith("/on_stream_not_found"));
    }

    private Instant parseTimestamp(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            if (normalized.matches("[0-9]{10}")) {
                return Instant.ofEpochSecond(Long.parseLong(normalized));
            }
            if (normalized.matches("[0-9]{13}")) {
                return Instant.ofEpochMilli(Long.parseLong(normalized));
            }
            return Instant.parse(normalized);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return null;
        }
    }

    private Duration durationBetween(Instant left, Instant right) {
        Duration duration = Duration.between(left, right);
        return duration.isNegative() ? duration.negated() : duration;
    }

    private String digest(byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] value : values) {
                digest.update(ByteBuffer.allocate(Integer.BYTES)
                        .putInt(value.length)
                        .array());
                digest.update(value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
