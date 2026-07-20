package com.genersoft.iot.vmp.media.security;

/**
 * Normalized fields used to authorize a media-node on_play callback.
 */
public record MediaPlayHookRequest(
        String mediaServerId,
        String app,
        String stream,
        String schema,
        String ticket) {
}
