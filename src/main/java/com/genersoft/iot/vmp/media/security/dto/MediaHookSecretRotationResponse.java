package com.genersoft.iot.vmp.media.security.dto;

import java.time.LocalDateTime;

/**
 * Secret-free result of a media Hook credential rotation.
 */
public record MediaHookSecretRotationResponse(
        String mediaServerId,
        long hookSecretVersion,
        LocalDateTime previousSecretExpiresAt,
        boolean appliedToNode) {
}
