package com.genersoft.iot.vmp.auth.compat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyApiDeprecationAdviceTest {

    private final LegacyApiDeprecationAdvice advice = new LegacyApiDeprecationAdvice();

    @Test
    void legacyUserManagementPointsToScopedSuccessor() {
        HttpHeaders responseHeaders = apply("/api/user/users");

        assertEquals("true", responseHeaders.getFirst("Deprecation"));
        assertEquals(LegacyApiDeprecationAdvice.SUNSET,
                responseHeaders.getFirst("Sunset"));
        assertEquals("</api/system/user>; rel=\"successor-version\"",
                responseHeaders.getFirst(HttpHeaders.LINK));
    }

    @Test
    void legacyRoleAndLoginExposeTheirOwnSuccessors() {
        assertEquals("</api/system/role>; rel=\"successor-version\"",
                apply("/api/role/all").getFirst(HttpHeaders.LINK));
        assertEquals("</api/auth/login>; rel=\"successor-version\"",
                apply("/api/user/login").getFirst(HttpHeaders.LINK));
    }

    private HttpHeaders apply(String path) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse serverResponse = new ServletServerHttpResponse(servletResponse);
        advice.beforeBodyWrite(
                null,
                null,
                null,
                null,
                new ServletServerHttpRequest(servletRequest),
                serverResponse);
        return serverResponse.getHeaders();
    }
}
