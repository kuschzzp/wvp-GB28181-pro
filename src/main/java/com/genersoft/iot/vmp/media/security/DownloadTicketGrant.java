package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;

import java.util.List;
import java.util.Objects;

/** Trusted grant assembled from server-side records, never from a client path. */
public record DownloadTicketGrant(
        DownloadResourceType resourceType,
        List<Long> resourceIds,
        String permissionCode,
        List<VideoResourceRef> authorizedResources,
        DownloadPayload payload) {

    public DownloadTicketGrant {
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
        if (resourceIds.isEmpty() || resourceIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("resourceIds must contain positive IDs");
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        permissionCode = permissionCode.trim();
        authorizedResources = authorizedResources == null
                ? List.of()
                : List.copyOf(authorizedResources);
        if (authorizedResources.isEmpty()) {
            throw new IllegalArgumentException("authorizedResources must not be empty");
        }
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
