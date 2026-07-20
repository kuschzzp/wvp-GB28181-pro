package com.genersoft.iot.vmp.media.abl;

import com.genersoft.iot.vmp.media.abl.bean.hook.OnPlayABLHookParam;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.AblSchemaDecision;
import com.genersoft.iot.vmp.media.security.AblSchemaResolver;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaNodePrincipal;
import com.genersoft.iot.vmp.media.security.MediaNodeType;
import com.genersoft.iot.vmp.media.security.MediaPlayHookRequest;
import com.genersoft.iot.vmp.media.security.MediaSchema;
import com.genersoft.iot.vmp.media.security.PlayTicketDecision;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.zlm.dto.hook.HookResult;
import com.genersoft.iot.vmp.service.IMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ABLHttpHookListenerTest {

    @Mock private IMediaServerService mediaServerService;
    @Mock private IMediaService mediaService;
    @Mock private PlayTicketService playTicketService;
    @Mock private AblSchemaResolver ablSchemaResolver;
    @Mock private ABLRESTfulUtils ablRestfulUtils;

    private ABLHttpHookListener listener;
    private MediaServer mediaServer;

    @BeforeEach
    void setUp() {
        listener = new ABLHttpHookListener();
        ReflectionTestUtils.setField(
                listener, "mediaServerService", mediaServerService);
        ReflectionTestUtils.setField(listener, "mediaService", mediaService);
        ReflectionTestUtils.setField(
                listener, "playTicketService", playTicketService);
        ReflectionTestUtils.setField(
                listener, "ablSchemaResolver", ablSchemaResolver);
        ReflectionTestUtils.setField(
                listener, "ablresTfulUtils", ablRestfulUtils);
        mediaServer = new MediaServer();
        mediaServer.setId("abl-1");
    }

    @Test
    void validMappedSchemaAndTicketAreAllowed() {
        when(mediaServerService.getOne("abl-1")).thenReturn(mediaServer);
        when(ablSchemaResolver.resolve(7)).thenReturn(
                AblSchemaDecision.mapped(MediaSchema.WS_FLV));
        when(playTicketService.verify(any())).thenReturn(
                PlayTicketDecision.allow());

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 7, "live", "stream-1",
                        "ticket=opaque%2Bticket"));

        assertEquals(0, result.getCode());
        ArgumentCaptor<MediaPlayHookRequest> requestCaptor =
                ArgumentCaptor.forClass(MediaPlayHookRequest.class);
        verify(playTicketService).verify(requestCaptor.capture());
        assertEquals(new MediaPlayHookRequest(
                        "abl-1", "live", "stream-1", "WS_FLV",
                        "opaque+ticket"),
                requestCaptor.getValue());
        verify(ablRestfulUtils, never()).closeStreams(
                any(), anyString(), anyString());
    }

    @Test
    void missingMediaNodeIsDeniedWithoutClosingOrClaimingSuccess() {
        when(mediaServerService.getOne("abl-1")).thenReturn(null);

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 7, "live", "stream-1",
                        "ticket=opaque"));

        assertEquals(401, result.getCode());
        verifyNoInteractions(ablSchemaResolver, playTicketService, mediaService);
        verifyNoInteractions(ablRestfulUtils);
    }

    @Test
    void ticketDenialReturnsFailureAndNeverClosesAnotherStream() {
        when(mediaServerService.getOne("abl-1")).thenReturn(mediaServer);
        when(ablSchemaResolver.resolve(7)).thenReturn(
                AblSchemaDecision.mapped(MediaSchema.FLV));
        when(playTicketService.verify(any())).thenReturn(
                PlayTicketDecision.deny("BINDING_MISMATCH"));

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 7, "live", "stream-1",
                        "ticket=opaque"));

        assertEquals(401, result.getCode());
        verifyNoInteractions(mediaService, ablRestfulUtils);
    }

    @Test
    void unmappedSchemaIsFailClosedOutsideExplicitAuditCompatibility() {
        when(mediaServerService.getOne("abl-1")).thenReturn(mediaServer);
        when(ablSchemaResolver.resolve(99)).thenReturn(
                AblSchemaDecision.deny());

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 99, "live", "stream-1", null));

        assertEquals(401, result.getCode());
        verifyNoInteractions(playTicketService, mediaService, ablRestfulUtils);
    }

    @Test
    void auditCompatibilityIsTheOnlyLegacyPath() {
        when(mediaServerService.getOne("abl-1")).thenReturn(mediaServer);
        when(ablSchemaResolver.resolve(99)).thenReturn(
                AblSchemaDecision.legacyAudit());
        when(mediaService.authenticateLegacyPlayInAudit(
                "live", "stream-1", "call-1")).thenReturn(true);

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 99, "live", "stream-1",
                        "callId=call-1"));

        assertEquals(0, result.getCode());
        verifyNoInteractions(playTicketService, ablRestfulUtils);
    }

    @Test
    void failedAuditCompatibilityReturnsFailure() {
        when(mediaServerService.getOne("abl-1")).thenReturn(mediaServer);
        when(ablSchemaResolver.resolve(99)).thenReturn(
                AblSchemaDecision.legacyAudit());
        when(mediaService.authenticateLegacyPlayInAudit(
                eq("live"), eq("stream-1"), eq(null))).thenReturn(false);

        HookResult result = listener.onPlay(
                authenticatedRequest("abl-1", MediaNodeType.ABL),
                playParam("abl-1", 99, "live", "stream-1", null));

        assertEquals(401, result.getCode());
        verifyNoInteractions(playTicketService, ablRestfulUtils);
    }

    @Test
    void wrongNodePrincipalFailsBeforeAllPlaybackDependencies() {
        HookResult result = listener.onPlay(
                authenticatedRequest("other-node", MediaNodeType.ABL),
                playParam("abl-1", 7, "live", "stream-1",
                        "ticket=opaque"));

        assertEquals(401, result.getCode());
        verifyNoInteractions(
                mediaServerService, ablSchemaResolver, playTicketService,
                mediaService, ablRestfulUtils);
    }

    private MockHttpServletRequest authenticatedRequest(
            String mediaServerId,
            MediaNodeType nodeType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                MediaHookAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new MediaNodePrincipal(mediaServerId, nodeType, 2L));
        return request;
    }

    private OnPlayABLHookParam playParam(
            String mediaServerId,
            Integer networkType,
            String app,
            String stream,
            String params) {
        OnPlayABLHookParam param = new OnPlayABLHookParam();
        param.setMediaServerId(mediaServerId);
        param.setNetworkType(networkType);
        param.setApp(app);
        param.setStream(stream);
        param.setParams(params);
        return param;
    }
}
