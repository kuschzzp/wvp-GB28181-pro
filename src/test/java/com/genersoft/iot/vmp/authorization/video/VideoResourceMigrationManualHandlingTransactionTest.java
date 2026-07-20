package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.video.impl.DefaultVideoResourceMigrationIssueRecorder;
import com.genersoft.iot.vmp.authorization.video.impl.VideoResourceLifecycleServiceImpl;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        VideoResourceLifecycleServiceImpl.class,
        DefaultVideoResourceMigrationIssueRecorder.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoResourceMigrationManualHandlingTransactionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VideoResourceLifecycleService lifecycleService;

    @MockitoBean
    private TokenVersionService tokenVersionService;

    @MockitoBean
    private ResourceResolverRegistry resolverRegistry;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, status)
                values (10, 1, 'MIGRATION_TX', '迁移事务测试', 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure
                    (ancestor_id, descendant_id, depth)
                values (1, 10, 1), (10, 10, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (group_id, resource_type, resource_id)
                values (10, 'JT_CHANNEL', 32)
                """);
    }

    @Test
    void manualHandlingAuditSurvivesStoppedOuterMigrationTransaction() {
        VideoResourceRef source =
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, 32);

        assertThrows(
                VideoResourceMigrationRequiredException.class,
                () -> lifecycleService.migrateUnique(
                        source,
                        Set.of(
                                new VideoResourceRef(
                                        VideoResourceType.JT_CHANNEL, 33),
                                new VideoResourceRef(
                                        VideoResourceType.JT_CHANNEL, 34)),
                        "jt:13800138000:channel:2",
                        null));

        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                  from wvp_resource_group_item
                 where resource_type = 'JT_CHANNEL'
                   and resource_id = 32
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                  from wvp_authz_audit
                 where reason_code = 'RESOURCE_GRANT_MIGRATION_AMBIGUOUS'
                   and resource_type = 'JT_CHANNEL'
                   and resource_id = 32
                """, Integer.class));
    }
}
