package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.media.security.dto.LegacyMediaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@Slf4j
public class LegacyMediaResponsePolicy {

    private static final String AUDIT_REASON = "LEGACY_MEDIA_RESPONSE_EMITTED";

    private final AuthorizationProperties authorizationProperties;
    private final MediaAccessProperties mediaAccessProperties;
    private final CurrentPrincipalService currentPrincipalService;
    private final AuthorizationAuditSink auditSink;
    private final Clock clock;

    @Autowired
    public LegacyMediaResponsePolicy(
            AuthorizationProperties authorizationProperties,
            MediaAccessProperties mediaAccessProperties,
            CurrentPrincipalService currentPrincipalService,
            AuthorizationAuditSink auditSink) {
        this(
                authorizationProperties,
                mediaAccessProperties,
                currentPrincipalService,
                auditSink,
                Clock.systemUTC());
    }

    LegacyMediaResponsePolicy(
            AuthorizationProperties authorizationProperties,
            MediaAccessProperties mediaAccessProperties,
            CurrentPrincipalService currentPrincipalService,
            AuthorizationAuditSink auditSink,
            Clock clock) {
        this.authorizationProperties = Objects.requireNonNull(
                authorizationProperties, "authorizationProperties must not be null");
        this.mediaAccessProperties = Objects.requireNonNull(
                mediaAccessProperties, "mediaAccessProperties must not be null");
        this.currentPrincipalService = Objects.requireNonNull(
                currentPrincipalService, "currentPrincipalService must not be null");
        this.auditSink = Objects.requireNonNull(
                auditSink, "auditSink must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public LegacyMediaResponse create(
            StreamInfo streamInfo,
            VideoResourceType resourceType,
            long resourceId) {
        Objects.requireNonNull(streamInfo, "streamInfo must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (authorizationProperties.mode() != AuthorizationMode.AUDIT
                || !mediaAccessProperties.legacyResponseEnabled()) {
            return null;
        }
        Instant now = clock.instant();
        if (!now.isBefore(mediaAccessProperties.legacyResponseSunset())) {
            log.warn("旧媒体地址兼容已超过 Sunset，拒绝返回长期地址，resourceType={}, resourceId={}",
                    resourceType, resourceId);
            return null;
        }

        recordAudit(resourceType, resourceId, now);
        log.warn("正在返回已弃用的长期媒体地址，Sunset={}，resourceType={}, resourceId={}",
                mediaAccessProperties.legacyResponseSunset(), resourceType, resourceId);
        return LegacyMediaResponse.from(
                streamInfo, mediaAccessProperties.legacyResponseSunset());
    }

    private void recordAudit(
            VideoResourceType resourceType,
            long resourceId,
            Instant now) {
        PrincipalType principalType = PrincipalType.INTERNAL;
        long principalId = 0L;
        Long departmentId = null;
        try {
            AuthPrincipal principal = currentPrincipalService.requireCurrent();
            principalType = principal.type();
            principalId = principal.id();
            departmentId = principal.deptId();
        } catch (RuntimeException ignored) {
            // Deferred callbacks may execute after the request SecurityContext is released.
        }
        auditSink.record(new AuthzAuditRecord(
                principalType,
                principalId,
                departmentId,
                MediaAccessPolicy.playPermission(resourceType),
                resourceType.name(),
                resourceId,
                true,
                AUDIT_REASON,
                null,
                null,
                LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
    }
}
