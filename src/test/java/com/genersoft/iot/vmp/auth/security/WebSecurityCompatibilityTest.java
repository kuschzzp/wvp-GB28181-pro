package com.genersoft.iot.vmp.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationService;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.DefaultUserDetailsServiceImpl;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.conf.security.LogoutHandler;
import com.genersoft.iot.vmp.conf.security.WebSecurityConfig;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationService;
import com.genersoft.iot.vmp.media.security.MediaHookReplayGuard;
import com.genersoft.iot.vmp.media.security.MediaNodePrincipal;
import com.genersoft.iot.vmp.media.security.MediaNodeType;
import com.genersoft.iot.vmp.service.IUserService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        WebSecurityConfig.class,
        WebSecurityCompatibilityTest.TestBeans.class
})
@WebAppConfiguration
@ActiveProfiles("test")
class WebSecurityCompatibilityTest {

    @jakarta.annotation.Resource(name = "springSecurityFilterChain")
    private FilterChainProxy filterChainProxy;

    @jakarta.annotation.Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @jakarta.annotation.Resource
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Test
    void mediaHooksUseTheirOwnCompatibilityChainWithoutUserCredentials() throws Exception {
        List<Filter> hookFilters = filterChainProxy.getFilters("/index/hook/zlm/on_publish");
        List<Filter> applicationFilters = filterChainProxy.getFilters("/api/device/query/devices");

        assertFalse(hookFilters.contains(jwtAuthenticationFilter));
        assertFalse(hookFilters.contains(apiKeyAuthenticationFilter));
        assertTrue(applicationFilters.contains(jwtAuthenticationFilter));
        assertTrue(applicationFilters.contains(apiKeyAuthenticationFilter));

        AtomicBoolean endpointReached = new AtomicBoolean();
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest(
                        "POST", "/index/hook/zlm/on_publish");
        request.setServletPath("/index/hook/zlm/on_publish");
        request.setParameter("mediaServerId", "media-server-1");
        request.setParameter("hookSecret", "hook-secret");
        request.setParameter("hookTimestamp", "1710000000");
        request.setParameter("hookNonce", "unique-nonce");
        request.setContentType("application/json");
        request.setContent("{\"mediaServerId\":\"media-server-1\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) -> endpointReached.set(true));

        assertTrue(endpointReached.get(), "Plan 1 不能让既有 ZLM/ABL 回调被用户登录链拦截");
    }

    @Test
    void getLoginRequestsReachMvcSoItCanReturnMethodNotAllowed() throws Exception {
        for (String loginPath : List.of("/api/auth/login", "/api/user/login")) {
            AtomicBoolean mvcReached = new AtomicBoolean();
            org.springframework.mock.web.MockHttpServletRequest request =
                    new org.springframework.mock.web.MockHttpServletRequest("GET", loginPath);
            request.setServletPath(loginPath);
            org.springframework.mock.web.MockHttpServletResponse response =
                    new org.springframework.mock.web.MockHttpServletResponse();

            filterChainProxy.doFilter(request, response, (servletRequest, servletResponse) -> {
                mvcReached.set(true);
                ((jakarta.servlet.http.HttpServletResponse) servletResponse)
                        .setStatus(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            });

            assertTrue(mvcReached.get(), loginPath + " 应交由 MVC 判断请求方法");
            assertEquals(405, response.getStatus());
        }
    }

    @Test
    void publicShareTicketExchangeReachesMvcWithoutUserCredentials() throws Exception {
        AtomicBoolean endpointReached = new AtomicBoolean();
        String path = "/api/public/share/abcdefghijklmnopqrstuvwxyzABCDEF/play-ticket";
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) -> endpointReached.set(true));

        assertTrue(endpointReached.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void publicDownloadTicketConsumptionReachesMvcWithoutUserCredentials() throws Exception {
        AtomicBoolean endpointReached = new AtomicBoolean();
        String path = "/api/media/download/"
                + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) -> endpointReached.set(true));

        assertTrue(endpointReached.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void downloadTicketIssuanceStillRequiresAuthentication() throws Exception {
        AtomicBoolean endpointReached = new AtomicBoolean();
        String path = "/api/media/download-ticket";
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) -> endpointReached.set(true));

        assertFalse(endpointReached.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    void logWebSocketHandshakeReachesTicketConfiguratorWithoutJwtHeader()
            throws Exception {
        AtomicBoolean websocketContainerReached = new AtomicBoolean();
        String path = "/channel/log";
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("Upgrade", "websocket");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) ->
                        websocketContainerReached.set(true));

        assertTrue(websocketContainerReached.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void logWebSocketTicketIssuanceRequiresAuthenticatedHttpRequest()
            throws Exception {
        AtomicBoolean endpointReached = new AtomicBoolean();
        String path = "/api/log/ws-ticket";
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filterChainProxy.doFilter(request, response,
                (servletRequest, servletResponse) -> endpointReached.set(true));

        assertFalse(endpointReached.get());
        assertEquals(401, response.getStatus());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestBeans {

        @Bean
        UserSetting userSetting() {
            UserSetting setting = new UserSetting();
            setting.setInterfaceAuthentication(true);
            setting.setDocEnable(true);
            setting.setAllowedOrigins(List.of());
            return setting;
        }

        @Bean
        DefaultUserDetailsServiceImpl defaultUserDetailsService() {
            return mock(DefaultUserDetailsServiceImpl.class);
        }

        @Bean
        LogoutHandler logoutHandler() {
            return mock(LogoutHandler.class);
        }

        @Bean
        AnonymousAuthenticationEntryPoint anonymousAuthenticationEntryPoint(ObjectMapper objectMapper) {
            return new AnonymousAuthenticationEntryPoint(objectMapper);
        }

        @Bean
        JsonAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper objectMapper) {
            return new JsonAccessDeniedHandler(objectMapper);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                UserSetting userSetting,
                AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
            return new JwtAuthenticationFilter(
                    mock(JwtUtils.class),
                    mock(TokenVersionService.class),
                    mock(TokenRevocationService.class),
                    mock(IUserService.class),
                    mock(PermissionRepository.class),
                    mock(PermissionService.class),
                    userSetting,
                    authenticationEntryPoint);
        }

        @Bean
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
                AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
            return new ApiKeyAuthenticationFilter(
                    mock(ApiKeyAuthenticationService.class), authenticationEntryPoint);
        }

        @Bean
        MediaHookAuthenticationFilter mediaHookAuthenticationFilter(
                ObjectMapper objectMapper,
                AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
            MediaHookAuthenticationService authenticationService =
                    mock(MediaHookAuthenticationService.class);
            MediaHookReplayGuard replayGuard = mock(MediaHookReplayGuard.class);
            when(authenticationService.authenticate(
                    any(MediaNodeType.class),
                    anyString(),
                    anyString(),
                    any(java.net.InetAddress.class)))
                    .thenReturn(new MediaNodePrincipal(
                            "media-server-1", MediaNodeType.ZLM, 1));
            when(replayGuard.accept(
                    any(MediaNodePrincipal.class),
                    anyString(),
                    any(byte[].class),
                    anyString(),
                    anyString()))
                    .thenReturn(true);
            return new MediaHookAuthenticationFilter(
                    authenticationService,
                    replayGuard,
                    objectMapper,
                    authenticationEntryPoint);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
