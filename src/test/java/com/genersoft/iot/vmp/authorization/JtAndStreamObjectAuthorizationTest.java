package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.authorization.video.JtResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.StreamResourceAccessAuthorizer;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.bean.JTQueryMediaDataCommand;
import com.genersoft.iot.vmp.jt1078.bean.JTShootingCommand;
import com.genersoft.iot.vmp.jt1078.controller.JT1078Controller;
import com.genersoft.iot.vmp.jt1078.controller.JT1078TerminalController;
import com.genersoft.iot.vmp.jt1078.controller.bean.QueryMediaDataParam;
import com.genersoft.iot.vmp.jt1078.controller.bean.ShootingParam;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078PlayService;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.streamProxy.bean.StreamProxy;
import com.genersoft.iot.vmp.streamProxy.controller.StreamProxyController;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyPlayService;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyService;
import com.genersoft.iot.vmp.streamPush.bean.BatchRemoveParam;
import com.genersoft.iot.vmp.streamPush.controller.StreamPushController;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JtAndStreamObjectAuthorizationTest {

    @Mock
    private Ijt1078Service jtService;

    @Mock
    private Ijt1078PlayService jtPlayService;

    @Mock
    private JtResourceAccessAuthorizer jtAuthorizer;

    @Mock
    private IStreamProxyService proxyService;

    @Mock
    private IStreamProxyPlayService proxyPlayService;

    @Mock
    private IStreamPushService pushService;

    @Mock
    private IStreamPushPlayService pushPlayService;

    @Mock
    private StreamResourceAccessAuthorizer streamAuthorizer;

    private JT1078Controller jtController;
    private JT1078TerminalController terminalController;
    private StreamProxyController proxyController;
    private StreamPushController pushController;

    @BeforeEach
    void setUp() {
        UserSetting userSetting = new UserSetting();
        userSetting.setPlayTimeout(1000);

        jtController = new JT1078Controller();
        ReflectionTestUtils.setField(jtController, "service", jtService);
        ReflectionTestUtils.setField(
                jtController, "jt1078PlayService", jtPlayService);
        ReflectionTestUtils.setField(jtController, "userSetting", userSetting);
        ReflectionTestUtils.setField(
                jtController, "jtResourceAccessAuthorizer", jtAuthorizer);

        terminalController = new JT1078TerminalController();
        ReflectionTestUtils.setField(terminalController, "service", jtService);
        ReflectionTestUtils.setField(
                terminalController, "jtResourceAccessAuthorizer", jtAuthorizer);

        proxyController = new StreamProxyController();
        ReflectionTestUtils.setField(
                proxyController, "streamProxyService", proxyService);
        ReflectionTestUtils.setField(
                proxyController, "streamProxyPlayService", proxyPlayService);
        ReflectionTestUtils.setField(proxyController, "userSetting", userSetting);
        ReflectionTestUtils.setField(
                proxyController,
                "streamResourceAccessAuthorizer",
                streamAuthorizer);

        pushController = new StreamPushController();
        ReflectionTestUtils.setField(
                pushController, "streamPushService", pushService);
        ReflectionTestUtils.setField(
                pushController, "streamPushPlayService", pushPlayService);
        ReflectionTestUtils.setField(pushController, "userSetting", userSetting);
        ReflectionTestUtils.setField(
                pushController,
                "streamResourceAccessAuthorizer",
                streamAuthorizer);
    }

    @Test
    void deniedJtChannelPlayHasNoMediaSideEffect() {
        when(jtAuthorizer.requireChannel(
                "video:jt:play", "13800138000", 2))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> jtController.startLive(
                        new MockHttpServletRequest(),
                        "13800138000",
                        2,
                        0));

        verifyNoInteractions(jtPlayService);
    }

    @Test
    void deniedJtTerminalControlHasNoCommandSideEffect() {
        when(jtAuthorizer.requireTerminal(
                "video:jt:control", "13800138000"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> jtController.resetControl("13800138000"));

        verifyNoInteractions(jtService);
    }

    @Test
    void shootingUsesItsNestedJtChannelBeforeSendingCommand() {
        JTShootingCommand command = new JTShootingCommand();
        command.setChanelId(3);
        ShootingParam param = new ShootingParam();
        param.setPhoneNumber("13800138000");
        param.setShootingCommand(command);
        when(jtService.shooting("13800138000", command))
                .thenReturn(List.of(1L));

        jtController.shooting(param);

        verify(jtAuthorizer).requireChannel(
                "video:jt:record", "13800138000", 3);
        verify(jtService).shooting("13800138000", command);
    }

    @Test
    void wildcardMediaQueryRequiresTheWholeTerminal() {
        JTQueryMediaDataCommand command = new JTQueryMediaDataCommand();
        command.setChanelId(0);
        QueryMediaDataParam param = new QueryMediaDataParam();
        param.setPhoneNumber("13800138000");
        param.setQueryMediaDataCommand(command);
        when(jtService.queryMediaData("13800138000", command))
                .thenReturn(List.of());

        jtController.queryMediaData(param);

        verify(jtAuthorizer).requireChannelOrTerminal(
                "video:jt:list", "13800138000", 0);
        verify(jtService).queryMediaData("13800138000", command);
    }

    @Test
    void terminalUpdateUsesStableIdAndDenialStopsWrite() {
        JTDevice device = new JTDevice();
        device.setId(31);
        device.setPhoneNumber("victim-phone");
        when(jtAuthorizer.requireTerminalId("video:jt:edit", 31))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> terminalController.updateDevice(device));

        verify(jtService, never()).updateDevice(device);
    }

    @Test
    void addingJtChannelRequiresItsParentTerminal() {
        JTChannel channel = new JTChannel();
        channel.setTerminalDbId(31);
        channel.setChannelId(2);
        when(jtAuthorizer.requireTerminalId("video:jt:edit", 31))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> terminalController.addChannel(channel));

        verify(jtService, never()).addChannel(channel);
    }

    @Test
    void readingJtChannelRequiresItsStableId() {
        terminalController.getChannel(32);

        verify(jtAuthorizer).requireChannelId("video:jt:list", 32);
        verify(jtService).getChannelByDbId(32);
    }

    @Test
    void proxyIdReplacementIsRejectedBeforeUpdate() {
        StreamProxy proxy = new StreamProxy();
        proxy.setId(41);
        when(streamAuthorizer.requireProxyId("video:proxy:edit", 41))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> proxyController.update(proxy));

        verify(proxyService, never()).update(proxy);
    }

    @Test
    void legacyProxyAppStreamIsResolvedBeforeDelete() {
        when(streamAuthorizer.requireProxy(
                "video:proxy:remove", "live", "proxy-1"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> proxyController.del("live", "proxy-1"));

        verify(proxyService, never())
                .delteByAppAndStream("live", "proxy-1");
    }

    @Test
    void pushBatchIsAllOrNothingBeforeDelete() {
        BatchRemoveParam param = new BatchRemoveParam();
        param.setIds(Set.of(42, 43));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(streamAuthorizer)
                .requireAllPushIds(
                        "video:push:remove", Set.of(42, 43));

        assertThrows(
                AccessDeniedException.class,
                () -> pushController.batchStop(param));

        verify(pushService, never()).batchRemove(Set.of(42, 43));
    }

    @Test
    void legacyPushAppStreamIsResolvedBeforeForceClose() {
        when(streamAuthorizer.requirePush(
                "video:push:stop", "live", "push-1"))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(
                AccessDeniedException.class,
                () -> pushController.stop("live", "push-1"));

        verify(pushPlayService, never()).stop("live", "push-1");
    }
}
