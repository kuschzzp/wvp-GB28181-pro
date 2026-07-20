package com.genersoft.iot.vmp.authorization.video.model;

import java.util.Locale;

public enum VideoScopeType {
    ALL,
    CUSTOM,
    NONE;

    public static VideoScopeType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("角色视频范围为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("未知角色视频范围: " + value, exception);
        }
    }
}
