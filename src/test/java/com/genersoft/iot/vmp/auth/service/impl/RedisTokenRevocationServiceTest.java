package com.genersoft.iot.vmp.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenRevocationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RedisTokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new RedisTokenRevocationService(redisTemplate);
    }

    @Test
    void readsExactJtiKeyAndFailsClosedOnIndeterminateRedisResult() {
        when(redisTemplate.hasKey("auth:revoked:jti-1")).thenReturn(false, true, null);

        assertFalse(service.isRevoked("jti-1"));
        assertTrue(service.isRevoked("jti-1"));
        assertThrows(IllegalStateException.class, () -> service.isRevoked("jti-1"));
    }
}
