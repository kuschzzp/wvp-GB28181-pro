package com.genersoft.iot.vmp.media.security;

import java.util.Locale;

public enum MediaNodeType {
    ZLM,
    ABL;

    public static MediaNodeType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("媒体节点类型不能为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
