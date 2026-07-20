package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.impl.DefaultVideoResourceMigrationIssueRecorder;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DefaultVideoResourceMigrationIssueRecorder.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoResourceMigrationIssueRecorderTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VideoResourceMigrationIssueRecorder recorder;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
    }

    @Test
    void persistsAmbiguousMigrationAsInternalManualHandlingAudit() {
        VideoResourceRef source =
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, 32);
        recorder.record(
                source,
                Set.of(
                        new VideoResourceRef(VideoResourceType.JT_CHANNEL, 33),
                        new VideoResourceRef(VideoResourceType.JT_CHANNEL, 34)),
                "jt:13800138000:channel:2",
                7L,
                "RESOURCE_GRANT_MIGRATION_AMBIGUOUS");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select principal_type, principal_id, permission_code,
                       resource_type, resource_id, decision,
                       reason_code, request_id
                  from wvp_authz_audit
                """);
        assertEquals("INTERNAL", row.get("PRINCIPAL_TYPE"));
        assertEquals(7L, ((Number) row.get("PRINCIPAL_ID")).longValue());
        assertEquals(
                "internal:video-resource:migrate",
                row.get("PERMISSION_CODE"));
        assertEquals("JT_CHANNEL", row.get("RESOURCE_TYPE"));
        assertEquals(32L, ((Number) row.get("RESOURCE_ID")).longValue());
        assertEquals("DENY", row.get("DECISION"));
        assertEquals(
                "RESOURCE_GRANT_MIGRATION_AMBIGUOUS",
                row.get("REASON_CODE"));
        assertEquals(
                "jt:13800138000:channel:2",
                row.get("REQUEST_ID"));
    }
}
