package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record StreamShareCreateRequest(
        @NotNull VideoResourceType resourceType,
        @Positive long resourceId,
        @NotNull Instant expiresAt,
        @Positive Long maxViews,
        @Size(max = 128) String passcode) {
}
