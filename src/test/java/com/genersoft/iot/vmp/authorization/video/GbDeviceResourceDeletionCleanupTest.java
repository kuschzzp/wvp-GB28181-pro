package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.dao.CommonGBChannelMapper;
import com.genersoft.iot.vmp.gb28181.dao.DeviceChannelMapper;
import com.genersoft.iot.vmp.gb28181.dao.DeviceMapper;
import com.genersoft.iot.vmp.gb28181.dao.PlatformChannelMapper;
import com.genersoft.iot.vmp.gb28181.event.EventPublisher;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.gb28181.service.impl.DeviceServiceImpl;
import com.genersoft.iot.vmp.gb28181.task.deviceStatus.DeviceStatusManager;
import com.genersoft.iot.vmp.gb28181.task.deviceSubscribe.SubscribeTaskRunner;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
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
import static com.genersoft.iot.vmp.authorization.video.model.VideoResourceType.GB_DEVICE;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbDeviceResourceDeletionCleanupTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceChannelMapper deviceChannelMapper;

    @Mock
    private CommonGBChannelMapper commonChannelMapper;

    @Mock
    private PlatformChannelMapper platformChannelMapper;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private IRedisCatchStorage redisCatchStorage;

    @Mock
    private IInviteStreamService inviteStreamService;

    @Mock
    private SubscribeTaskRunner subscribeTaskRunner;

    @Mock
    private DeviceStatusManager deviceStatusManager;

    @Mock
    private VideoResourceLifecycleService lifecycleService;

    private DeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceServiceImpl();
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(
                service, "deviceChannelMapper", deviceChannelMapper);
        ReflectionTestUtils.setField(
                service, "commonGBChannelMapper", commonChannelMapper);
        ReflectionTestUtils.setField(
                service, "platformChannelMapper", platformChannelMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(
                service, "redisCatchStorage", redisCatchStorage);
        ReflectionTestUtils.setField(
                service, "inviteStreamService", inviteStreamService);
        ReflectionTestUtils.setField(
                service, "subscribeTaskRunner", subscribeTaskRunner);
        ReflectionTestUtils.setField(
                service, "deviceStatusManager", deviceStatusManager);
        ReflectionTestUtils.setField(
                service, "resourceLifecycleService", lifecycleService);
    }

    @Test
    void deletingGbDeviceCleansDeviceAndEveryChannelGrantBeforeRows() {
        Device device = new Device();
        device.setId(11);
        device.setDeviceId("34020000002000000001");
        CommonGBChannel first = new CommonGBChannel();
        first.setGbId(101);
        CommonGBChannel second = new CommonGBChannel();
        second.setGbId(102);
        when(deviceMapper.getDeviceByDeviceId(device.getDeviceId()))
                .thenReturn(device);
        when(commonChannelMapper.queryByDataTypeAndDeviceIds(1, List.of(11)))
                .thenReturn(List.of(first, second));

        service.delete(device.getDeviceId());

        InOrder order = inOrder(
                lifecycleService,
                platformChannelMapper,
                deviceChannelMapper,
                deviceMapper);
        order.verify(lifecycleService).removeAll(Set.of(
                ref(GB_DEVICE, 11),
                ref(CHANNEL, 101),
                ref(CHANNEL, 102)));
        order.verify(platformChannelMapper)
                .delChannelForDeviceId(device.getDeviceId());
        order.verify(deviceChannelMapper).cleanChannelsByDeviceId(11);
        order.verify(deviceMapper).del(device.getDeviceId());
    }

    private com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef ref(
            com.genersoft.iot.vmp.authorization.video.model.VideoResourceType type,
            long id) {
        return new com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef(
                type, id);
    }
}
