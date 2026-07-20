package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.video.DerivedResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.gb28181.controller.DeviceQuery;
import com.genersoft.iot.vmp.gb28181.controller.MobilePositionController;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.PlayableStreamService;
import com.genersoft.iot.vmp.service.IAlarmService;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.IMobilePositionService;
import com.genersoft.iot.vmp.service.IRecordPlanService;
import com.genersoft.iot.vmp.service.redisMsg.IRedisRpcService;
import com.genersoft.iot.vmp.vmanager.alarm.AlarmController;
import com.genersoft.iot.vmp.vmanager.cloudRecord.CloudRecordController;
import com.genersoft.iot.vmp.vmanager.recordPlan.RecordPlanController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                CloudRecordController.class,
                AlarmController.class,
                DeviceQuery.class,
                MobilePositionController.class,
                RecordPlanController.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        ApiKeyAuthenticationFilter.class
                }))
@AutoConfigureMockMvc(addFilters = false)
@Import(DerivedObjectFunctionalPermissionMvcTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class DerivedObjectFunctionalPermissionMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean(name = "authz") private PermissionEnforcer authz;
    @MockitoBean private AuthorizationFacade authorizationFacade;
    @MockitoBean private DerivedResourceAccessAuthorizer derivedAuthorizer;
    @MockitoBean private GbChannelAccessAuthorizer channelAuthorizer;
    @MockitoBean private ICloudRecordService cloudRecordService;
    @MockitoBean private IMediaServerService mediaServerService;
    @MockitoBean private IAlarmService alarmService;
    @MockitoBean private IMobilePositionService mobilePositionService;
    @MockitoBean private IRecordPlanService recordPlanService;
    @MockitoBean private IDeviceChannelService deviceChannelService;
    @MockitoBean private IDeviceService deviceService;
    @MockitoBean private IInviteStreamService inviteStreamService;
    @MockitoBean private ISIPCommander sipCommander;
    @MockitoBean private DeferredResultHolder resultHolder;
    @MockitoBean private DynamicTask dynamicTask;
    @MockitoBean private IRedisRpcService redisRpcService;
    @MockitoBean private UserSetting userSetting;
    @MockitoBean private DownloadTicketService downloadTicketService;
    @MockitoBean private PlayableStreamService playableStreamService;

    @Test
    void missingCloudRecordRemovePermissionStopsBeforeBatchResolution()
            throws Exception {
        when(authz.check("video:cloud-record:remove")).thenReturn(false);

        mockMvc.perform(delete("/api/cloud/record/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[41,42]}"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:cloud-record:remove");
        verifyNoInteractions(derivedAuthorizer, cloudRecordService);
    }

    @Test
    void missingAlarmRemovePermissionStopsBeforeBatchResolution()
            throws Exception {
        when(authz.check("video:alarm:remove")).thenReturn(false);

        mockMvc.perform(delete("/api/alarm/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[51,52]"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:alarm:remove");
        verifyNoInteractions(derivedAuthorizer, alarmService);
    }

    @Test
    void missingDeviceListPermissionStopsSnapshotBeforeObjectResolution()
            throws Exception {
        when(authz.check("video:device:list")).thenReturn(false);

        mockMvc.perform(get("/api/device/query/snap/device-a/channel-a")
                        .param("mark", "latest"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:device:list");
        verifyNoInteractions(channelAuthorizer);
    }

    @Test
    void missingPositionListPermissionStopsBeforeDeviceResolution()
            throws Exception {
        when(authz.check("video:position:list")).thenReturn(false);

        mockMvc.perform(get("/api/position/realtime/device-a"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:position:list");
        verifyNoInteractions(
                channelAuthorizer,
                deviceService,
                sipCommander,
                resultHolder);
    }

    @Test
    void missingPositionSubscribePermissionStopsBeforeDeviceResolution()
            throws Exception {
        when(authz.check("video:position:subscribe")).thenReturn(false);

        mockMvc.perform(get("/api/position/subscribe/device-a")
                        .param("expires", "60")
                        .param("interval", "5"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:position:subscribe");
        verifyNoInteractions(channelAuthorizer, deviceService);
    }

    @Test
    void missingRecordPlanCollectPermissionStopsBeforeBatchResolution()
            throws Exception {
        when(authz.check("video:cloud-record:collect")).thenReturn(false);

        mockMvc.perform(post("/api/record/plan/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelIds\":[101,202],\"planId\":7}"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:cloud-record:collect");
        verifyNoInteractions(
                channelAuthorizer,
                deviceChannelService,
                recordPlanService);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
