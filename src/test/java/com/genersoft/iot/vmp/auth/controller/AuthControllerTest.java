package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.auth.dto.ProfileResponse;
import com.genersoft.iot.vmp.auth.dto.UserSummary;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.service.ProfileService;
import com.genersoft.iot.vmp.auth.service.UserAuthenticationService;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAuthenticationService authenticationService;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void loginOnlyAcceptsPostJsonAndNeverReturnsCredentials() throws Exception {
        Instant expiresAt = Instant.parse("2026-07-13T12:00:00Z");
        when(authenticationService.login(any(LoginRequest.class), anyString()))
                .thenReturn(new LoginResponse("signed-token", expiresAt, "wvp-1"));

        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"plain-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("access-token", "signed-token"))
                .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-13T12:00:00Z"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.credentials").doesNotExist());
    }

    @Test
    void rejectsInvalidLoginPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileExposesStableSystemUserFlag() throws Exception {
        when(profileService.currentProfile()).thenReturn(new ProfileResponse(
                new UserSummary(1L, "admin", "管理员", 1L, 0, false, Set.of(1L), null),
                Set.of("super_admin"),
                Set.of("system:role:add"),
                7L,
                true));

        mockMvc.perform(get("/api/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemUser").value(true));
    }
}
