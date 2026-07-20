package com.genersoft.iot.vmp.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtils jwtUtils;
    @Mock private TokenVersionService tokenVersionService;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private IUserService userService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionService permissionService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        UserSetting settings = new UserSetting();
        settings.setDocEnable(true);
        filter = new JwtAuthenticationFilter(
                jwtUtils,
                tokenVersionService,
                tokenRevocationService,
                userService,
                permissionRepository,
                permissionService,
                settings,
                new AnonymousAuthenticationEntryPoint(new ObjectMapper()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void headerTokenCreatesCredentialFreeAuthenticationWithRealAuthorities() throws Exception {
        VerifiedAccessToken token = token();
        User user = activeUser();
        when(jwtUtils.verifyAccessToken("signed-token")).thenReturn(token);
        when(tokenRevocationService.isRevoked("jti-1")).thenReturn(false);
        when(tokenVersionService.matches(12L, 7L)).thenReturn(true);
        when(userService.getUserById(12L)).thenReturn(user);
        when(permissionRepository.findEnabledRoleIds(12L)).thenReturn(List.of(3L));
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of("video:channel:play"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/device/query/devices");
        request.addHeader(JwtUtils.HEADER, "signed-token");
        MockHttpServletResponse response = invoke(request);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertNull(authentication.getCredentials());
        assertInstanceOf(AuthPrincipal.class, authentication.getPrincipal());
        assertEquals(Set.of("video:channel:play"),
                AuthorityUtils.authorityListToSet(authentication.getAuthorities()));
    }

    @Test
    void queryTokenAndApiKeyHeaderAreIgnoredByJwtFilter() throws Exception {
        MockHttpServletRequest queryRequest = new MockHttpServletRequest("GET", "/api/device/query/devices");
        queryRequest.setParameter(JwtUtils.HEADER, "query-token");
        assertEquals(200, invoke(queryRequest).getStatus());

        MockHttpServletRequest apiKeyRequest = new MockHttpServletRequest("GET", "/api/device/query/devices");
        apiKeyRequest.addHeader(JwtUtils.API_KEY_HEADER, "machine-key");
        assertEquals(200, invoke(apiKeyRequest).getStatus());
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void simultaneousJwtAndApiKeyCredentialsAreRejectedBeforeEitherIsParsed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/device/query/devices");
        request.addHeader(JwtUtils.HEADER, "signed-token");
        request.addHeader(JwtUtils.API_KEY_HEADER, "machine-key");

        assertEquals(401, invoke(request).getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void tokenVersionMismatchAndRevokedJtiReturn401() throws Exception {
        when(jwtUtils.verifyAccessToken("old-token")).thenReturn(token());
        when(tokenRevocationService.isRevoked("jti-1")).thenReturn(false);
        when(tokenVersionService.matches(12L, 7L)).thenReturn(false);
        MockHttpServletRequest oldRequest = new MockHttpServletRequest("GET", "/api/device/query/devices");
        oldRequest.addHeader(JwtUtils.HEADER, "old-token");
        assertEquals(401, invoke(oldRequest).getStatus());

        SecurityContextHolder.clearContext();
        when(jwtUtils.verifyAccessToken("revoked-token")).thenReturn(token());
        when(tokenRevocationService.isRevoked("jti-1")).thenReturn(true);
        MockHttpServletRequest revokedRequest = new MockHttpServletRequest("GET", "/api/device/query/devices");
        revokedRequest.addHeader(JwtUtils.HEADER, "revoked-token");
        assertEquals(401, invoke(revokedRequest).getStatus());
    }

    @Test
    void disabledDocumentationReturns404ForUiSchemaAndAssets() throws Exception {
        UserSetting settings = new UserSetting();
        settings.setDocEnable(false);
        filter = new JwtAuthenticationFilter(
                jwtUtils,
                tokenVersionService,
                tokenRevocationService,
                userService,
                permissionRepository,
                permissionService,
                settings,
                new AnonymousAuthenticationEntryPoint(new ObjectMapper()));

        for (String path : List.of(
                "/doc.html",
                "/swagger-ui/index.html",
                "/swagger-resources/configuration/ui",
                "/v3/api-docs",
                "/webjars/swagger-ui/index.css")) {
            assertEquals(404, invoke(new MockHttpServletRequest("GET", path)).getStatus(), path);
        }
        verifyNoInteractions(jwtUtils);
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private VerifiedAccessToken token() {
        return new VerifiedAccessToken(
                12L,
                "jti-1",
                7L,
                Instant.parse("2026-07-13T10:00:00Z"),
                Instant.parse("2026-07-13T11:00:00Z"));
    }

    private User activeUser() {
        User user = new User();
        user.setId(12L);
        user.setUsername("operator");
        return user;
    }
}
