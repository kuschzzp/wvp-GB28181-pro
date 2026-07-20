package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Objects;

/**
 * A server-side binding between one stable video resource and its active media stream.
 */
public record MediaStreamBinding(
        String mediaServerId,
        String app,
        String stream,
        VideoResourceType resourceType,
        long resourceId) {

    public MediaStreamBinding {
        mediaServerId = requireText(mediaServerId, "mediaServerId");
        app = requireText(app, "app");
        stream = requireText(stream, "stream");
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
