package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.dao.CommonGBChannelMapper;
import com.genersoft.iot.vmp.gb28181.event.EventPublisher;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelService;
import com.genersoft.iot.vmp.gb28181.service.IPlatformChannelService;
import com.genersoft.iot.vmp.gb28181.service.impl.GbChannelServiceImpl;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.dao.JTChannelMapper;
import com.genersoft.iot.vmp.jt1078.dao.JTTerminalMapper;
import com.genersoft.iot.vmp.jt1078.service.impl.jt1078ServiceImpl;
import com.genersoft.iot.vmp.streamProxy.bean.StreamProxy;
import com.genersoft.iot.vmp.streamProxy.dao.StreamProxyMapper;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyPlayService;
import com.genersoft.iot.vmp.streamProxy.service.impl.StreamProxyServiceImpl;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.genersoft.iot.vmp.streamPush.dao.StreamPushMapper;
import com.genersoft.iot.vmp.streamPush.service.impl.StreamPushServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.CHANNEL;
import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.JT_CHANNEL;
import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.JT_TERMINAL;
import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.STREAM_PROXY;
import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.STREAM_PUSH;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoResourceDeletionCleanupTest {

    @Mock
    private VideoResourceLifecycleService lifecycleService;

    @Mock
    private JTTerminalMapper terminalMapper;

    @Mock
    private JTChannelMapper jtChannelMapper;

    @Mock
    private IGbChannelService gbChannelService;

    @Mock
    private StreamProxyMapper proxyMapper;

    @Mock
    private IStreamProxyPlayService proxyPlayService;

    @Mock
    private StreamPushMapper pushMapper;

    @Mock
    private CommonGBChannelMapper commonChannelMapper;

    @Mock
    private IPlatformChannelService platformChannelService;

    @Mock
    private EventPublisher eventPublisher;

    private jt1078ServiceImpl jtService;
    private StreamProxyServiceImpl proxyService;
    private StreamPushServiceImpl pushService;
    private GbChannelServiceImpl commonChannelService;

    @BeforeEach
    void setUp() {
        jtService = new jt1078ServiceImpl();
        ReflectionTestUtils.setField(jtService, "jtDeviceMapper", terminalMapper);
        ReflectionTestUtils.setField(jtService, "jtChannelMapper", jtChannelMapper);
        ReflectionTestUtils.setField(jtService, "channelService", gbChannelService);
        ReflectionTestUtils.setField(
                jtService, "resourceLifecycleService", lifecycleService);

        proxyService = new StreamProxyServiceImpl();
        ReflectionTestUtils.setField(proxyService, "streamProxyMapper", proxyMapper);
        ReflectionTestUtils.setField(proxyService, "playService", proxyPlayService);
        ReflectionTestUtils.setField(proxyService, "gbChannelService", gbChannelService);
        ReflectionTestUtils.setField(
                proxyService, "resourceLifecycleService", lifecycleService);

        pushService = new StreamPushServiceImpl();
        ReflectionTestUtils.setField(pushService, "streamPushMapper", pushMapper);
        ReflectionTestUtils.setField(pushService, "gbChannelService", gbChannelService);
        ReflectionTestUtils.setField(
                pushService, "resourceLifecycleService", lifecycleService);

        commonChannelService = new GbChannelServiceImpl();
        ReflectionTestUtils.setField(
                commonChannelService, "commonGBChannelMapper", commonChannelMapper);
        ReflectionTestUtils.setField(
                commonChannelService, "platformChannelService", platformChannelService);
        ReflectionTestUtils.setField(
                commonChannelService, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(
                commonChannelService, "resourceLifecycleService", lifecycleService);
    }

    @Test
    void deletingJtTerminalCleansTerminalChannelsAndUnifiedGrantsBeforeRows() {
        JTDevice terminal = new JTDevice();
        terminal.setId(31);
        terminal.setPhoneNumber("13800138000");
        JTChannel first = jtChannel(32, 303);
        JTChannel second = jtChannel(33, 0);
        when(terminalMapper.getDevice("13800138000")).thenReturn(terminal);
        when(jtChannelMapper.selectAllForInternalMaintenance(31, null))
                .thenReturn(List.of(first, second));

        jtService.deleteDeviceByPhoneNumber("13800138000");

        Set<com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef> refs =
                Set.of(
                        ref(JT_TERMINAL, 31),
                        ref(JT_CHANNEL, 32),
                        ref(JT_CHANNEL, 33),
                        ref(CHANNEL, 303));
        InOrder order = inOrder(
                lifecycleService,
                gbChannelService,
                jtChannelMapper,
                terminalMapper);
        order.verify(lifecycleService).removeAll(refs);
        order.verify(gbChannelService).delete(Set.of(303));
        order.verify(jtChannelMapper).deleteByTerminalDbId(31);
        order.verify(terminalMapper).deleteDeviceById(31);
    }

    @Test
    void deletingOneJtChannelCleansSourceAndUnifiedGrants() {
        JTChannel channel = jtChannel(32, 303);
        when(jtChannelMapper.selectChannelById(32)).thenReturn(channel);

        jtService.deleteChannelById(32);

        InOrder order = inOrder(
                lifecycleService, gbChannelService, jtChannelMapper);
        order.verify(lifecycleService).removeAll(Set.of(
                ref(JT_CHANNEL, 32), ref(CHANNEL, 303)));
        order.verify(gbChannelService).delete(303);
        order.verify(jtChannelMapper).delete(32);
    }

    @Test
    void deletingProxyCleansProxyAndUnifiedGrantsBeforeMediaOrRows() {
        StreamProxy proxy = new StreamProxy();
        proxy.setId(41);
        proxy.setPulling(false);
        proxy.setGbId(303);
        when(proxyMapper.select(41)).thenReturn(proxy);

        proxyService.delete(41);

        InOrder order = inOrder(
                lifecycleService, gbChannelService, proxyMapper);
        order.verify(lifecycleService).removeAll(Set.of(
                ref(STREAM_PROXY, 41), ref(CHANNEL, 303)));
        order.verify(gbChannelService).delete(303);
        order.verify(proxyMapper).delete(41);
        verify(proxyPlayService, never()).stopProxy(proxy);
    }

    @Test
    void batchDeletingPushesCleansAllRelationshipsBeforeAnyRows() {
        StreamPush first = streamPush(42, 303);
        StreamPush second = streamPush(43, 0);
        when(pushMapper.selectInSet(Set.of(42, 43)))
                .thenReturn(List.of(first, second));

        pushService.batchRemove(Set.of(42, 43));

        InOrder order = inOrder(
                lifecycleService, pushMapper, gbChannelService);
        order.verify(lifecycleService).removeAll(Set.of(
                ref(STREAM_PUSH, 42),
                ref(STREAM_PUSH, 43),
                ref(CHANNEL, 303)));
        order.verify(pushMapper).batchDel(List.of(first, second));
        order.verify(gbChannelService).delete(Set.of(303));
    }

    @Test
    void deletingUnifiedChannelAlwaysCleansItsExplicitGrantFirst() {
        CommonGBChannel channel = new CommonGBChannel();
        channel.setGbId(303);
        when(commonChannelMapper.queryById(303)).thenReturn(channel);

        commonChannelService.delete(303);

        InOrder order = inOrder(
                lifecycleService, platformChannelService, commonChannelMapper);
        order.verify(lifecycleService).remove(ref(CHANNEL, 303));
        order.verify(platformChannelService).removeChannel(303);
        order.verify(commonChannelMapper).delete(303);
    }

    private JTChannel jtChannel(int id, int gbId) {
        JTChannel channel = new JTChannel();
        channel.setId(id);
        channel.setGbId(gbId);
        return channel;
    }

    private StreamPush streamPush(int id, int gbId) {
        StreamPush streamPush = new StreamPush();
        streamPush.setId(id);
        streamPush.setGbId(gbId);
        streamPush.setPushing(false);
        return streamPush;
    }

    private com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef ref(
            com.genersoft.iot.vmp.authorization.video.model.VideoResourceType type,
            long id) {
        return new com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef(
                type, id);
    }
}
