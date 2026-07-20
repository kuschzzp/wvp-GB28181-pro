package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.dao.DeviceChannelMapper;
import com.genersoft.iot.vmp.gb28181.service.IPlatformChannelService;
import com.genersoft.iot.vmp.gb28181.service.impl.DeviceChannelServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceChannelGrantLifecycleTest {

    private static final String CHANNEL_CODE = "34020000001320000001";

    @Mock
    private DeviceChannelMapper channelMapper;

    @Mock
    private IPlatformChannelService platformChannelService;

    @Mock
    private VideoResourceLifecycleService lifecycleService;

    private DeviceChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceChannelServiceImpl();
        ReflectionTestUtils.setField(service, "channelMapper", channelMapper);
        ReflectionTestUtils.setField(
                service, "platformChannelService", platformChannelService);
        ReflectionTestUtils.setField(
                service, "resourceLifecycleService", lifecycleService);
    }

    @Test
    void resetMigratesExplicitGrantWhenStableSourceReceivesANewInternalId() {
        DeviceChannel existing = channel(101, 11, CHANNEL_CODE);
        DeviceChannel replacement = channel(202, 11, CHANNEL_CODE);
        DeviceChannel incoming = channel(0, 11, CHANNEL_CODE);
        when(channelMapper.queryAllChannelsForRefresh(11))
                .thenReturn(List.of(existing));
        when(channelMapper.queryChannelsByDeviceDbId(11))
                .thenReturn(List.of(replacement));
        when(channelMapper.batchUpsert(anyList())).thenReturn(1);

        service.resetChannels(11, List.of(incoming));

        verify(lifecycleService).migrateUnique(
                ref(101),
                Set.of(ref(202)),
                "gb:11:" + CHANNEL_CODE,
                null);
    }

    @Test
    void resetCleansGrantBeforeDeletingChannelsMissingFromTheNewCatalog() {
        DeviceChannel removed = channel(101, 11, CHANNEL_CODE);
        DeviceChannel incoming = channel(0, 11, "34020000001320000002");
        when(channelMapper.queryAllChannelsForRefresh(11))
                .thenReturn(List.of(removed));
        when(channelMapper.queryChannelsByDeviceDbId(11))
                .thenReturn(List.of(removed, incoming));
        when(channelMapper.batchUpsert(anyList())).thenReturn(1);

        service.resetChannels(11, List.of(incoming));

        InOrder order = inOrder(
                lifecycleService, platformChannelService, channelMapper);
        order.verify(lifecycleService).removeAll(Set.of(ref(101)));
        order.verify(platformChannelService).removeChannels(List.of(101));
        order.verify(channelMapper).batchDel(List.of(removed));
    }

    private DeviceChannel channel(int id, int sourceId, String deviceId) {
        DeviceChannel channel = new DeviceChannel();
        channel.setId(id);
        channel.setDataDeviceId(sourceId);
        channel.setDeviceId(deviceId);
        channel.setStatus("ON");
        channel.setCreateTime("old");
        return channel;
    }

    private com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef ref(
            long id) {
        return new com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef(
                CHANNEL, id);
    }
}
