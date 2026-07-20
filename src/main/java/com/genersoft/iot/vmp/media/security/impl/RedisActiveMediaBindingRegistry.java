package com.genersoft.iot.vmp.media.security.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.ActiveMediaBindingRegistry;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RedisActiveMediaBindingRegistry implements ActiveMediaBindingRegistry {

    private static final String KEY_PREFIX = "wvp:media:active-binding:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisActiveMediaBindingRegistry(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "objectMapper must not be null");
    }

    @Override
    public void register(MediaStreamBinding binding, Duration ttl) {
        Objects.requireNonNull(binding, "binding must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("active binding TTL must be positive");
        }
        try {
            redisTemplate.opsForValue().set(
                    key(binding.resourceType(), binding.resourceId()),
                    objectMapper.writeValueAsString(binding),
                    ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("active media binding serialization failed", exception);
        }
    }

    @Override
    public Optional<MediaStreamBinding> find(
            VideoResourceType resourceType,
            long resourceId) {
        String serialized = redisTemplate.opsForValue().get(key(resourceType, resourceId));
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(serialized, MediaStreamBinding.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("active media binding data is invalid", exception);
        }
    }

    @Override
    public void remove(VideoResourceType resourceType, long resourceId) {
        redisTemplate.delete(key(resourceType, resourceId));
    }

    private String key(VideoResourceType resourceType, long resourceId) {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        return KEY_PREFIX + resourceType.name() + ":" + resourceId;
    }
}
