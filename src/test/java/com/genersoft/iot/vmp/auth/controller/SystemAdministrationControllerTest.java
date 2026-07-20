package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.auth.user.SystemUserController;
import com.genersoft.iot.vmp.auth.user.SystemUserService;
import com.genersoft.iot.vmp.auth.user.dto.AdminUpdateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeUserStatusRequest;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.service.IRoleService;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SystemUserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemAdministrationControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class SystemAdministrationControllerTest {

    private static final String UPDATE_BODY = """
            {
              "nickname": "范围用户",
              "deptId": 100
            }
            """;

    private static final String STATUS_BODY = """
            {
              "status": 1,
              "locked": true
            }
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SystemUserService systemUserService;
    @MockitoBean private DepartmentScopeService departmentScopeService;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean private DepartmentTreeService departmentTreeService;
    @MockitoBean private IRoleService roleService;
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
                Set.of("system:user:edit"));
        scope = new DepartmentScope(false, Set.of(100L), false, 10L);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(departmentScopeService.resolveDecisionScope(principal, "system:user:edit"))
                .thenReturn(scope);
    }

    @Test
    void functionalPermissionIsRejectedBeforeObjectScope() throws Exception {
        when(authz.require("system:user:edit")).thenReturn(false);

        mockMvc.perform(put("/api/system/user/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(systemUserService);
    }

    @Test
    void outOfScopeTargetIsHiddenAsNotFound() throws Exception {
        when(authz.require("system:user:edit")).thenReturn(true);
        doThrow(new ControllerException(404, "用户不存在"))
                .when(systemUserService)
                .update(eq(20L), any(AdminUpdateUserRequest.class), eq(scope));

        mockMvc.perform(put("/api/system/user/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void permissionAndScopeTogetherAllowUpdate() throws Exception {
        when(authz.require("system:user:edit")).thenReturn(true);

        mockMvc.perform(put("/api/system/user/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void statusMutationRequiresItsOwnPermissionAndDecisionScope() throws Exception {
        when(authz.require("system:user:changeStatus")).thenReturn(true);
        when(departmentScopeService.resolveDecisionScope(principal, "system:user:changeStatus"))
                .thenReturn(scope);

        mockMvc.perform(put("/api/system/user/20/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_BODY))
                .andExpect(status().isNoContent());

        verify(systemUserService).changeStatus(
                eq(20L), any(ChangeUserStatusRequest.class), eq(scope));
    }

    @Test
    void userReferencesReuseUserListScopeAndHideSystemRolesFromOrdinaryAdmins() throws Exception {
        Role ordinary = new Role();
        ordinary.setId(3L);
        Role system = new Role();
        system.setId(1L);
        system.setSystemRole(1);
        when(authz.require("system:user:list")).thenReturn(true);
        when(departmentScopeService.resolveDecisionScope(principal, "system:user:list"))
                .thenReturn(scope);
        when(departmentTreeService.tree(scope)).thenReturn(List.of());
        when(roleService.getAll()).thenReturn(List.of(system, ordinary));

        mockMvc.perform(get("/api/system/user/references"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departments").isArray())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0].id").value(3));
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
