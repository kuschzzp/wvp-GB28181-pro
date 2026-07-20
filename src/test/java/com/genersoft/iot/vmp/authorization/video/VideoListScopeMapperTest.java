package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.dao.CommonGBChannelMapper;
import com.genersoft.iot.vmp.gb28181.dao.DeviceChannelMapper;
import com.genersoft.iot.vmp.gb28181.dao.DeviceMapper;
import com.genersoft.iot.vmp.streamProxy.bean.StreamProxy;
import com.genersoft.iot.vmp.streamProxy.dao.StreamProxyMapper;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.genersoft.iot.vmp.streamPush.dao.StreamPushMapper;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import com.genersoft.iot.vmp.vmanager.bean.ResourceBaseInfo;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(PageHelperAutoConfiguration.class)
@TestPropertySource(properties = "mybatis.configuration.map-underscore-to-camel-case=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoListScopeMapperTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceChannelMapper deviceChannelMapper;

    @Autowired
    private CommonGBChannelMapper commonGBChannelMapper;

    @Autowired
    private StreamProxyMapper streamProxyMapper;

    @Autowired
    private StreamPushMapper streamPushMapper;

    @Autowired
    private CloudRecordServiceMapper cloudRecordServiceMapper;

    @BeforeEach
    void loadSchemasAndFixtures() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.7.4-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        jdbcTemplate.execute("""
                alter table wvp_device_channel
                    add column if not exists enable_broadcast integer default 0
                """);
        jdbcTemplate.execute("""
                alter table wvp_device_channel
                    add column if not exists map_level integer default 0
                """);
        clearBusinessRows();
        insertBusinessFixtures();
        insertAuthorizationFixtures();
    }

    @Test
    void customScopeFiltersEveryCoreListAtSqlLayer() {
        VideoScopeQuery scope = customScope();

        assertEquals(Set.of(11, 22), deviceIds(
                deviceMapper.getDeviceList(1, null, null, scope)));
        assertEquals(Set.of(101, 102, 201), channelIds(
                deviceChannelMapper.queryChannels(
                        null, null, null, null, null, null,
                        null, null, null, null, scope)));
        assertEquals(Set.of(101, 102, 201), extendedChannelIds(
                deviceChannelMapper.queryChannelsWithDeviceInfo(
                        null, null, null, null, null, null, scope)));
        assertEquals(Set.of(101, 102, 201, 301, 302, 303), commonChannelIds(
                commonGBChannelMapper.queryList(
                        null, null, null, null, null, null, scope)));
        assertEquals(Set.of(41, 43), proxyIds(
                streamProxyMapper.selectAll(null, null, null, scope)));
        assertEquals(Set.of(42), pushIds(
                streamPushMapper.selectAll(null, null, null, scope)));
    }

    @Test
    void allAndNoneScopesRemainExplicitAndInternalMaintenanceStaysUnscoped() {
        VideoScopeQuery all = allScope();
        VideoScopeQuery none = noneScope();

        assertEquals(Set.of(11, 22, 33), deviceIds(
                deviceMapper.getDeviceList(1, null, null, all)));
        assertTrue(deviceMapper.getDeviceList(1, null, null, none).isEmpty());
        assertTrue(deviceChannelMapper.queryChannels(
                null, null, null, null, null, null,
                null, null, null, null, none).isEmpty());
        assertTrue(commonGBChannelMapper.queryList(
                null, null, null, null, null, null, none).isEmpty());
        assertTrue(streamProxyMapper.selectAll(null, null, null, none).isEmpty());
        assertTrue(streamPushMapper.selectAll(null, null, null, none).isEmpty());

        assertEquals(Set.of(42, 44), pushIds(
                streamPushMapper.selectAllForInternalMaintenance(null, null, null)));
    }

    @Test
    void pageHelperTotalIsCalculatedAfterScopePredicate() {
        PageHelper.startPage(1, 1);
        List<Device> page = deviceMapper.getDeviceList(1, null, null, customScope());
        PageInfo<Device> pageInfo = new PageInfo<>(page);

        assertEquals(1, page.size());
        assertEquals(2, pageInfo.getTotal());
    }

    @Test
    void overviewCountsMatchTheAuthorizedListSets() {
        jdbcTemplate.update("update wvp_device set on_line = false");
        jdbcTemplate.update("update wvp_device set on_line = true where id in (11, 33)");
        jdbcTemplate.update("update wvp_device_channel set status = 'OFF'");
        jdbcTemplate.update("""
                update wvp_device_channel
                   set status = 'ON'
                 where id in (101, 201, 302, 303, 304)
                """);
        jdbcTemplate.update("update wvp_stream_proxy set pulling = false");
        jdbcTemplate.update("update wvp_stream_proxy set pulling = true where id in (41, 45)");
        jdbcTemplate.update("update wvp_stream_push set pushing = true");

        VideoScopeQuery scope = customScope();
        assertOverview(deviceMapper.getOverview(scope), 2, 1);
        assertOverview(deviceChannelMapper.getOverview(scope), 6, 4);
        assertOverview(streamProxyMapper.getOverview(scope), 2, 1);
        assertOverview(streamPushMapper.getOverview(scope), 1, 1);

        assertOverview(deviceMapper.getOverview(noneScope()), 0, 0);
        assertOverview(deviceChannelMapper.getOverview(noneScope()), 0, 0);
        assertOverview(streamProxyMapper.getOverview(noneScope()), 0, 0);
        assertOverview(streamPushMapper.getOverview(noneScope()), 0, 0);
    }

    @Test
    void cloudRecordListAndExportUseTheSameAuthorizedSet() {
        VideoScopeQuery scope = customScope();

        List<CloudRecordItem> records = cloudRecordServiceMapper.getList(
                null, null, null, null, null,
                null, null, null, null, scope);
        List<String> exportPaths = cloudRecordServiceMapper.queryRecordFilePathList(
                null, null, null, null, null, null, scope);

        assertEquals(Set.of(501, 503), records.stream()
                .map(CloudRecordItem::getId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of("/record/501.mp4", "/record/503.mp4"),
                Set.copyOf(exportPaths));
        assertEquals(records.size(), exportPaths.size());
    }

    @Test
    void cloudRecordInsertAndBackfillMapperPersistStableSourceAtomically() {
        CloudRecordItem item = new CloudRecordItem();
        item.setApp("rtp");
        item.setStream("new-record");
        item.setStartTime(1000);
        item.setEndTime(2000);
        item.setFileSize(10);
        item.setTimeLen(1.0);
        item.setSourceType("CHANNEL");
        item.setSourceId(101L);
        item.setChannelId(101L);

        assertEquals(1, cloudRecordServiceMapper.add(item));
        assertEquals("CHANNEL:101:101", jdbcTemplate.queryForObject("""
                select source_type || ':' || source_id || ':' || channel_id
                  from wvp_cloud_record
                 where app = 'rtp' and stream = 'new-record'
                """, String.class));

        assertEquals(List.of(505), cloudRecordServiceMapper.findLegacyUnbound(10)
                .stream().map(CloudRecordItem::getId).toList());
        assertEquals(1, cloudRecordServiceMapper.bindLegacyRecord(
                505, "CHANNEL", 101, 101, "BOUND_CALL_ID"));
        assertEquals(0, cloudRecordServiceMapper.bindLegacyRecord(
                505, "CHANNEL", 102, 102, "BOUND_APP_STREAM"));
        assertEquals("CHANNEL:101:101:BOUND_CALL_ID", jdbcTemplate.queryForObject("""
                select source_type || ':' || source_id || ':' || channel_id
                       || ':' || source_reason
                  from wvp_cloud_record
                 where id = 505
                """, String.class));
    }

    private VideoScopeQuery customScope() {
        return new VideoScopeQuery(
                false,
                Set.of(900L),
                Set.of(
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 11L),
                        new VideoResourceRef(VideoResourceType.STREAM_PROXY, 41L),
                        new VideoResourceRef(VideoResourceType.STREAM_PUSH, 42L)),
                Set.of(201L, 303L),
                "video:channel:list",
                12L);
    }

    private VideoScopeQuery allScope() {
        return new VideoScopeQuery(
                true, Set.of(), Set.of(), Set.of(), "video:channel:list", 12L);
    }

    private VideoScopeQuery noneScope() {
        return new VideoScopeQuery(
                false, Set.of(), Set.of(), Set.of(), "video:channel:list", 12L);
    }

    private Set<Integer> deviceIds(List<Device> devices) {
        return devices.stream().map(Device::getId).collect(Collectors.toSet());
    }

    private Set<Integer> channelIds(List<DeviceChannel> channels) {
        return channels.stream().map(DeviceChannel::getId).collect(Collectors.toSet());
    }

    private Set<Integer> extendedChannelIds(
            List<com.genersoft.iot.vmp.web.gb28181.dto.DeviceChannelExtend> channels) {
        return channels.stream()
                .map(com.genersoft.iot.vmp.web.gb28181.dto.DeviceChannelExtend::getId)
                .collect(Collectors.toSet());
    }

    private Set<Integer> commonChannelIds(List<CommonGBChannel> channels) {
        return channels.stream().map(CommonGBChannel::getGbId).collect(Collectors.toSet());
    }

    private Set<Integer> proxyIds(List<StreamProxy> proxies) {
        return proxies.stream().map(StreamProxy::getId).collect(Collectors.toSet());
    }

    private Set<Integer> pushIds(List<StreamPush> pushes) {
        return pushes.stream().map(StreamPush::getId).collect(Collectors.toSet());
    }

    private void clearBusinessRows() {
        jdbcTemplate.update("delete from wvp_cloud_record");
        jdbcTemplate.update("delete from wvp_stream_push");
        jdbcTemplate.update("delete from wvp_stream_proxy");
        jdbcTemplate.update("delete from wvp_device_channel");
        jdbcTemplate.update("delete from wvp_device");
    }

    private void insertBusinessFixtures() {
        jdbcTemplate.update("""
                insert into wvp_device (id, device_id, name, create_time)
                values
                    (11, 'device-a', 'Device A', '2026-01-01'),
                    (22, 'device-b', 'Device B', '2026-01-02'),
                    (33, 'device-c', 'Device C', '2026-01-03')
                """);
        jdbcTemplate.update("""
                insert into wvp_stream_proxy (id, app, stream, create_time)
                values
                    (41, 'proxy-app-a', 'proxy-stream-a', '2026-01-01'),
                    (43, 'proxy-app-b', 'proxy-stream-b', '2026-01-02'),
                    (45, 'proxy-app-c', 'proxy-stream-c', '2026-01-03')
                """);
        jdbcTemplate.update("""
                insert into wvp_stream_push (id, app, stream, create_time)
                values
                    (42, 'push-app-a', 'push-stream-a', '2026-01-01'),
                    (44, 'push-app-b', 'push-stream-b', '2026-01-02')
                """);
        jdbcTemplate.update("""
                insert into wvp_device_channel
                    (id, data_type, data_device_id, device_id, name, status,
                     create_time, update_time)
                values
                    (101, 1, 11, 'gb-a-1', 'GB A1', 'ON', '2026-01-01', '2026-01-01'),
                    (102, 1, 11, 'gb-a-2', 'GB A2', 'ON', '2026-01-02', '2026-01-02'),
                    (201, 1, 22, 'gb-b-1', 'GB B1', 'ON', '2026-01-03', '2026-01-03'),
                    (202, 1, 22, 'gb-b-2', 'GB B2', 'ON', '2026-01-04', '2026-01-04'),
                    (203, 1, 33, 'gb-c-1', 'GB C1', 'ON', '2026-01-05', '2026-01-05'),
                    (301, 3, 41, 'proxy-a', 'Proxy A', 'ON', '2026-01-06', '2026-01-06'),
                    (303, 3, 43, 'proxy-b-1', 'Proxy B1', 'ON', '2026-01-07', '2026-01-07'),
                    (305, 3, 43, 'proxy-b-2', 'Proxy B2', 'ON', '2026-01-08', '2026-01-08'),
                    (304, 3, 45, 'proxy-c', 'Proxy C', 'ON', '2026-01-09', '2026-01-09'),
                    (302, 2, 42, 'push-a', 'Push A', 'ON', '2026-01-10', '2026-01-10'),
                    (306, 2, 44, 'push-b', 'Push B', 'ON', '2026-01-11', '2026-01-11')
                """);
        jdbcTemplate.update("""
                insert into wvp_cloud_record
                    (id, app, stream, call_id, start_time, end_time, file_path,
                     source_type, source_id, channel_id)
                values
                    (501, 'rtp', 'record-a', 'call-a', 1000, 2000, '/record/501.mp4',
                     'CHANNEL', 101, 101),
                    (502, 'rtp', 'record-b', 'call-b', 1000, 2000, '/record/502.mp4',
                     'CHANNEL', 202, 202),
                    (503, 'live', 'proxy-a', 'call-c', 1000, 2000, '/record/503.mp4',
                     'STREAM_PROXY', 41, 301),
                    (504, 'live', 'proxy-c', 'call-d', 1000, 2000, '/record/504.mp4',
                     'STREAM_PROXY', 45, 304),
                    (505, 'legacy', 'unbound', 'call-legacy', 1000, 2000,
                     '/record/505.mp4', 'LEGACY_UNBOUND', null, null)
                """);
    }

    private void insertAuthorizationFixtures() {
        jdbcTemplate.update("""
                insert into wvp_role
                    (id, name, role_key, role_sort, dept_data_scope,
                     video_scope_type, status, system_role)
                values (900, 'Scoped viewer', 'scoped_viewer', 1, 'NONE', 'CUSTOM', 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, order_num, status)
                values
                    (901, 0, 'TEST_PARENT', 'Test parent', 1, 0),
                    (902, 901, 'TEST_CHILD', 'Test child', 1, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure
                    (ancestor_id, descendant_id, depth)
                values (901, 901, 0), (902, 902, 0), (901, 902, 1)
                """);
        jdbcTemplate.update("""
                insert into wvp_role_resource_group (role_id, resource_group_id)
                values (900, 901)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (group_id, resource_type, resource_id)
                values
                    (902, 'GB_DEVICE', 11),
                    (902, 'CHANNEL', 201),
                    (902, 'CHANNEL', 303),
                    (902, 'STREAM_PROXY', 41),
                    (902, 'STREAM_PUSH', 42)
                """);
    }

    private void assertOverview(
            ResourceBaseInfo overview,
            int total,
            int online) {
        assertEquals(total, overview.getTotal());
        assertEquals(online, overview.getOnline());
    }
}
