package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.impl.PermissionEnforcerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionEnforcerTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private CurrentPrincipalService currentPrincipalService;

    @Mock
    private AuthorizationAuditSink auditSink;

    @Test
    void auditModeRecordsRealDenyButKeepsLegacyRequestCompatible() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "video:channel:play")).thenReturn(false);
        PermissionEnforcer enforcer = enforcer(AuthorizationMode.AUDIT, "prod");

        assertTrue(enforcer.check("video:channel:play"));

        ArgumentCaptor<AuthzAuditRecord> captor = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(captor.capture());
        assertFalse(captor.getValue().allowed());
        assertTrue(captor.getValue().reasonCode().equals("PERMISSION_MISSING"));
    }

    @Test
    void alwaysEnforcedPermissionReturnsRealDenyInAuditMode() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "system:user:add")).thenReturn(false);
        PermissionEnforcer enforcer = enforcer(AuthorizationMode.AUDIT, "prod");

        assertFalse(enforcer.require("system:user:add"));

        ArgumentCaptor<AuthzAuditRecord> captor = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(captor.capture());
        assertFalse(captor.getValue().allowed());
        assertTrue(captor.getValue().reasonCode().equals("PERMISSION_MISSING"));
    }

    @Test
    void enforceModeReturnsTheRealDecision() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "video:channel:play")).thenReturn(false);
        PermissionEnforcer enforcer = enforcer(AuthorizationMode.ENFORCE, "prod");

        assertFalse(enforcer.check("video:channel:play"));
        verify(auditSink).record(org.mockito.ArgumentMatchers.any(AuthzAuditRecord.class));
    }

    @Test
    void disabledModeIsOnlyAvailableInDevelopmentProfiles() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "video:channel:play")).thenReturn(false);

        PermissionEnforcer enforcer = enforcer(AuthorizationMode.DISABLED, "274-dev");

        assertTrue(enforcer.check("video:channel:play"));
        verifyNoInteractions(auditSink);
    }

    @Test
    void alwaysEnforcedPermissionStillReturnsRealDenyInDisabledMode() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "system:user:add")).thenReturn(false);

        PermissionEnforcer enforcer = enforcer(AuthorizationMode.DISABLED, "local");

        assertFalse(enforcer.require("system:user:add"));
        verifyNoInteractions(auditSink);
    }

    @Test
    void disabledModeFailsFastInProduction() {
        assertThrows(IllegalStateException.class,
                () -> enforcer(AuthorizationMode.DISABLED, "prod"));
    }

    @Test
    void permissionCodeAllowsApprovedHyphenatedActions() {
        AuthPrincipal principal = principal();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionService.hasPermission(principal, "video:platform:share-channel")).thenReturn(true);

        assertTrue(enforcer(AuthorizationMode.ENFORCE, "prod")
                .check("video:platform:share-channel"));
    }

    @Test
    void invalidPermissionCodeIsRejectedBeforeReadingThePrincipal() {
        PermissionEnforcer enforcer = enforcer(AuthorizationMode.AUDIT, "prod");

        assertThrows(IllegalArgumentException.class,
                () -> enforcer.check("video:channel"));
        verifyNoInteractions(currentPrincipalService, permissionService, auditSink);
    }

    private PermissionEnforcer enforcer(AuthorizationMode mode, String activeProfile) {
        AuthorizationProperties properties = new AuthorizationProperties(
                mode, false, Duration.ofMinutes(5));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return new PermissionEnforcerImpl(
                permissionService,
                currentPrincipalService,
                auditSink,
                properties,
                environment);
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "operator",
                5L,
                Set.of(7L),
                2L,
                false,
                Set.of("video:channel:list"));
    }
}
