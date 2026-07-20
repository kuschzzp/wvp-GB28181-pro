package com.genersoft.iot.vmp.media.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.common.StreamURL;

import java.time.Instant;

/**
 * Temporary audit-mode compatibility payload. It is never populated in enforce mode.
 */
public record LegacyMediaResponse(
        boolean deprecated,
        Instant sunsetAt,
        String flv,
        @JsonProperty("https_flv") String httpsFlv,
        @JsonProperty("ws_flv") String wsFlv,
        @JsonProperty("wss_flv") String wssFlv,
        String hls,
        @JsonProperty("https_hls") String httpsHls,
        @JsonProperty("ws_hls") String wsHls,
        @JsonProperty("wss_hls") String wssHls,
        String rtc,
        String rtcs,
        String rtsp,
        String rtsps,
        String rtmp,
        String rtmps) {

    public static LegacyMediaResponse from(
            StreamInfo streamInfo,
            Instant sunsetAt) {
        return new LegacyMediaResponse(
                true,
                sunsetAt,
                url(streamInfo.getFlv()),
                url(streamInfo.getHttps_flv()),
                url(streamInfo.getWs_flv()),
                url(streamInfo.getWss_flv()),
                url(streamInfo.getHls()),
                url(streamInfo.getHttps_hls()),
                url(streamInfo.getWs_hls()),
                url(streamInfo.getWss_hls()),
                url(streamInfo.getRtc()),
                url(streamInfo.getRtcs()),
                url(streamInfo.getRtsp()),
                url(streamInfo.getRtsps()),
                url(streamInfo.getRtmp()),
                url(streamInfo.getRtmps()));
    }

    private static String url(StreamURL streamURL) {
        return streamURL == null ? null : streamURL.getUrl();
    }
}
