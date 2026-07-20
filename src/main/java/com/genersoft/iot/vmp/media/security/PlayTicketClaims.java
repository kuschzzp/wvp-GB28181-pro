package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side play ticket data. This object is stored in Redis and is never sent to clients.
 */
public record PlayTicketClaims(
        PrincipalType principalType,
        long principalId,
        long userId,
        String permissionCode,
        VideoResourceType resourceType,
        long resourceId,
        String mediaServerId,
        String app,
        String stream,
        Set<MediaSchema> allowedSchemas,
        long authzVersion,
        Instant issuedAt,
        Instant expiresAt) {

    public PlayTicketClaims {
        principalType = Objects.requireNonNull(principalType, "principalType must not be null");
        permissionCode = requireText(permissionCode, "permissionCode");
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        mediaServerId = requireText(mediaServerId, "mediaServerId");
        app = requireText(app, "app");
        stream = requireText(stream, "stream");
        allowedSchemas = allowedSchemas == null ? Set.of() : Set.copyOf(allowedSchemas);
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (principalId <= 0 || userId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("ticket identifiers must be positive");
        }
        if (allowedSchemas.isEmpty()) {
            throw new IllegalArgumentException("allowedSchemas must not be empty");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public MediaStreamBinding binding() {
        return new MediaStreamBinding(
                mediaServerId, app, stream, resourceType, resourceId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
