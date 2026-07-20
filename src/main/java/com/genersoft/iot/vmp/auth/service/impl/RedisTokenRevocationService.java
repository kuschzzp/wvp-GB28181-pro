package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final String KEY_PREFIX = "auth:revoked:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(VerifiedAccessToken token) {
        Objects.requireNonNull(token, "token must not be null");
        Duration remaining = Duration.between(Instant.now(), token.expiresAt());
        if (remaining.isZero() || remaining.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(key(token.jti()), "1", remaining);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return true;
        }
        Boolean revoked = redisTemplate.hasKey(key(jti));
        if (revoked == null) {
            throw new IllegalStateException("无法确定令牌撤销状态");
        }
        return revoked;
    }

    static String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
