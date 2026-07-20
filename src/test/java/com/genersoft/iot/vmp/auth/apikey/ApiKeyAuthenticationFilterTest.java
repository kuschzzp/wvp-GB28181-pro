package com.genersoft.iot.vmp.auth.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock private ApiKeyAuthenticationService authenticationService;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(
                authenticationService,
                new AnonymousAuthenticationEntryPoint(new ObjectMapper()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiKeyCreatesCredentialFreeMachineAuthentication() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.API_KEY,
                88L,
                12L,
                "machine-key",
                10L,
                Set.of(3L),
                7L,
                false,
                Set.of("video:channel:list"));
        when(authenticationService.authenticate(eq("machine-key"), any(InetAddress.class)))
                .thenReturn(principal);
        MockHttpServletRequest request = request();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(JwtUtils.API_KEY_HEADER, "machine-key");

        assertEquals(200, invoke(request).getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertNull(authentication.getCredentials());
        assertEquals(principal, authentication.getPrincipal());
    }

    @Test
    void simultaneousCredentialsAreRejectedEvenIfAnotherFilterAuthenticatedFirst() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(JwtUtils.HEADER, "signed-token");
        request.addHeader(JwtUtils.API_KEY_HEADER, "machine-key");

        assertEquals(401, invoke(request).getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void dependencyFailureReturns401AndDoesNotContinueTheChain() throws Exception {
        when(authenticationService.authenticate(eq("machine-key"), any(InetAddress.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        MockHttpServletRequest request = request();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(JwtUtils.API_KEY_HEADER, "machine-key");

        assertEquals(401, invoke(request).getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/device/query/devices");
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request)
            throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
