package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlayTicketRequest(
        @NotNull VideoResourceType resourceType,
        @Positive long resourceId,
        @NotBlank String schema) {
}
