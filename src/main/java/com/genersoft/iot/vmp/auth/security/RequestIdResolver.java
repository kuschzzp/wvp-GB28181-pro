package com.genersoft.iot.vmp.auth.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves a bounded request identifier that is safe for audit storage and log correlation.
 */
public final class RequestIdResolver {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final int MAX_LENGTH = 64;

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    private RequestIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        return normalize(request == null ? null : request.getHeader(HEADER_NAME));
    }

    public static String normalize(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
