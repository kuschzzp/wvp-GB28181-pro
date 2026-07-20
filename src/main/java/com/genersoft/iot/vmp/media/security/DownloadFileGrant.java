package com.genersoft.iot.vmp.media.security;

import java.util.Objects;

/** Server-resolved file target. Never build this object from a client path. */
public record DownloadFileGrant(
        DownloadStorageType storageType,
        String path,
        String allowedRoot,
        String fileName,
        String mediaServerId) {

    public DownloadFileGrant {
        storageType = Objects.requireNonNull(storageType, "storageType must not be null");
        path = requireText(path, "path");
        allowedRoot = requireText(allowedRoot, "allowedRoot");
        fileName = requireText(fileName, "fileName");
        mediaServerId = mediaServerId == null ? null : mediaServerId.trim();
        if (storageType == DownloadStorageType.REMOTE_ZLM
                && (mediaServerId == null || mediaServerId.isBlank())) {
            throw new IllegalArgumentException("remote target requires mediaServerId");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
