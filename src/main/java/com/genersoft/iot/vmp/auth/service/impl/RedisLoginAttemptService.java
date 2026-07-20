package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.service.LoginAttemptService;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class RedisLoginAttemptService implements LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:login-failure:";

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void assertAllowed(String username, String clientIp) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key(username, clientIp));
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException("登录保护服务不可用", exception);
        }
        if (parseCount(value) >= MAX_FAILURES) {
            throw new LockedException("用户名或密码错误");
        }
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        String key = key(username, clientIp);
        try {
            Long failures = redisTemplate.opsForValue().increment(key);
            if (failures == null) {
                throw new AuthenticationServiceException("登录保护服务不可用");
            }
            redisTemplate.expire(key, failures >= MAX_FAILURES ? LOCK_DURATION : FAILURE_WINDOW);
        } catch (AuthenticationServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException("登录保护服务不可用", exception);
        }
    }

    @Override
    public void clear(String username, String clientIp) {
        try {
            redisTemplate.delete(key(username, clientIp));
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException("登录保护服务不可用", exception);
        }
    }

    static String key(String username, String clientIp) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return KEY_PREFIX + normalizedUsername + ":" + normalizedIp;
    }

    private int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new AuthenticationServiceException("登录保护数据损坏", exception);
        }
    }
}
