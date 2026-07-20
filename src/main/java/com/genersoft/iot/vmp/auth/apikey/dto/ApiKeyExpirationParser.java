package com.genersoft.iot.vmp.auth.apikey.dto;

import com.genersoft.iot.vmp.utils.DateUtil;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class ApiKeyExpirationParser {

    private ApiKeyExpirationParser() {
    }

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.matches("[+-]?\\d+")) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(normalized));
            } catch (RuntimeException exception) {
                throw invalidExpiration(value, exception);
            }
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // 旧版查询参数使用 yyyy-MM-dd HH:mm:ss，在兼容端点保留读取能力。
        }
        try {
            return Instant.ofEpochMilli(DateUtil.yyyy_MM_dd_HH_mm_ssToTimestampMs(normalized));
        } catch (RuntimeException exception) {
            throw invalidExpiration(value, exception);
        }
    }

    private static IllegalArgumentException invalidExpiration(String value, RuntimeException cause) {
        return new IllegalArgumentException(
                "API Key 过期时间必须是 ISO-8601、UTC 毫秒时间戳或 yyyy-MM-dd HH:mm:ss: " + value,
                cause);
    }
}
