package com.genersoft.iot.vmp.auth.compat;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.UserAuthenticationService;
import com.genersoft.iot.vmp.auth.user.SystemUserService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.vmanager.user.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyUserCompatibilityTest {

    @Mock private IUserService legacyUserService;
    @Mock private SystemUserService scopedUserService;
    @Mock private DepartmentScopeService departmentScopeService;
    @Mock private UserAuthenticationService authenticationService;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private AuthorizationProperties authorizationProperties;

    private UserController controller;
    private AuthPrincipal principal;
    private DepartmentScope scope;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                legacyUserService,
                scopedUserService,
                departmentScopeService,
                authenticationService,
                currentPrincipalService,
                authorizationProperties);
        principal = new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "legacy-admin",
                100L,
                Set.of(3L),
                0L,
                false,
                Set.of("system:user:edit"));
        scope = new DepartmentScope(false, Set.of(100L, 101L), false, 10L);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(departmentScopeService.resolveDecisionScope(principal, "system:user:edit"))
                .thenReturn(scope);
    }

    @Test
    void legacyPushKeyEndpointDelegatesToScopedUserService() {
        controller.changePushKey(20L, "rotated-key");

        verify(departmentScopeService)
                .resolveDecisionScope(principal, "system:user:edit");
        verify(scopedUserService).changePushKey(20L, "rotated-key", scope);
    }
}
