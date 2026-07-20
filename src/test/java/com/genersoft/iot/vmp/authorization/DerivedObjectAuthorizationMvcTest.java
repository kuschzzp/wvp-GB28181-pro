package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.authorization.video.DerivedResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.JtResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.conf.GlobalExceptionHandler;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.ftpServer.FtpSetting;
import com.genersoft.iot.vmp.gb28181.controller.DeviceQuery;
import com.genersoft.iot.vmp.gb28181.controller.MobilePositionController;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.jt1078.controller.JT1078Controller;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078PlayService;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.service.IAlarmService;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.IMobilePositionService;
import com.genersoft.iot.vmp.service.IRecordPlanService;
import com.genersoft.iot.vmp.vmanager.alarm.AlarmController;
import com.genersoft.iot.vmp.vmanager.cloudRecord.CloudRecordController;
import com.genersoft.iot.vmp.vmanager.recordPlan.RecordPlanController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DerivedObjectAuthorizationMvcTest {

    @Mock private ICloudRecordService cloudRecordService;
    @Mock private IMediaServerService mediaServerService;
    @Mock private IAlarmService alarmService;
    @Mock private IMobilePositionService mobilePositionService;
    @Mock private IDeviceService deviceService;
    @Mock private IDeviceChannelService deviceChannelService;
    @Mock private ISIPCommander sipCommander;
    @Mock private DeferredResultHolder resultHolder;
    @Mock private IRecordPlanService recordPlanService;
    @Mock private Ijt1078Service jtService;
    @Mock private Ijt1078PlayService jtPlayService;
    @Mock private AuthorizationFacade authorizationFacade;
    @Mock private DerivedResourceAccessAuthorizer derivedAuthorizer;
    @Mock private GbChannelAccessAuthorizer channelAuthorizer;
    @Mock private JtResourceAccessAuthorizer jtAuthorizer;
    @Mock private DownloadTicketService downloadTicketService;
    @Mock private DownloadResourceResolver downloadResourceResolver;

    private MockMvc cloudMvc;
    private MockMvc alarmMvc;
    private MockMvc snapshotMvc;
    private MockMvc positionMvc;
    private MockMvc recordPlanMvc;
    private MockMvc jtMvc;
    private FtpSetting ftpSetting;

    @BeforeEach
    void setUp() {
        UserSetting userSetting = new UserSetting();
        userSetting.setPlayTimeout(1000);

        CloudRecordController cloudController = inject(
                new CloudRecordController(),
                "cloudRecordService", cloudRecordService,
                "authorizationFacade", authorizationFacade,
                "derivedResourceAccessAuthorizer", derivedAuthorizer,
                "channelAccessAuthorizer", channelAuthorizer,
                "mediaServerService", mediaServerService,
                "downloadTicketService", downloadTicketService,
                "userSetting", userSetting);
        AlarmController alarmController = new AlarmController(
                alarmService,
                authorizationFacade,
                derivedAuthorizer,
                downloadTicketService);
        DeviceQuery deviceQuery = inject(
                new DeviceQuery(),
                "channelAccessAuthorizer", channelAuthorizer,
                "downloadTicketService", downloadTicketService);
        MobilePositionController positionController = inject(
                new MobilePositionController(),
                "mobilePositionService", mobilePositionService,
                "authorizationFacade", authorizationFacade,
                "channelAccessAuthorizer", channelAuthorizer,
                "cmder", sipCommander,
                "resultHolder", resultHolder,
                "deviceService", deviceService);
        RecordPlanController planController = inject(
                new RecordPlanController(),
                "recordPlanService", recordPlanService,
                "deviceChannelService", deviceChannelService,
                "authorizationFacade", authorizationFacade,
                "channelAccessAuthorizer", channelAuthorizer);
        ftpSetting = new FtpSetting();
        ftpSetting.setEnable(true);
        JT1078Controller jtController = inject(
                new JT1078Controller(),
                "service", jtService,
                "jt1078PlayService", jtPlayService,
                "jtResourceAccessAuthorizer", jtAuthorizer,
                "downloadTicketService", downloadTicketService,
                "downloadResourceResolver", downloadResourceResolver,
                "userSetting", userSetting,
                "ftpSetting", ftpSetting);

        cloudMvc = mvc(cloudController);
        alarmMvc = mvc(alarmController);
        snapshotMvc = mvc(deviceQuery);
        positionMvc = mvc(positionController);
        recordPlanMvc = mvc(planController);
        jtMvc = mvc(jtController);
    }

    @Test
    void deniedCloudRecordDownloadIsHiddenBeforeTicketIssue() throws Exception {
        when(derivedAuthorizer.requireRecord(
                "video:cloud-record:download", 41))
                .thenThrow(new VideoResourceNotFoundException("hidden"));

        cloudMvc.perform(get("/api/cloud/record/play/path")
                        .param("recordId", "41"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(
                cloudRecordService, mediaServerService, downloadTicketService);
    }

    @Test
    void deniedCloudRecordLoadNeverStartsAsyncOrLoadsFile() throws Exception {
        when(derivedAuthorizer.requireRecord(
                "video:cloud-record:play", 41))
                .thenThrow(new VideoResourceNotFoundException("hidden"));

        cloudMvc.perform(get("/api/cloud/record/loadRecord")
                        .param("app", "record")
                        .param("stream", "stream-41")
                        .param("cloudRecordId", "41"))
                .andExpect(status().isNotFound())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(cloudRecordService, mediaServerService);
    }

    @Test
    void mixedCloudRecordBatchIsRejectedBeforeDeletingAnyFile()
            throws Exception {
        Set<Integer> ids = Set.of(41, 42);
        doThrow(new AccessDeniedException("denied"))
                .when(derivedAuthorizer)
                .requireAllRecords("video:cloud-record:remove", ids);

        cloudMvc.perform(delete("/api/cloud/record/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[41,42]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cloudRecordService, mediaServerService);
    }

    @Test
    void explicitZipIdsAreAuthorizedAsOneBatchBeforeLookup()
            throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(derivedAuthorizer)
                .requireAllRecords(
                        "video:cloud-record:download", List.of(41, 42));

        cloudMvc.perform(get("/api/cloud/record/download/zip")
                        .param("ids", "41", "42"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cloudRecordService, mediaServerService);
    }

    @Test
    void blankZipIdIsBadRequestBeforeAnyRecordLookup() throws Exception {
        List<Integer> idsWithBlankMember = Arrays.asList(41, null);
        doThrow(new IllegalArgumentException("录像 ID 必须大于零"))
                .when(derivedAuthorizer)
                .requireAllRecords(
                        "video:cloud-record:download", idsWithBlankMember);

        cloudMvc.perform(get("/api/cloud/record/download/zip")
                        .param("ids", "41", ""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cloudRecordService, mediaServerService);
    }

    @Test
    void filterCollectUsesPermissionCoupledDatabaseScope() throws Exception {
        VideoScopeQuery scope = scope("video:cloud-record:collect");
        when(authorizationFacade.resolveVideoScopeQuery(
                "video:cloud-record:collect")).thenReturn(scope);

        cloudMvc.perform(get("/api/cloud/record/collect/add")
                        .param("app", "record")
                        .param("stream", "stream-41"))
                .andExpect(status().isOk());

        verify(cloudRecordService).changeCollect(
                true, "record", "stream-41", null, null, null, null, scope);
    }

    @Test
    void deniedAlarmSnapshotIsHiddenBeforeReadingDatabasePath()
            throws Exception {
        when(derivedAuthorizer.requireAlarm("video:alarm:list", 51))
                .thenThrow(new VideoResourceNotFoundException("hidden"));

        alarmMvc.perform(get("/api/alarm/snap/51"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(alarmService);
    }

    @Test
    void mixedAlarmDeleteIsRejectedBeforeDeletingAnyAlarm()
            throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(derivedAuthorizer)
                .requireAllAlarms(
                        "video:alarm:remove", List.of(51L, 52L));

        alarmMvc.perform(delete("/api/alarm/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[51,52]"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(alarmService);
    }

    @Test
    void alarmClearPassesEffectiveScopeIntoDeleteQuery() throws Exception {
        VideoScopeQuery scope = scope("video:alarm:clear");
        when(authorizationFacade.resolveVideoScopeQuery(
                "video:alarm:clear")).thenReturn(scope);

        alarmMvc.perform(delete("/api/alarm/clear"))
                .andExpect(status().isOk());

        verify(alarmService).clearAlarmsByCondition(
                null, null, null, scope);
    }

    @Test
    void snapshotMarkCannotReplaceServerControlledFilename()
            throws Exception {
        snapshotMvc.perform(get("/api/device/query/snap/device-a/channel-a")
                        .param("mark", "../secret"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelAuthorizer);
    }

    @Test
    void deniedDeviceSnapshotIsHiddenBeforeFileRead() throws Exception {
        when(channelAuthorizer.requireGbChannelHidden(
                "video:device:list", "device-a", "channel-a"))
                .thenThrow(new VideoResourceNotFoundException("hidden"));

        snapshotMvc.perform(get("/api/device/query/snap/device-a/channel-a"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniedPositionHistoryNeverQueriesTrackRows() throws Exception {
        when(channelAuthorizer.requireUnifiedChannelHidden(
                "video:position:list", "channel-a", null))
                .thenThrow(new VideoResourceNotFoundException("hidden"));

        positionMvc.perform(get("/api/position/history/channel-a")
                        .param("channelId", "101"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(mobilePositionService);
    }

    @Test
    void replacedPositionChannelIdIsRejectedBeforeQuery() throws Exception {
        when(channelAuthorizer.requireUnifiedChannelHidden(
                "video:position:list", "channel-a", null))
                .thenReturn(new VideoResourceRef(VideoResourceType.CHANNEL, 101));

        positionMvc.perform(get("/api/position/history/channel-a")
                        .param("channelId", "202"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(mobilePositionService);
    }

    @Test
    void malformedPositionChannelIdIsBadRequestBeforeAuthorization()
            throws Exception {
        positionMvc.perform(get("/api/position/history/channel-a")
                        .param("channelId", "not-a-number"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelAuthorizer, mobilePositionService);
    }

    @Test
    void deniedRealtimePositionNeverLoadsDeviceOrSendsSipCommand()
            throws Exception {
        when(channelAuthorizer.requireGbDevice(
                "video:position:list", "device-a"))
                .thenThrow(new AccessDeniedException("denied"));

        positionMvc.perform(get("/api/position/realtime/device-a"))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(deviceService, sipCommander, resultHolder);
    }

    @Test
    void explicitPlanChannelBatchIsRejectedBeforeLinkingAnyChannel()
            throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(channelAuthorizer)
                .requireAllChannels(
                        "video:cloud-record:collect", List.of(101, 202));

        recordPlanMvc.perform(post("/api/record/plan/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channelIds":[101,202],"planId":7}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(recordPlanService);
    }

    @Test
    void planLinkAllOnlyDelegatesTheEffectiveVideoScope() throws Exception {
        VideoScopeQuery scope = scope("video:cloud-record:collect");
        when(authorizationFacade.resolveVideoScopeQuery(
                "video:cloud-record:collect")).thenReturn(scope);

        recordPlanMvc.perform(post("/api/record/plan/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allLink\":true,\"planId\":7}"))
                .andExpect(status().isOk());

        verify(recordPlanService).linkAll(7, scope);
    }

    @Test
    void oldJtDirectDownloadEndpointIsRemoved() throws Exception {
        jtMvc.perform(get("/api/jt1078/playback/download")
                        .param("path", "/etc/passwd"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(jtService, jtAuthorizer);
    }

    @Test
    void oldJtDownloadIdEndpointCannotBypassTicketFlow() throws Exception {
        jtMvc.perform(get("/api/jt1078/playback/download")
                        .param("downloadId", "../secret"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(jtService, jtAuthorizer);
    }

    private VideoScopeQuery scope(String permission) {
        return new VideoScopeQuery(
                false,
                Set.of(7L),
                Set.of(),
                Set.of(101L),
                permission,
                9L);
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
