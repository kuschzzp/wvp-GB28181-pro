package com.genersoft.iot.vmp.auth.model;

import java.time.Instant;

/**
 * 已完成签名、受众和时效校验的网页登录令牌。
 */
public record VerifiedAccessToken(
        long userId,
        String jti,
        long tokenVersion,
        Instant issuedAt,
        Instant expiresAt) {
}
