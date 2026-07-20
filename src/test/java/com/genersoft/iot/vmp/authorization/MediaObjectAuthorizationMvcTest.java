package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.common.InviteSessionType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.GlobalExceptionHandler;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.gb28181.controller.ChannelController;
import com.genersoft.iot.vmp.gb28181.controller.ChannelFrontEndController;
import com.genersoft.iot.vmp.gb28181.controller.DeviceControl;
import com.genersoft.iot.vmp.gb28181.controller.GBRecordController;
import com.genersoft.iot.vmp.gb28181.controller.PlayController;
import com.genersoft.iot.vmp.gb28181.controller.PlaybackController;
import com.genersoft.iot.vmp.gb28181.controller.PtzController;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelControlService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelPlayService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelService;
import com.genersoft.iot.vmp.gb28181.service.IPlayService;
import com.genersoft.iot.vmp.gb28181.service.IPTZService;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadTicketGrant;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import com.genersoft.iot.vmp.web.custom.CameraChannelController;
import com.genersoft.iot.vmp.web.custom.service.CameraChannelService;
import com.genersoft.iot.vmp.web.gb28181.ApiControlController;
import com.genersoft.iot.vmp.web.gb28181.ApiDeviceController;
import com.genersoft.iot.vmp.web.gb28181.ApiStreamController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaObjectAuthorizationMvcTest {

    @Mock private GbChannelAccessAuthorizer authorizer;
    @Mock private IPlayService playService;
    @Mock private IPTZService ptzService;
    @Mock private SIPCommander sipCommander;
    @Mock private IGbChannelService channelService;
    @Mock private IGbChannelPlayService channelPlayService;
    @Mock private IGbChannelControlService channelControlService;
    @Mock private DeferredResultHolder resultHolder;
    @Mock private IDeviceService deviceService;
    @Mock private IDeviceChannelService deviceChannelService;
    @Mock private UserSetting userSetting;
    @Mock private CameraChannelService cameraChannelService;
    @Mock private DynamicTask dynamicTask;
    @Mock private DownloadResourceResolver downloadResourceResolver;
    @Mock private DownloadTicketService downloadTicketService;

    @Test
    void allowedLivePlayCallsPlayServiceExactlyOnce() throws Exception {
        Device device = device("device-a", 11);
        DeviceChannel channel = channel("channel-a", 101);
        PlayController controller = inject(new PlayController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService,
                "deviceService", deviceService,
                "deviceChannelService", deviceChannelService,
                "userSetting", userSetting);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        when(deviceChannelService.getOne("device-a", "channel-a"))
                .thenReturn(channel);
        when(userSetting.getPlayTimeout()).thenReturn(1000);

        mvc(controller).perform(get("/api/play/start/device-a/channel-a"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(authorizer).requireGbChannel(
                "video:channel:play", "device-a", "channel-a");
        verify(playService).play(eq(device), eq(channel), any());
    }

    @Test
    void allowedLiveStopCallsStopServiceExactlyOnce() throws Exception {
        Device device = device("device-a", 11);
        DeviceChannel channel = channel("channel-a", 101);
        PlayController controller = inject(new PlayController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService,
                "deviceService", deviceService,
                "deviceChannelService", deviceChannelService);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        when(deviceChannelService.getOneForSource("device-a", "channel-a"))
                .thenReturn(channel);

        mvc(controller).perform(get("/api/play/stop/device-a/channel-a"))
                .andExpect(status().isOk());

        verify(authorizer).requireGbChannel(
                "video:channel:stop", "device-a", "channel-a");
        verify(playService).stop(
                InviteSessionType.PLAY,
                device,
                channel,
                "device-a_channel-a");
    }

    @Test
    void allowedPtzControlCallsCommandServiceExactlyOnce() throws Exception {
        Device device = device("device-a", 11);
        PtzController controller = inject(new PtzController(),
                "channelAccessAuthorizer", authorizer,
                "deviceService", deviceService,
                "ptzService", ptzService);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);

        mvc(controller).perform(get("/api/front-end/common/device-a/channel-a")
                        .param("cmdCode", "1")
                        .param("parameter1", "2")
                        .param("parameter2", "3")
                        .param("combindCode2", "4"))
                .andExpect(status().isOk());

        verify(authorizer).requireGbChannel(
                "video:ptz:control", "device-a", "channel-a");
        verify(ptzService).frontEndCommand(
                device, "channel-a", 1, 2, 3, 4);
    }

    @Test
    void allowedPresetAndCruiseUseTheirExactPermissions() throws Exception {
        Device device = device("device-a", 11);
        PtzController controller = inject(new PtzController(),
                "channelAccessAuthorizer", authorizer,
                "deviceService", deviceService,
                "ptzService", ptzService);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        MockMvc mockMvc = mvc(controller);

        mockMvc.perform(get("/api/front-end/preset/add/device-a/channel-a")
                        .param("presetId", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/front-end/cruise/start/device-a/channel-a")
                        .param("cruiseId", "2"))
                .andExpect(status().isOk());

        verify(authorizer).requireGbChannel(
                "video:ptz:preset", "device-a", "channel-a");
        verify(authorizer).requireGbChannel(
                "video:ptz:cruise", "device-a", "channel-a");
        verify(authorizer, never()).requireGbChannel(
                "video:ptz:control", "device-a", "channel-a");
        verify(ptzService).frontEndCommand(
                device, "channel-a", 0x81, 1, 1, 0);
        verify(ptzService).frontEndCommand(
                device, "channel-a", 0x88, 2, 0, 0);
    }

    @Test
    void allowedTalkAndBroadcastCallTheirBusinessServicesExactlyOnce()
            throws Exception {
        CommonGBChannel channel = new CommonGBChannel();
        channel.setGbId(101);
        ChannelController controller = inject(new ChannelController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", channelService,
                "channelPlayService", channelPlayService);
        when(channelService.getOne(101)).thenReturn(channel);
        MockMvc mockMvc = mvc(controller);

        mockMvc.perform(get("/api/common/channel/talk/stop")
                        .param("channelId", "101"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/common/channel/broadcast/stop")
                        .param("channelId", "101"))
                .andExpect(status().isOk());

        verify(authorizer).requireChannel("video:channel:talk", 101L);
        verify(authorizer).requireChannel("video:channel:broadcast", 101L);
        verify(channelPlayService).stopTalk(channel);
        verify(channelPlayService).stopBroadcast(channel);
    }

    @Test
    void allowedRecordQueryCallsQueryServiceExactlyOnce() throws Exception {
        Device device = device("device-a", 11);
        DeviceChannel channel = channel("channel-a", 101);
        GBRecordController controller = inject(new GBRecordController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", deviceChannelService,
                "deviceService", deviceService,
                "userSetting", userSetting);
        when(userSetting.getRecordInfoTimeout()).thenReturn(1000);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        when(deviceChannelService.getOneForSource(11, "channel-a"))
                .thenReturn(channel);

        mvc(controller).perform(get("/api/gb_record/query/device-a/channel-a")
                        .param("startTime", "2026-07-15 00:00:00")
                        .param("endTime", "2026-07-15 01:00:00"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(authorizer).requireGbChannel(
                "video:record:list", "device-a", "channel-a");
        verify(deviceChannelService).queryRecordInfo(
                eq(device),
                eq(channel),
                eq("2026-07-15 00:00:00"),
                eq("2026-07-15 01:00:00"),
                any());
    }

    @Test
    void allowedPlaybackControlCallsPlayServiceExactlyOnce() throws Exception {
        PlaybackController controller = inject(new PlaybackController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);

        mvc(controller).perform(get("/api/playback/pause/stream-a"))
                .andExpect(status().isOk());

        verify(authorizer).requireRtpStream(
                "video:record:play", "stream-a");
        verify(playService).playbackPause("stream-a");
    }

    @Test
    void allowedRecordDownloadStopCallsPlayServiceExactlyOnce()
            throws Exception {
        Device device = device("device-a", 11);
        DeviceChannel channel = channel("channel-a", 101);
        GBRecordController controller = inject(new GBRecordController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", deviceChannelService,
                "deviceService", deviceService,
                "playService", playService);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        when(deviceChannelService.getOneForSource("device-a", "channel-a"))
                .thenReturn(channel);

        mvc(controller).perform(get(
                        "/api/gb_record/download/stop/device-a/channel-a/stream-a"))
                .andExpect(status().isOk());

        verify(authorizer).requireGbChannel(
                "video:record:download", "device-a", "channel-a");
        verify(playService).stop(
                InviteSessionType.DOWNLOAD, device, channel, "stream-a");
    }

    @Test
    void completedRecordDownloadReturnsOnlyBoundShortTicket() throws Exception {
        Device device = device("device-a", 11);
        DeviceChannel channel = channel("channel-a", 101);
        VideoResourceRef channelResource = new VideoResourceRef(
                VideoResourceType.CHANNEL, 101L);
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setProgress(1.0);
        streamInfo.setDownloadRecordId(73);
        DownloadTicketGrant grant = mock(DownloadTicketGrant.class);
        Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
        DownloadTicketResponse ticket = new DownloadTicketResponse(
                "D".repeat(43),
                issuedAt,
                issuedAt.plusSeconds(120),
                "/api/media/download/" + "D".repeat(43),
                "record.mp4",
                "video/mp4");
        GBRecordController controller = inject(new GBRecordController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", deviceChannelService,
                "deviceService", deviceService,
                "playService", playService,
                "downloadResourceResolver", downloadResourceResolver,
                "downloadTicketService", downloadTicketService);
        when(authorizer.requireGbChannel(
                "video:record:download", "device-a", "channel-a"))
                .thenReturn(channelResource);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);
        when(deviceChannelService.getOne("device-a", "channel-a"))
                .thenReturn(channel);
        when(playService.getDownLoadInfo(device, channel, "stream-a"))
                .thenReturn(streamInfo);
        when(downloadResourceResolver.resolveGbRecord(73L, channelResource))
                .thenReturn(grant);
        when(downloadTicketService.issueTrusted(grant)).thenReturn(ticket);

        mvc(controller).perform(get(
                        "/api/gb_record/download/progress/device-a/channel-a/stream-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadTicket.downloadUrl").value(
                        "/api/media/download/" + "D".repeat(43)))
                .andExpect(jsonPath("$.downloadRecordId").doesNotExist())
                .andExpect(jsonPath("$.downLoadFilePath").doesNotExist())
                .andExpect(jsonPath("$.httpPath").doesNotExist());

        verify(downloadResourceResolver).resolveGbRecord(73L, channelResource);
        verify(downloadTicketService).issueTrusted(grant);
    }

    @Test
    void allowedLegacyPtzCallsSipCommanderExactlyOnce() throws Exception {
        Device device = device("device-a", 11);
        ApiControlController controller = inject(new ApiControlController(),
                "channelAccessAuthorizer", authorizer,
                "cmder", sipCommander,
                "deviceService", deviceService);
        when(deviceService.getDeviceByDeviceId("device-a")).thenReturn(device);

        mvc(controller).perform(get("/api/v1/control/ptz")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("command", "left")
                        .param("speed", "10"))
                .andExpect(status().isOk());

        verify(authorizer).requireGbChannel(
                "video:ptz:control", "device-a", "channel-a");
        verify(sipCommander).frontEndCmd(
                device, "channel-a", 2, 10, 10, 10);
    }

    @Test
    void allowedCustomCameraStopCallsCameraServiceExactlyOnce()
            throws Exception {
        CameraChannelController controller = inject(
                new CameraChannelController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", cameraChannelService);

        mvc(controller).perform(get("/api/sy/camera/control/stop")
                        .param("deviceId", "channel-a")
                        .param("deviceCode", "device-a"))
                .andExpect(status().isOk());

        verify(authorizer).requireUnifiedChannel(
                "video:channel:stop", "channel-a", "device-a");
        verify(cameraChannelService).stopPlay("channel-a", "device-a");
    }

    @Test
    void nonexistentLiveChannelReturnsNotFoundWithoutBusinessExecution()
            throws Exception {
        PlayController controller = inject(new PlayController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);
        when(authorizer.requireGbChannel(
                "video:channel:play", "device-a", "missing"))
                .thenThrow(new VideoResourceNotFoundException("missing"));

        mvc(controller).perform(get("/api/play/start/device-a/missing"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(playService);
    }

    @Test
    void deniedLiveStopNeverCallsPlayService() throws Exception {
        PlayController controller = inject(new PlayController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);
        when(authorizer.requireGbChannel(
                "video:channel:stop", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/play/stop/device-a/channel-a"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playService);
    }

    @Test
    void deniedPresetAndCruiseNeverCallCommandService() throws Exception {
        PtzController controller = inject(new PtzController(),
                "channelAccessAuthorizer", authorizer,
                "ptzService", ptzService);
        when(authorizer.requireGbChannel(
                "video:ptz:preset", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));
        when(authorizer.requireGbChannel(
                "video:ptz:cruise", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));
        MockMvc mockMvc = mvc(controller);

        mockMvc.perform(get("/api/front-end/preset/add/device-a/channel-a")
                        .param("presetId", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/front-end/cruise/start/device-a/channel-a")
                        .param("cruiseId", "2"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ptzService);
    }

    @Test
    void deniedBroadcastNeverCallsBusinessServices() throws Exception {
        ChannelController controller = inject(new ChannelController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", channelService,
                "channelPlayService", channelPlayService);
        when(authorizer.requireChannel("video:channel:broadcast", 101L))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/common/channel/broadcast/start")
                        .param("channelId", "101"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(channelService, channelPlayService);
    }

    @Test
    void deniedRecordQueryNeverCallsDeviceOrChannelServices()
            throws Exception {
        GBRecordController controller = inject(new GBRecordController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", deviceChannelService,
                "deviceService", deviceService);
        when(authorizer.requireGbChannel(
                "video:record:list", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/gb_record/query/device-a/channel-a")
                        .param("startTime", "2026-07-15 00:00:00")
                        .param("endTime", "2026-07-15 01:00:00"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceService, deviceChannelService);
    }

    @Test
    void deniedLivePlayNeverCallsPlayService() throws Exception {
        PlayController controller = inject(new PlayController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);
        when(authorizer.requireGbChannel(
                "video:channel:play", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/play/start/device-a/channel-a"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playService);
    }

    @Test
    void deniedPlaybackStreamControlNeverCallsPlayService() throws Exception {
        PlaybackController controller = inject(new PlaybackController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);
        when(authorizer.requireRtpStream(
                "video:record:play", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/playback/pause/stream-a"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playService);
    }

    @Test
    void deniedGbPtzNeverCallsCommandService() throws Exception {
        PtzController controller = inject(new PtzController(),
                "channelAccessAuthorizer", authorizer,
                "ptzService", ptzService);
        when(authorizer.requireGbChannel(
                "video:ptz:control", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/front-end/ptz/device-a/channel-a")
                        .param("command", "left")
                        .param("horizonSpeed", "10")
                        .param("verticalSpeed", "10")
                        .param("zoomSpeed", "10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ptzService);
    }

    @Test
    void deniedUnifiedChannelTalkNeverCallsBusinessServices() throws Exception {
        ChannelController controller = inject(new ChannelController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", channelService,
                "channelPlayService", channelPlayService);
        when(authorizer.requireChannel("video:channel:talk", 101L))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/common/channel/talk/start")
                        .param("channelId", "101"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(channelService, channelPlayService);
    }

    @Test
    void deniedUnifiedPtzNeverCallsBusinessServices() throws Exception {
        ChannelFrontEndController controller = inject(
                new ChannelFrontEndController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", channelService,
                "channelControlService", channelControlService);
        when(authorizer.requireChannel("video:ptz:control", 101L))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/common/channel/front-end/ptz")
                        .param("channelId", "101")
                        .param("command", "left"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(channelService, channelControlService);
    }

    @Test
    void deniedRecordDownloadNeverRegistersCallbackOrStartsInvite() throws Exception {
        GBRecordController controller = inject(new GBRecordController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService,
                "resultHolder", resultHolder);
        when(authorizer.requireGbChannel(
                "video:record:download", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get(
                        "/api/gb_record/download/start/device-a/channel-a")
                        .param("startTime", "2026-07-15 00:00:00")
                        .param("endTime", "2026-07-15 01:00:00")
                        .param("downloadSpeed", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(resultHolder, playService);
    }

    @Test
    void deniedManualRecordNeverCallsDeviceService() throws Exception {
        DeviceControl controller = inject(new DeviceControl(),
                "channelAccessAuthorizer", authorizer,
                "deviceService", deviceService);
        when(authorizer.requireGbChannel(
                "video:record:play", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/device/control/record")
                        .param("deviceId", "device-a")
                        .param("channelId", "channel-a")
                        .param("recordCmdStr", "Record"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedLegacyStreamApiNeverCallsPlayService() throws Exception {
        ApiStreamController controller = inject(new ApiStreamController(),
                "channelAccessAuthorizer", authorizer,
                "playService", playService);
        when(authorizer.requireGbChannel(
                "video:channel:play", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/v1/stream/start")
                        .param("serial", "device-a")
                        .param("code", "channel-a"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playService);
    }

    @Test
    void deniedLegacyControlApiNeverCallsSipCommander() throws Exception {
        ApiControlController controller = inject(new ApiControlController(),
                "channelAccessAuthorizer", authorizer,
                "cmder", sipCommander);
        when(authorizer.requireGbChannel(
                "video:ptz:control", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/v1/control/ptz")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("command", "left")
                        .param("speed", "10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sipCommander);
    }

    @Test
    void deniedLegacyPresetQueryNeverCallsDeviceService() throws Exception {
        ApiDeviceController controller = inject(new ApiDeviceController(),
                "channelAccessAuthorizer", authorizer,
                "deviceService", deviceService);
        when(authorizer.requireGbChannel(
                "video:ptz:preset", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/v1/device/fetchpreset")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("timeout", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedCustomCameraApiNeverCallsCameraServiceOrSchedulesStop() throws Exception {
        CameraChannelController controller = inject(new CameraChannelController(),
                "channelAccessAuthorizer", authorizer,
                "channelService", cameraChannelService,
                "dynamicTask", dynamicTask);
        when(authorizer.requireUnifiedChannel(
                "video:ptz:control", "channel-a", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        mvc(controller).perform(get("/api/sy/camera/control/ptz")
                        .param("deviceId", "channel-a")
                        .param("deviceCode", "device-a")
                        .param("command", "left")
                        .param("speed", "10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cameraChannelService, dynamicTask);
    }

    private Device device(String deviceId, int id) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceId(deviceId);
        return device;
    }

    private DeviceChannel channel(String channelId, int id) {
        DeviceChannel channel = new DeviceChannel();
        channel.setId(id);
        channel.setDeviceId(channelId);
        return channel;
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private <T> T inject(T target, Object... fieldAndValuePairs) {
        for (int index = 0; index < fieldAndValuePairs.length; index += 2) {
            ReflectionTestUtils.setField(
                    target,
                    (String) fieldAndValuePairs[index],
                    fieldAndValuePairs[index + 1]);
        }
        return target;
    }
}
