package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.menu.SystemMenuService;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.role.SystemRoleController;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.service.IRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SystemRoleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemRoleControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class SystemRoleControllerTest {

    private static final String CUSTOM_ROLE_BODY = """
            {
              "name": "运维角色",
              "roleKey": "ops_admin",
              "roleSort": 10,
              "deptDataScope": "CUSTOM",
              "videoScopeType": "CUSTOM",
              "status": 0,
              "systemRole": false,
              "menuIds": [11, 12],
              "deptIds": [100, 101],
              "resourceGroupIds": [200, 201]
            }
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IRoleService roleService;
    @MockitoBean private SystemMenuService menuService;
    @MockitoBean private DepartmentTreeService departmentTreeService;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    @Test
    void functionalPermissionIsCheckedBeforeRoleMutation() throws Exception {
        when(authz.require("system:role:edit")).thenReturn(false);

        mockMvc.perform(put("/api/system/role/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOM_ROLE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleService);
    }

    @Test
    void customRolePersistsMenusAndDepartmentGrantsTogether() throws Exception {
        when(authz.require("system:role:edit")).thenReturn(true);

        mockMvc.perform(put("/api/system/role/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOM_ROLE_BODY))
                .andExpect(status().isNoContent());

        verify(roleService).saveRole(
                argThat(command -> command.id() == 7L
                        && "CUSTOM".equals(command.deptDataScope())),
                org.mockito.ArgumentMatchers.eq(Set.of(11L, 12L)),
                org.mockito.ArgumentMatchers.eq(Set.of(100L, 101L)),
                org.mockito.ArgumentMatchers.eq(Set.of(200L, 201L)));
    }

    @Test
    void serviceLevelVideoScopeDenialIsReturnedAsForbidden() throws Exception {
        when(authz.require("system:role:edit")).thenReturn(true);
        doThrow(new AccessDeniedException("缺少视频资源组分配权限"))
                .when(roleService)
                .saveRole(any(), any(), any(), any());

        mockMvc.perform(put("/api/system/role/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOM_ROLE_BODY.replace("[200, 201]", "[200, 202]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleEditSubmitsPreservedVideoScopeToTheService() throws Exception {
        when(authz.require("system:role:edit")).thenReturn(true);

        mockMvc.perform(put("/api/system/role/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOM_ROLE_BODY))
                .andExpect(status().isNoContent());

        verify(roleService).saveRole(any(), any(), any(), any());
    }

    @Test
    void roleStatusUsesIndependentPermission() throws Exception {
        when(authz.require("system:role:changeStatus")).thenReturn(true);

        mockMvc.perform(put("/api/system/role/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isNoContent());

        verify(roleService).changeStatus(7L, 1);
    }

    @Test
    void roleReferencesAreAvailableWithRoleListPermissionAlone() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "role-admin",
                100L,
                Set.of(3L),
                0L,
                false,
                Set.of("system:role:list"));
        when(authz.require("system:role:list")).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(menuService.list()).thenReturn(java.util.List.of());
        when(departmentTreeService.tree(any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/system/role/references"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menus").isArray())
                .andExpect(jsonPath("$.data.departments").isArray());
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
