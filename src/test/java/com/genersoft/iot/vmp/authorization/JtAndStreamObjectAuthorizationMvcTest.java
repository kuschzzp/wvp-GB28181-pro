package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.authorization.video.JtResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.StreamResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.resolver.AmbiguousVideoResourceException;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.conf.GlobalExceptionHandler;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.jt1078.controller.JT1078Controller;
import com.genersoft.iot.vmp.jt1078.controller.JT1078TerminalController;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078PlayService;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.streamProxy.controller.StreamProxyController;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyPlayService;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyService;
import com.genersoft.iot.vmp.streamPush.controller.StreamPushController;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushService;
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

import java.util.Set;

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
class JtAndStreamObjectAuthorizationMvcTest {

    @Mock private Ijt1078Service jtService;
    @Mock private Ijt1078PlayService jtPlayService;
    @Mock private JtResourceAccessAuthorizer jtAuthorizer;
    @Mock private IStreamProxyService proxyService;
    @Mock private IStreamProxyPlayService proxyPlayService;
    @Mock private IStreamPushService pushService;
    @Mock private IStreamPushPlayService pushPlayService;
    @Mock private StreamResourceAccessAuthorizer streamAuthorizer;

    private MockMvc jtMvc;
    private MockMvc terminalMvc;
    private MockMvc proxyMvc;
    private MockMvc pushMvc;

    @BeforeEach
    void setUp() {
        UserSetting userSetting = new UserSetting();
        userSetting.setPlayTimeout(1000);

        JT1078Controller jtController = inject(
                new JT1078Controller(),
                "service", jtService,
                "jt1078PlayService", jtPlayService,
                "jtResourceAccessAuthorizer", jtAuthorizer,
                "userSetting", userSetting);
        JT1078TerminalController terminalController = inject(
                new JT1078TerminalController(),
                "service", jtService,
                "jtResourceAccessAuthorizer", jtAuthorizer);
        StreamProxyController proxyController = inject(
                new StreamProxyController(),
                "streamProxyService", proxyService,
                "streamProxyPlayService", proxyPlayService,
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "userSetting", userSetting);
        StreamPushController pushController = inject(
                new StreamPushController(),
                "streamPushService", pushService,
                "streamPushPlayService", pushPlayService,
                "streamResourceAccessAuthorizer", streamAuthorizer,
                "userSetting", userSetting);

        jtMvc = mvc(jtController);
        terminalMvc = mvc(terminalController);
        proxyMvc = mvc(proxyController);
        pushMvc = mvc(pushController);
    }

    @Test
    void deniedJtLiveStartReturnsForbiddenBeforeAsyncOrMediaExecution()
            throws Exception {
        when(jtAuthorizer.requireChannel(
                "video:jt:play", "13800138000", 2))
                .thenThrow(new AccessDeniedException("denied"));

        jtMvc.perform(get("/api/jt1078/live/start")
                        .param("phoneNumber", "13800138000")
                        .param("channelId", "2")
                        .param("type", "0"))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(jtPlayService, jtService);
    }

    @Test
    void deniedNestedJtShootingReturnsForbiddenBeforeCommandExecution()
            throws Exception {
        when(jtAuthorizer.requireChannel(
                "video:jt:record", "13800138000", 3))
                .thenThrow(new AccessDeniedException("denied"));

        jtMvc.perform(post("/api/jt1078/shooting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "13800138000",
                                  "shootingCommand": {"chanelId": 3}
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jtService, jtPlayService);
    }

    @Test
    void missingJtTerminalStableIdReturnsNotFoundBeforeUpdate()
            throws Exception {
        when(jtAuthorizer.requireTerminalId("video:jt:edit", 31))
                .thenThrow(new VideoResourceNotFoundException(
                        "JT_TERMINAL:31 missing"));

        terminalMvc.perform(post("/api/jt1078/terminal/update")
                        .param("id", "31")
                        .param("phoneNumber", "13800138000"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(jtService);
    }

    @Test
    void deniedProxyStableIdUpdateReturnsForbiddenBeforeWrite()
            throws Exception {
        when(streamAuthorizer.requireProxyId("video:proxy:edit", 41))
                .thenThrow(new AccessDeniedException("denied"));

        proxyMvc.perform(post("/api/proxy/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 41, "app": "live", "stream": "proxy-1"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(proxyService, proxyPlayService);
    }

    @Test
    void ambiguousLegacyProxyDeleteReturnsNotFoundBeforeWrite()
            throws Exception {
        when(streamAuthorizer.requireProxy(
                "video:proxy:remove", "live", "proxy-1"))
                .thenThrow(new AmbiguousVideoResourceException(
                        "multiple proxies"));

        proxyMvc.perform(delete("/api/proxy/del")
                        .param("app", "live")
                        .param("stream", "proxy-1"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(proxyService, proxyPlayService);
    }

    @Test
    void missingProxyStartReturnsNotFoundBeforeAsyncOrMediaExecution()
            throws Exception {
        when(streamAuthorizer.requireProxyId("video:proxy:play", 41))
                .thenThrow(new VideoResourceNotFoundException(
                        "STREAM_PROXY:41 missing"));

        proxyMvc.perform(get("/api/proxy/start").param("id", "41"))
                .andExpect(status().isNotFound())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(proxyService, proxyPlayService);
    }

    @Test
    void allowedProxyStopUsesStableIdBeforeMediaExecution()
            throws Exception {
        proxyMvc.perform(get("/api/proxy/stop").param("id", "41"))
                .andExpect(status().isOk());

        verify(streamAuthorizer).requireProxyId("video:proxy:stop", 41);
        verify(proxyPlayService).stop(41);
        verifyNoInteractions(proxyService);
    }

    @Test
    void deniedPushStableIdUpdateReturnsForbiddenBeforeWrite()
            throws Exception {
        when(streamAuthorizer.requirePushId("video:push:edit", 42))
                .thenThrow(new AccessDeniedException("denied"));

        pushMvc.perform(post("/api/push/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 42, "app": "live", "stream": "push-1"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pushService, pushPlayService);
    }

    @Test
    void deniedPushBatchReturnsForbiddenBeforeDeletingAnyItem()
            throws Exception {
        Set<Integer> ids = Set.of(42, 43);
        doThrow(new AccessDeniedException("denied"))
                .when(streamAuthorizer)
                .requireAllPushIds("video:push:remove", ids);

        pushMvc.perform(delete("/api/push/batchRemove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42,43]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pushService, pushPlayService);
    }

    @Test
    void ambiguousLegacyPushStopReturnsNotFoundBeforeMediaExecution()
            throws Exception {
        when(streamAuthorizer.requirePush(
                "video:push:stop", "live", "push-1"))
                .thenThrow(new AmbiguousVideoResourceException(
                        "multiple pushes"));

        pushMvc.perform(get("/api/push/forceClose")
                        .param("app", "live")
                        .param("stream", "push-1"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(pushService, pushPlayService);
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
