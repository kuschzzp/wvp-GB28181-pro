package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.service.TokenVersionService;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(VideoResourceLifecycleServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoResourceLifecycleServiceTest {

    private static final VideoResourceRef TERMINAL =
            new VideoResourceRef(VideoResourceType.JT_TERMINAL, 31);
    private static final VideoResourceRef JT_CHANNEL =
            new VideoResourceRef(VideoResourceType.JT_CHANNEL, 32);
    private static final VideoResourceRef UNIFIED_CHANNEL =
            new VideoResourceRef(VideoResourceType.CHANNEL, 303);
    private static final VideoResourceRef PROXY =
            new VideoResourceRef(VideoResourceType.STREAM_PROXY, 41);
    private static final VideoResourceRef PUSH =
            new VideoResourceRef(VideoResourceType.STREAM_PUSH, 42);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VideoResourceLifecycleService service;

    @MockitoBean
    private TokenVersionService tokenVersionService;

    @MockitoBean
    private ResourceResolverRegistry resolverRegistry;

    @MockitoBean
    private VideoResourceMigrationIssueRecorder issueRecorder;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, status)
                values (10, 1, 'LIFECYCLE', '生命周期测试', 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure
                    (ancestor_id, descendant_id, depth)
                values (1, 10, 1), (10, 10, 0)
                """);
        jdbcTemplate.update("""
                update wvp_role
                   set video_scope_type = 'CUSTOM'
                 where id = 99
                """);
        jdbcTemplate.update("""
                insert into wvp_role_resource_group
                    (role_id, resource_group_id)
                values (99, 10)
                """);
        for (VideoResourceRef resource : List.of(
                TERMINAL, JT_CHANNEL, UNIFIED_CHANNEL, PROXY, PUSH)) {
            insertItem(resource);
        }
    }

    @Test
    void removesAllRequestedRelationsAndBumpsAffectedUsersOnce() {
        service.removeAll(Set.of(TERMINAL, JT_CHANNEL, UNIFIED_CHANNEL));

        assertEquals(0, itemCount(TERMINAL));
        assertEquals(0, itemCount(JT_CHANNEL));
        assertEquals(0, itemCount(UNIFIED_CHANNEL));
        assertEquals(1, itemCount(PROXY));
        assertEquals(1, itemCount(PUSH));
        verify(tokenVersionService).bumpUsers(
                Set.of(99L), "VIDEO_RESOURCE_REMOVED");
    }

    @Test
    void rollsBackRelationCleanupWhenAuthorizationVersionUpdateFails() {
        doThrow(new IllegalStateException("token version failed"))
                .when(tokenVersionService)
                .bumpUsers(Set.of(99L), "VIDEO_RESOURCE_REMOVED");

        assertThrows(
                IllegalStateException.class,
                () -> service.remove(PROXY));

        assertEquals(1, itemCount(PROXY));
    }

    @Test
    void migratesSameTypeStableIdWithoutDuplicatingGroupMembership() {
        VideoResourceRef replacement =
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, 33);
        insertItem(replacement);
        when(resolverRegistry.requireExisting(replacement))
                .thenReturn(replacement);

        service.migrate(JT_CHANNEL, replacement, 7L);

        assertEquals(0, itemCount(JT_CHANNEL));
        assertEquals(1, itemCount(replacement));
        verify(resolverRegistry).requireExisting(replacement);
        verify(tokenVersionService).bumpUsers(
                Set.of(99L), "VIDEO_RESOURCE_ID_MIGRATED");
    }

    @Test
    void rejectsCrossTypeMigrationWithoutChangingRelations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.migrate(JT_CHANNEL, TERMINAL, 7L));

        assertEquals(1, itemCount(JT_CHANNEL));
        assertEquals(1, itemCount(TERMINAL));
        verify(resolverRegistry, never()).requireExisting(TERMINAL);
        verify(tokenVersionService, never())
                .bumpUsers(Set.of(99L), "VIDEO_RESOURCE_ID_MIGRATED");
    }

    @Test
    void ambiguousStableSourceStopsAndRecordsManualHandlingWithoutExpandingScope() {
        VideoResourceRef first =
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, 33);
        VideoResourceRef second =
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, 34);

        assertThrows(
                VideoResourceMigrationRequiredException.class,
                () -> service.migrateUnique(
                        JT_CHANNEL,
                        Set.of(first, second),
                        "jt:13800138000:channel:2",
                        7L));

        assertEquals(1, itemCount(JT_CHANNEL));
        assertEquals(0, itemCount(first));
        assertEquals(0, itemCount(second));
        assertEquals(1, itemCount(TERMINAL));
        verify(issueRecorder).record(
                JT_CHANNEL,
                Set.of(first, second),
                "jt:13800138000:channel:2",
                7L,
                "RESOURCE_GRANT_MIGRATION_AMBIGUOUS");
        verify(resolverRegistry, never()).requireExisting(first);
        verify(resolverRegistry, never()).requireExisting(second);
        verify(tokenVersionService, never())
                .bumpUsers(Set.of(99L), "VIDEO_RESOURCE_ID_MIGRATED");
    }

    private void insertItem(VideoResourceRef resource) {
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (group_id, resource_type, resource_id)
                values (?, ?, ?)
                """, 10, resource.type().name(), resource.id());
    }

    private int itemCount(VideoResourceRef resource) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                  from wvp_resource_group_item
                 where resource_type = ? and resource_id = ?
                """, Integer.class, resource.type().name(), resource.id());
    }
}
