package com.genersoft.iot.vmp.media.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "authorization.media")
public record MediaAccessProperties(
        boolean legacyResponseEnabled,
        Instant legacyResponseSunset,
        String hookSecretPepper,
        Duration hookReplayWindow,
        Duration hookTimestampSkew,
        Duration hookNonceTtl,
        Map<Integer, String> ablSchemaByNetworkType) {

    private static final Instant DEFAULT_SUNSET =
            Instant.parse("2026-10-01T00:00:00Z");

    public MediaAccessProperties(
            boolean legacyResponseEnabled,
            Instant legacyResponseSunset) {
        this(
                legacyResponseEnabled,
                legacyResponseSunset,
                "",
                Duration.ofSeconds(2),
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                Map.of());
    }

    public MediaAccessProperties(
            boolean legacyResponseEnabled,
            Instant legacyResponseSunset,
            String hookSecretPepper,
            Duration hookReplayWindow,
            Duration hookTimestampSkew,
            Duration hookNonceTtl) {
        this(
                legacyResponseEnabled,
                legacyResponseSunset,
                hookSecretPepper,
                hookReplayWindow,
                hookTimestampSkew,
                hookNonceTtl,
                Map.of());
    }

    public MediaAccessProperties {
        legacyResponseSunset = legacyResponseSunset == null
                ? DEFAULT_SUNSET
                : legacyResponseSunset;
        hookSecretPepper = hookSecretPepper == null ? "" : hookSecretPepper;
        hookReplayWindow = positiveOrDefault(
                hookReplayWindow, Duration.ofSeconds(2), "hookReplayWindow");
        hookTimestampSkew = positiveOrDefault(
                hookTimestampSkew, Duration.ofMinutes(2), "hookTimestampSkew");
        hookNonceTtl = positiveOrDefault(
                hookNonceTtl, Duration.ofMinutes(5), "hookNonceTtl");
        ablSchemaByNetworkType = ablSchemaByNetworkType == null
                ? Map.of()
                : Map.copyOf(ablSchemaByNetworkType);
    }

    private static Duration positiveOrDefault(
            Duration value,
            Duration defaultValue,
            String fieldName) {
        Duration resolved = value == null ? defaultValue : value;
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return resolved;
    }
}
