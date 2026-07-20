package com.genersoft.iot.vmp.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        String serverId) {
}
