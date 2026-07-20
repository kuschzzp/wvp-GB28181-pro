package com.genersoft.iot.vmp.vmanager.bean;

import com.genersoft.iot.vmp.common.StreamInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Session-scoped browser-to-media-node endpoint. This DTO deliberately exposes
 * only WebRTC publish URLs; it is not accepted by playback components.
 */
@Schema(description = "浏览器向媒体节点发布音频的会话端点")
public record MediaPublishEndpoint(
        String mediaServerId,
        String app,
        String stream,
        String rtc,
        String rtcs) {

    public MediaPublishEndpoint {
        mediaServerId = requireText(mediaServerId, "mediaServerId");
        app = requireText(app, "app");
        stream = requireText(stream, "stream");
        if ((rtc == null || rtc.isBlank())
                && (rtcs == null || rtcs.isBlank())) {
            throw new IllegalArgumentException(
                    "at least one WebRTC publish endpoint is required");
        }
        if (rtc != null && !rtc.contains("type=push")) {
            throw new IllegalArgumentException("rtc endpoint must be publish-only");
        }
        if (rtcs != null && !rtcs.contains("type=push")) {
            throw new IllegalArgumentException("rtcs endpoint must be publish-only");
        }
    }

    public static MediaPublishEndpoint from(StreamInfo streamInfo) {
        Objects.requireNonNull(streamInfo, "streamInfo must not be null");
        if (streamInfo.getMediaServer() == null) {
            throw new IllegalArgumentException("media server must not be null");
        }
        return new MediaPublishEndpoint(
                streamInfo.getMediaServer().getId(),
                streamInfo.getApp(),
                streamInfo.getStream(),
                streamInfo.getRtc() == null
                        ? null
                        : streamInfo.getRtc().getUrl(),
                streamInfo.getRtcs() == null
                        ? null
                        : streamInfo.getRtcs().getUrl());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
