package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.menu.SystemMenuController;
import com.genersoft.iot.vmp.auth.menu.SystemMenuService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SystemMenuController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemMenuControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class SystemMenuControllerTest {

    private static final String UPDATE_BODY = """
            {
              "parentId": 0,
              "menuName": "权限管理",
              "menuType": "M",
              "routeName": "System",
              "path": "/system",
              "component": "Layout",
              "icon": "setting",
              "orderNum": 10,
              "visible": 0,
              "status": 0,
              "cacheable": false,
              "external": false,
              "systemMenu": false
            }
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SystemMenuService menuService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    @Test
    void functionalPermissionIsCheckedBeforeMenuMutation() throws Exception {
        when(authz.require("system:menu:edit")).thenReturn(false);

        mockMvc.perform(put("/api/system/menu/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(menuService);
    }

    @Test
    void permittedUpdateDelegatesTheValidatedMenuCommand() throws Exception {
        when(authz.require("system:menu:edit")).thenReturn(true);

        mockMvc.perform(put("/api/system/menu/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNoContent());

        verify(menuService).update(
                org.mockito.ArgumentMatchers.eq(7L),
                argThat(request -> "权限管理".equals(request.menuName())
                        && "M".equals(request.menuType())
                        && "Layout".equals(request.component())));
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
