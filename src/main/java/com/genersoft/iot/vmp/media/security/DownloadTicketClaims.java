package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Download ticket data stored in Redis and never returned to clients. */
public record DownloadTicketClaims(
        PrincipalType principalType,
        long principalId,
        long userId,
        long authzVersion,
        DownloadResourceType resourceType,
        List<Long> resourceIds,
        String permissionCode,
        List<VideoResourceRef> authorizedResources,
        DownloadPayload payload,
        Instant issuedAt,
        Instant expiresAt) {

    public DownloadTicketClaims {
        principalType = Objects.requireNonNull(principalType, "principalType must not be null");
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
        authorizedResources = authorizedResources == null
                ? List.of()
                : List.copyOf(authorizedResources);
        payload = Objects.requireNonNull(payload, "payload must not be null");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (principalId <= 0 || userId <= 0 || authzVersion < 0
                || resourceIds.isEmpty() || authorizedResources.isEmpty()) {
            throw new IllegalArgumentException("download ticket identifiers are invalid");
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        permissionCode = permissionCode.trim();
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
