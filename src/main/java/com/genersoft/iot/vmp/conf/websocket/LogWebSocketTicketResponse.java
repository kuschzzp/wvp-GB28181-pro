package com.genersoft.iot.vmp.conf.websocket;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LogWebSocketTicketResponse(
        Instant issuedAt,
        Instant expiresAt,
        List<String> protocols) {

    public LogWebSocketTicketResponse {
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        protocols = protocols == null ? List.of() : List.copyOf(protocols);
        if (!expiresAt.isAfter(issuedAt) || protocols.size() != 2) {
            throw new IllegalArgumentException("log websocket ticket response is invalid");
        }
    }
}
