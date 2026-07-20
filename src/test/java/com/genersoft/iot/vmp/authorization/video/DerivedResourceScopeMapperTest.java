package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.MobilePosition;
import com.genersoft.iot.vmp.gb28181.dao.CommonGBChannelMapper;
import com.genersoft.iot.vmp.gb28181.dao.MobilePositionMapper;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.dao.JTChannelMapper;
import com.genersoft.iot.vmp.jt1078.dao.JTTerminalMapper;
import com.genersoft.iot.vmp.service.bean.Alarm;
import com.genersoft.iot.vmp.storager.dao.AlarmMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "mybatis.configuration.map-underscore-to-camel-case=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DerivedResourceScopeMapperTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JTTerminalMapper terminalMapper;
    @Autowired private JTChannelMapper jtChannelMapper;
    @Autowired private AlarmMapper alarmMapper;
    @Autowired private MobilePositionMapper positionMapper;
    @Autowired private CommonGBChannelMapper commonChannelMapper;

    @BeforeEach
    void loadSchemasAndFixtures() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.7.4-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        createDerivedTables();
        insertBusinessFixtures();
        insertAuthorizationFixtures();
    }

    @Test
    void jtTerminalGrantDynamicallyIncludesChannelsAndExplicitChannelStaysFineGrained() {
        VideoScopeQuery scope = customScope();

        assertEquals(Set.of(31, 32), terminalMapper.getDeviceList(
                        null, null, scope).stream()
                .map(JTDevice::getId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(311, 312), jtChannelMapper.selectAll(
                        31, null, scope).stream()
                .map(JTChannel::getId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(321), jtChannelMapper.selectAll(
                        32, null, scope).stream()
                .map(JTChannel::getId)
                .collect(Collectors.toSet()));
        assertTrue(jtChannelMapper.selectAll(33, null, scope).isEmpty());
    }

    @Test
    void alarmAndPositionTimeFiltersCannotEscapeChannelScope() {
        VideoScopeQuery scope = customScope();

        assertEquals(Set.of(1L, 2L), alarmMapper.getAlarms(
                        null, null, null, scope).stream()
                .map(Alarm::getId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(101, 201), positionMapper
                .queryPositionByDeviceIdAndTime(null, null, null, scope)
                .stream()
                .map(MobilePosition::getChannelId)
                .collect(Collectors.toSet()));
        assertEquals(List.of(201), positionMapper
                .queryPositionByDeviceIdAndTime(null, 150L, 250L, scope)
                .stream()
                .map(MobilePosition::getChannelId)
                .toList());
        assertEquals(201, positionMapper.queryLatestPosition(null, scope)
                .getChannelId());

        VideoScopeQuery none = noneScope();
        assertTrue(alarmMapper.getAlarms(null, null, null, none).isEmpty());
        assertTrue(positionMapper.queryPositionByDeviceIdAndTime(
                null, null, null, none).isEmpty());
        assertNull(positionMapper.queryLatestPosition(null, none));
    }

    @Test
    void recordPlanListAndBulkIdsCannotEscapeChannelScope() {
        VideoScopeQuery scope = customScope();

        assertEquals(Set.of(101, 201), commonChannelMapper
                .queryForRecordPlanForWebListScoped(
                        7, null, null, null, true, scope)
                .stream()
                .map(CommonGBChannel::getGbId)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(101, 201), Set.copyOf(commonChannelMapper
                .queryRecordPlanChannelIds(7, true, scope)));
        assertEquals(Set.of(101, 201), Set.copyOf(commonChannelMapper
                .queryRecordPlanChannelIds(null, null, scope)));

        VideoScopeQuery none = noneScope();
        assertTrue(commonChannelMapper.queryForRecordPlanForWebListScoped(
                7, null, null, null, true, none).isEmpty());
        assertTrue(commonChannelMapper.queryRecordPlanChannelIds(
                null, null, none).isEmpty());
    }

    private void createDerivedTables() {
        jdbcTemplate.execute("drop table if exists wvp_jt_channel");
        jdbcTemplate.execute("drop table if exists wvp_jt_terminal");
        jdbcTemplate.execute("drop table if exists wvp_alarm");
        jdbcTemplate.execute("drop table if exists wvp_mobile_position");
        jdbcTemplate.execute("""
                create table wvp_jt_terminal (
                    id integer primary key,
                    terminal_id varchar(64),
                    province_id varchar(32),
                    city_id varchar(32),
                    maker_id varchar(64),
                    phone_number varchar(64),
                    model varchar(64),
                    plate_no varchar(64),
                    status boolean,
                    create_time varchar(32)
                )
                """);
        jdbcTemplate.execute("""
                create table wvp_jt_channel (
                    id integer primary key,
                    terminal_db_id integer,
                    channel_id integer,
                    name varchar(64),
                    has_audio boolean,
                    create_time varchar(32),
                    update_time varchar(32)
                )
                """);
        jdbcTemplate.execute("""
                create table wvp_alarm (
                    id bigint primary key,
                    channel_id integer,
                    description varchar(255),
                    snap_path varchar(255),
                    record_path varchar(255),
                    longitude double precision,
                    latitude double precision,
                    alarm_type varchar(64),
                    alarm_time bigint
                )
                """);
        jdbcTemplate.execute("""
                create table wvp_mobile_position (
                    id bigint generated by default as identity primary key,
                    channel_id integer,
                    timestamp bigint,
                    longitude double precision,
                    latitude double precision,
                    altitude double precision,
                    speed double precision,
                    direction double precision,
                    create_time varchar(32)
                )
                """);
    }

    private void insertBusinessFixtures() {
        jdbcTemplate.update("delete from wvp_device_channel");
        jdbcTemplate.update("delete from wvp_device");
        jdbcTemplate.update("""
                insert into wvp_device (id, device_id, name, create_time)
                values
                    (11, 'device-a', 'Device A', '2026-01-01'),
                    (22, 'device-b', 'Device B', '2026-01-02')
                """);
        jdbcTemplate.update("""
                insert into wvp_jt_terminal
                    (id, terminal_id, phone_number, status, create_time)
                values
                    (31, 'terminal-a', 'phone-a', true, '2026-01-01'),
                    (32, 'terminal-b', 'phone-b', true, '2026-01-02'),
                    (33, 'terminal-c', 'phone-c', false, '2026-01-03')
                """);
        jdbcTemplate.update("""
                insert into wvp_jt_channel
                    (id, terminal_db_id, channel_id, name, has_audio,
                     create_time, update_time)
                values
                    (311, 31, 1, 'JT A1', true, '2026-01-01', '2026-01-01'),
                    (312, 31, 2, 'JT A2', true, '2026-01-02', '2026-01-02'),
                    (321, 32, 1, 'JT B1', false, '2026-01-03', '2026-01-03'),
                    (331, 33, 1, 'JT C1', false, '2026-01-04', '2026-01-04')
                """);
        jdbcTemplate.update("""
                insert into wvp_device_channel
                    (id, data_type, data_device_id, device_id, name, status,
                     record_plan_id, create_time, update_time)
                values
                    (101, 1, 11, 'gb-a-1', 'GB A1', 'ON', 7, '2026-01-01', '2026-01-01'),
                    (201, 1, 22, 'gb-b-1', 'GB B1', 'ON', 7, '2026-01-02', '2026-01-02'),
                    (202, 1, 22, 'gb-b-2', 'GB B2', 'ON', 7, '2026-01-03', '2026-01-03'),
                    (311, 200, 311, 'jt-a-1', 'JT A1', 'ON', 7, '2026-01-04', '2026-01-04'),
                    (312, 200, 312, 'jt-a-2', 'JT A2', 'ON', 7, '2026-01-05', '2026-01-05'),
                    (321, 200, 321, 'jt-b-1', 'JT B1', 'ON', 7, '2026-01-06', '2026-01-06'),
                    (331, 200, 331, 'jt-c-1', 'JT C1', 'ON', 7, '2026-01-07', '2026-01-07')
                """);
        jdbcTemplate.update("""
                insert into wvp_alarm
                    (id, channel_id, description, alarm_type, alarm_time)
                values
                    (1, 101, 'authorized by device', 'VideoLoss', 100),
                    (2, 201, 'authorized explicitly', 'VideoLoss', 200),
                    (3, 202, 'not authorized', 'VideoLoss', 300)
                """);
        jdbcTemplate.update("""
                insert into wvp_mobile_position
                    (channel_id, timestamp, longitude, latitude, altitude,
                     speed, direction, create_time)
                values
                    (101, 100, 1, 1, 0, 0, 0, '2026-01-01'),
                    (201, 200, 2, 2, 0, 0, 0, '2026-01-02'),
                    (202, 300, 3, 3, 0, 0, 0, '2026-01-03')
                """);
    }

    private void insertAuthorizationFixtures() {
        jdbcTemplate.update("""
                insert into wvp_role
                    (id, name, role_key, role_sort, dept_data_scope,
                     video_scope_type, status, system_role)
                values
                    (900, 'Derived viewer', 'derived_viewer', 1,
                     'NONE', 'CUSTOM', 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, order_num, status)
                values
                    (901, 0, 'DERIVED_PARENT', 'Derived parent', 1, 0),
                    (902, 901, 'DERIVED_CHILD', 'Derived child', 1, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure
                    (ancestor_id, descendant_id, depth)
                values
                    (901, 901, 0),
                    (902, 902, 0),
                    (901, 902, 1)
                """);
        jdbcTemplate.update("""
                insert into wvp_role_resource_group (role_id, resource_group_id)
                values (900, 901)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (group_id, resource_type, resource_id)
                values
                    (902, 'JT_TERMINAL', 31),
                    (902, 'JT_CHANNEL', 321),
                    (902, 'GB_DEVICE', 11),
                    (902, 'CHANNEL', 201)
                """);
    }

    private VideoScopeQuery customScope() {
        return new VideoScopeQuery(
                false,
                Set.of(900L),
                Set.of(
                        new VideoResourceRef(VideoResourceType.JT_TERMINAL, 31),
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 11)),
                Set.of(201L, 321L),
                "video:position:list",
                12L,
                false);
    }

    private VideoScopeQuery noneScope() {
        return new VideoScopeQuery(
                false, Set.of(), Set.of(), Set.of(),
                "video:position:list", 12L, false);
    }
}
