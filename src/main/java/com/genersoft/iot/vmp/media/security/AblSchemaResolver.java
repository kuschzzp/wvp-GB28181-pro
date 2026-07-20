package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class AblSchemaResolver {

    private static final String AUDIT_PERMISSION = "media:hook:on-play";

    private final Map<Integer, MediaSchema> schemas;
    private final AuthorizationMode mode;
    private final AuthorizationAuditSink auditSink;

    public AblSchemaResolver(
            MediaAccessProperties mediaAccessProperties,
            AuthorizationProperties authorizationProperties,
            AuthorizationAuditSink auditSink) {
        this.schemas = parseMappings(
                Objects.requireNonNull(
                        mediaAccessProperties,
                        "mediaAccessProperties must not be null")
                        .ablSchemaByNetworkType());
        this.mode = Objects.requireNonNull(
                authorizationProperties,
                "authorizationProperties must not be null").mode();
        this.auditSink = Objects.requireNonNull(
                auditSink, "auditSink must not be null");
    }

    public AblSchemaDecision resolve(Integer networkType) {
        MediaSchema schema = networkType == null
                ? null
                : schemas.get(networkType);
        if (schema != null) {
            return AblSchemaDecision.mapped(schema);
        }

        String reason = networkType == null
                ? "ABL_NETWORK_TYPE_MISSING"
                : "ABL_NETWORK_TYPE_UNMAPPED_" + networkType;
        auditSink.record(new AuthzAuditRecord(
                PrincipalType.INTERNAL,
                0L,
                null,
                AUDIT_PERMISSION,
                "ABL_NETWORK_TYPE",
                networkType == null ? null : networkType.longValue(),
                false,
                reason,
                null,
                null,
                LocalDateTime.now()));
        log.warn("[ABL HOOK] on_play networkType 未显式映射：{}，mode={}",
                networkType, mode);
        return mode == AuthorizationMode.AUDIT
                ? AblSchemaDecision.legacyAudit()
                : AblSchemaDecision.deny();
    }

    private Map<Integer, MediaSchema> parseMappings(
            Map<Integer, String> configured) {
        LinkedHashMap<Integer, MediaSchema> parsed = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : configured.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0) {
                throw new IllegalArgumentException(
                        "ABL networkType must be a non-negative integer");
            }
            parsed.put(entry.getKey(), MediaSchema.parse(entry.getValue()));
        }
        return Map.copyOf(parsed);
    }
}
