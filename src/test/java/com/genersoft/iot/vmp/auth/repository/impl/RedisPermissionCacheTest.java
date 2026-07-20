package com.genersoft.iot.vmp.auth.repository.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPermissionCacheTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private SetOperations<String, Object> setOperations;

    private RedisPermissionCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisPermissionCache(redisTemplate);
    }

    @Test
    void storesVersionedEntryAndRegistersExactUserIndex() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        Set<String> permissions = Set.of("video:channel:list");

        cache.put(12L, 7L, permissions, TTL);

        verify(valueOperations).set("auth:principal:12:7", permissions, TTL);
        verify(setOperations).add("auth:principal-index:12", "auth:principal:12:7");
        verify(redisTemplate).expire("auth:principal-index:12", TTL);
    }

    @Test
    void evictsOnlyKeysRecordedInTheUsersIndex() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:principal-index:12")).thenReturn(Set.of(
                "auth:principal:12:6",
                "auth:principal:12:7"));

        cache.evictUser(12L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> deletedKeys = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(deletedKeys.capture());
        assertEquals(Set.of("auth:principal:12:6", "auth:principal:12:7"),
                Set.copyOf(deletedKeys.getValue()));
        verify(redisTemplate).delete("auth:principal-index:12");
    }

    @Test
    void readsOnlyTheRequestedUserAndTokenVersion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:principal:12:7"))
                .thenReturn(Set.of("video:channel:list", "video:channel:play"));

        assertEquals(
                Set.of("video:channel:list", "video:channel:play"),
                cache.get(12L, 7L).orElseThrow());
    }
}
