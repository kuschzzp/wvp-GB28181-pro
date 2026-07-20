package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.gb28181.service.IPlayService;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;
import com.genersoft.iot.vmp.web.gb28181.ApiControlController;
import com.genersoft.iot.vmp.web.gb28181.ApiDeviceController;
import com.genersoft.iot.vmp.web.gb28181.ApiStreamController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                ApiControlController.class,
                ApiDeviceController.class,
                ApiStreamController.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        ApiKeyAuthenticationFilter.class
                }))
@AutoConfigureMockMvc(addFilters = false)
@Import(MediaObjectFunctionalPermissionMvcTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class MediaObjectFunctionalPermissionMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean(name = "authz") private PermissionEnforcer authz;
    @MockitoBean private GbChannelAccessAuthorizer authorizer;
    @MockitoBean private SIPCommander sipCommander;
    @MockitoBean private IDeviceService deviceService;
    @MockitoBean private IDeviceChannelService deviceChannelService;
    @MockitoBean private DeferredResultHolder resultHolder;
    @MockitoBean private AuthorizationFacade authorizationFacade;
    @MockitoBean private UserSetting userSetting;
    @MockitoBean private IPlayService playService;
    @MockitoBean private IInviteStreamService inviteStreamService;

    @Test
    void missingLegacyPtzPermissionStopsBeforeObjectResolution() throws Exception {
        when(authz.check("video:ptz:control")).thenReturn(false);

        mockMvc.perform(get("/api/v1/control/ptz")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("command", "left")
                        .param("speed", "10"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:ptz:control");
        verifyNoInteractions(authorizer, sipCommander, deviceService);
    }

    @Test
    void missingLegacyPresetPermissionStopsBeforeObjectResolution() throws Exception {
        when(authz.check("video:ptz:preset")).thenReturn(false);

        mockMvc.perform(get("/api/v1/control/preset")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("command", "goto")
                        .param("preset", "1"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:ptz:preset");
        verifyNoInteractions(authorizer, sipCommander, deviceService);
    }

    @Test
    void missingLegacyPresetQueryPermissionStopsBeforeObjectResolution() throws Exception {
        when(authz.check("video:ptz:preset")).thenReturn(false);

        mockMvc.perform(get("/api/v1/device/fetchpreset")
                        .param("serial", "device-a")
                        .param("code", "channel-a")
                        .param("timeout", "1"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:ptz:preset");
        verifyNoInteractions(authorizer, deviceService, resultHolder);
    }

    @Test
    void missingLegacyPlayPermissionStopsBeforeObjectResolution() throws Exception {
        when(authz.check("video:channel:play")).thenReturn(false);

        mockMvc.perform(get("/api/v1/stream/start")
                        .param("serial", "device-a")
                        .param("code", "channel-a"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:channel:play");
        verifyNoInteractions(
                authorizer,
                deviceService,
                deviceChannelService,
                playService,
                inviteStreamService);
    }

    @Test
    void missingLegacyStopPermissionStopsBeforeObjectResolution() throws Exception {
        when(authz.check("video:channel:stop")).thenReturn(false);

        mockMvc.perform(get("/api/v1/stream/stop")
                        .param("serial", "device-a")
                        .param("code", "channel-a"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:channel:stop");
        verifyNoInteractions(
                authorizer,
                sipCommander,
                deviceService,
                deviceChannelService,
                inviteStreamService);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
