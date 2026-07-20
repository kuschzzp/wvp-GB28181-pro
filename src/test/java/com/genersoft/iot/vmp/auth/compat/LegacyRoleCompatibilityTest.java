package com.genersoft.iot.vmp.auth.compat;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.service.IRoleService;
import com.genersoft.iot.vmp.vmanager.user.RoleController;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RoleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import({LegacyApiDeprecationAdvice.class,
        LegacyRoleCompatibilityTest.MethodSecurityConfiguration.class})
@ActiveProfiles("test")
class LegacyRoleCompatibilityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IRoleService roleService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    @Test
    void legacyRoleResponseCarriesBrowserVisibleDeprecationHeaders() throws Exception {
        when(authz.require("system:role:list")).thenReturn(true);
        when(roleService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/role/all"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", LegacyApiDeprecationAdvice.SUNSET))
                .andExpect(header().string(
                        "Link", "</api/system/role>; rel=\"successor-version\""));
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
