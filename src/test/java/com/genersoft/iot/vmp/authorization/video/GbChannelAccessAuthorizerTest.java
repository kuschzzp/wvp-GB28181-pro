package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.common.enums.MediaStreamUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbChannelAccessAuthorizerTest {

    private static final String CHANNEL_PLAY = "video:channel:play";

    @Mock
    private AuthorizationFacade authorizationFacade;

    @Mock
    private ResourceResolverRegistry resolverRegistry;

    private GbChannelAccessAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new GbChannelAccessAuthorizer(
                authorizationFacade, resolverRegistry);
    }

    @Test
    void resolvesExternalGbChannelBeforeRequiringItsInternalResource() {
        VideoResourceRef channel = channel(101);
        when(resolverRegistry.resolveGbChannel("device-a", "channel-a"))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireGbChannel(
                CHANNEL_PLAY, "device-a", "channel-a"));

        InOrder order = inOrder(resolverRegistry, authorizationFacade);
        order.verify(resolverRegistry).resolveGbChannel("device-a", "channel-a");
        order.verify(authorizationFacade).requireVideoResource(
                CHANNEL_PLAY, VideoResourceType.CHANNEL, 101L);
    }

    @Test
    void resolvesUnifiedCompatibilityChannelWithoutGuessingAmbiguousSources() {
        VideoResourceRef channel = channel(202);
        when(resolverRegistry.resolveUnifiedChannel("channel-a", null))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireUnifiedChannel(
                CHANNEL_PLAY, "channel-a", null));

        verify(authorizationFacade).requireVideoResource(
                CHANNEL_PLAY, VideoResourceType.CHANNEL, 202L);
    }

    @Test
    void resolvesExternalGbDeviceBeforeDeviceLevelPositionCommand() {
        VideoResourceRef device = new VideoResourceRef(
                VideoResourceType.GB_DEVICE, 11);
        when(resolverRegistry.resolveGbDevice("device-a")).thenReturn(device);

        assertEquals(device, authorizer.requireGbDevice(
                "video:position:subscribe", "device-a"));

        verify(authorizationFacade).requireVideoResource(
                "video:position:subscribe", VideoResourceType.GB_DEVICE, 11L);
    }

    @Test
    void snapshotAndPositionDetailsHideDeniedChannelAsNotFound() {
        VideoResourceRef channel = channel(202);
        when(resolverRegistry.resolveGbChannel("device-a", "channel-a"))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireGbChannelHidden(
                "video:device:list", "device-a", "channel-a"));
        assertEquals(channel(303), authorizer.requireChannelHidden(
                "video:position:list", 303));

        verify(authorizationFacade).requireVideoResource(
                "video:device:list",
                VideoResourceType.CHANNEL,
                202,
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
        verify(authorizationFacade).requireVideoResource(
                "video:position:list",
                VideoResourceType.CHANNEL,
                303,
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
    }

    @Test
    void planChannelBatchUsesOneAllOrNothingAuthorizationCall() {
        authorizer.requireAllChannels(
                "video:cloud-record:collect", List.of(101, 202));

        verify(authorizationFacade).requireAllVideoResources(
                "video:cloud-record:collect",
                List.of(channel(101), channel(202)));
    }

    @Test
    void deviceIdsAndBatchUseStableGbDeviceResources() {
        VideoResourceRef device = new VideoResourceRef(
                VideoResourceType.GB_DEVICE, 11);

        assertEquals(device, authorizer.requireGbDeviceId(
                "video:device:edit", 11));
        authorizer.requireAllGbDevices(
                "video:platform:share-channel", List.of(11, 12, 11));

        verify(authorizationFacade).requireVideoResource(
                "video:device:edit", VideoResourceType.GB_DEVICE, 11L);
        verify(authorizationFacade).requireAllVideoResources(
                "video:platform:share-channel",
                List.of(
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 11),
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 12)));
    }

    @Test
    void deviceDetailCanHideDeniedResourceAsNotFound() {
        VideoResourceRef device = new VideoResourceRef(
                VideoResourceType.GB_DEVICE, 11);
        when(resolverRegistry.resolveGbDevice("device-a")).thenReturn(device);

        assertEquals(device, authorizer.requireGbDeviceHidden(
                "video:device:list", "device-a"));
        assertEquals(device, authorizer.requireGbDeviceIdHidden(
                "video:device:list", 11));

        verify(authorizationFacade, times(2)).requireVideoResource(
                "video:device:list",
                VideoResourceType.GB_DEVICE,
                11,
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
    }

    @Test
    void requiresStableInternalChannelId() {
        assertEquals(channel(303), authorizer.requireChannel(CHANNEL_PLAY, 303));

        verify(authorizationFacade).requireVideoResource(
                CHANNEL_PLAY, VideoResourceType.CHANNEL, 303L);
        verify(resolverRegistry, never()).resolveGbChannel(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resolvesCurrentRtpStreamBackToItsChannel() {
        VideoResourceRef channel = channel(404);
        when(resolverRegistry.resolveAppStream(MediaStreamUtil.RTP_APP, "stream-a"))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireRtpStream(
                "video:record:play", "stream-a"));

        verify(authorizationFacade).requireVideoResource(
                "video:record:play", VideoResourceType.CHANNEL, 404L);
    }

    @Test
    void missingResourceAndResourceDenialPropagateBeforeBusinessExecution() {
        when(resolverRegistry.resolveGbChannel("missing", "channel-a"))
                .thenThrow(new VideoResourceNotFoundException("missing"));

        assertThrows(VideoResourceNotFoundException.class,
                () -> authorizer.requireGbChannel(
                        CHANNEL_PLAY, "missing", "channel-a"));
        verify(authorizationFacade, never()).requireVideoResource(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());

        when(resolverRegistry.resolveGbChannel("device-a", "channel-a"))
                .thenReturn(channel(101));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationFacade)
                .requireVideoResource(
                        CHANNEL_PLAY, VideoResourceType.CHANNEL, 101L);

        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireGbChannel(
                        CHANNEL_PLAY, "device-a", "channel-a"));
    }

    private VideoResourceRef channel(long id) {
        return new VideoResourceRef(VideoResourceType.CHANNEL, id);
    }
}
