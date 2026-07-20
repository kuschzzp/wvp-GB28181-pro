package com.genersoft.iot.vmp.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.conf.security.LogoutHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JwtUtils jwtUtils;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private TokenVersionService tokenVersionService;

    private LogoutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LogoutHandler(
                jwtUtils, tokenRevocationService, tokenVersionService, objectMapper);
    }

    @Test
    void missingTokenUsesTheUnifiedSecurityErrorShape() throws Exception {
        MockHttpServletRequest request = request(null);
        request.addHeader(RequestIdResolver.HEADER_NAME, "logout-request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request, response, null);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(401, response.getStatus());
        assertEquals(401, body.path("code").asInt());
        assertEquals("请登录后重新请求", body.path("msg").asText());
        assertEquals("logout-request-1", body.path("requestId").asText());
        verifyNoInteractions(jwtUtils, tokenRevocationService, tokenVersionService);
    }

    @Test
    void staleTokenCannotTriggerLogoutFallbackForCurrentSessions() throws Exception {
        when(jwtUtils.verifyAccessToken("stale-token")).thenReturn(token());
        when(tokenVersionService.matches(12L, 7L)).thenReturn(false);
        MockHttpServletRequest request = request("stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request, response, null);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void redisFailureBumpsVersionOnlyForCurrentToken() throws Exception {
        VerifiedAccessToken token = token();
        when(jwtUtils.verifyAccessToken("current-token")).thenReturn(token);
        when(tokenVersionService.matches(12L, 7L)).thenReturn(true);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(tokenRevocationService).revoke(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request("current-token"), response, null);

        assertEquals(200, response.getStatus());
        verify(tokenVersionService).bumpUser(12L, "LOGOUT_REDIS_FALLBACK");
    }

    @Test
    void versionCheckFailureReturns503WithoutClaimingLogoutSucceeded() throws Exception {
        when(jwtUtils.verifyAccessToken("current-token")).thenReturn(token());
        when(tokenVersionService.matches(12L, 7L))
                .thenThrow(new IllegalStateException("database unavailable"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request("current-token"), response, null);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(tokenRevocationService);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        if (token != null) {
            request.addHeader(JwtUtils.HEADER, token);
        }
        return request;
    }

    private VerifiedAccessToken token() {
        return new VerifiedAccessToken(
                12L,
                "jti-1",
                7L,
                Instant.parse("2026-07-13T10:00:00Z"),
                Instant.parse("2099-07-13T11:00:00Z"));
    }
}
