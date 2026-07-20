package com.genersoft.iot.vmp.auth.apikey;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class ApiKeyIpAllowlist {

    private static final int MAX_ENTRIES = 64;
    private static final int MAX_STORED_LENGTH = 2000;

    private ApiKeyIpAllowlist() {
    }

    public static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("IP 白名单不能包含 null");
            }
            for (String candidate : value.split("[,\\r\\n]+")) {
                String entry = candidate.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                validate(entry);
                normalized.add(entry);
                if (normalized.size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException("IP 白名单最多允许 " + MAX_ENTRIES + " 项");
                }
            }
        }
        List<String> result = List.copyOf(normalized);
        if (encode(result).length() > MAX_STORED_LENGTH) {
            throw new IllegalArgumentException("IP 白名单总长度不能超过 " + MAX_STORED_LENGTH + " 个字符");
        }
        return result;
    }

    public static List<String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return normalize(List.of(stored));
    }

    public static String encode(List<String> values) {
        List<String> normalized = values == null ? List.of() : values;
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    public static boolean allows(String stored, InetAddress clientAddress) {
        List<String> entries = decode(stored);
        if (entries.isEmpty()) {
            return true;
        }
        Objects.requireNonNull(clientAddress, "clientAddress must not be null");
        String address = clientAddress.getHostAddress();
        for (String entry : entries) {
            if (new IpAddressMatcher(entry).matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static void validate(String entry) {
        try {
            new IpAddressMatcher(entry);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("IP 白名单格式非法: " + entry, exception);
        }
    }
}
