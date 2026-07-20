package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.common.StreamURL;
import com.genersoft.iot.vmp.media.bean.MediaInfo;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketedMediaUrlFactoryTest {

    private static final MediaStreamBinding BINDING = new MediaStreamBinding(
            "zlm-1", "rtp", "stream-1", VideoResourceType.CHANNEL, 42L);

    private IMediaServerService mediaServerService;
    private MediaServer mediaServer;
    private MediaInfo mediaInfo;
    private StreamInfo streamInfo;
    private TicketedMediaUrlFactory factory;

    @BeforeEach
    void setUp() {
        mediaServerService = mock(IMediaServerService.class);
        mediaServer = new MediaServer();
        mediaServer.setId("zlm-1");
        mediaServer.setStatus(true);
        mediaInfo = new MediaInfo();
        mediaInfo.setOnline(true);
        mediaInfo.setMediaServer(mediaServer);
        mediaInfo.setApp("rtp");
        mediaInfo.setStream("stream-1");
        streamInfo = streamInfo();
        when(mediaServerService.getOne("zlm-1")).thenReturn(mediaServer);
        when(mediaServerService.getMediaInfo(mediaServer, "rtp", "stream-1"))
                .thenReturn(mediaInfo);
        when(mediaServerService.getStreamInfoByAppAndStream(
                mediaServer, "rtp", "stream-1", mediaInfo, null, null, true))
                .thenReturn(streamInfo);
        factory = new TicketedMediaUrlFactory(mediaServerService);
    }

    @Test
    void createsOnlyRequestedSchemasAndPrefersSecureEndpoints() {
        Map<MediaSchema, URI> result = factory.create(
                BINDING,
                "opaque-ticket",
                Set.of(MediaSchema.FLV, MediaSchema.WS_FLV, MediaSchema.HLS,
                        MediaSchema.WEBRTC, MediaSchema.RTSP, MediaSchema.RTMP));

        assertEquals("https", result.get(MediaSchema.FLV).getScheme());
        assertEquals("wss", result.get(MediaSchema.WS_FLV).getScheme());
        assertEquals("https", result.get(MediaSchema.HLS).getScheme());
        assertEquals("https", result.get(MediaSchema.WEBRTC).getScheme());
        assertEquals("rtsps", result.get(MediaSchema.RTSP).getScheme());
        assertEquals("rtmps", result.get(MediaSchema.RTMP).getScheme());
        result.values().forEach(uri -> assertEquals(
                "opaque-ticket",
                UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket")));
    }

    @Test
    void preservesExistingQueryAndSafelyReplacesTicket() {
        streamInfo.setHttps_flv(new StreamURL(
                "https", "media.example", 443,
                "rtp/stream-1.live.flv?origin=player&ticket=old"));

        URI uri = factory.create(BINDING, "a+b/c?=", Set.of(MediaSchema.FLV))
                .get(MediaSchema.FLV);

        var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertEquals("player", query.getFirst("origin"));
        assertEquals(
                "a+b/c?=",
                UriUtils.decode(
                        query.getFirst("ticket"), StandardCharsets.UTF_8));
        assertEquals(1, query.get("ticket").size());
        assertFalse(uri.toASCIIString().contains("ticket=old"));
        assertEquals(
                "https://media.example:443/rtp/stream-1.live.flv"
                        + "?origin=player&ticket=a%2Bb%2Fc%3F%3D",
                uri.toASCIIString());
    }

    @Test
    void failsClosedWhenNodeStreamOrSchemaIsUnavailable() {
        mediaInfo.setOnline(false);
        assertThrows(IllegalStateException.class,
                () -> factory.create(BINDING, "ticket", Set.of(MediaSchema.FLV)));

        mediaInfo.setOnline(true);
        streamInfo.setWss_flv(null);
        streamInfo.setWs_flv(null);
        assertThrows(IllegalStateException.class,
                () -> factory.create(BINDING, "ticket", Set.of(MediaSchema.WS_FLV)));
    }

    private StreamInfo streamInfo() {
        StreamInfo result = new StreamInfo();
        result.setApp("rtp");
        result.setStream("stream-1");
        result.setMediaServer(mediaServer);
        result.setHttps_flv(new StreamURL(
                "https", "media.example", 443, "rtp/stream-1.live.flv?origin=player"));
        result.setWss_flv(new StreamURL(
                "wss", "media.example", 443, "rtp/stream-1.live.flv"));
        result.setHttps_hls(new StreamURL(
                "https", "media.example", 443, "rtp/stream-1/hls.m3u8"));
        result.setRtcs(new StreamURL(
                "https", "media.example", 443,
                "index/api/webrtc?app=rtp&stream=stream-1&type=play"));
        result.setRtsps(new StreamURL(
                "rtsps", "media.example", 322, "rtp/stream-1"));
        result.setRtmps(new StreamURL(
                "rtmps", "media.example", 1936, "rtp/stream-1"));
        return result;
    }
}
