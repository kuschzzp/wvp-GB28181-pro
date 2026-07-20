package com.genersoft.iot.vmp.media.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.common.StreamURL;
import com.genersoft.iot.vmp.media.bean.MediaInfo;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.dto.PlayableStreamRef;
import com.genersoft.iot.vmp.vmanager.bean.MediaPublishEndpoint;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaResponseLeakTest {

    @Test
    void browserStreamResponseContainsOnlyStableReferenceAndSafeMetadata() throws Exception {
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlm-1");
        mediaServer.setSecret("node-super-secret");
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setApp("rtp");
        streamInfo.setStream("stream-1");
        streamInfo.setMediaServer(mediaServer);
        streamInfo.setFlv(new StreamURL(
                "http", "media.example", 80, "rtp/stream-1.live.flv?callId=secret-call"));
        streamInfo.setWs_flv(new StreamURL(
                "ws", "media.example", 80, "rtp/stream-1.live.flv"));
        streamInfo.setHls(new StreamURL(
                "http", "media.example", 80, "rtp/stream-1/hls.m3u8"));
        streamInfo.setRtsp(new StreamURL(
                "rtsp", "media.example", 554, "rtp/stream-1"));
        streamInfo.setRtmp(new StreamURL(
                "rtmp", "media.example", 1935, "rtp/stream-1"));
        streamInfo.setRtc(new StreamURL(
                "http", "media.example", 80,
                "index/api/webrtc?app=rtp&stream=stream-1&type=play"));
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setOriginUrl("rtsp://camera.example/private");
        mediaInfo.setCallId("secret-call");
        mediaInfo.setParamMap(Map.of("callId", "secret-call"));
        mediaInfo.setMediaServer(mediaServer);
        streamInfo.setMediaInfo(mediaInfo);
        streamInfo.setCallId("secret-call");
        streamInfo.setKey("internal-stream-key");

        StreamContent response = new StreamContent(
                streamInfo,
                new PlayableStreamRef(
                        VideoResourceType.CHANNEL,
                        42L,
                        "zlm-1",
                        "rtp",
                        "stream-1"));
        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"resourceType\":\"CHANNEL\""));
        assertTrue(json.contains("\"resourceId\":42"));
        assertFalse(json.contains("rtsp://"));
        assertFalse(json.contains("rtmp://"));
        assertFalse(json.contains(".flv"));
        assertFalse(json.contains(".m3u8"));
        assertFalse(json.contains("ws://"));
        assertFalse(json.contains("webrtc"));
        assertFalse(json.contains("callId"));
        assertFalse(json.contains("node-super-secret"));
        assertFalse(json.contains("internal-stream-key"));
    }

    @Test
    void browserPublishResponseContainsOnlyWebRtcPushEndpoints() throws Exception {
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlm-1");
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setApp("talk");
        streamInfo.setStream("audio-1");
        streamInfo.setMediaServer(mediaServer);
        streamInfo.setRtc(new StreamURL(
                "http", "media.example", 80,
                "index/api/webrtc?app=talk&stream=audio-1&type=push"));
        streamInfo.setRtcs(new StreamURL(
                "https", "media.example", 443,
                "index/api/webrtc?app=talk&stream=audio-1&type=push"));
        streamInfo.setFlv(new StreamURL(
                "http", "media.example", 80, "talk/audio-1.live.flv"));

        String json = new ObjectMapper().writeValueAsString(
                MediaPublishEndpoint.from(streamInfo));

        assertTrue(json.contains("type=push"));
        assertTrue(json.contains("\"mediaServerId\":\"zlm-1\""));
        assertFalse(json.contains(".flv"));
        assertFalse(json.contains("type=play"));
    }

    @Test
    void browserPublishResponseRejectsPlaybackEndpoints() {
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlm-1");
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setApp("talk");
        streamInfo.setStream("audio-1");
        streamInfo.setMediaServer(mediaServer);
        streamInfo.setRtc(new StreamURL(
                "http", "media.example", 80,
                "index/api/webrtc?app=talk&stream=audio-1&type=play"));

        assertThrows(IllegalArgumentException.class,
                () -> MediaPublishEndpoint.from(streamInfo));
    }

    @Test
    void enforceModeNeverEmitsLegacyUrlsEvenWhenCompatibilityIsEnabled() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        LegacyMediaResponsePolicy policy = policy(
                AuthorizationMode.ENFORCE,
                true,
                Instant.parse("2099-10-01T00:00:00Z"),
                mock(CurrentPrincipalService.class),
                auditSink);

        assertNull(policy.create(
                streamWithLegacyUrl(), VideoResourceType.CHANNEL, 42L));
        verifyNoInteractions(auditSink);
    }

    @Test
    void auditCompatibilityIsTimeBoundAndWritesDeprecationAudit() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        CurrentPrincipalService currentPrincipalService =
                mock(CurrentPrincipalService.class);
        when(currentPrincipalService.requireCurrent()).thenReturn(new AuthPrincipal(
                PrincipalType.USER,
                7L,
                7L,
                "operator",
                3L,
                Set.of(2L),
                9L,
                false,
                Set.of("video:channel:play")));
        LegacyMediaResponsePolicy policy = policy(
                AuthorizationMode.AUDIT,
                true,
                Instant.parse("2099-10-01T00:00:00Z"),
                currentPrincipalService,
                auditSink);

        var response = policy.create(
                streamWithLegacyUrl(), VideoResourceType.CHANNEL, 42L);

        assertNotNull(response);
        assertTrue(response.deprecated());
        assertEquals("http://media.example:80/rtp/stream-1.live.flv",
                response.flv());
        ArgumentCaptor<AuthzAuditRecord> audit =
                ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(audit.capture());
        assertEquals("LEGACY_MEDIA_RESPONSE_EMITTED",
                audit.getValue().reasonCode());
        assertEquals(42L, audit.getValue().resourceId());
    }

    @Test
    void auditCompatibilityCannotOutliveSunset() {
        AuthorizationAuditSink auditSink = mock(AuthorizationAuditSink.class);
        LegacyMediaResponsePolicy policy = policy(
                AuthorizationMode.AUDIT,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                mock(CurrentPrincipalService.class),
                auditSink);

        assertNull(policy.create(
                streamWithLegacyUrl(), VideoResourceType.CHANNEL, 42L));
        verifyNoInteractions(auditSink);
    }

    private LegacyMediaResponsePolicy policy(
            AuthorizationMode mode,
            boolean compatibilityEnabled,
            Instant sunset,
            CurrentPrincipalService currentPrincipalService,
            AuthorizationAuditSink auditSink) {
        return new LegacyMediaResponsePolicy(
                new AuthorizationProperties(
                        mode, false, Duration.ofMinutes(5)),
                new MediaAccessProperties(compatibilityEnabled, sunset),
                currentPrincipalService,
                auditSink,
                Clock.fixed(
                        Instant.parse("2026-07-15T00:00:00Z"),
                        ZoneOffset.UTC));
    }

    private StreamInfo streamWithLegacyUrl() {
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setFlv(new StreamURL(
                "http", "media.example", 80, "rtp/stream-1.live.flv"));
        return streamInfo;
    }
}
