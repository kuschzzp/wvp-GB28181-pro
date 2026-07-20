package com.genersoft.iot.vmp.conf.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RedisLogWebSocketTicketStore
        implements LogWebSocketTicketStore {

    private static final String KEY_PREFIX = "wvp:log:websocket-ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisLogWebSocketTicketStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "objectMapper must not be null");
    }

    @Override
    public void save(
            String ticketHash,
            LogWebSocketTicketClaims claims,
            Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("websocket ticket TTL must be positive");
        }
        try {
            redisTemplate.opsForValue().set(
                    key(ticketHash), objectMapper.writeValueAsString(claims), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "websocket ticket serialization failed", exception);
        }
    }

    @Override
    public Optional<LogWebSocketTicketClaims> consume(String ticketHash) {
        String serialized = redisTemplate.opsForValue().getAndDelete(key(ticketHash));
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    serialized, LogWebSocketTicketClaims.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "websocket ticket data is invalid", exception);
        }
    }

    private String key(String ticketHash) {
        if (ticketHash == null || ticketHash.isBlank()) {
            throw new IllegalArgumentException("ticketHash must not be blank");
        }
        return KEY_PREFIX + ticketHash.trim();
    }
}
