package com.genersoft.iot.vmp.media.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "authorization.media.download")
public record DownloadAccessProperties(
        Duration ticketTtl,
        int maxBatchSize,
        String snapshotRoot,
        String accelRoot,
        String accelInternalPrefix) {

    public DownloadAccessProperties {
        ticketTtl = ticketTtl == null ? Duration.ofMinutes(2) : ticketTtl;
        if (ticketTtl.isZero() || ticketTtl.isNegative()) {
            throw new IllegalArgumentException("download ticket TTL must be positive");
        }
        maxBatchSize = maxBatchSize <= 0 ? 100 : maxBatchSize;
        snapshotRoot = textOrDefault(snapshotRoot, "snap");
        accelRoot = accelRoot == null ? "" : accelRoot.trim();
        accelInternalPrefix = textOrDefault(
                accelInternalPrefix, "/_protected_media/");
        if (!accelInternalPrefix.startsWith("/")
                || !accelInternalPrefix.endsWith("/")) {
            throw new IllegalArgumentException(
                    "download accel internal prefix must start and end with '/'");
        }
    }

    private static String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
