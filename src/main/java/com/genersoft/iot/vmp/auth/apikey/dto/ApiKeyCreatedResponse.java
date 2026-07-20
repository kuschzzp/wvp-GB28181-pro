package com.genersoft.iot.vmp.auth.apikey.dto;

import java.time.Instant;

public record ApiKeyCreatedResponse(
        long id,
        String secret,
        String prefix,
        String last4,
        Instant expiresAt) {
}
