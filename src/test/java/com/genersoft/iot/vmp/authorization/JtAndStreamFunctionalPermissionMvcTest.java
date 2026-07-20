package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.video.JtResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.StreamResourceAccessAuthorizer;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.ftpServer.FtpSetting;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.jt1078.controller.JT1078Controller;
import com.genersoft.iot.vmp.jt1078.controller.JT1078TerminalController;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078PlayService;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.PlayableStreamService;
import com.genersoft.iot.vmp.service.IMediaService;
import com.genersoft.iot.vmp.streamProxy.controller.StreamProxyController;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyPlayService;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyService;
import com.genersoft.iot.vmp.streamPush.controller.StreamPushController;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushService;
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
                JT1078Controller.class,
                JT1078TerminalController.class,
                StreamProxyController.class,
                StreamPushController.class
        },
        properties = "jt1078.enable=true",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        ApiKeyAuthenticationFilter.class
                }))
@AutoConfigureMockMvc(addFilters = false)
@Import(JtAndStreamFunctionalPermissionMvcTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class JtAndStreamFunctionalPermissionMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean(name = "authz") private PermissionEnforcer authz;
    @MockitoBean private JtResourceAccessAuthorizer jtAuthorizer;
    @MockitoBean private StreamResourceAccessAuthorizer streamAuthorizer;
    @MockitoBean private Ijt1078Service jtService;
    @MockitoBean private Ijt1078PlayService jtPlayService;
    @MockitoBean private IStreamProxyService proxyService;
    @MockitoBean private IStreamProxyPlayService proxyPlayService;
    @MockitoBean private IStreamPushService pushService;
    @MockitoBean private IStreamPushPlayService pushPlayService;
    @MockitoBean private IMediaServerService mediaServerService;
    @MockitoBean private IMediaService mediaService;
    @MockitoBean private DeferredResultHolder resultHolder;
    @MockitoBean private AuthorizationFacade authorizationFacade;
    @MockitoBean private UserSetting userSetting;
    @MockitoBean private FtpSetting ftpSetting;
    @MockitoBean private DownloadResourceResolver downloadResourceResolver;
    @MockitoBean private DownloadTicketService downloadTicketService;
    @MockitoBean private PlayableStreamService playableStreamService;

    @Test
    void missingJtPlayPermissionStopsBeforeObjectResolution()
            throws Exception {
        when(authz.check("video:jt:play")).thenReturn(false);

        mockMvc.perform(get("/api/jt1078/live/start")
                        .param("phoneNumber", "13800138000")
                        .param("channelId", "2")
                        .param("type", "0"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:jt:play");
        verifyNoInteractions(jtAuthorizer, jtService, jtPlayService);
    }

    @Test
    void missingJtEditPermissionStopsBeforeTerminalResolution()
            throws Exception {
        when(authz.check("video:jt:edit")).thenReturn(false);

        mockMvc.perform(delete("/api/jt1078/terminal/delete")
                        .param("phoneNumber", "13800138000"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:jt:edit");
        verifyNoInteractions(jtAuthorizer, jtService);
    }

    @Test
    void missingJtRecordPermissionStopsDownloadBeforeTaskResolution()
            throws Exception {
        when(authz.check("video:jt:record")).thenReturn(false);

        mockMvc.perform(get("/api/jt1078/playback/downloadUrl")
                        .param("phoneNumber", "13800138000")
                        .param("channelId", "2")
                        .param("startTime", "2026-07-16 08:00:00")
                        .param("endTime", "2026-07-16 09:00:00"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:jt:record");
        verifyNoInteractions(jtAuthorizer, jtService);
    }

    @Test
    void missingProxyEditPermissionStopsBeforeObjectResolution()
            throws Exception {
        when(authz.check("video:proxy:edit")).thenReturn(false);

        mockMvc.perform(post("/api/proxy/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 41, "app": "live", "stream": "proxy-1"}
                                """))
                .andExpect(status().isForbidden());

        verify(authz).check("video:proxy:edit");
        verifyNoInteractions(streamAuthorizer, proxyService, proxyPlayService);
    }

    @Test
    void missingPushRemovePermissionStopsBeforeBatchResolution()
            throws Exception {
        when(authz.check("video:push:remove")).thenReturn(false);

        mockMvc.perform(delete("/api/push/batchRemove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42,43]}"))
                .andExpect(status().isForbidden());

        verify(authz).check("video:push:remove");
        verifyNoInteractions(streamAuthorizer, pushService, pushPlayService);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
