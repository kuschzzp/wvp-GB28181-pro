package com.genersoft.iot.vmp.auth.config;

import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "authorization")
public record AuthorizationProperties(
        AuthorizationMode mode,
        boolean allowUrlTokenCompatibility,
        Duration principalCacheTtl,
        boolean legacyMd5LoginEnabled,
        String legacyMd5LoginSunset,
        String apiKeyPepper,
        boolean legacyApiKeyEnabled,
        String legacyApiKeySunset,
        boolean legacyRoleDualWriteEnabled) {

    public AuthorizationProperties(
            AuthorizationMode mode,
            boolean allowUrlTokenCompatibility,
            Duration principalCacheTtl) {
        this(mode, allowUrlTokenCompatibility, principalCacheTtl,
                false, "2026-10-01T00:00:00Z", null, false,
                "2026-10-01T00:00:00Z", true);
    }

    public AuthorizationProperties(
            AuthorizationMode mode,
            boolean allowUrlTokenCompatibility,
            Duration principalCacheTtl,
            boolean legacyMd5LoginEnabled,
            String legacyMd5LoginSunset,
            String apiKeyPepper,
            boolean legacyApiKeyEnabled,
            String legacyApiKeySunset) {
        this(mode, allowUrlTokenCompatibility, principalCacheTtl,
                legacyMd5LoginEnabled, legacyMd5LoginSunset, apiKeyPepper,
                legacyApiKeyEnabled, legacyApiKeySunset, true);
    }

    @ConstructorBinding
    public AuthorizationProperties {
        mode = mode == null ? AuthorizationMode.AUDIT : mode;
        principalCacheTtl = principalCacheTtl == null ? Duration.ofMinutes(5) : principalCacheTtl;
        legacyMd5LoginSunset = legacyMd5LoginSunset == null || legacyMd5LoginSunset.isBlank()
                ? "2026-10-01T00:00:00Z"
                : legacyMd5LoginSunset;
        apiKeyPepper = apiKeyPepper == null ? "" : apiKeyPepper;
        legacyApiKeySunset = legacyApiKeySunset == null || legacyApiKeySunset.isBlank()
                ? "2026-10-01T00:00:00Z"
                : legacyApiKeySunset;
        if (principalCacheTtl.isNegative() || principalCacheTtl.isZero()) {
            throw new IllegalArgumentException("authorization.principal-cache-ttl must be positive");
        }
    }
}
