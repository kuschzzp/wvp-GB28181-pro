package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.time.Instant;
import java.util.Objects;

/**
 * A trusted grant produced only after a public share has passed all lifecycle
 * checks and atomically consumed one allowed view.
 */
public record SharePlayTicketGrant(
        long creatorUserId,
        long authzVersion,
        VideoResourceType resourceType,
        long resourceId,
        String schema,
        Instant shareExpiresAt) {

    public SharePlayTicketGrant {
        resourceType = Objects.requireNonNull(
                resourceType, "resourceType must not be null");
        shareExpiresAt = Objects.requireNonNull(
                shareExpiresAt, "shareExpiresAt must not be null");
        if (creatorUserId <= 0 || resourceId <= 0 || authzVersion < 0) {
            throw new IllegalArgumentException("share grant identifiers are invalid");
        }
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        schema = schema.trim();
    }
}
