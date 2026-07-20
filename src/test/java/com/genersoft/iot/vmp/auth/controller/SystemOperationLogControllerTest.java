package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.audit.AuthorizationAuditQueryService;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.vmanager.system.log.SystemOperationLogController;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SystemOperationLogController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemOperationLogControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class SystemOperationLogControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthorizationAuditQueryService auditQueryService;
    @MockitoBean private DepartmentScopeService departmentScopeService;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    private AuthPrincipal principal;
    private DepartmentScope scope;

    @BeforeEach
    void setUp() {
        principal = new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "audit-admin",
                100L,
                Set.of(3L),
                7L,
                false,
                Set.of("operations:log:view"));
        scope = new DepartmentScope(false, Set.of(100L, 101L), false, 12L);
    }

    @Test
    void functionalPermissionIsCheckedBeforeAuditQuery() throws Exception {
        when(authz.require("operations:log:view")).thenReturn(false);

        mockMvc.perform(get("/api/system/operation-log"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditQueryService, departmentScopeService);
    }

    @Test
    void permittedQueryUsesPermissionSpecificDecisionScope() throws Exception {
        when(authz.require("operations:log:view")).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(departmentScopeService.resolveDecisionScope(principal, "operations:log:view"))
                .thenReturn(scope);
        when(auditQueryService.query(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(scope)))
                .thenReturn(new PageInfo<>(List.of()));

        mockMvc.perform(get("/api/system/operation-log")
                        .param("decision", "deny")
                        .param("principalType", "user"))
                .andExpect(status().isOk());

        verify(auditQueryService).query(
                argThat(query -> "DENY".equals(query.decision())
                        && "USER".equals(query.principalType())),
                org.mockito.ArgumentMatchers.eq(scope));
        verify(departmentScopeService)
                .resolveDecisionScope(principal, "operations:log:view");
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
