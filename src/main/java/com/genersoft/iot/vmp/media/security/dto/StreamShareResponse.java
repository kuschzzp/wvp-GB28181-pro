package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.StreamShareStatus;

import java.time.Instant;

public record StreamShareResponse(
        String shareId,
        VideoResourceType resourceType,
        long resourceId,
        Instant expiresAt,
        Long maxViews,
        long viewCount,
        boolean passcodeRequired,
        StreamShareStatus status,
        Instant createTime) {
}
