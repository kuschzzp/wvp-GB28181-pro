package com.genersoft.iot.vmp.authorization.audit;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.storager.dao.provider.AuthzAuditProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthzAuditProviderTest {

    private final AuthzAuditProvider provider = new AuthzAuditProvider();

    @Test
    void scopedQueryUsesDepartmentSnapshotAndNeverJoinsCurrentUserDepartment() {
        String sql = provider.query(Map.of(
                "query", query(),
                "scope", new DepartmentScope(false, Set.of(100L, 101L), true, 12L)));

        assertTrue(sql.contains("a.principal_dept_id in"));
        assertTrue(sql.contains("collection=\"scope.deptIds\""));
        assertTrue(sql.contains("a.principal_type = 'USER'"));
        assertTrue(sql.contains("a.principal_id = #{scope.userId}"));
        assertFalse(sql.toLowerCase().contains("join wvp_user"));
    }

    @Test
    void emptyScopeIsFailClosedAndAllScopeCanSeeDepartmentlessSystemRecords() {
        String noneSql = provider.query(Map.of(
                "query", query(),
                "scope", DepartmentScope.none(12L)));
        String allSql = provider.query(Map.of(
                "query", query(),
                "scope", DepartmentScope.all(12L)));

        assertTrue(noneSql.contains("and 1 = 0"));
        assertFalse(allSql.contains("principal_dept_id in"));
        assertFalse(allSql.contains("principal_dept_id is null"));
    }

    @Test
    void optionalFiltersRemainBoundParameters() {
        String sql = provider.query(Map.of(
                "query", new AuthorizationAuditQuery(
                        1,
                        20,
                        "system:user:edit",
                        "deny",
                        "user",
                        20L,
                        "USER",
                        20L,
                        LocalDateTime.of(2026, 7, 1, 0, 0),
                        LocalDateTime.of(2026, 7, 31, 23, 59)),
                "scope", DepartmentScope.all(12L)));

        assertTrue(sql.contains("#{query.permissionCode}"));
        assertTrue(sql.contains("#{query.decision}"));
        assertTrue(sql.contains("#{query.startTime}"));
        assertTrue(sql.contains("#{query.endTime}"));
        assertFalse(sql.contains("system:user:edit"));
    }

    private AuthorizationAuditQuery query() {
        return new AuthorizationAuditQuery(
                1, 20, null, null, null, null, null, null, null, null);
    }
}
