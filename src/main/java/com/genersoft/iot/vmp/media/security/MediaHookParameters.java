package com.genersoft.iot.vmp.media.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Strict parser for credential-bearing parameters embedded in media URLs.
 */
public final class MediaHookParameters {

    private static final int MAX_PARAMETERS_LENGTH = 4096;

    private MediaHookParameters() {
    }

    public static String unique(String parameters, String name) {
        if (parameters == null
                || parameters.isBlank()
                || parameters.length() > MAX_PARAMETERS_LENGTH
                || name == null
                || name.isBlank()) {
            return null;
        }
        String normalized = parameters.charAt(0) == '?'
                ? parameters.substring(1)
                : parameters;
        String result = null;
        try {
            for (String pair : normalized.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(
                        parts[0], StandardCharsets.UTF_8);
                if (!name.equals(key)) {
                    continue;
                }
                if (result != null || parts.length != 2) {
                    return null;
                }
                result = URLDecoder.decode(
                        parts[1], StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return result == null || result.isBlank() ? null : result;
    }
}
