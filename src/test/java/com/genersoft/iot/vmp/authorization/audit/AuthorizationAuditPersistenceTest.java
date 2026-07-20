package com.genersoft.iot.vmp.authorization.audit;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditSummary;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorizationAuditPersistenceTest {

    @Autowired private DataSource dataSource;
    @Autowired private AuthzAuditMapper auditMapper;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"))
                .execute(dataSource);
    }

    @Test
    void queryUsesStoredDepartmentSnapshotAndHidesDepartmentlessRecords() {
        insert(PrincipalType.USER, 20L, 100L, "system:user:edit", 1);
        insert(PrincipalType.USER, 21L, 200L, "system:user:edit", 2);
        insert(PrincipalType.USER, 12L, 999L, "system:user:list", 3);
        insert(PrincipalType.INTERNAL, 1L, null, "operations:system:view", 4);

        DepartmentScope scope = new DepartmentScope(false, Set.of(100L), true, 12L);
        List<AuthorizationAuditSummary> scoped = auditMapper.query(query(), scope);

        assertEquals(Set.of(12L, 20L),
                scoped.stream().map(AuthorizationAuditSummary::principalId).collect(
                        java.util.stream.Collectors.toSet()));
        assertEquals(4, auditMapper.query(query(), DepartmentScope.all(12L)).size());
    }

    private void insert(
            PrincipalType principalType,
            long principalId,
            Long principalDeptId,
            String permissionCode,
            int minute) {
        auditMapper.insert(new AuthzAuditRecord(
                principalType,
                principalId,
                principalDeptId,
                permissionCode,
                null,
                null,
                true,
                "PERMISSION_GRANTED",
                "request-" + minute,
                "127.0.0.1",
                LocalDateTime.of(2026, 7, 14, 10, minute)));
    }

    private AuthorizationAuditQuery query() {
        return new AuthorizationAuditQuery(
                1, 20, null, null, null, null, null, null, null, null);
    }
}
