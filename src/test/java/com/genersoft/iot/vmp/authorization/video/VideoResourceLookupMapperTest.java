package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource;
import com.genersoft.iot.vmp.authorization.video.resolver.ChannelAuthorizationSource;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceLookupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoResourceLookupMapperTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VideoResourceLookupMapper mapper;

    @BeforeEach
    void loadSchemas() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.7.4-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        jdbcTemplate.execute("""
                create table if not exists wvp_jt_terminal (
                    id bigint primary key,
                    phone_number varchar(50)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists wvp_jt_channel (
                    id bigint primary key,
                    terminal_db_id bigint,
                    channel_id integer
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists wvp_alarm (
                    id bigint primary key,
                    channel_id bigint
                )
                """);
        clearBusinessRows();
        insertFixtures();
    }

    @Test
    void readsStableIdsAndDerivedSourcesAcrossBusinessTables() {
        assertEquals(11L, mapper.findGbDeviceId(11));
        assertEquals(List.of(11L), mapper.findGbDeviceIdsByDeviceId("device-a"));
        assertEquals(101L, mapper.findChannelId(101));
        assertEquals(31L, mapper.findJtTerminalId(31));
        assertEquals(32L, mapper.findJtChannelId(32));
        assertEquals(41L, mapper.findStreamProxyId(41));
        assertEquals(42L, mapper.findStreamPushId(42));

        assertEquals(List.of(101L),
                mapper.findGbChannelIds("device-a", "same-channel"));
        assertEquals(List.of(202L),
                mapper.findGbChannelIds("device-b", "same-channel"));
        assertEquals(List.of(101L),
                mapper.findUnifiedChannelIds("same-channel", "device-a"));
        assertEquals(List.of(101L, 202L),
                mapper.findUnifiedChannelIds("same-channel", null));
        assertEquals(List.of(404L),
                mapper.findUnifiedChannelIds("proxy-channel", null));
        assertEquals(41L,
                mapper.findStreamProxyIdByAppStream("proxy-app", "proxy-stream"));
        assertEquals(42L,
                mapper.findStreamPushIdByAppStream("push-app", "push-stream"));
        assertEquals(List.of(404L), mapper.findChannelIdsBySource(3, 41));
        assertEquals(List.of(405L), mapper.findChannelIdsBySource(2, 42));

        assertEquals(new ChannelAuthorizationSource(1, 11L, null),
                mapper.findChannelAuthorizationSource(101));
        assertEquals(new ChannelAuthorizationSource(200, 32L, 31L),
                mapper.findChannelAuthorizationSource(303));
        assertEquals(31L, mapper.findJtTerminalIdByChannelId(32));
        assertEquals(List.of(31L),
                mapper.findJtTerminalIdsByPhoneNumber("13800138000"));
        assertEquals(List.of(32L),
                mapper.findJtChannelIdsByPhoneNumberAndChannelId(
                        "13800138000", 1));

        assertEquals(new CloudRecordSource(51, "CHANNEL", 101L, 101L),
                mapper.findCloudRecordSource(51));
        assertEquals(101L, mapper.findAlarmChannelId(61));
        assertNull(mapper.findCloudRecordSource(999));
    }

    private void clearBusinessRows() {
        jdbcTemplate.update("delete from wvp_alarm");
        jdbcTemplate.update("delete from wvp_jt_channel");
        jdbcTemplate.update("delete from wvp_jt_terminal");
        jdbcTemplate.update("delete from wvp_cloud_record");
        jdbcTemplate.update("delete from wvp_stream_push");
        jdbcTemplate.update("delete from wvp_stream_proxy");
        jdbcTemplate.update("delete from wvp_device_channel");
        jdbcTemplate.update("delete from wvp_device");
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                insert into wvp_device (id, device_id)
                values (11, 'device-a'), (22, 'device-b')
                """);
        jdbcTemplate.update("""
                insert into wvp_device_channel
                    (id, data_type, data_device_id, device_id, create_time, update_time)
                values
                    (101, 1, 11, 'same-channel', 'now', 'now'),
                    (202, 1, 22, 'same-channel', 'now', 'now'),
                    (303, 200, 32, 'jt-channel', 'now', 'now'),
                    (404, 3, 41, 'proxy-channel', 'now', 'now'),
                    (405, 2, 42, 'push-channel', 'now', 'now')
                """);
        jdbcTemplate.update("""
                insert into wvp_jt_terminal (id, phone_number)
                values (31, '13800138000')
                """);
        jdbcTemplate.update("""
                insert into wvp_jt_channel (id, terminal_db_id, channel_id)
                values (32, 31, 1)
                """);
        jdbcTemplate.update("""
                insert into wvp_stream_proxy (id, app, stream)
                values (41, 'proxy-app', 'proxy-stream')
                """);
        jdbcTemplate.update("""
                insert into wvp_stream_push (id, app, stream)
                values (42, 'push-app', 'push-stream')
                """);
        jdbcTemplate.update("""
                insert into wvp_cloud_record
                    (id, app, stream, source_type, source_id, channel_id)
                values
                    (51, 'record-app', 'record-stream', 'CHANNEL', 101, 101)
                """);
        jdbcTemplate.update("insert into wvp_alarm (id, channel_id) values (61, 101)");
    }
}
