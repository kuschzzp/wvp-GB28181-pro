package com.genersoft.iot.vmp.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.conf.GlobalExceptionHandler;
import com.genersoft.iot.vmp.conf.GlobalResponseAdvice;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.authorization.video.resolver.AmbiguousVideoResourceException;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.DefaultUserDetailsServiceImpl;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.conf.security.LogoutHandler;
import com.genersoft.iot.vmp.conf.security.WebSecurityConfig;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.cors.CorsConfiguration;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityStatusCodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unauthenticatedAndForbiddenAreDifferentHttpStatuses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/profile");

        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        new AnonymousAuthenticationEntryPoint(objectMapper).commence(
                request,
                unauthorized,
                new InsufficientAuthenticationException("missing"));
        assertEquals(401, unauthorized.getStatus());

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        new JsonAccessDeniedHandler(objectMapper).handle(
                request,
                forbidden,
                new AccessDeniedException("denied"));
        assertEquals(403, forbidden.getStatus());
    }

    @Test
    void emptyAllowedOriginsDoesNotReflectAnUntrustedOrigin() {
        UserSetting settings = new UserSetting();
        settings.setInterfaceAuthentication(true);
        settings.setAllowedOrigins(java.util.List.of());
        WebSecurityConfig security = new WebSecurityConfig(
                settings,
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                mock(DefaultUserDetailsServiceImpl.class),
                mock(LogoutHandler.class),
                mock(AnonymousAuthenticationEntryPoint.class),
                mock(JsonAccessDeniedHandler.class),
                mock(JwtAuthenticationFilter.class),
                mock(ApiKeyAuthenticationFilter.class),
                mock(MediaHookAuthenticationFilter.class));

        CorsConfiguration configuration = security.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/auth/login"));

        assertNull(configuration.checkOrigin("https://evil.example"));
        assertTrue(configuration.getAllowedHeaders().contains(RequestIdResolver.HEADER_NAME));
    }

    @Test
    void controllerLayerUsesTheSameSecurityErrorShapeAndRequestId() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Request-Id", "request-123");

        var unauthorized = handler.handleAuthenticationFailure(
                new BadCredentialsException("隐藏的内部原因"), request);
        var forbidden = handler.handleAccessDenied(
                new AccessDeniedException("隐藏的内部原因"), request);

        assertEquals(401, unauthorized.getStatusCode().value());
        assertNotNull(unauthorized.getBody());
        assertEquals("request-123", unauthorized.getBody().requestId());
        assertEquals(403, forbidden.getStatusCode().value());
        assertNotNull(forbidden.getBody());
        assertEquals("request-123", forbidden.getBody().requestId());
    }

    @Test
    void missingVideoResourceUsesGenericNotFoundWithoutLeakingItsIdentity() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleVideoResourceNotFound(
                new VideoResourceNotFoundException("敏感资源 CHANNEL:101 不存在"));

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源未找到", response.getBody().getMsg());
    }

    @Test
    void ambiguousVideoResourceAlsoFailsClosedAsGenericNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleVideoResourceNotFound(
                new AmbiguousVideoResourceException(
                        "手机号匹配到 JT_TERMINAL:31 与 JT_TERMINAL:32"));

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源未找到", response.getBody().getMsg());
    }

    @Test
    void missingRequiredRequestParameterIsBadRequestInsteadOfServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBadRequest(
                new MissingServletRequestParameterException(
                        "downloadId", "String"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
    }

    @Test
    void invalidRequestIdIsReplacedBeforeItReachesSecurityResponses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(RequestIdResolver.HEADER_NAME, "forged\r\nrequest-id");

        var response = handler.handleAuthenticationFailure(
                new BadCredentialsException("隐藏的内部原因"), request);

        assertNotNull(response.getBody());
        UUID.fromString(response.getBody().requestId());
    }

    @Test
    void requestIdResolverKeepsOnlyBoundedLogSafeIdentifiers() {
        assertEquals("trace_123:span-456", RequestIdResolver.normalize("trace_123:span-456"));

        String overlong = "a".repeat(RequestIdResolver.MAX_LENGTH + 1);
        UUID.fromString(RequestIdResolver.normalize(overlong));
        UUID.fromString(RequestIdResolver.normalize("contains whitespace"));
    }

    @Test
    void globalResponseAdviceDoesNotWrapSecurityErrorsAsSuccess() {
        SecurityError error = new SecurityError(401, "未认证", "request-123");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/auth/login"));

        Object response = new GlobalResponseAdvice().beforeBodyWrite(
                error,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                request,
                mock(ServerHttpResponse.class));

        assertSame(error, response);
    }
}
