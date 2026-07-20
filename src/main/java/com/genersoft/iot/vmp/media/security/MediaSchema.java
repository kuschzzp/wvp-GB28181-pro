package com.genersoft.iot.vmp.media.security;

import java.util.Locale;

/**
 * Media protocols that may be granted by a play ticket.
 */
public enum MediaSchema {
    FLV,
    WS_FLV,
    HLS,
    WEBRTC,
    RTSP,
    RTMP;

    public static MediaSchema parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "flv", "http-flv", "https-flv" -> FLV;
            case "ws-flv", "wss-flv", "websocket-flv", "websockets-flv" -> WS_FLV;
            case "hls", "http-hls", "https-hls", "m3u8" -> HLS;
            case "webrtc", "rtc", "rtcs" -> WEBRTC;
            case "rtsp", "rtsps" -> RTSP;
            case "rtmp", "rtmps" -> RTMP;
            default -> throw new IllegalArgumentException("unsupported media schema: " + value);
        };
    }
}
