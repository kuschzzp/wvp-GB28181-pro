package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.storager.dao.StreamShareMapper;
import com.genersoft.iot.vmp.storager.dao.dto.StreamShare;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StreamShareMapperPersistenceTest {

    @Autowired private DataSource dataSource;
    @Autowired private StreamShareMapper shareMapper;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
    }

    @Test
    void consumesTheFinalViewAtomicallyAndPersistsEnumFields() {
        Instant now = Instant.parse("2026-07-15T09:00:00Z");
        StreamShare share = new StreamShare();
        share.setShareId("abcdefghijklmnopqrstuvwxyzABCDEF");
        share.setCreatorUserId(1L);
        share.setResourceType(VideoResourceType.CHANNEL);
        share.setResourceId(42L);
        share.setExpiresAt(now.plusSeconds(60));
        share.setMaxViews(1L);
        share.setViewCount(0);
        share.setPasscodeHash("{bcrypt}$2a$04$placeholder");
        share.setStatus(StreamShareStatus.ACTIVE);
        share.setAuthzVersion(7L);
        share.setCreateTime(now);
        share.setUpdateTime(now);

        assertEquals(1, shareMapper.insert(share));
        assertEquals(1, shareMapper.consume(share.getShareId(), now));
        assertEquals(0, shareMapper.consume(share.getShareId(), now));

        StreamShare stored = shareMapper.selectById(share.getShareId());
        assertNotNull(stored);
        assertEquals(VideoResourceType.CHANNEL, stored.getResourceType());
        assertEquals(StreamShareStatus.ACTIVE, stored.getStatus());
        assertEquals(1, stored.getViewCount());
        assertEquals(7L, stored.getAuthzVersion());
    }
}
