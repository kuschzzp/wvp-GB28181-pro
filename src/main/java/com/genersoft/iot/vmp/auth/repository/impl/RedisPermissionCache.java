package com.genersoft.iot.vmp.auth.repository.impl;

import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Repository
public class RedisPermissionCache implements PermissionCache {

    private static final String KEY_PREFIX = "auth:principal:";
    private static final String INDEX_KEY_PREFIX = "auth:principal-index:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisPermissionCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Set<String>> get(long userId, long tokenVersion) {
        Object cached = redisTemplate.opsForValue().get(key(userId, tokenVersion));
        if (cached == null) {
            return Optional.empty();
        }
        if (!(cached instanceof Collection<?> values)) {
            throw new IllegalStateException("权限缓存内容类型非法");
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String permission)) {
                throw new IllegalStateException("权限缓存包含非字符串值");
            }
            permissions.add(permission);
        }
        return Optional.of(Set.copyOf(permissions));
    }

    @Override
    public void put(long userId, long tokenVersion, Set<String> permissions, Duration ttl) {
        String cacheKey = key(userId, tokenVersion);
        String indexKey = indexKey(userId);
        redisTemplate.opsForValue().set(cacheKey, permissions, ttl);
        redisTemplate.opsForSet().add(indexKey, cacheKey);
        redisTemplate.expire(indexKey, ttl);
    }

    @Override
    public void evictUser(long userId) {
        String indexKey = indexKey(userId);
        Set<Object> indexedKeys = redisTemplate.opsForSet().members(indexKey);
        if (indexedKeys != null && !indexedKeys.isEmpty()) {
            redisTemplate.delete(indexedKeys.stream().map(String::valueOf).toList());
        }
        redisTemplate.delete(indexKey);
    }

    static String key(long userId, long tokenVersion) {
        return KEY_PREFIX + userId + ":" + tokenVersion;
    }

    static String indexKey(long userId) {
        return INDEX_KEY_PREFIX + userId;
    }
}
