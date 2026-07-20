package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AblSchemaResolverTest {

    @Test
    void explicitNetworkTypeMappingIsRequiredAndParsedAtStartup() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        AblSchemaResolver resolver = resolver(
                AuthorizationMode.ENFORCE,
                Map.of(7, "ws-flv", 8, "RTSP"),
                auditSink);

        AblSchemaDecision wsFlv = resolver.resolve(7);
        AblSchemaDecision rtsp = resolver.resolve(8);

        assertTrue(wsFlv.mapped());
        assertEquals(MediaSchema.WS_FLV, wsFlv.schema());
        assertEquals(MediaSchema.RTSP, rtsp.schema());
        verifyNoInteractions(auditSink);
    }

    @Test
    void unmappedTypeIsAuditedAndOnlyAuditModeReturnsCompatibility() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        AblSchemaResolver resolver = resolver(
                AuthorizationMode.AUDIT, Map.of(), auditSink);

        AblSchemaDecision decision = resolver.resolve(99);

        assertFalse(decision.mapped());
        assertTrue(decision.legacyAuditCompatibility());
        ArgumentCaptor<AuthzAuditRecord> recordCaptor =
                ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(recordCaptor.capture());
        AuthzAuditRecord record = recordCaptor.getValue();
        assertEquals("media:hook:on-play", record.permissionCode());
        assertEquals("ABL_NETWORK_TYPE", record.resourceType());
        assertEquals(99L, record.resourceId());
        assertFalse(record.allowed());
        assertEquals("ABL_NETWORK_TYPE_UNMAPPED_99", record.reasonCode());
    }

    @Test
    void enforceModeFailsClosedForMissingType() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        AblSchemaResolver resolver = resolver(
                AuthorizationMode.ENFORCE, Map.of(), auditSink);

        AblSchemaDecision decision = resolver.resolve(null);

        assertFalse(decision.mapped());
        assertFalse(decision.legacyAuditCompatibility());
        ArgumentCaptor<AuthzAuditRecord> recordCaptor =
                ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(recordCaptor.capture());
        assertEquals(
                "ABL_NETWORK_TYPE_MISSING",
                recordCaptor.getValue().reasonCode());
    }

    @Test
    void invalidConfiguredSchemaFailsApplicationStartup() {
        assertThrows(IllegalArgumentException.class, () -> resolver(
                AuthorizationMode.AUDIT,
                Map.of(7, "guessed-flv"),
                mock(AuthorizationAuditSink.class)));
    }

    private AblSchemaResolver resolver(
            AuthorizationMode mode,
            Map<Integer, String> mappings,
            AuthorizationAuditSink auditSink) {
        MediaAccessProperties mediaProperties = new MediaAccessProperties(
                false,
                Instant.parse("2026-10-01T00:00:00Z"),
                "test-pepper",
                Duration.ofSeconds(2),
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                mappings);
        AuthorizationProperties authorizationProperties =
                new AuthorizationProperties(mode, false, Duration.ofMinutes(5));
        return new AblSchemaResolver(
                mediaProperties, authorizationProperties, auditSink);
    }
}
