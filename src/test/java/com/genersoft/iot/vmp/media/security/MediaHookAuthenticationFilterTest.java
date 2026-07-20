package com.genersoft.iot.vmp.media.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaHookAuthenticationFilterTest {

    private static final String PATH = "/index/hook/zlm/on_publish";
    private static final byte[] BODY =
            "{\"mediaServerId\":\"zlm-1\",\"app\":\"live\"}"
                    .getBytes(StandardCharsets.UTF_8);

    @Mock private MediaHookAuthenticationService authenticationService;
    @Mock private MediaHookReplayGuard replayGuard;

    private MediaHookAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MediaHookAuthenticationFilter(
                authenticationService,
                replayGuard,
                new ObjectMapper(),
                new AnonymousAuthenticationEntryPoint(new ObjectMapper()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validNodeCredentialCreatesIndependentPrincipalAndPreservesBody()
            throws Exception {
        MediaNodePrincipal principal =
                new MediaNodePrincipal("zlm-1", MediaNodeType.ZLM, 3L);
        when(authenticationService.authenticate(
                eq(MediaNodeType.ZLM),
                eq("zlm-1"),
                eq("raw-secret"),
                any(InetAddress.class)))
                .thenReturn(principal);
        when(replayGuard.accept(
                eq(principal), eq(PATH), aryEq(BODY), isNull(), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = validRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> downstreamRequest =
                new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            downstreamRequest.set((HttpServletRequest) servletRequest);
            ((MockHttpServletResponse) servletResponse).setStatus(204);
        };

        filter.doFilter(request, response, chain);

        assertEquals(204, response.getStatus());
        assertNotNull(downstreamRequest.get());
        assertArrayEquals(
                BODY,
                downstreamRequest.get().getInputStream().readAllBytes());
        assertEquals(
                principal,
                downstreamRequest.get().getAttribute(
                        MediaHookAuthenticationFilter.PRINCIPAL_ATTRIBUTE));
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(principal, authentication.getPrincipal());
        assertNull(authentication.getCredentials());
    }

    @Test
    void queryAndBodyNodeMismatchFailsBeforeCredentialLookup()
            throws Exception {
        MockHttpServletRequest request = validRequest();
        request.setParameter(
                MediaHookAuthenticationFilter.MEDIA_SERVER_ID_PARAM,
                "other-node");

        Invocation result = invoke(request);

        assertEquals(401, result.response().getStatus());
        assertEquals(0, result.chainCalls());
        verifyNoInteractions(authenticationService, replayGuard);
    }

    @Test
    void jwtAndApiKeyCredentialsAreRejectedOnMediaHookChain()
            throws Exception {
        for (String header : new String[]{JwtUtils.HEADER, JwtUtils.API_KEY_HEADER}) {
            MockHttpServletRequest request = validRequest();
            request.addHeader(header, "user-credential");

            Invocation result = invoke(request);

            assertEquals(401, result.response().getStatus());
            assertEquals(0, result.chainCalls());
        }
        verifyNoInteractions(authenticationService, replayGuard);
    }

    @Test
    void unknownHookNamespaceAndReplayAreFailClosed()
            throws Exception {
        MockHttpServletRequest unknown = validRequest();
        unknown.setRequestURI("/index/hook/unknown/on_publish");
        Invocation unknownResult = invoke(unknown);
        assertEquals(401, unknownResult.response().getStatus());
        assertEquals(0, unknownResult.chainCalls());
        verifyNoInteractions(authenticationService, replayGuard);

        MediaNodePrincipal principal =
                new MediaNodePrincipal("zlm-1", MediaNodeType.ZLM, 3L);
        when(authenticationService.authenticate(
                eq(MediaNodeType.ZLM),
                eq("zlm-1"),
                eq("raw-secret"),
                any(InetAddress.class)))
                .thenReturn(principal);
        when(replayGuard.accept(
                eq(principal), anyString(), aryEq(BODY), isNull(), isNull()))
                .thenReturn(false);

        Invocation replay = invoke(validRequest());
        assertEquals(401, replay.response().getStatus());
        assertEquals(0, replay.chainCalls());
    }

    @Test
    void authenticationDependencyFailureIsMaskedAndDoesNotContinue()
            throws Exception {
        when(authenticationService.authenticate(
                eq(MediaNodeType.ZLM),
                eq("zlm-1"),
                eq("raw-secret"),
                any(InetAddress.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        Invocation result = invoke(validRequest());

        assertEquals(401, result.response().getStatus());
        assertEquals(0, result.chainCalls());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest validRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", PATH);
        request.setRemoteAddr("127.0.0.1");
        request.setContentType("application/json");
        request.setContent(BODY);
        request.addParameter(
                MediaHookAuthenticationFilter.MEDIA_SERVER_ID_PARAM,
                "zlm-1");
        request.addParameter(
                MediaHookAuthenticationFilter.HOOK_SECRET_PARAM,
                "raw-secret");
        return request;
    }

    private Invocation invoke(MockHttpServletRequest request)
            throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        chainCalls.incrementAndGet());
        return new Invocation(response, chainCalls.get());
    }

    private record Invocation(
            MockHttpServletResponse response,
            int chainCalls) {
    }
}
