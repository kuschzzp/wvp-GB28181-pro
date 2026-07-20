package com.genersoft.iot.vmp.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.LockedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptServiceTest {

    private static final String KEY = "auth:login-failure:operator:127.0.0.1";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisLoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new RedisLoginAttemptService(redisTemplate);
    }

    @Test
    void fifthFailureChangesTheKeyTtlFromTenToFifteenMinutes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L, 5L);

        service.recordFailure("Operator", "127.0.0.1");
        service.recordFailure("operator", "127.0.0.1");

        verify(redisTemplate).expire(KEY, RedisLoginAttemptService.FAILURE_WINDOW);
        verify(redisTemplate).expire(KEY, RedisLoginAttemptService.LOCK_DURATION);
    }

    @Test
    void allowsFourFailuresButRejectsTheFifthWithTheSamePublicMessage() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("4", "5");

        assertDoesNotThrow(() -> service.assertAllowed("operator", "127.0.0.1"));
        LockedException exception = assertThrows(LockedException.class,
                () -> service.assertAllowed("operator", "127.0.0.1"));

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    void redisFailureFailsClosedInsteadOfResettingTheCounter() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(AuthenticationServiceException.class,
                () -> service.assertAllowed("operator", "127.0.0.1"));
    }

    @Test
    void successfulLoginClearsOnlyTheNormalizedUserAndClientKey() {
        service.clear(" Operator ", " 127.0.0.1 ");

        verify(redisTemplate).delete(KEY);
    }
}
