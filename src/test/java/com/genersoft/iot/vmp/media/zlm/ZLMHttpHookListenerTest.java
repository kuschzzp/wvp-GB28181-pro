package com.genersoft.iot.vmp.media.zlm;

import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaNodePrincipal;
import com.genersoft.iot.vmp.media.security.MediaNodeType;
import com.genersoft.iot.vmp.media.security.MediaPlayHookRequest;
import com.genersoft.iot.vmp.media.security.PlayTicketDecision;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.zlm.dto.hook.HookResult;
import com.genersoft.iot.vmp.media.zlm.dto.hook.OnPlayHookParam;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZLMHttpHookListenerTest {

    @Mock private PlayTicketService playTicketService;
    @Mock private IMediaService mediaService;

    private ZLMHttpHookListener listener;

    @BeforeEach
    void setUp() {
        listener = new ZLMHttpHookListener();
        ReflectionTestUtils.setField(
                listener, "playTicketService", playTicketService);
        ReflectionTestUtils.setField(listener, "mediaService", mediaService);
    }

    @Test
    void validNodeAndExactlyBoundTicketAreAllowed() {
        when(playTicketService.verify(any())).thenReturn(
                PlayTicketDecision.allow());

        HookResult result = listener.onPlay(
                authenticatedRequest("zlm-1", MediaNodeType.ZLM),
                playParam("zlm-1", "live", "stream-1", "ws-flv",
                        "foo=bar&ticket=opaque%2Bticket"));

        assertEquals(0, result.getCode());
        ArgumentCaptor<MediaPlayHookRequest> requestCaptor =
                ArgumentCaptor.forClass(MediaPlayHookRequest.class);
        verify(playTicketService).verify(requestCaptor.capture());
        assertEquals(new MediaPlayHookRequest(
                        "zlm-1", "live", "stream-1", "ws-flv",
                        "opaque+ticket"),
                requestCaptor.getValue());
        verifyNoInteractions(mediaService);
    }

    @Test
    void rtpAndLegacyAuthorityCannotBypassMissingTicket() {
        when(playTicketService.verify(any())).thenReturn(
                PlayTicketDecision.deny("MALFORMED_REQUEST"));

        HookResult result = listener.onPlay(
                authenticatedRequest("zlm-1", MediaNodeType.ZLM),
                playParam("zlm-1", "rtp", "stream-1", "flv", null));

        assertEquals(401, result.getCode());
        ArgumentCaptor<MediaPlayHookRequest> requestCaptor =
                ArgumentCaptor.forClass(MediaPlayHookRequest.class);
        verify(playTicketService).verify(requestCaptor.capture());
        assertEquals("rtp", requestCaptor.getValue().app());
        assertNull(requestCaptor.getValue().ticket());
        verifyNoInteractions(mediaService);
    }

    @Test
    void duplicateTicketAndChangedSchemaAreDenied() {
        when(playTicketService.verify(any())).thenReturn(
                PlayTicketDecision.deny("SCHEMA_NOT_ALLOWED"));

        HookResult result = listener.onPlay(
                authenticatedRequest("zlm-1", MediaNodeType.ZLM),
                playParam("zlm-1", "live", "stream-1", "hls",
                        "ticket=one&ticket=two"));

        assertEquals(401, result.getCode());
        ArgumentCaptor<MediaPlayHookRequest> requestCaptor =
                ArgumentCaptor.forClass(MediaPlayHookRequest.class);
        verify(playTicketService).verify(requestCaptor.capture());
        assertEquals("hls", requestCaptor.getValue().schema());
        assertNull(requestCaptor.getValue().ticket());
    }

    @Test
    void missingOrMismatchedNodePrincipalFailsBeforeTicketVerification() {
        OnPlayHookParam param = playParam(
                "zlm-1", "live", "stream-1", "flv", "ticket=opaque");

        HookResult missing = listener.onPlay(
                new MockHttpServletRequest(), param);
        HookResult wrongId = listener.onPlay(
                authenticatedRequest("zlm-2", MediaNodeType.ZLM), param);
        HookResult wrongType = listener.onPlay(
                authenticatedRequest("zlm-1", MediaNodeType.ABL), param);

        assertEquals(401, missing.getCode());
        assertEquals(401, wrongId.getCode());
        assertEquals(401, wrongType.getCode());
        verifyNoInteractions(playTicketService, mediaService);
    }

    @Test
    void ticketDependencyFailureFailsClosed() {
        when(playTicketService.verify(any())).thenThrow(
                new IllegalStateException("redis unavailable"));

        HookResult result = listener.onPlay(
                authenticatedRequest("zlm-1", MediaNodeType.ZLM),
                playParam("zlm-1", "live", "stream-1", "flv",
                        "ticket=opaque"));

        assertEquals(401, result.getCode());
        verify(mediaService, never()).authenticateLegacyPlayInAudit(
                any(), any(), any());
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

    private OnPlayHookParam playParam(
            String mediaServerId,
            String app,
            String stream,
            String schema,
            String params) {
        OnPlayHookParam param = new OnPlayHookParam();
        param.setMediaServerId(mediaServerId);
        param.setApp(app);
        param.setStream(stream);
        param.setSchema(schema);
        param.setParams(params);
        return param;
    }
}
