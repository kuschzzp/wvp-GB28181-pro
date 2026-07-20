package com.genersoft.iot.vmp.media.security.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.security.DownloadTicketStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RedisDownloadTicketStore implements DownloadTicketStore {

    private static final String KEY_PREFIX = "wvp:media:download-ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisDownloadTicketStore(
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
            DownloadTicketClaims claims,
            Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("download ticket TTL must be positive");
        }
        try {
            redisTemplate.opsForValue().set(
                    key(ticketHash), objectMapper.writeValueAsString(claims), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "download ticket serialization failed", exception);
        }
    }

    @Override
    public Optional<DownloadTicketClaims> consume(String ticketHash) {
        String serialized = redisTemplate.opsForValue().getAndDelete(key(ticketHash));
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    serialized, DownloadTicketClaims.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "download ticket data is invalid", exception);
        }
    }

    private String key(String ticketHash) {
        if (ticketHash == null || ticketHash.isBlank()) {
            throw new IllegalArgumentException("ticketHash must not be blank");
        }
        return KEY_PREFIX + ticketHash.trim();
    }
}
