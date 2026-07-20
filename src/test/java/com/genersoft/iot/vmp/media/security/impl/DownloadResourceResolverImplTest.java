package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.jt1078.dao.JTChannelMapper;
import com.genersoft.iot.vmp.jt1078.dao.JTTerminalMapper;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.storager.dao.AlarmMapper;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DownloadResourceResolverImplTest {

    @Test
    void gbRecordMustBelongToTheAuthorizedChannelBeforeReadingFileMetadata() {
        ResourceResolverRegistry registry = mock(ResourceResolverRegistry.class);
        AuthorizationFacade authorizationFacade = mock(AuthorizationFacade.class);
        CloudRecordServiceMapper recordMapper = mock(CloudRecordServiceMapper.class);
        AlarmMapper alarmMapper = mock(AlarmMapper.class);
        IDeviceChannelService channelService = mock(IDeviceChannelService.class);
        IDeviceService deviceService = mock(IDeviceService.class);
        JTChannelMapper jtChannelMapper = mock(JTChannelMapper.class);
        JTTerminalMapper jtTerminalMapper = mock(JTTerminalMapper.class);
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        VideoResourceRef requestedChannel =
                new VideoResourceRef(VideoResourceType.CHANNEL, 101L);
        VideoResourceRef recordChannel =
                new VideoResourceRef(VideoResourceType.CHANNEL, 102L);
        when(registry.resolveRecord(51L)).thenReturn(recordChannel);

        DownloadResourceResolverImpl resolver = new DownloadResourceResolverImpl(
                registry,
                authorizationFacade,
                recordMapper,
                alarmMapper,
                channelService,
                deviceService,
                jtChannelMapper,
                jtTerminalMapper,
                mediaServerService,
                new DownloadPathGuard(),
                new DownloadAccessProperties(
                        Duration.ofMinutes(2),
                        100,
                        "snap",
                        "",
                        "/_protected_media/"));

        assertThrows(
                VideoResourceNotFoundException.class,
                () -> resolver.resolveGbRecord(51L, requestedChannel));

        verifyNoInteractions(authorizationFacade, recordMapper, mediaServerService);
    }

    @Test
    void authorizesWholeZipBeforeReadingAnyFileMetadataOrMediaNode() {
        ResourceResolverRegistry registry = mock(ResourceResolverRegistry.class);
        AuthorizationFacade authorizationFacade = mock(AuthorizationFacade.class);
        CloudRecordServiceMapper recordMapper = mock(CloudRecordServiceMapper.class);
        AlarmMapper alarmMapper = mock(AlarmMapper.class);
        IDeviceChannelService channelService = mock(IDeviceChannelService.class);
        IDeviceService deviceService = mock(IDeviceService.class);
        JTChannelMapper jtChannelMapper = mock(JTChannelMapper.class);
        JTTerminalMapper jtTerminalMapper = mock(JTTerminalMapper.class);
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        VideoResourceRef first = new VideoResourceRef(VideoResourceType.CHANNEL, 101L);
        VideoResourceRef second = new VideoResourceRef(VideoResourceType.CHANNEL, 102L);
        when(registry.resolveRecord(51L)).thenReturn(first);
        when(registry.resolveRecord(52L)).thenReturn(second);
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationFacade)
                .requireAllVideoResources(
                        "video:cloud-record:download",
                        List.of(first, second));

        DownloadResourceResolverImpl resolver = new DownloadResourceResolverImpl(
                registry,
                authorizationFacade,
                recordMapper,
                alarmMapper,
                channelService,
                deviceService,
                jtChannelMapper,
                jtTerminalMapper,
                mediaServerService,
                new DownloadPathGuard(),
                new DownloadAccessProperties(
                        Duration.ofMinutes(2),
                        100,
                        "snap",
                        "",
                        "/_protected_media/"));

        assertThrows(
                AccessDeniedException.class,
                () -> resolver.resolve(DownloadTicketRequest.batch(
                        DownloadResourceType.CLOUD_RECORD_ZIP,
                        List.of(51L, 52L))));

        verifyNoInteractions(recordMapper, mediaServerService);
    }
}
