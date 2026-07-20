package com.genersoft.iot.vmp.media.security.dto;

import java.time.Instant;

public record DownloadTicketResponse(
        String ticket,
        Instant issuedAt,
        Instant expiresAt,
        String downloadUrl,
        String fileName,
        String contentType) {
}
