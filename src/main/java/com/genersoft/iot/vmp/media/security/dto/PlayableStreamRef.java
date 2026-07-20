package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;

import java.util.Objects;

/**
 * Safe browser-facing reference to an active stream. It intentionally contains no URL.
 */
public record PlayableStreamRef(
        VideoResourceType resourceType,
        long resourceId,
        String mediaServerId,
        String app,
        String stream) {

    public PlayableStreamRef {
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        mediaServerId = requireText(mediaServerId, "mediaServerId");
        app = requireText(app, "app");
        stream = requireText(stream, "stream");
    }

    public PlayableStreamRef(MediaStreamBinding binding) {
        this(
                binding.resourceType(),
                binding.resourceId(),
                binding.mediaServerId(),
                binding.app(),
                binding.stream());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
