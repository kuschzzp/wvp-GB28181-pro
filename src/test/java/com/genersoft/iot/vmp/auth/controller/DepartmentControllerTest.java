package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.department.DepartmentController;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.dto.UpdateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DepartmentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(DepartmentControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class DepartmentControllerTest {

    private static final String UPDATE_BODY = """
            {
              "parentId": 100,
              "deptName": "摄像头运维组",
              "deptCode": "OPS-CAMERA",
              "orderNum": 10,
              "leaderUserId": 8,
              "status": 0
            }
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DepartmentTreeService departmentTreeService;
    @MockitoBean private DepartmentScopeService departmentScopeService;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    private AuthPrincipal principal;
    private DepartmentScope scope;

    @BeforeEach
    void setUp() {
        principal = new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "department-admin",
                100L,
                Set.of(3L),
                0L,
                false,
                Set.of("system:dept:edit"));
        scope = new DepartmentScope(false, Set.of(100L, 101L), false, 10L);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(departmentScopeService.resolveDecisionScope(principal, "system:dept:edit"))
                .thenReturn(scope);
    }

    @Test
    void functionalPermissionIsCheckedBeforeDepartmentScope() throws Exception {
        when(authz.require("system:dept:edit")).thenReturn(false);

        mockMvc.perform(put("/api/system/dept/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(departmentTreeService);
    }

    @Test
    void updateUsesThePermissionSpecificDecisionScope() throws Exception {
        when(authz.require("system:dept:edit")).thenReturn(true);

        mockMvc.perform(put("/api/system/dept/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNoContent());

        verify(departmentTreeService).update(
                any(UpdateDepartmentCommand.class),
                org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(scope));
        verify(departmentScopeService)
                .resolveDecisionScope(principal, "system:dept:edit");
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
