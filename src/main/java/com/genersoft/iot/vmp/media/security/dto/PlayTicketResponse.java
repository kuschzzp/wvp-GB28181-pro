package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.media.security.MediaSchema;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record PlayTicketResponse(
        String ticket,
        Instant issuedAt,
        Instant expiresAt,
        MediaStreamBinding binding,
        Set<MediaSchema> allowedSchemas,
        Map<MediaSchema, String> sources) {

    public PlayTicketResponse {
        allowedSchemas = allowedSchemas == null ? Set.of() : Set.copyOf(allowedSchemas);
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }

    public PlayTicketResponse(
            String ticket,
            Instant issuedAt,
            Instant expiresAt,
            MediaStreamBinding binding,
            Set<MediaSchema> allowedSchemas) {
        this(ticket, issuedAt, expiresAt, binding, allowedSchemas, Map.of());
    }
}
