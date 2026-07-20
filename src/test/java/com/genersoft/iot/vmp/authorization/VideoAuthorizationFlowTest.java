package com.genersoft.iot.vmp.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.StreamResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.BasicParam;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.PlatformChannel;
import com.genersoft.iot.vmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.vmp.gb28181.controller.ChannelController;
import com.genersoft.iot.vmp.gb28181.controller.DeviceConfig;
import com.genersoft.iot.vmp.gb28181.controller.DeviceControl;
import com.genersoft.iot.vmp.gb28181.controller.DeviceQuery;
import com.genersoft.iot.vmp.gb28181.controller.MediaController;
import com.genersoft.iot.vmp.gb28181.controller.PlatformController;
import com.genersoft.iot.vmp.gb28181.controller.PlayController;
import com.genersoft.iot.vmp.gb28181.controller.bean.ChannelToGroupByGbDeviceParam;
import com.genersoft.iot.vmp.gb28181.controller.bean.ChannelToRegionParam;
import com.genersoft.iot.vmp.gb28181.controller.bean.UpdateChannelParam;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelService;
import com.genersoft.iot.vmp.gb28181.service.IPlatformChannelService;
import com.genersoft.iot.vmp.gb28181.service.IPlayService;
import com.genersoft.iot.vmp.gb28181.session.SipInviteSessionManager;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushService;
import com.genersoft.iot.vmp.web.custom.CameraChannelController;
import com.genersoft.iot.vmp.web.custom.service.CameraChannelService;
import com.genersoft.iot.vmp.vmanager.ps.PsController;
import com.genersoft.iot.vmp.vmanager.rtp.RtpController;
import com.genersoft.iot.vmp.vmanager.server.ServerController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoAuthorizationFlowTest {

    @Mock private GbChannelAccessAuthorizer channelAuthorizer;
    @Mock private StreamResourceAccessAuthorizer streamAuthorizer;
    @Mock private IDeviceService deviceService;
    @Mock private IDeviceChannelService deviceChannelService;
    @Mock private IGbChannelService channelService;
    @Mock private IMediaServerService mediaServerService;
    @Mock private IStreamProxyService streamProxyService;
    @Mock private IRedisCatchStorage redisCatchStorage;
    @Mock private CameraChannelService cameraChannelService;
    @Mock private IStreamPushService streamPushService;
    @Mock private IStreamPushPlayService streamPushPlayService;
    @Mock private AuthorizationFacade authorizationFacade;
    @Mock private VideoScopeQuery videoScopeQuery;
    @Mock private SipInviteSessionManager sessionManager;
    @Mock private DeferredResultHolder resultHolder;
    @Mock private IPlayService playService;
    @Mock private IPlatformChannelService platformChannelService;

    @Test
    void deviceLevelConfigDenialStopsLookupAndSipCommand() {
        DeviceConfig controller = inject(new DeviceConfig(),
                "channelAccessAuthorizer", channelAuthorizer,
                "deviceService", deviceService);
        BasicParam param = new BasicParam();
        param.setDeviceId("device-a");
        param.setChannelId("device-a");
        when(channelAuthorizer.requireGbDevice(
                "video:device:config", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.homePositionApi(param));

        verifyNoInteractions(deviceService);
    }

    @Test
    void channelLevelConfigDenialStopsLookupAndSipCommand() {
        DeviceConfig controller = inject(new DeviceConfig(),
                "channelAccessAuthorizer", channelAuthorizer,
                "deviceService", deviceService);
        when(channelAuthorizer.requireGbChannel(
                "video:device:config", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.queryBasicParam("device-a", "channel-a"));

        verifyNoInteractions(deviceService);
    }

    @Test
    void deviceControlDenialStopsDeviceLookupAndSipCommand() {
        DeviceControl controller = deviceControl();
        when(channelAuthorizer.requireGbDevice(
                "video:device:config", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.teleBootApi("device-a"));

        verifyNoInteractions(deviceService);
    }

    @Test
    void channelControlDenialStopsDeviceLookupAndSipCommand() {
        DeviceControl controller = deviceControl();
        when(channelAuthorizer.requireGbChannel(
                "video:device:config", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.homePositionApi(
                        "device-a", "channel-a", true, 30, 1));

        verifyNoInteractions(deviceService);
    }

    @Test
    void mediaLookupDenialStopsRedisMediaAndProxyAccess() {
        MediaController controller = mediaController();
        when(streamAuthorizer.requireAppStream(
                "video:channel:play", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getStreamInfoByAppAndStream(
                        new MockHttpServletRequest(),
                        "live", "stream-a", null, null, false));

        verifyNoInteractions(redisCatchStorage, mediaServerService, streamProxyService);
    }

    @Test
    void directPlayUrlDenialStopsMediaLookup() {
        MediaController controller = mediaController();
        when(streamAuthorizer.requireAppStream(
                "video:channel:play", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getPlayUrl("live", "stream-a", null));

        verifyNoInteractions(mediaServerService);
    }

    @Test
    void deniedDeviceDetailIsHiddenBeforeDatabaseLookup() {
        DeviceQuery controller = deviceQuery();
        when(channelAuthorizer.requireGbDeviceHidden(
                "video:device:list", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.devices("device-a"));

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedDeviceDeleteStopsLifecycleCleanup() {
        DeviceQuery controller = deviceQuery();
        when(channelAuthorizer.requireGbDevice(
                "video:device:remove", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.delete("device-a"));

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedChannelEditStopsDatabaseWrite() {
        DeviceQuery controller = deviceQuery();
        when(channelAuthorizer.requireChannel(
                "video:channel:edit", 101))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.changeAudio(101, true));

        verifyNoInteractions(deviceChannelService);
    }

    @Test
    void deviceUpdateUsesStableInternalIdBeforeWrite() {
        DeviceQuery controller = deviceQuery();
        Device device = new Device();
        device.setId(11);
        device.setDeviceId("device-a");
        when(channelAuthorizer.requireGbDeviceId(
                "video:device:edit", 11))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.updateDevice(device));

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedRawChannelDetailIsHiddenBeforeLookup() {
        DeviceQuery controller = deviceQuery();
        when(channelAuthorizer.requireChannelHidden(
                "video:device:list", 101))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getRawChannel(101));

        verifyNoInteractions(deviceChannelService);
    }

    @Test
    void deniedDeviceSubscriptionStopsSipAndStateChanges() {
        DeviceQuery controller = deviceQuery();
        when(channelAuthorizer.requireGbDeviceId(
                "video:position:subscribe", 11))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.subscribeMobilePosition(11, 60, 5));

        verifyNoInteractions(deviceService);
    }

    @Test
    void deniedUnifiedChannelDetailIsHiddenBeforeLookup() {
        ChannelController controller = channelController();
        when(channelAuthorizer.requireChannelHidden(
                "video:channel:list", 101))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getOne(101));

        verifyNoInteractions(channelService);
    }

    @Test
    void deniedUnifiedChannelUpdateStopsWrite() {
        ChannelController controller = channelController();
        CommonGBChannel channel = new CommonGBChannel();
        channel.setGbId(101);
        channel.setGbName("camera-a");
        when(channelAuthorizer.requireChannel(
                "video:channel:edit", 101))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.update(channel));

        verifyNoInteractions(channelService);
    }

    @Test
    void explicitChannelBatchIsAllOrNothingBeforeWrite() {
        ChannelController controller = channelController();
        ChannelToRegionParam param = new ChannelToRegionParam();
        param.setCivilCode("340100");
        param.setChannelIds(List.of(101, 102));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllChannels(
                        "video:channel:edit", List.of(101, 102));

        assertThrows(AccessDeniedException.class,
                () -> controller.addChannelToRegion(param));

        verifyNoInteractions(channelService);
    }

    @Test
    void deviceBatchUsesSourceResourceBeforeExpandingChannels() {
        ChannelController controller = channelController();
        ChannelToGroupByGbDeviceParam param =
                new ChannelToGroupByGbDeviceParam();
        param.setDeviceIds(List.of(11, 12));
        param.setParentId("group-node");
        param.setBusinessGroup("business-a");
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllGbDevices(
                        "video:channel:edit", List.of(11, 12));

        assertThrows(AccessDeniedException.class,
                () -> controller.addChannelToGroupByGbDevice(param));

        verifyNoInteractions(channelService);
    }

    @Test
    void customCameraListUsesPermissionCoupledDatabaseScope() {
        CameraChannelController controller = cameraChannelController();
        when(authorizationFacade.resolveVideoScopeQuery("video:channel:list"))
                .thenReturn(videoScopeQuery);

        controller.queryList(1, 20, "group-a", null, true);

        org.mockito.Mockito.verify(cameraChannelService).queryList(
                1, 20, "group-a", true, null, videoScopeQuery);
    }

    @Test
    void deniedCustomCameraDetailIsHiddenBeforeLookup() {
        CameraChannelController controller = cameraChannelController();
        when(channelAuthorizer.requireUnifiedChannelHidden(
                "video:channel:list", "channel-a", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getOne("channel-a", "device-a", null));

        verifyNoInteractions(cameraChannelService);
    }

    @Test
    void deniedCustomCameraUpdateStopsWrite() {
        CameraChannelController controller = cameraChannelController();
        when(channelAuthorizer.requireUnifiedChannel(
                "video:channel:edit", "channel-a", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.updateCamera(
                        "channel-a", "device-a", "camera-a", 120D, 30D, null));

        verifyNoInteractions(cameraChannelService);
    }

    @Test
    void deniedCustomPushPlayStopsStreamLookup() {
        CameraChannelController controller = cameraChannelController();
        when(streamAuthorizer.requirePush(
                "video:push:play", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getStreamInfoByAppAndStream(
                        new MockHttpServletRequest(),
                        "live", "stream-a", "call-a"));

        verifyNoInteractions(streamPushService, redisCatchStorage);
    }

    @Test
    void deniedCustomForceCloseStopsStreamMutation() {
        CameraChannelController controller = cameraChannelController();
        when(streamAuthorizer.requirePush(
                "video:push:stop", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.stop("live", "stream-a"));

        verifyNoInteractions(streamPushPlayService);
    }

    @Test
    void deniedCustomMobileMemberIsHiddenBeforeRedisLookup() {
        CameraChannelController controller = cameraChannelController();
        when(channelAuthorizer.requireUnifiedChannelHidden(
                "video:channel:list", "channel-a", null))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.test("channel-a"));

        verifyNoInteractions(cameraChannelService);
    }

    @Test
    void deniedRtpSendStopsMediaAndRedisSideEffects() {
        RtpController controller = inject(new RtpController(),
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "mediaServerService", mediaServerService);
        when(streamAuthorizer.requireAppStream(
                "video:rtp:manage", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendRTP(
                        "100001",
                        "127.0.0.1",
                        "127.0.0.1",
                        10000,
                        10002,
                        "live",
                        "stream-a",
                        "call-a",
                        true,
                        8,
                        96));

        verifyNoInteractions(mediaServerService);
    }

    @Test
    void deniedPsSendStopsMediaAndRedisSideEffects() {
        PsController controller = inject(new PsController(),
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "mediaServerService", mediaServerService);
        when(streamAuthorizer.requireAppStream(
                "video:ps:manage", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendRTP(
                        "100001",
                        "127.0.0.1",
                        10000,
                        "live",
                        "stream-a",
                        "call-a",
                        true));

        verifyNoInteractions(mediaServerService);
    }

    @Test
    void deniedMediaInfoStopsMediaServerLookup() {
        ServerController controller = inject(new ServerController(),
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "mediaServerService", mediaServerService);
        when(streamAuthorizer.requireAppStream(
                "video:media-server:list", "live", "stream-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getMediaInfo(
                        "live", "stream-a", "media-a"));

        verifyNoInteractions(mediaServerService);
    }

    @Test
    void deniedSnapshotStopsDeferredRegistrationAndSipCommand() {
        PlayController controller = playController();
        when(channelAuthorizer.requireGbChannel(
                "video:channel:play", "device-a", "channel-a"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.getSnap("device-a", "channel-a"));

        verifyNoInteractions(resultHolder, playService);
    }

    @Test
    void ssrcListOmitsChannelsOutsideTheCurrentVideoScope() {
        PlayController controller = playController();
        SsrcTransaction allowed = new SsrcTransaction();
        allowed.setDeviceId("device-a");
        allowed.setChannelId(101);
        allowed.setSsrc("0101");
        allowed.setStream("stream-a");
        SsrcTransaction denied = new SsrcTransaction();
        denied.setDeviceId("device-b");
        denied.setChannelId(102);
        denied.setSsrc("0102");
        denied.setStream("stream-b");
        when(sessionManager.getAll()).thenReturn(List.of(allowed, denied));
        org.mockito.Mockito.doAnswer(invocation -> {
            long channelId = invocation.getArgument(1);
            if (channelId == 102L) {
                throw new AccessDeniedException("denied");
            }
            return null;
        }).when(channelAuthorizer).requireChannel(
                org.mockito.ArgumentMatchers.eq("video:channel:play"),
                org.mockito.ArgumentMatchers.anyLong());

        JSONObject response = controller.getSSRC();

        assertEquals(1, response.getInteger("count"));
        JSONArray data = response.getJSONArray("data");
        assertEquals(101, data.getJSONObject(0).getInteger("channelId"));
    }

    @Test
    void platformChannelListUsesPermissionCoupledDatabaseScope() {
        PlatformController controller = platformController();
        when(authorizationFacade.resolveVideoScopeQuery(
                "video:platform:list")).thenReturn(videoScopeQuery);

        controller.queryChannelList(
                1, 20, 7, "camera", 1, true, false);

        org.mockito.Mockito.verify(platformChannelService).queryChannelList(
                1, 20, "camera", 1, true, 7, false, videoScopeQuery);
    }

    @Test
    void platformAddAllAuthorizesEveryImplicitChannelBeforeMutation() {
        PlatformController controller = platformController();
        UpdateChannelParam param = new UpdateChannelParam();
        param.setPlatformId(7);
        param.setAll(true);
        when(platformChannelService.queryChannelIds(7, false))
                .thenReturn(List.of(101, 102));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllChannels(
                        "video:platform:share-channel", List.of(101, 102));

        assertThrows(AccessDeniedException.class,
                () -> controller.addChannel(param));

        org.mockito.Mockito.verify(platformChannelService,
                org.mockito.Mockito.never()).addAllChannel(7);
    }

    @Test
    void platformDeviceBatchAuthorizesSourcesBeforeExpansion() {
        PlatformController controller = platformController();
        UpdateChannelParam param = new UpdateChannelParam();
        param.setPlatformId(7);
        param.setDeviceIds(List.of(11, 12));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllGbDevices(
                        "video:platform:share-channel", List.of(11, 12));

        assertThrows(AccessDeniedException.class,
                () -> controller.addChannelByDevice(param));

        verifyNoInteractions(platformChannelService);
    }

    @Test
    void platformPushRequiresEverySharedChannelBeforeSipOrRpc() {
        PlatformController controller = platformController();
        when(platformChannelService.queryChannelIds(7, true))
                .thenReturn(List.of(101, 102));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllChannels(
                        "video:platform:share-channel", List.of(101, 102));

        assertThrows(AccessDeniedException.class,
                () -> controller.pushChannel(7));

        org.mockito.Mockito.verify(platformChannelService,
                org.mockito.Mockito.never()).pushChannel(7);
    }

    @Test
    void platformCustomUpdateResolvesAssociationInsteadOfTrustingBodyChannelId() {
        PlatformController controller = platformController();
        PlatformChannel channel = new PlatformChannel();
        channel.setId(88);
        channel.setPlatformId(7);
        channel.setGbId(999);
        when(platformChannelService.queryChannelIdByPlatformChannelId(7, 88))
                .thenReturn(101);
        when(channelAuthorizer.requireChannel(
                "video:platform:share-channel", 101))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.updateCustomChannel(channel));

        org.mockito.Mockito.verify(platformChannelService,
                org.mockito.Mockito.never()).updateCustomChannel(channel);
    }

    private MediaController mediaController() {
        return inject(new MediaController(),
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "redisCatchStorage", redisCatchStorage,
                "mediaServerService", mediaServerService,
                "streamProxyService", streamProxyService);
    }

    private DeviceQuery deviceQuery() {
        return inject(new DeviceQuery(),
                "channelAccessAuthorizer", channelAuthorizer,
                "deviceService", deviceService,
                "deviceChannelService", deviceChannelService);
    }

    private DeviceControl deviceControl() {
        return inject(new DeviceControl(),
                "channelAccessAuthorizer", channelAuthorizer,
                "deviceService", deviceService);
    }

    private ChannelController channelController() {
        return inject(new ChannelController(),
                "channelAccessAuthorizer", channelAuthorizer,
                "channelService", channelService);
    }

    private CameraChannelController cameraChannelController() {
        return inject(new CameraChannelController(),
                "channelAccessAuthorizer", channelAuthorizer,
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "authorizationFacade", authorizationFacade,
                "channelService", cameraChannelService,
                "streamPushService", streamPushService,
                "streamPushPlayService", streamPushPlayService,
                "redisCatchStorage", redisCatchStorage);
    }

    private PlayController playController() {
        return inject(new PlayController(),
                "channelAccessAuthorizer", channelAuthorizer,
                "sessionManager", sessionManager,
                "resultHolder", resultHolder,
                "playService", playService);
    }

    private PlatformController platformController() {
        return inject(new PlatformController(),
                "channelAccessAuthorizer", channelAuthorizer,
                "authorizationFacade", authorizationFacade,
                "platformChannelService", platformChannelService);
    }

    private <T> T inject(T target, Object... fields) {
        for (int index = 0; index < fields.length; index += 2) {
            ReflectionTestUtils.setField(
                    target, (String) fields[index], fields[index + 1]);
        }
        return target;
    }
}
